/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import javax.imageio.ImageIO

/**
 * The golden corpus rendered through the public Mapsforge AWT API, plus the text dumps.
 *
 * Compiled twice — against upstream Mapsforge 0.30.0 (`:formats:mapsforge:golden-upstream`) and
 * against the vendored modules (`:formats:mapsforge:golden`) — so both renders run the same code on
 * the same machine.
 */
object GoldenCorpus {

    /**
     * Renders every set × tile combination into `outDir` as PNG files, plus a label dump for each
     * set that keeps labels out of the tiles, the reader and theme-callback dumps and the Paint
     * counts.
     */
    fun renderAll(fixturesDir: File, outDir: File) {
        outDir.mkdirs()
        // the device scale is a JVM-wide static in Mapsforge; leave it as found
        val originalDeviceScale = DisplayModel.getDeviceScaleFactor()
        try {
            for (set in Corpus.SETS) {
                val labelStore = Corpus.renderSet(
                    set = set,
                    fixturesDir = fixturesDir,
                    graphicFactory = AwtGraphicFactory.INSTANCE,
                ) { tile, bitmap ->
                    val file = File(outDir, Corpus.tileFileName(set = set, tile = tile, extension = "png"))
                    ImageIO.write(AwtGraphicFactory.getBitmap(bitmap), "png", file)
                }
                if (labelStore != null) {
                    LabelDump.write(store = labelStore, tiles = Corpus.tiles, file = File(outDir, "${set.name}_labels.txt"))
                }
            }
            writeDumps(fixturesDir = fixturesDir, outDir = outDir)
            PaintCount.write(
                mapFile = Corpus.mapFile(fixturesDir),
                themeFile = Corpus.themeFile(fixturesDir, "theme"),
                tileSize = Corpus.TILE_SIZE,
                lat = Corpus.HARBOUR_LAT,
                lon = Corpus.HARBOUR_LON,
                file = File(outDir, "paint_counts.txt"),
            )
        } finally {
            DisplayModel.setDeviceScaleFactor(originalDeviceScale)
        }
    }

    /**
     * Reader and theme-callback dumps for `Corpus.dumpTiles`, with the day theme, `hike_bike` and
     * device scale 1.
     */
    private fun writeDumps(fixturesDir: File, outDir: File) {
        DisplayModel.setDeviceScaleFactor(1.0f)
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(Corpus.TILE_SIZE)
        val mapDataStore = MapFile(Corpus.mapFile(fixturesDir))
        val xmlTheme = ExternalRenderTheme(Corpus.themeFile(fixturesDir, "theme"), Corpus.MenuSelection("hike_bike"))
        val themeFuture = RenderThemeFuture(AwtGraphicFactory.INSTANCE, xmlTheme, displayModel)
        themeFuture.run()
        themeFuture.get()
        try {
            MapDataDump.writeReader(store = mapDataStore, tiles = Corpus.dumpTiles, file = File(outDir, "reader.txt"))
            MapDataDump.writeTheme(
                store = mapDataStore,
                themeFuture = themeFuture,
                displayModel = displayModel,
                tiles = Corpus.dumpTiles,
                file = File(outDir, "theme_callbacks.txt"),
            )
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }
}
