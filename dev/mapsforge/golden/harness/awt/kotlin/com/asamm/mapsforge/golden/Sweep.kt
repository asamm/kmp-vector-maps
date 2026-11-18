/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import javax.imageio.ImageIO

/**
 * A sweep over many tiles of a large map, the same code compiled against upstream and vendored
 * Mapsforge as the corpus: `sweep <map> <theme> <menuLayer> <tiles.txt> <outDir> <deviceScale>
 * <labelsInTile> <side>` writes `<outDir>/<side>/<z>_<x>_<y>.png` per tile of the list (lines
 * `z/x/y`) and `<outDir>/<side>/times.txt` with each render's milliseconds. A tile that fails is
 * listed in `<outDir>/<side>/failed.txt`.
 */
object Sweep {

    fun run(args: List<String>) {
        require(args.size == 8) {
            "usage: sweep <map> <theme> <menuLayer> <tiles.txt> <outDir> <deviceScale> <labelsInTile> <side>"
        }
        val map = File(args[0])
        val theme = File(args[1])
        val menuLayer = args[2]
        val tiles = File(args[3]).readLines().filter { it.isNotBlank() }.map { line ->
            val (z, x, y) = line.trim().split('/').map { it.toInt() }
            Tile(x, y, z.toByte(), Corpus.TILE_SIZE)
        }
        val outDir = File(args[4], args[7]).apply { mkdirs() }
        val deviceScale = args[5].toFloat()
        val labelsInTile = args[6].toBoolean()
        IdentityHashes.align("sweep theme")
        DisplayModel.setDeviceScaleFactor(deviceScale)
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(Corpus.TILE_SIZE)
        displayModel.setMaxTextWidthFactor(0.7f)
        val mapDataStore = MapFile(map)
        val themeFuture = RenderThemeFuture(
            AwtGraphicFactory.INSTANCE,
            ExternalRenderTheme(theme, Corpus.MenuSelection(menuLayer)),
            displayModel,
        )
        themeFuture.run()
        themeFuture.get()
        val renderer = DatabaseRenderer(
            mapDataStore,
            AwtGraphicFactory.INSTANCE,
            if (labelsInTile) InMemoryTileCache(64) else null,
            if (labelsInTile) null else TileBasedLabelStore(100),
            labelsInTile,
            true,
            null,
        )
        val times = StringBuilder()
        val failed = StringBuilder()
        try {
            for ((i, tile) in tiles.withIndex()) {
                val name = "${tile.zoomLevel}_${tile.tileX}_${tile.tileY}"
                IdentityHashes.align("sweep $name")
                try {
                    val started = System.nanoTime()
                    val bitmap = renderer.executeJob(RendererJob(tile, mapDataStore, themeFuture, displayModel, 1f, false, false))
                    val ms = (System.nanoTime() - started) / 1e6
                    if (bitmap == null) {
                        failed.append("$name no bitmap\n")
                        continue
                    }
                    ImageIO.write(AwtGraphicFactory.getBitmap(bitmap), "png", File(outDir, "$name.png"))
                    bitmap.decrementRefCount()
                    times.append("$name ${"%.1f".format(ms)}\n")
                } catch (e: Exception) {
                    failed.append("$name ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}\n")
                }
                if ((i + 1) % 200 == 0) {
                    println("${i + 1} / ${tiles.size}")
                }
            }
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
            File(outDir, "times.txt").writeText(times.toString())
            File(outDir, "failed.txt").writeText(failed.toString())
        }
    }
}
