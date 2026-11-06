/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import com.asamm.vectormaps.mapsforge.core.graphics.FontFamily
import com.asamm.vectormaps.mapsforge.core.graphics.TileBitmap
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
import java.awt.Font
import java.io.File

/**
 * The vendored side renders through the Mapsforge driver, so the golden test compares the driver
 * against plain upstream Mapsforge (`golden-upstream`'s `SetRenderer`). The driver draws no labels
 * into tiles (D39), so the sets with labels in the tiles render through the vendored engine, as
 * upstream does, and stay engine goldens. Text is drawn in the bundled DejaVu Sans, so the
 * committed label goldens hold on every system (D37).
 */
object SetRenderer {

    fun render(set: Corpus.RenderSet, fixturesDir: File, onTile: (Tile, TileBitmap) -> Unit): TileBasedLabelStore? {
        val font = Font.createFont(Font.TRUETYPE_FONT, Corpus.labelFontFile(fixturesDir))
        val graphicFactory = AwtGraphicFactory.withFonts(FontFamily.entries.associateWith { font })
        val driver = MapsforgeDriver(graphicFactory)
        val style = driver.loadStyle(Corpus.themeFile(fixturesDir, set.theme))
        if (set.labelsInTile) {
            // the theme menu and map probe ran above, outside the slots, as upstream's
            // mirrorDriverSetup does for these sets
            driver.open(listOf(Corpus.mapFile(fixturesDir))).close()
            return Corpus.renderSet(set = set, fixturesDir = fixturesDir, graphicFactory = graphicFactory, onTile = onTile)
        }
        val config = RenderConfig(
            tileSizePx = Corpus.TILE_SIZE,
            deviceScale = set.deviceScale,
            textScale = set.textScale,
        )
        driver.open(listOf(Corpus.mapFile(fixturesDir))).use { dataset ->
            // loads the driver's render-path classes outside the slots; upstream has none of them
            TileBitmapTarget(Corpus.TILE_SIZE)
            TileRequest(x = 0, y = 0, zoom = 0)
            RenderResult.DRAWN
            val isCancelled = { false }
            isCancelled()
            IdentityHashes.align("${set.name} theme")
            dataset.createRenderer(style, StyleSelection(layerId = set.menuLayer), config).use { renderer ->
                for (tile in Corpus.tiles) {
                    IdentityHashes.align("${set.name} z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}")
                    val target = TileBitmapTarget(Corpus.TILE_SIZE)
                    val request = TileRequest(x = tile.tileX, y = tile.tileY, zoom = tile.zoomLevel.toInt())
                    check(renderer.render(request, target, isCancelled) == RenderResult.DRAWN) {
                        "render(${set.name}), $tile not drawn"
                    }
                    val bitmap = target.bitmap!!
                    onTile(tile, bitmap)
                    bitmap.decrementRefCount()
                }
                return renderer.labelStore
            }
        }
    }
}
