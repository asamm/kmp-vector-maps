/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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

import java.io.Serializable
import java.util.Objects

/**
 * An immutable pair of double coordinates.
 */
open class Point(
    @JvmField val x: Double,
    @JvmField val y: Double,
) : Comparable<Point>, Serializable {

    override fun compareTo(other: Point): Int {
        return compareCoord(x, y, other.x, other.y)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Point) {
            return false
        }
        return java.lang.Double.compare(x, other.x) == 0
            && java.lang.Double.compare(y, other.y) == 0
    }

    override fun hashCode(): Int {
        return Objects.hash(x, y)
    }

    /**
     * The Euclidean distance to `point`.
     */
    fun distance(point: Point): Double {
        return Math.hypot(x - point.x, y - point.y)
    }

    /**
     * This point moved by (`dx`, `dy`); this instance when both are 0.
     */
    fun offset(dx: Double, dy: Double): Point {
        if (0.0 == dx
            && 0.0 == dy
        ) {
            return this
        }
        return Point(x + dx, y + dy)
    }

    fun rotate(rotation: Rotation): Point {
        return rotation.rotate(this)
    }

    override fun toString(): String {
        return "x=$x, y=$y"
    }

    companion object {

        private const val serialVersionUID = 1L

        /**
         * Orders by x, then y.
         */
        @JvmStatic
        fun compareCoord(x1: Double, y1: Double, x2: Double, y2: Double): Int {
            if (x1 > x2) {
                return 1
            } else if (x1 < x2) {
                return -1
            } else if (y1 > y2) {
                return 1
            } else if (y1 < y2) {
                return -1
            }
            return 0
        }
    }
}
