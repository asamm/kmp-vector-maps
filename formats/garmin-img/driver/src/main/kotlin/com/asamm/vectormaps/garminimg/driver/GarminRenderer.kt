/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.render.ImgRenderer
import com.asamm.vectormaps.garminimg.render.RenderTarget
import com.asamm.vectormaps.garminimg.render.Viewport
import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader
import com.asamm.vectormaps.garminimg.render.StyleResolver
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import com.asamm.vectormaps.core.RenderTarget as CoreRenderTarget

/**
 * Renders all maps of its files through the engine's `ImgRenderer` into [GarminTileTarget]s, up to
 * one tile per core at once: each worker reads the shared parsed maps through its own readers and
 * resolves styles with its own caches. A worker opens a file on the first tile that reads one of
 * its maps, then holds 3–4 handles for it, shared by the file's maps, until [close]: a renderer
 * holds up to about 4 × files rendered × [maxParallelism] handles. Labels stop at the tile edge
 * until M6.1. [close] waits for running renders, so it must not be called from inside [render].
 */
class GarminRenderer internal constructor(
    override val key: RenderKey,
    private val sources: List<Source>,
    private val style: GarminStyle,
    private val canvasFactory: GarminCanvasFactory,
    private val options: GarminOptions,
) : MapRenderer {

    override val maxParallelism: Int = Runtime.getRuntime().availableProcessors()

    private val permits = Semaphore(maxParallelism)

    private val closed = AtomicBoolean(false)

    private val idle = ConcurrentLinkedQueue<Worker>()

    private val workers = ConcurrentLinkedQueue<Worker>()

    override fun render(tile: TileRequest, target: CoreRenderTarget, isCancelled: () -> Boolean): RenderResult {
        require(target is GarminTileTarget) {
            "render(), unsupported target ${target::class.java.name}"
        }
        val tileSize = key.config.tileSizePx
        require(target.widthPx == tileSize) {
            "render(), target ${target.widthPx} px, tile $tileSize px"
        }
        permits.acquireUninterruptibly()
        try {
            check(!closed.get()) {
                "render($tile), renderer closed"
            }
            if (isCancelled()) {
                return RenderResult.CANCELLED
            }
            val viewport = viewportOf(tile, tileSize)
            // EMPTY only where no map lies, also between the maps of a dataset
            val inTile = sources.indices.filter { i ->
                sources[i].bounds.any { bounds ->
                    viewport.maxLonDeg > bounds.minLon
                        && viewport.minLonDeg < bounds.maxLon
                        && viewport.maxLatDeg > bounds.minLat
                        && viewport.minLatDeg < bounds.maxLat
                }
            }
            if (inTile.isEmpty()) {
                return RenderResult.EMPTY
            }
            val worker = idle.poll() ?: newWorker()
            try {
                // opened here, as the engine's parsers skip what fails to read
                for (i in inTile) {
                    worker.readers[i].forEach(LazyReader::open)
                }
                val canvas = canvasFactory.create(
                    sizePx = tileSize,
                    antiAliased = options.antiAliasing,
                    renderScale = key.config.deviceScale,
                )
                worker.renderer.render(
                    viewport = viewport,
                    target = canvas,
                    objectKindFilter = options.objectKinds,
                    level = options.level,
                )
                target.canvas = canvas
                return RenderResult.DRAWN
            } finally {
                idle.add(worker)
            }
        } finally {
            permits.release()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        // waits for running renders; later ones fail the closed check
        permits.acquireUninterruptibly(maxParallelism)
        try {
            for (worker in workers) {
                for (reader in worker.readers.flatten()) {
                    reader.close()
                }
            }
        } finally {
            permits.release(maxParallelism)
        }
    }

    // created under a permit, so close never misses one
    private fun newWorker(): Worker {
        // the maps of a gmapsupp share one TYP: one style cache for all of them
        val resolvers = IdentityHashMap<Any, StyleResolver>()
        val readers = sources.map { ArrayList<LazyReader>() }
        val layers = sources.flatMapIndexed { i, source ->
            // one reader per sub-file type serves all maps of the file, one map at a time
            val containers = HashMap<String, LazyReader>()
            source.maps.map { parsed ->
                val map = parsed.reopenOn { type ->
                    containers.getOrPut(type) { LazyReader(source.openReader).also { readers[i] += it } }
                }
                val resolver = resolvers.getOrPut(map.typStyles ?: NO_TYP) { style.resolver(map) }
                ImgRenderer.Layer(img = map, styleResolver = resolver)
            }
        }
        val worker = Worker(readers = readers, renderer = ImgRenderer(layers))
        workers.add(worker)
        return worker
    }

    /**
     * One file of the renderer: its parsed maps with their bounds, and how to open a reader over
     * the file.
     */
    internal class Source(
        val maps: List<ImgFile>,
        val bounds: List<GeoBounds>,
        val openReader: () -> ImgRandomAccessReader,
    )

    private companion object {

        // the resolver key of maps without a TYP
        val NO_TYP = Any()
    }

    // the readers per source, opened on the first tile of the source
    private class Worker(
        val readers: List<List<LazyReader>>,
        val renderer: ImgRenderer,
    )

    // a file opened only for its first tile, so a stale file fails its own tiles
    private class LazyReader(private val openReader: () -> ImgRandomAccessReader) : ImgRandomAccessReader {

        private var reader: ImgRandomAccessReader? = null

        override val size: Long
            get() = reader().size

        override fun position(): Long {
            return reader().position()
        }

        override fun seek(pos: Long) {
            reader().seek(pos)
        }

        override fun readByte(): Int {
            return reader().readByte()
        }

        override fun readBlock(buffer: ByteArray) {
            reader().readBlock(buffer)
        }

        override fun readUInt16(): Int {
            return reader().readUInt16()
        }

        override fun readUInt24(): Int {
            return reader().readUInt24()
        }

        override fun readInt32(): Int {
            return reader().readInt32()
        }

        override fun close() {
            reader?.close()
        }

        fun open() {
            reader()
        }

        private fun reader(): ImgRandomAccessReader {
            return reader ?: openReader().also { reader = it }
        }
    }

    private fun viewportOf(tile: TileRequest, sizePx: Int): Viewport {
        val n = (1 shl tile.zoom).toDouble()
        return Viewport.ofDegrees(
            widthPx = sizePx,
            heightPx = sizePx,
            minLonDeg = tile.x / n * 360.0 - 180.0,
            maxLonDeg = (tile.x + 1) / n * 360.0 - 180.0,
            minLatDeg = latitudeOf(tile.y + 1, n),
            maxLatDeg = latitudeOf(tile.y, n),
        )
    }

    private fun latitudeOf(y: Int, n: Double): Double {
        return atan(sinh(PI * (1.0 - 2.0 * y / n))) * 180.0 / PI
    }
}

/**
 * Creates the engine canvas a tile is drawn into, such as `AwtRenderTarget` or
 * `AndroidRenderTarget`; called from up to `maxParallelism` render threads at once.
 */
fun interface GarminCanvasFactory {

    /**
     * @param renderScale factor on TYP sizes, strokes and text; the renderer's device scale
     */
    fun create(sizePx: Int, antiAliased: Boolean, renderScale: Float): RenderTarget
}

/**
 * Receives the canvas the renderer created through its [GarminCanvasFactory]. After
 * [RenderResult.DRAWN] the caller owns [canvas] and reads its pixels, such as
 * `AwtRenderTarget.image`; the canvas holds nothing else of its own, so dropping it releases it
 * (recycle an `AndroidRenderTarget.bitmap` to free it early), while converted TYP bitmaps stay in
 * the host's bitmap cache.
 */
class GarminTileTarget(sizePx: Int) : CoreRenderTarget {

    override val widthPx: Int = sizePx

    override val heightPx: Int = sizePx

    var canvas: RenderTarget? = null
        internal set
}
