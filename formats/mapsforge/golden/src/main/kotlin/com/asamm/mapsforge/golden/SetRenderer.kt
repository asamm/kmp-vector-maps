/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import java.io.File

/**
 * The vendored side renders through the Mapsforge driver, so the golden test compares the driver
 * against plain upstream Mapsforge (`golden-upstream`'s `SetRenderer`).
 */
object SetRenderer {

    fun render(set: Corpus.RenderSet, fixturesDir: File, onTile: (Tile, TileBitmap) -> Unit): TileBasedLabelStore? {
        val driver = MapsforgeDriver(AwtGraphicFactory.INSTANCE)
        val style = driver.loadStyle(Corpus.themeFile(fixturesDir, set.theme))
        val config = RenderConfig(
            tileSizePx = Corpus.TILE_SIZE,
            deviceScale = set.deviceScale,
            textScale = set.textScale,
            labelMode = if (set.labelsInTile) LabelMode.IN_TILE else LabelMode.SEPARATE,
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
