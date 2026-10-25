/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.TileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a driver's render target into an image the viewer can draw; one per driver and platform.
 */
interface TileImages<I : Any> {

    fun newTarget(sizePx: Int): RenderTarget

    /**
     * Takes the tile out of a target the renderer reported as drawn.
     */
    fun take(target: RenderTarget): I

    fun release(image: I)
}

/**
 * Draws the tiles a screen shows with one renderer, in the background, and keeps the most recently
 * used ones. A tile that leaves the screen before its render starts is skipped through the
 * renderer's cancel check. Owns the renderer; a different renderer needs a new engine.
 *
 * @param threads parallel renders, capped by the renderer's own limit
 * @param capacity kept tiles; those on screen stay even above it
 * @param onChange called on a render thread once a tile or an error arrives
 */
class TileEngine<I : Any>(
    private val renderer: MapRenderer,
    private val images: TileImages<I>,
    threads: Int,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val onChange: () -> Unit,
) : Closeable {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(threads.coerceIn(1, renderer.maxParallelism)),
    )

    private val running = ConcurrentHashMap<TileRequest, Job>()

    // tiles that came back empty or failed; not tried again until cleared
    private val settled = ConcurrentHashMap.newKeySet<TileRequest>()

    // access order, so eviction drops the least recently drawn; guarded by itself
    private val ready = LinkedHashMap<TileRequest, I>(64, 0.75f, true)

    @Volatile
    private var onScreen: Set<TileRequest> = emptySet()

    @Volatile
    private var closed = false

    val tileSizePx: Int
        get() = renderer.key.config.tileSizePx

    val stats = RenderStats()

    /**
     * Last render failure, for the user to see.
     */
    @Volatile
    var error: String? = null
        private set

    init {
        require(capacity > 0) {
            "TileEngine(), capacity $capacity"
        }
    }

    /**
     * The kept image of `tile`; none once the engine is closing.
     */
    operator fun get(tile: TileRequest): I? {
        synchronized(ready) {
            if (closed) {
                return null
            }
            return ready[tile]
        }
    }

    /**
     * Sets what the screen shows: starts the missing tiles, lets the others stop, and evicts kept
     * tiles off screen above the capacity. A tile that came back empty or failed waits for
     * [clear]. Call from the thread that draws the images.
     */
    fun show(tiles: List<TileRequest>) {
        onScreen = tiles.toHashSet()
        for (tile in tiles) {
            start(tile)
        }
        evict(capacity = capacity)
    }

    /**
     * Forgets every kept tile, timing and error, so the screen draws again from scratch.
     */
    fun clear() {
        evict(capacity = 0)
        settled.clear()
        stats.reset()
        error = null
    }

    /**
     * Closes the renderer, whose close may wait for renders in progress, and releases the kept
     * tiles.
     */
    override fun close() {
        closed = true
        scope.cancel()
        try {
            renderer.close()
        } finally {
            clear()
        }
    }

    /**
     * Closes on a background thread, so a caller on the UI thread never waits for renders in
     * progress; `then` runs once closed. Tiles stop arriving at once.
     */
    fun closeLater(then: () -> Unit = {}) {
        closed = true
        CoroutineScope(Dispatchers.IO).launch {
            // the engine is gone, so no one is left to show a failure; an unhandled one would
            // end the process
            runCatching {
                try {
                    close()
                } finally {
                    then()
                }
            }
        }
    }

    private fun start(tile: TileRequest) {
        // running first: once its entry is gone, the job's ready and settled writes are visible
        if (running.containsKey(tile)
            || get(tile) != null
            || tile in settled
        ) {
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            draw(tile)
        }
        if (running.putIfAbsent(tile, job) == null) {
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun draw(tile: TileRequest) {
        var result: RenderResult? = null
        try {
            // a render that finished between show's checks and this start already handled the tile
            if (get(tile) != null
                || tile in settled
            ) {
                return
            }
            val target = images.newTarget(tileSizePx)
            val start = System.nanoTime()
            result = renderer.render(tile, target) {
                closed || tile !in onScreen
            }
            if (result == RenderResult.EMPTY) {
                settled.add(tile)
            }
            if (result != RenderResult.DRAWN) {
                return
            }
            stats.add(System.nanoTime() - start)
            val image = images.take(target)
            synchronized(ready) {
                if (closed) {
                    images.release(image)
                    return
                }
                ready.put(tile, image)?.let(images::release)
            }
            onChange()
        } catch (e: Exception) {
            settled.add(tile)
            if (!closed) {
                error = "draw(${tile.zoom}/${tile.x}/${tile.y}), ${e.javaClass.simpleName}: ${e.message}"
                onChange()
            }
        } finally {
            running.remove(tile)
            // back on screen while it was stopping: nothing else would start it again
            if (result == RenderResult.CANCELLED
                && !closed
                && tile in onScreen
            ) {
                start(tile)
            }
        }
    }

    private fun evict(capacity: Int) {
        val keep = if (capacity == 0) emptySet() else onScreen
        synchronized(ready) {
            val entries = ready.entries.iterator()
            while (ready.size > capacity && entries.hasNext()) {
                val entry = entries.next()
                if (entry.key !in keep) {
                    entries.remove()
                    images.release(entry.value)
                }
            }
        }
    }

    companion object {

        const val DEFAULT_CAPACITY = 400
    }
}

/**
 * Render times of an engine's tiles; the median and p95 come from the latest samples.
 */
class RenderStats {

    private val latest = LongArray(WINDOW)

    private var count = 0L

    private var totalNs = 0L

    private var maxNs = 0L

    @Synchronized
    fun add(ns: Long) {
        latest[(count % WINDOW).toInt()] = ns
        count++
        totalNs += ns
        maxNs = maxOf(maxNs, ns)
    }

    @Synchronized
    fun reset() {
        count = 0
        totalNs = 0
        maxNs = 0
    }

    @Synchronized
    fun summary(): String {
        if (count == 0L) {
            return "no tiles yet"
        }
        val window = latest.copyOf(minOf(count, WINDOW.toLong()).toInt())
        window.sort()
        return "%d tiles, mean %.1f ms, median %.1f, p95 %.1f, max %.1f".format(
            count,
            totalNs / count / 1e6,
            window[window.size / 2] / 1e6,
            window[(window.size * 95 / 100).coerceAtMost(window.size - 1)] / 1e6,
            maxNs / 1e6,
        )
    }

    private companion object {

        const val WINDOW = 1000
    }
}
