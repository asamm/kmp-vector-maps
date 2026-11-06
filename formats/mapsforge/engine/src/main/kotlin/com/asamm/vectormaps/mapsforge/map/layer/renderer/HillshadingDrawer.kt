/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.graphics.Canvas
import com.asamm.vectormaps.mapsforge.core.model.Tile

/**
 * Draws the host's hillshading into a tile, in place of upstream's `HillsRenderConfig`: at the
 * theme's `<hillshading>` layer and level, or after all ways when the theme has none.
 */
fun interface HillshadingDrawer {

    fun draw(canvas: Canvas, tile: Tile)
}
