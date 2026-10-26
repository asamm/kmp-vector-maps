/*
 * Copyright 2015 Ludwig M Brinckmann
 * Copyright 2024 devemux86
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

/**
 * A rotation by an angle around a pivot point.
 */
open class Rotation(
    @JvmField val degrees: Float,
    @JvmField val px: Float,
    @JvmField val py: Float,
) : Serializable {

    @JvmField
    val radians: Double = Math.toRadians(degrees.toDouble())

    /**
     * This rotation with the angle reversed.
     */
    fun reverseRotation(): Rotation {
        if (noRotation(this)) {
            return NULL_ROTATION
        }
        return Rotation(-degrees, px, py)
    }

    /**
     * The point (`x`, `y`) rotated around the pivot.
     */
    @JvmOverloads
    fun rotate(x: Double, y: Double, reverse: Boolean = false): Point {
        if (reverse) {
            return rotateInternal(radians, x, y)
        }
        return rotateInternal(-radians, x, y)
    }

    /**
     * `p` rotated around the pivot; `p` itself without a rotation.
     */
    @JvmOverloads
    fun rotate(p: Point, reverse: Boolean = false): Point {
        if (noRotation(this)) {
            return p
        }
        return rotate(p.x, p.y, reverse)
    }

    /**
     * The envelope of `r` rotated around the pivot; `r` itself without a rotation.
     */
    @JvmOverloads
    fun rotate(r: Rectangle, reverse: Boolean = false): Rectangle {
        if (noRotation(this)) {
            return r
        }
        val lowerRight = rotate(r.right, r.bottom, reverse)
        val upperLeft = rotate(r.left, r.top, reverse)
        val lowerLeft = rotate(r.left, r.bottom, reverse)
        val upperRight = rotate(r.right, r.top, reverse)
        return Rectangle(lowerLeft, upperLeft, lowerRight, upperRight)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Rotation) {
            return false
        }
        if (noRotation(this)) {
            return noRotation(other)
        }
        if (noRotation(other)) {
            return noRotation(this)
        }
        return java.lang.Float.floatToIntBits(px) == java.lang.Float.floatToIntBits(other.px)
            && java.lang.Float.floatToIntBits(py) == java.lang.Float.floatToIntBits(other.py)
            && java.lang.Double.doubleToLongBits(radians) == java.lang.Double.doubleToLongBits(other.radians)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        var temp: Long = java.lang.Float.floatToIntBits(px).toLong()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Float.floatToIntBits(py).toLong()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(radians)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun toString(): String {
        return "degrees=$degrees, px=$px, py=$py, radians=$radians"
    }

    private fun rotateInternal(radians: Double, x: Double, y: Double): Point {
        if (noRotation(this)) {
            return Point(x, y)
        }
        val cosTheta = Math.cos(radians)
        val sinTheta = Math.sin(radians)
        val rotatedX = (x - px) * cosTheta - (y - py) * sinTheta + px
        val rotatedY = (x - px) * sinTheta + (y - py) * cosTheta + py
        return Point(rotatedX, rotatedY)
    }

    companion object {

        private const val serialVersionUID = 1L

        @JvmField
        val NULL_ROTATION = Rotation(0f, 0f, 0f)

        /**
         * True for null, [NULL_ROTATION] or an angle of 0.
         */
        @JvmStatic
        fun noRotation(rotation: Rotation?): Boolean {
            return NULL_ROTATION === rotation
                || rotation == null
                || rotation.degrees == 0f
        }
    }
}
