/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
package org.mapsforge.map.rendertheme

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.SymbolOrientation
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.model.Rectangle
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer

/**
 * Callback methods for rendering areas, ways and points of interest (POIs).
 */
interface RenderCallback {

    /**
     * Renders an area with the given fill and casing paints.
     */
    fun renderArea(
        renderContext: RenderContext,
        fill: Paint,
        stroke: Paint,
        level: Int,
        way: PolylineContainer,
    )

    /**
     * Renders an area caption, offset from the area centre.
     */
    fun renderAreaCaption(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        caption: String,
        horizontalOffset: Float,
        verticalOffset: Float,
        fill: Paint,
        stroke: Paint,
        position: Position,
        maxTextWidth: Int,
        way: PolylineContainer,
    )

    /**
     * Renders an area symbol with the given bitmap.
     */
    fun renderAreaSymbol(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        symbol: Bitmap,
        way: PolylineContainer,
    )

    /**
     * Renders a point of interest caption, offset from the POI.
     */
    fun renderPointOfInterestCaption(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        caption: String,
        horizontalOffset: Float,
        verticalOffset: Float,
        fill: Paint,
        stroke: Paint,
        position: Position,
        maxTextWidth: Int,
        poi: PointOfInterest,
    )

    /**
     * Renders a point of interest circle with the given fill and casing paints.
     */
    fun renderPointOfInterestCircle(
        renderContext: RenderContext,
        radius: Float,
        fill: Paint,
        stroke: Paint,
        level: Int,
        poi: PointOfInterest,
    )

    /**
     * Renders a point of interest symbol with the given bitmap.
     */
    fun renderPointOfInterestSymbol(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        boundary: Rectangle?,
        symbol: Bitmap,
        poi: PointOfInterest,
    )

    /**
     * Renders a way, offset by [dy].
     */
    fun renderWay(
        renderContext: RenderContext,
        stroke: Paint,
        dy: Float,
        curveStyle: Curve,
        level: Int,
        way: PolylineContainer,
    )

    /**
     * Renders a symbol along the way path, offset by [dy]; [boundary] is the symbol's position
     * relative to the path, [repeatGap] the distance between repetitions.
     */
    fun renderWaySymbol(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        symbol: Bitmap,
        dy: Float,
        boundary: Rectangle?,
        repeat: Boolean,
        repeatGap: Float,
        repeatStart: Float,
        symbolOrientation: SymbolOrientation,
        way: PolylineContainer,
    )

    /**
     * Renders a text along the way path, offset by [dy].
     */
    fun renderWayText(
        renderContext: RenderContext,
        display: Display,
        priority: Int,
        text: String,
        dy: Float,
        fill: Paint,
        stroke: Paint,
        repeat: Boolean,
        repeatGap: Float,
        repeatStart: Float,
        textOrientation: TextOrientation,
        way: PolylineContainer,
    )
}
