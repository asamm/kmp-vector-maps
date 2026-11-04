/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.mapsforge.core.graphics.Position
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mapsforge.map.datastore.PointOfInterest
import com.asamm.vectormaps.mapsforge.map.layer.renderer.PolylineContainer
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderCallback
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext
import com.asamm.vectormaps.mapsforge.map.rendertheme.renderinstruction.RenderInstruction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The boundary of a non-square symbol offsets its top by the symbol's height; upstream takes the
 * width (A8, C9).
 */
class SymbolBoundaryTest {

    @Test
    fun topScalesByHeight() {
        assertEquals(Rectangle(-3.5, -6.0, 3.5, 0.0), boundary(Position.ABOVE))
        assertEquals(Rectangle(-3.5, -3.0, 3.5, 3.0), boundary(Position.CENTER))
        assertEquals(Rectangle(-3.5, 0.0, 3.5, 6.0), boundary(Position.BELOW))
    }

    private fun boundary(position: Position): Rectangle {
        return BoundaryProbe().boundary(width = 7, height = 6, position = position)
    }

    private class BoundaryProbe : RenderInstruction(AwtGraphicFactory.INSTANCE, DisplayModel()) {

        fun boundary(width: Int, height: Int, position: Position): Rectangle {
            return computeBoundary(width, height, position)
        }

        override fun destroy() {
            // nothing held
        }

        override fun renderNode(renderCallback: RenderCallback, renderContext: RenderContext, poi: PointOfInterest) {
            // not rendered
        }

        override fun renderWay(renderCallback: RenderCallback, renderContext: RenderContext, way: PolylineContainer) {
            // not rendered
        }

        override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
            // nothing to scale
        }

        override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
            // nothing to scale
        }
    }
}
