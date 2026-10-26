/*
 * Copyright 2019 Adrian Batzill
 * Copyright 2019 devemux86
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
/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.mapsforge.map.awt.graphics

import java.awt.Font
import java.awt.Shape
import java.awt.Stroke
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.FlatteningPathIterator
import java.awt.geom.GeneralPath
import java.awt.geom.PathIterator

/**
 * See http://www.jhlabs.com/java/java2d/strokes/
 */
open class TextStroke(
    private val text: String,
    private val font: Font?,
    private val frc: FontRenderContext,
    private val stretchToFit: Boolean,
    private val repeat: Boolean,
) : Stroke {

    private val t = AffineTransform()

    constructor(text: String, font: Font?, frc: FontRenderContext) : this(text, font, frc, true, false)

    final override fun createStrokedShape(shape: Shape): Shape {
        val glyphVector = font!!.createGlyphVector(frc, text)

        val result = GeneralPath()
        val it: PathIterator = FlatteningPathIterator(shape.getPathIterator(null), FLATNESS.toDouble())
        val points = FloatArray(6)
        var moveX = 0f
        var moveY = 0f
        var lastX = 0f
        var lastY = 0f
        var thisX: Float
        var thisY: Float
        var type: Int
        var next = 0f
        var currentChar = 0
        val length = glyphVector.numGlyphs

        if (length == 0) {
            return result
        }

        val factor = if (stretchToFit) {
            measurePathLength(shape) / glyphVector.logicalBounds.width.toFloat()
        } else {
            1.0f
        }
        var nextAdvance = 0f

        while (currentChar < length
            && !it.isDone
        ) {
            type = it.currentSegment(points)
            when (type) {
                PathIterator.SEG_MOVETO -> {
                    lastX = points[0]
                    moveX = lastX
                    lastY = points[1]
                    moveY = lastY
                    result.moveTo(moveX, moveY)
                    nextAdvance = glyphVector.getGlyphMetrics(currentChar).advance * 0.5f
                    next = nextAdvance
                }

                PathIterator.SEG_CLOSE, PathIterator.SEG_LINETO -> {
                    if (type == PathIterator.SEG_CLOSE) {
                        points[0] = moveX
                        points[1] = moveY
                        // then on as a line to the start, as upstream's fall-through
                    }

                    thisX = points[0]
                    thisY = points[1]
                    val dx = thisX - lastX
                    val dy = thisY - lastY
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (distance >= next) {
                        val r = 1.0f / distance
                        val angle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        while (currentChar < length
                            && distance >= next
                        ) {
                            val glyph = glyphVector.getGlyphOutline(currentChar)
                            val p = glyphVector.getGlyphPosition(currentChar)
                            val px = p.x.toFloat()
                            val py = p.y.toFloat()
                            val x = lastX + next * dx * r
                            val y = lastY + next * dy * r
                            val advance = nextAdvance
                            nextAdvance = if (currentChar < length - 1) {
                                glyphVector.getGlyphMetrics(currentChar + 1).advance * 0.5f
                            } else {
                                0f
                            }
                            t.setToTranslation(x.toDouble(), y.toDouble())
                            t.rotate(angle.toDouble())
                            t.translate((-px - advance).toDouble(), (-py).toDouble())
                            // Way text container was made larger by text height
                            t.translate(0.0, glyphVector.visualBounds.height / 4)
                            result.append(t.createTransformedShape(glyph), false)
                            next += (advance + nextAdvance) * factor
                            currentChar++
                            if (repeat) {
                                currentChar %= length
                            }
                        }
                    }
                    next -= distance
                    lastX = thisX
                    lastY = thisY
                }
            }
            it.next()
        }

        return result
    }

    fun measurePathLength(shape: Shape): Float {
        val it: PathIterator = FlatteningPathIterator(shape.getPathIterator(null), FLATNESS.toDouble())
        val points = FloatArray(6)
        var moveX = 0f
        var moveY = 0f
        var lastX = 0f
        var lastY = 0f
        var thisX: Float
        var thisY: Float
        var type: Int
        var total = 0f

        while (!it.isDone) {
            type = it.currentSegment(points)
            when (type) {
                PathIterator.SEG_MOVETO -> {
                    lastX = points[0]
                    moveX = lastX
                    lastY = points[1]
                    moveY = lastY
                }

                PathIterator.SEG_CLOSE, PathIterator.SEG_LINETO -> {
                    if (type == PathIterator.SEG_CLOSE) {
                        points[0] = moveX
                        points[1] = moveY
                        // then on as a line to the start, as upstream's fall-through
                    }

                    thisX = points[0]
                    thisY = points[1]
                    val dx = thisX - lastX
                    val dy = thisY - lastY
                    total += Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    lastX = thisX
                    lastY = thisY
                }
            }
            it.next()
        }

        return total
    }

    private companion object {

        private const val FLATNESS = 1f
    }
}
