/*
 * Copyright 2017 usrusr
 * Copyright 2017 oruxman
 * Copyright 2024 Sublimis
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.asamm.vectormaps.mapsforge.map.rendertheme.renderinstruction

import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.map.layer.renderer.HillshadingContainer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.HillshadingDrawer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.ShapePaintContainer
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext

/**
 * Represents hillshading on a painter algorithm layer/level in the parsed rendertheme; the host
 * draws the shading itself, so only the position remains of upstream's instruction.
 */
class Hillshading(
    private val layer: Byte,
    private val level: Int,
) {

    /**
     * Places [drawer] at this layer and level of [renderContext]'s ways; [paint] is unused.
     */
    fun render(renderContext: RenderContext, drawer: HillshadingDrawer, paint: Paint) {
        val container = HillshadingContainer(drawer = drawer)
        renderContext.setDrawingLayer(layer)
        renderContext.addToCurrentDrawingLayer(level, ShapePaintContainer(container, paint))
    }
}
