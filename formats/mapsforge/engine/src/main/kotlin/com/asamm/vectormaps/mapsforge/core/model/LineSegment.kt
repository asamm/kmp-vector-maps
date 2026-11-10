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
package com.asamm.vectormaps.mapsforge.core.model

/**
 * A directed line segment between two points.
 */
class LineSegment(
    val start: Point,
    val end: Point,
) {

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

    override fun toString(): String {
        return "$start $end"
    }
}
