/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import javax.imageio.ImageIO

/**
 * Golden render corpus: a fixed tile set rendered through the public Mapsforge AWT API.
 *
 * Compiled twice — against upstream Mapsforge 0.30.0 (`:formats:mapsforge:golden-upstream`) and
 * against the vendored modules (`:formats:mapsforge:golden`) — so both renders run the same code on
 * the same machine.
 */
object GoldenCorpus {

    private const val TILE_SIZE = 256

    // Monaco harbour, centre of the fixture map
    private const val CENTER_LAT = 43.7350
    private const val CENTER_LON = 7.4230

    private val THEMES = listOf("theme", "theme_dark")
    private val TEXT_SCALES = listOf(1.0f, 1.5f)

    /**
     * Tiles in render order; the order matters because labels crossing tile edges depend on
     * already rendered neighbours.
     */
    val tiles: List<Tile> by lazy {
        buildTiles()
    }

    /**
     * Renders every theme × text scale × tile combination into `outDir` as PNG files.
     */
    fun renderAll(fixturesDir: File, outDir: File) {
        outDir.mkdirs()
        val mapFile = File(fixturesDir, "maps/monaco.osm.map")
        for (theme in THEMES) {
            for (textScale in TEXT_SCALES) {
                renderSet(
                    mapFile = mapFile,
                    themeFile = File(fixturesDir, "themes/lomaps/$theme.xml"),
                    textScale = textScale,
                    outDir = outDir,
                    prefix = "${theme}_ts$textScale",
                )
            }
        }
    }

    private fun renderSet(
        mapFile: File,
        themeFile: File,
        textScale: Float,
        outDir: File,
        prefix: String,
    ) {
        val graphicFactory = AwtGraphicFactory.INSTANCE
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(TILE_SIZE)
        val mapDataStore = MapFile(mapFile)
        val themeFuture = RenderThemeFuture(graphicFactory, ExternalRenderTheme(themeFile), displayModel)
        themeFuture.run()
        // the renderer only logs a parse failure and returns null tiles; fail with the real cause
        themeFuture.get()
        val renderer = DatabaseRenderer(
            mapDataStore,
            graphicFactory,
            InMemoryTileCache(64),
            null,
            true,
            true,
            null,
        )
        try {
            for (tile in tiles) {
                val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, textScale, false, false)
                val bitmap = renderer.executeJob(job)
                    ?: throw IllegalStateException("renderSet($prefix), no bitmap for $tile")
                val file = File(outDir, "${prefix}_z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}.png")
                ImageIO.write(AwtGraphicFactory.getBitmap(bitmap), "png", file)
                bitmap.decrementRefCount()
            }
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }

    private fun buildTiles(): List<Tile> {
        val result = mutableListOf<Tile>()
        // one centre tile per zoom
        for (zoom in listOf(8, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20)) {
            result += centerTile(zoom = zoom, dx = 0, dy = 0)
        }
        // 2x2 blocks where labels cross tile edges
        for ((zoom, offset) in listOf(15 to -1, 17 to 1)) {
            for (dy in 0..1) {
                for (dx in 0..1) {
                    result += centerTile(zoom = zoom, dx = dx + offset, dy = dy + offset)
                }
            }
        }
        return result.distinct()
    }

    private fun centerTile(zoom: Int, dx: Int, dy: Int): Tile {
        val z = zoom.toByte()
        val x = MercatorProjection.longitudeToTileX(CENTER_LON, z) + dx
        val y = MercatorProjection.latitudeToTileY(CENTER_LAT, z) + dy
        return Tile(x, y, z, TILE_SIZE)
    }
}
