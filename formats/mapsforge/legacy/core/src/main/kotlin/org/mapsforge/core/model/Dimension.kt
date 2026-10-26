/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
 * A width and height; negative values throw [IllegalArgumentException].
 */
open class Dimension(width: Int, height: Int) : Serializable {

    @JvmField
    val height: Int

    @JvmField
    val width: Int

    init {
        if (width < 0) {
            throw IllegalArgumentException("width must not be negative: $width")
        } else if (height < 0) {
            throw IllegalArgumentException("height must not be negative: $height")
        }
        this.width = width
        this.height = height
    }

    val center: Point
        get() {
            return Point((width.toFloat() / 2).toDouble(), (height.toFloat() / 2).toDouble())
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Dimension) {
            return false
        }
        return width == other.width
            && height == other.height
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + width
        result = prime * result + height
        return result
    }

    override fun toString(): String {
        return "width=$width, height=$height"
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
