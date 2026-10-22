/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders tiles of one map + theme + text scale combination on a bounded thread pool and keeps
 * the results in an observable cache. Create a new engine whenever any of the three changes.
 *
 * @param threads parallel render workers; DatabaseRenderer is shared between them, as in Locus
 */
class TileEngine(
    val mapFile: File,
    val themeFile: File,
    val textScale: Float,
    val tileSize: Int,
    threads: Int,
) {

    private val graphicFactory = AwtGraphicFactory.INSTANCE
    private val displayModel = DisplayModel().apply {
        setFixedTileSize(tileSize)
    }
    val mapDataStore = MapFile(mapFile)
    private val themeFuture = RenderThemeFuture(graphicFactory, ExternalRenderTheme(themeFile), displayModel)
    private val renderer = DatabaseRenderer(
        mapDataStore,
        graphicFactory,
        InMemoryTileCache(64),
        null,
        true,
        true,
        null,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(threads))
    private val inFlight = ConcurrentHashMap.newKeySet<Tile>()

    /**
     * Rendered tiles; reads from composition trigger a redraw when a tile arrives.
     */
    val tiles = mutableStateMapOf<Tile, ImageBitmap>()

    val stats = RenderStats()

    /**
     * Last render error, shown in the status bar.
     */
    val error = mutableStateOf<String?>(null)

    init {
        val start = System.nanoTime()
        themeFuture.run()
        stats.themeParseMs = (System.nanoTime() - start) / 1_000_000.0
    }

    /**
     * Queues every tile from `wanted` that is neither cached nor rendering, and drops cached tiles
     * far from the current view.
     */
    fun request(wanted: List<Tile>) {
        for (tile in wanted) {
            if (tiles.containsKey(tile) || !inFlight.add(tile)) {
                continue
            }
            scope.launch {
                render(tile)
            }
        }
        trimCache(keep = wanted.toSet())
    }

    /**
     * Drops every cached tile so the next request renders cold.
     */
    fun clear() {
        tiles.clear()
        stats.reset()
    }

    fun close() {
        scope.cancel()
        themeFuture.decrementRefCount()
        mapDataStore.close()
    }

    private fun render(tile: Tile) {
        try {
            val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, textScale, false, false)
            val start = System.nanoTime()
            val bitmap = renderer.executeJob(job)
            val renderNs = System.nanoTime() - start
            if (bitmap == null) {
                error.value = "render($tile), no bitmap"
                return
            }
            val image = AwtGraphicFactory.getBitmap(bitmap).toComposeImageBitmap()
            bitmap.decrementRefCount()
            stats.add(renderNs)
            tiles[tile] = image
        } catch (e: Exception) {
            error.value = "render($tile), ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            inFlight.remove(tile)
        }
    }

    private fun trimCache(keep: Set<Tile>) {
        if (tiles.size <= MAX_CACHED_TILES) {
            return
        }
        tiles.keys.filter { it !in keep }.forEach { tiles.remove(it) }
    }

    private companion object {
        const val MAX_CACHED_TILES = 400
    }
}

/**
 * Per-tile render timings of one engine, observable from Compose.
 */
class RenderStats {

    private val samples = mutableListOf<Long>()

    var themeParseMs = 0.0

    /**
     * Snapshot shown in the UI, updated after every tile.
     */
    val summary = mutableStateOf("")

    @Synchronized
    fun add(renderNs: Long) {
        samples += renderNs
        val sorted = samples.sorted()
        val ms = { ns: Long -> ns / 1_000_000.0 }
        summary.value = "tiles %d · avg %.1f ms · median %.1f ms · p95 %.1f ms · max %.1f ms · theme parse %.0f ms".format(
            sorted.size,
            ms(sorted.sum() / sorted.size),
            ms(sorted[sorted.size / 2]),
            ms(sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]),
            ms(sorted.last()),
            themeParseMs,
        )
    }

    @Synchronized
    fun reset() {
        samples.clear()
        summary.value = ""
    }
}
