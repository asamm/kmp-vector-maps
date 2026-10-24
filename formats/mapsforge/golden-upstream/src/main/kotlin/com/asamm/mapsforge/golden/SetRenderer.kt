/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler
import java.io.File

/**
 * The upstream side renders through plain Mapsforge; the vendored side has its own `SetRenderer`
 * that goes through the Mapsforge driver.
 */
object SetRenderer {

    fun render(set: Corpus.RenderSet, fixturesDir: File, onTile: (Tile, TileBitmap) -> Unit): TileBasedLabelStore? {
        mirrorDriverSetup(set = set, fixturesDir = fixturesDir)
        return Corpus.renderSet(
            set = set,
            fixturesDir = fixturesDir,
            graphicFactory = AwtGraphicFactory.INSTANCE,
            onTile = onTile,
        )
    }

    // the driver reads the theme menu and probes the map before the theme slot; doing the same
    // here keeps that class loading out of the slots on both sides
    private fun mirrorDriverSetup(set: Corpus.RenderSet, fixturesDir: File) {
        try {
            RenderThemeHandler.getRenderTheme(
                AwtGraphicFactory.INSTANCE,
                DisplayModel(),
                ExternalRenderTheme(Corpus.themeFile(fixturesDir, set.theme), StopAtMenu()),
            )
        } catch (e: MenuRead) {
            // expected: stops the parse right after the menu
        }
        val mapFile = MapFile(Corpus.mapFile(fixturesDir))
        mapFile.boundingBox()
        mapFile.mapLanguages
        mapFile.close()
    }

    private class StopAtMenu : XmlRenderThemeMenuCallback {

        override fun getCategories(style: XmlRenderThemeStyleMenu): Set<String> {
            throw MenuRead()
        }
    }

    private class MenuRead : RuntimeException() {

        override fun fillInStackTrace(): Throwable {
            return this
        }
    }
}
