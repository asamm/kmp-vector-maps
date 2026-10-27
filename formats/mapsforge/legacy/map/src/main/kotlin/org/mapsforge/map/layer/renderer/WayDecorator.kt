/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2019 Adrian Batzill
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
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.SymbolOrientation
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.mapelements.WayTextContainer
import org.mapsforge.core.model.LineSegment
import org.mapsforge.core.model.LineString
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Tile
import org.mapsforge.map.rendertheme.RenderContext

internal object WayDecorator {

    private const val MAX_LABEL_CORNER_ANGLE = 45.0

    /**
     * Adds symbols along the first ring of `coordinates`, repeated if `repeatSymbol`.
     */
    fun renderSymbol(
        symbolBitmap: Bitmap,
        display: Display,
        priority: Int,
        dy: Float,
        boundary: Rectangle?,
        repeatSymbol: Boolean,
        repeatGap: Float,
        repeatStart: Float,
        symbolOrientation: SymbolOrientation,
        coordinates: Array<Array<Point>>,
        renderContext: RenderContext,
    ) {
        var skipPixels = repeatStart.toInt()

        val c: Array<Point>
        if (dy == 0f) {
            c = coordinates[0]
        } else {
            c = RendererUtils.parallelPath(coordinates[0], dy.toDouble())
        }

        // get the first way point coordinates
        var previousX = c[0].x
        var previousY = c[0].y

        // draw the symbolContainer on each way segment
        for (i in 1 until c.size) {
            // get the current way point coordinates
            val currentX = c[i].x
            val currentY = c[i].y

            // compute rotation along the segment
            var theta = 0.0f
            when (symbolOrientation) {
                SymbolOrientation.AUTO -> {
                    if (currentX <= previousX) {
                        theta = Math.atan2(previousY - currentY, previousX - currentX).toFloat()
                    } else {
                        theta = Math.atan2(currentY - previousY, currentX - previousX).toFloat()
                    }
                }

                SymbolOrientation.AUTO_DOWN -> {
                    if (currentX <= previousX) {
                        theta = Math.atan2(currentY - previousY, currentX - previousX).toFloat()
                    } else {
                        theta = Math.atan2(previousY - currentY, previousX - currentX).toFloat()
                    }
                }

                SymbolOrientation.RIGHT -> {
                    // we always need to rotate "right"
                    theta = Math.atan2(previousY - currentY, previousX - currentX).toFloat()
                }

                SymbolOrientation.LEFT -> {
                    // we always need to rotate "left"
                    theta = Math.atan2(currentY - previousY, currentX - previousX).toFloat()
                }

                SymbolOrientation.UP -> {
                    theta = 0f
                }

                SymbolOrientation.DOWN -> {
                    theta = Math.PI.toFloat()
                }
            }

            // calculate the length of the current segment (Euclidian distance)
            val diffX = currentX - previousX
            val diffY = currentY - previousY
            val segmentLengthInPixel = Math.sqrt(diffX * diffX + diffY * diffY)

            // calculate changes in coordinates per single pixel
            val diffXpx = diffX / segmentLengthInPixel
            val diffYpx = diffY / segmentLengthInPixel

            // draw symbols till at least 75% of the last symbol will be correctly placed on the
            // segment
            var segmentOffset = skipPixels
            while (segmentOffset + symbolBitmap.width * 0.75 < segmentLengthInPixel) {
                // compute coordinate for symbol
                val cooX = previousX + segmentOffset * diffXpx
                val cooY = previousY + segmentOffset * diffYpx
                val point = Point(cooX, cooY)
                renderContext.addLabel(
                    SymbolContainer(point, display, priority, boundary, symbolBitmap, theta, false),
                )

                // increment offset by the gap and the width of drawn image; the float sum truncates
                segmentOffset = (segmentOffset + (symbolBitmap.width + repeatGap)).toInt()
                if (!repeatSymbol) {
                    return
                }
            }

            // prepare for the next segment
            previousX = currentX
            previousY = currentY
            skipPixels = (segmentOffset - segmentLengthInPixel).toInt()
            if (skipPixels < 0) {
                skipPixels = 0
            }
        }
    }

    /**
     * Finds the parts of a way along which `text` fits and adds a WayTextContainer for each to the
     * labels of `renderContext`; `upperLeft`, `lowerRight` and `repeat` are unused: the text repeats
     * every `repeatGap` regardless of `repeat`.
     *
     * @param dy if not 0, a line parallel to the coordinates is computed first
     * @param stroke stroke paint for the text, null to measure with `fill`
     */
    @Suppress("UNUSED_PARAMETER")
    fun renderText(
        graphicFactory: GraphicFactory,
        upperLeft: Tile,
        lowerRight: Tile,
        text: String,
        display: Display,
        priority: Int,
        dy: Float,
        fill: Paint,
        stroke: Paint?,
        repeat: Boolean,
        repeatGap: Float,
        repeatStart: Float,
        textOrientation: TextOrientation,
        coordinates: Array<Array<Point>>,
        renderContext: RenderContext,
    ) {
        if (coordinates.isEmpty()) {
            return
        }

        val c: Array<Point>
        if (dy == 0f) {
            c = coordinates[0]
        } else {
            c = RendererUtils.parallelPath(coordinates[0], dy.toDouble())
        }

        if (c.size < 2) {
            return
        }

        val path = LineString()
        for (i in 1 until c.size) {
            val segment = LineSegment(c[i - 1], c[i])
            path.segments.add(segment)
        }

        val textWidth = if (stroke == null) fill.getTextWidth(text) else stroke.getTextWidth(text)
        val textHeight = if (stroke == null) fill.getTextHeight(text) else stroke.getTextHeight(text)

        val pathLength = path.length()

        var pos = repeatStart
        while ((pos + textWidth).toDouble() < pathLength) {
            val linePart = path.extractPart(pos.toDouble(), (pos + textWidth).toDouble())

            var tooSharp = false
            for (i in 1 until linePart.segments.size) {
                val cornerAngle = linePart.segments[i - 1].angleTo(linePart.segments[i])
                if (Math.abs(cornerAngle) > MAX_LABEL_CORNER_ANGLE) {
                    tooSharp = true
                    break
                }
            }
            if (!tooSharp) {
                // check to prevent inverted way names now happens when drawing, because with
                // rotation we do not know the screen positions in advance any more.
                renderContext.addLabel(
                    WayTextContainer(
                        graphicFactory,
                        linePart,
                        display,
                        priority,
                        text,
                        fill,
                        stroke,
                        textHeight.toDouble(),
                        textOrientation,
                    ),
                )
            }

            pos += repeatGap + textWidth
        }
    }
}
