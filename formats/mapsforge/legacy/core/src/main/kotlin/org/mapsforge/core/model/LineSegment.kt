/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2019 Adrian Batzill
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
package org.mapsforge.core.model

/**
 * A directed line segment between two points.
 */
class LineSegment(
    @JvmField val start: Point,
    @JvmField val end: Point,
) {

    /**
     * The segment from `start` towards `direction`, `distance` long.
     */
    constructor(start: Point, direction: Point, distance: Double) : this(
        start,
        LineSegment(start, direction).pointAlongLineSegment(distance),
    )

    /**
     * The angle to `other` in degrees, -180 to 180.
     */
    fun angleTo(other: LineSegment): Double {
        val angle1 = Math.atan2(start.y - end.y, start.x - end.x)
        val angle2 = Math.atan2(other.start.y - other.end.y, other.start.x - other.end.x)
        var angle = Math.toDegrees(angle1 - angle2)
        if (angle <= -180) {
            angle += 360
        }
        if (angle >= 180) {
            angle -= 360
        }
        return angle
    }

    /**
     * The part inside `r` (Cohen-Sutherland), null when none.
     */
    fun clipToRectangle(r: Rectangle): LineSegment? {
        var a = start
        var b = end

        var codeStart = code(r, a)
        var codeEnd = code(r, b)

        while (true) {
            if (0 == (codeStart or codeEnd)) {
                // both points are inside, intersection is the computed line
                return LineSegment(a, b)
            } else if (0 != (codeStart and codeEnd)) {
                // both points are either below, above, left or right of the box, no intersection
                return null
            } else {
                val newX: Double
                val newY: Double
                // At least one endpoint is outside the clip rectangle; pick it.
                val outsideCode = if (0 != codeStart) codeStart else codeEnd

                if (0 != (outsideCode and TOP)) {
                    // point is above the clip rectangle
                    newX = a.x + (b.x - a.x) * (r.top - a.y) / (b.y - a.y)
                    newY = r.top
                } else if (0 != (outsideCode and BOTTOM)) {
                    // point is below the clip rectangle
                    newX = a.x + (b.x - a.x) * (r.bottom - a.y) / (b.y - a.y)
                    newY = r.bottom
                } else if (0 != (outsideCode and RIGHT)) {
                    // point is to the right of clip rectangle
                    newY = a.y + (b.y - a.y) * (r.right - a.x) / (b.x - a.x)
                    newX = r.right
                } else if (0 != (outsideCode and LEFT)) {
                    // point is to the left of clip rectangle
                    newY = a.y + (b.y - a.y) * (r.left - a.x) / (b.x - a.x)
                    newX = r.left
                } else {
                    throw IllegalStateException("Should not get here")
                }
                // Now we move outside point to intersection point to clip
                // and get ready for next pass.
                if (outsideCode == codeStart) {
                    a = Point(newX, newY)
                    codeStart = code(r, a)
                } else {
                    b = Point(newX, newY)
                    codeEnd = code(r, b)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LineSegment) {
            return false
        }
        return other.start == start
            && other.end == end
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + start.hashCode()
        result = prime * result + end.hashCode()
        return result
    }

    /**
     * True or false when the codes decide it fast, else `bias`, which may be wrong.
     */
    fun intersectsRectangle(r: Rectangle, bias: Boolean): Boolean {
        val codeStart = code(r, start)
        val codeEnd = code(r, end)

        if (0 == (codeStart or codeEnd)) {
            // both points are inside, trivial case
            return true
        } else if (0 != (codeStart and codeEnd)) {
            // both points are either below, above, left or right of the box, no intersection
            return false
        }
        return bias
    }

    fun length(): Double {
        return start.distance(end)
    }

    /**
     * The point `distance` from the start towards the end.
     */
    fun pointAlongLineSegment(distance: Double): Point {
        if (start.x == end.x) {
            // we have a vertical line
            return if (start.y > end.y) {
                Point(end.x, end.y + distance)
            } else {
                Point(start.x, start.y + distance)
            }
        }
        val slope = (end.y - start.y) / (end.x - start.x)
        var dx = Math.sqrt((distance * distance) / (1 + (slope * slope)))
        if (end.x < start.x) {
            dx *= -1
        }
        return Point(start.x + dx, start.y + slope * dx)
    }

    fun reverse(): LineSegment {
        return LineSegment(end, start)
    }

    /**
     * The part from `offset` to `offset + length` along this segment.
     */
    fun subSegment(offset: Double, length: Double): LineSegment {
        val subSegmentStart = pointAlongLineSegment(offset)
        val subSegmentEnd = pointAlongLineSegment(offset + length)
        return LineSegment(subSegmentStart, subSegmentEnd)
    }

    override fun toString(): String {
        return "$start $end"
    }

    private companion object {

        private const val INSIDE = 0

        private const val LEFT = 1

        private const val RIGHT = 2

        private const val BOTTOM = 4

        private const val TOP = 8

        // the Cohen-Sutherland location code of `p`
        fun code(r: Rectangle, p: Point): Int {
            var code = INSIDE
            if (p.x < r.left) {
                // to the left of clip window
                code = code or LEFT
            } else if (p.x > r.right) {
                // to the right of clip window
                code = code or RIGHT
            }
            if (p.y > r.bottom) {
                // below the clip window
                code = code or BOTTOM
            } else if (p.y < r.top) {
                // above the clip window
                code = code or TOP
            }
            return code
        }
    }
}
