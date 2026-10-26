/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2024-2025 Sublimis
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
 * An immutable rectangle; left above right or top below bottom throws
 * [IllegalArgumentException].
 */
open class Rectangle : Serializable {

    @JvmField
    val bottom: Double

    @JvmField
    val left: Double

    @JvmField
    val right: Double

    @JvmField
    val top: Double

    constructor(left: Double, top: Double, right: Double, bottom: Double) {
        check(left, top, right, bottom)
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    /**
     * The envelope of four points in any order.
     */
    constructor(a: Point, b: Point, c: Point, d: Point) {
        left = minimum(a.x, b.x, c.x, d.x)
        right = maximum(a.x, b.x, c.x, d.x)
        bottom = maximum(a.y, b.y, c.y, d.y)
        top = minimum(a.y, b.y, c.y, d.y)
        check(left, top, right, bottom)
    }

    fun contains(point: Point): Boolean {
        return left <= point.x
            && right >= point.x
            && top <= point.y
            && bottom >= point.y
    }

    /**
     * This rectangle with each side moved out by its own amount.
     */
    fun enlarge(left: Double, top: Double, right: Double, bottom: Double): Rectangle {
        return Rectangle(this.left - left, this.top - top, this.right + right, this.bottom + bottom)
    }

    fun envelope(padding: Double): Rectangle {
        return Rectangle(left - padding, top - padding, right + padding, bottom + padding)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Rectangle) {
            return false
        }
        return java.lang.Double.doubleToLongBits(left) == java.lang.Double.doubleToLongBits(other.left)
            && java.lang.Double.doubleToLongBits(top) == java.lang.Double.doubleToLongBits(other.top)
            && java.lang.Double.doubleToLongBits(right) == java.lang.Double.doubleToLongBits(other.right)
            && java.lang.Double.doubleToLongBits(bottom) == java.lang.Double.doubleToLongBits(other.bottom)
    }

    val center: Point
        get() {
            return Point(centerX, centerY)
        }

    val centerX: Double
        get() {
            return (left + right) / 2
        }

    val centerY: Double
        get() {
            return (top + bottom) / 2
        }

    val height: Double
        get() {
            return bottom - top
        }

    val width: Double
        get() {
            return right - left
        }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        var temp = java.lang.Double.doubleToLongBits(left)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(top)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(right)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(bottom)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    /**
     * False for null.
     */
    fun intersects(rectangle: Rectangle?): Boolean {
        if (rectangle == null) {
            return false
        }
        if (this === rectangle) {
            return true
        }
        return left <= rectangle.right
            && rectangle.left <= right
            && top <= rectangle.bottom
            && rectangle.top <= bottom
    }

    /**
     * False for null.
     */
    fun contains(rectangle: Rectangle?): Boolean {
        if (rectangle == null) {
            return false
        }
        if (this === rectangle) {
            return true
        }
        return left <= rectangle.left
            && right >= rectangle.right
            && top <= rectangle.top
            && bottom >= rectangle.bottom
    }

    fun intersectsCircle(pointX: Double, pointY: Double, radius: Double): Boolean {
        val halfWidth = width / 2
        val halfHeight = height / 2

        val centerDistanceX = Math.abs(pointX - centerX)
        val centerDistanceY = Math.abs(pointY - centerY)

        // is the circle is far enough away from the rectangle?
        if (centerDistanceX > halfWidth + radius) {
            return false
        } else if (centerDistanceY > halfHeight + radius) {
            return false
        }

        // is the circle close enough to the rectangle?
        if (centerDistanceX <= halfWidth) {
            return true
        } else if (centerDistanceY <= halfHeight) {
            return true
        }

        val cornerDistanceX = centerDistanceX - halfWidth
        val cornerDistanceY = centerDistanceY - halfHeight
        return cornerDistanceX * cornerDistanceX + cornerDistanceY * cornerDistanceY <= radius * radius
    }

    fun rotate(rotation: Rotation): Rectangle {
        return rotation.rotate(this)
    }

    /**
     * This rectangle moved by `origin`; this instance for (0, 0).
     */
    fun shift(origin: Point): Rectangle {
        if (origin.x == 0.0
            && origin.y == 0.0
        ) {
            return this
        }
        return Rectangle(left + origin.x, top + origin.y, right + origin.x, bottom + origin.y)
    }

    /**
     * This rectangle with each side beyond ±`horizontal` / ±`vertical` clamped to it, when it is
     * wider or taller than those.
     */
    fun clampClipCoordinates(horizontal: Double, vertical: Double): Rectangle {
        var output = this
        if (output.width > horizontal
            || output.height > vertical
        ) {
            val newLeft = if (Math.abs(output.left) > horizontal) Math.signum(output.left) * horizontal else output.left
            val newTop = if (Math.abs(output.top) > vertical) Math.signum(output.top) * vertical else output.top
            val newRight = if (Math.abs(output.right) > horizontal) {
                Math.signum(output.right) * horizontal
            } else {
                output.right
            }
            val newBottom = if (Math.abs(output.bottom) > vertical) {
                Math.signum(output.bottom) * vertical
            } else {
                output.bottom
            }
            output = Rectangle(newLeft, newTop, newRight, newBottom)
        }
        return output
    }

    override fun toString(): String {
        return "left=$left, top=$top, right=$right, bottom=$bottom"
    }

    private companion object {

        private const val serialVersionUID = 1L

        fun minimum(a: Double, b: Double, c: Double, d: Double): Double {
            var min = a
            if (b < min) {
                min = b
            }
            if (c < min) {
                min = c
            }
            if (d < min) {
                min = d
            }
            return min
        }

        fun maximum(a: Double, b: Double, c: Double, d: Double): Double {
            var max = a
            if (b > max) {
                max = b
            }
            if (c > max) {
                max = c
            }
            if (d > max) {
                max = d
            }
            return max
        }

        fun check(left: Double, top: Double, right: Double, bottom: Double) {
            if (left > right) {
                throw IllegalArgumentException("left: $left, right: $right")
            } else if (top > bottom) {
                throw IllegalArgumentException("top: $top, bottom: $bottom")
            }
        }
    }
}
