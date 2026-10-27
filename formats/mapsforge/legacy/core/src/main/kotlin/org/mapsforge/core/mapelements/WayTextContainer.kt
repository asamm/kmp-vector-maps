/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016 devemux86
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
package org.mapsforge.core.mapelements

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Path
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.model.LineString
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation

/**
 * A text drawn along a line; its point is the start of the line.
 */
open class WayTextContainer(
    graphicFactory: GraphicFactory,
    lineString: LineString,
    display: Display,
    priority: Int,
    text: String,
    paintFront: Paint,
    paintBack: Paint?,
    textHeight: Double,
    textOrientation: TextOrientation,
) : MapElementContainer(lineString.segments[0].start, display, priority) {

    private val graphicFactory: GraphicFactory

    @JvmField
    protected val boundary: Rectangle

    private val lineString: LineString

    private val paintFront: Paint

    private val paintBack: Paint?

    private val text: String

    private val textOrientation: TextOrientation

    init {
        this.graphicFactory = graphicFactory
        this.lineString = lineString
        this.text = text
        this.paintFront = paintFront
        this.paintBack = paintBack

        // a way text container should always run left to right, but I leave this in because it
        // might matter if we support right-to-left text.
        // we also need to make the container larger by textHeight as otherwise the end points do
        // not correctly reflect the size of the text on screen
        val absolute = lineString.bounds.enlarge(
            textHeight / 2.0,
            textHeight / 2.0,
            textHeight / 2.0,
            textHeight / 2.0,
        )
        this.boundaryAbsolute = absolute
        this.boundary = absolute.shift(Point(-this.xy.x, -this.xy.y))
        this.textOrientation = textOrientation
    }

    override fun getBoundary(): Rectangle {
        return boundary
    }

    final override fun draw(canvas: Canvas?, origin: Point?, matrix: Matrix?, rotation: Rotation?) {
        val path = generatePath(origin, rotation)

        if (this.paintBack != null) {
            canvas!!.drawPathText(this.text, path, this.paintBack)
        }
        canvas!!.drawPathText(this.text, path, this.paintFront)
    }

    private fun generatePath(origin: Point?, rotation: Rotation?): Path {
        // compute rotation so text isn't upside down
        val firstSegment = this.lineString.segments[0]
        val lastSegment = this.lineString.segments[this.lineString.segments.size - 1]
        val doInvert: Boolean
        when (textOrientation) {
            TextOrientation.AUTO_DOWN -> {
                if (!Rotation.noRotation(rotation)) {
                    val startRotated = rotation!!.rotate(firstSegment.start, true)
                    val endRotated = rotation.rotate(lastSegment.end, true)
                    doInvert = endRotated.x > startRotated.x
                } else {
                    doInvert = lastSegment.end.x > firstSegment.start.x
                }
            }
            TextOrientation.RIGHT -> {
                // TODO Rotation
                doInvert = false
            }
            TextOrientation.LEFT -> {
                // TODO Rotation
                doInvert = true
            }
            // AUTO
            else -> {
                if (!Rotation.noRotation(rotation)) {
                    val startRotated = rotation!!.rotate(firstSegment.start, true)
                    val endRotated = rotation.rotate(lastSegment.end, true)
                    doInvert = endRotated.x <= startRotated.x
                } else {
                    doInvert = lastSegment.end.x <= firstSegment.start.x
                }
            }
        }

        // draw text based on the orientation requirement
        val path = this.graphicFactory.createPath()
        if (!doInvert) {
            val start = firstSegment.start.offset(-origin!!.x, -origin.y)
            path.moveTo(start.x.toFloat(), start.y.toFloat())
            for (i in 0 until this.lineString.segments.size) {
                val segment = this.lineString.segments[i]
                val end = segment.end.offset(-origin.x, -origin.y)
                path.lineTo(end.x.toFloat(), end.y.toFloat())
            }
        } else {
            val end = this.lineString.segments[this.lineString.segments.size - 1].end
                .offset(-origin!!.x, -origin.y)
            path.moveTo(end.x.toFloat(), end.y.toFloat())
            for (i in this.lineString.segments.size - 1 downTo 0) {
                val segment = this.lineString.segments[i]
                val start = segment.start.offset(-origin.x, -origin.y)
                path.lineTo(start.x.toFloat(), start.y.toFloat())
            }
        }
        return path
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(super.toString())
        stringBuilder.append(", text=")
        stringBuilder.append(this.text)
        return stringBuilder.toString()
    }
}
