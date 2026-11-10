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
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.core.graphics.SymbolOrientation
import com.asamm.vectormaps.mapsforge.core.graphics.TextOrientation
import com.asamm.vectormaps.mapsforge.core.mapelements.SymbolContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.WayTextContainer
import com.asamm.vectormaps.mapsforge.core.model.LineSegment
import com.asamm.vectormaps.mapsforge.core.model.LineString
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext

internal object WayDecorator {

    private const val MAX_LABEL_CORNER_ANGLE = 45.0

    /**
     * Adds symbols along the first ring of `coordinates`, absolute x, y pairs, repeated if
     * `repeatSymbol`.
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
        coordinates: Array<DoubleArray>,
        renderContext: RenderContext,
    ) {
        var skipPixels = repeatStart.toInt()

        val c: DoubleArray
        if (dy == 0f) {
            c = coordinates[0]
        } else {
            c = RendererUtils.parallelPath(coordinates[0], dy.toDouble())
        }

        // get the first way point coordinates
        var previousX = c[0]
        var previousY = c[1]

        // draw the symbolContainer on each way segment
        for (i in 2 until c.size step 2) {
            // get the current way point coordinates
            val currentX = c[i]
            val currentY = c[i + 1]

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
     * Finds the parts of a way, absolute x, y pairs, along which `text` fits and adds a
     * WayTextContainer for each to the labels of `renderContext`; the text repeats every
     * `repeatGap`, whatever the theme's `repeat` says.
     *
     * @param dy if not 0, a line parallel to the coordinates is computed first
     * @param stroke stroke paint for the text, null to measure with `fill`
     */
    fun renderText(
        graphicFactory: GraphicFactory,
        text: String,
        display: Display,
        priority: Int,
        dy: Float,
        fill: Paint,
        stroke: Paint?,
        repeatGap: Float,
        repeatStart: Float,
        textOrientation: TextOrientation,
        coordinates: Array<DoubleArray>,
        renderContext: RenderContext,
    ) {
        if (coordinates.isEmpty()) {
            return
        }

        val c: DoubleArray
        if (dy == 0f) {
            c = coordinates[0]
        } else {
            c = RendererUtils.parallelPath(coordinates[0], dy.toDouble())
        }

        if (c.size < 4) {
            return
        }

        // as LineString.length, summed in the same order
        var pathLength = 0.0
        for (i in 2 until c.size step 2) {
            pathLength += Math.hypot(c[i - 2] - c[i], c[i - 1] - c[i + 1])
        }
        // no text fits a way shorter than its start, so it is measured only when it may (C15)
        if (pathLength <= repeatStart) {
            return
        }

        val measurePaint = stroke ?: fill
        val textWidth = measurePaint.getTextWidth(text)
        // measured with the first label that fits
        var textHeight = -1

        // no label fits when the loop's first test fails
        if (!((repeatStart + textWidth).toDouble() < pathLength)) {
            return
        }

        // the label parts take Points; built only for a way the text fits on once (C16)
        val path = LineString()
        var previous = Point(c[0], c[1])
        for (i in 2 until c.size step 2) {
            val current = Point(c[i], c[i + 1])
            path.segments.add(LineSegment(previous, current))
            previous = current
        }

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
                if (textHeight < 0) {
                    textHeight = measurePaint.getTextHeight(text)
                }
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
