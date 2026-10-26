/*
 * Copyright 2019 Adrian Batzill
 * Copyright 2025 Sublimis
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
 * A polyline as a list of segments.
 */
open class LineString {

    @JvmField
    val segments: MutableList<LineSegment> = ArrayList()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LineString) {
            return false
        }
        if (other.segments.size != segments.size) {
            return false
        }
        for (i in segments.indices) {
            if (segments[i] != other.segments[i]) {
                return false
            }
        }
        return true
    }

    /**
     * The part between `startDistance` and `endDistance` along the line.
     */
    fun extractPart(startDistance: Double, endDistance: Double): LineString {
        val result = LineString()
        var start = startDistance
        var end = endDistance

        var i = 0
        while (i < segments.size) {
            val segment = segments[i]

            // Skip first segments that we don't need; negated, so NaN runs the body as upstream
            val length = segment.length()
            if (!(length < start)) {
                var startPoint: Point? = null
                var endPoint: Point? = null
                if (start >= 0) {
                    // This will be our starting point
                    startPoint = segment.pointAlongLineSegment(start)
                }
                if (end < length) {
                    // this will be our ending point
                    endPoint = segment.pointAlongLineSegment(end)
                }

                if (startPoint != null
                    && endPoint == null
                ) {
                    // This ist the starting segment, end will come in a later segment
                    result.segments.add(LineSegment(startPoint, segment.end))
                } else if (startPoint == null
                    && endPoint == null
                ) {
                    // Center segment between start and end segment, add completely
                    result.segments.add(segment)
                } else if (startPoint == null
                    && endPoint != null
                ) {
                    // End segment, start was in earlier segment
                    result.segments.add(LineSegment(segment.start, endPoint))
                } else if (startPoint != null
                    && endPoint != null
                ) {
                    // Start and end on same segment
                    result.segments.add(LineSegment(startPoint, endPoint))
                }

                if (endPoint != null) {
                    break
                }
            }
            // the loop update of the upstream for statement
            start -= segments[i].length()
            end -= segments[i].length()
            i++
        }

        return result
    }

    val bounds: Rectangle
        get() {
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -minX
            var maxY = -minY

            for (segment in segments) {
                minX = Math.min(minX, Math.min(segment.start.x, segment.end.x))
                minY = Math.min(minY, Math.min(segment.start.y, segment.end.y))
                maxX = Math.max(maxX, Math.max(segment.start.x, segment.end.x))
                maxY = Math.max(maxY, Math.max(segment.start.y, segment.end.y))
            }
            return Rectangle(minX, minY, maxX, maxY)
        }

    /**
     * The point `distance` along the line, null when it lies outside 0 to [length].
     */
    fun interpolate(distance: Double): Point? {
        if (distance < 0) {
            return null
        }
        var remaining = distance
        for (segment in segments) {
            val length = segment.length()
            if (remaining <= length) {
                return segment.pointAlongLineSegment(remaining)
            }
            remaining -= length
        }
        return null
    }

    fun length(): Double {
        var result = 0.0
        for (segment in segments) {
            result += segment.length()
        }
        return result
    }
}
