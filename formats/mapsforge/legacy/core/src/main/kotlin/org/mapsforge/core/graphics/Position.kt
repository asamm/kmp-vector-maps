/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 devemux86
 * Copyright 2020 Adrian Batzill
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
package org.mapsforge.core.graphics

enum class Position {
    AUTO, CENTER, BELOW, BELOW_LEFT, BELOW_RIGHT, ABOVE, ABOVE_LEFT, ABOVE_RIGHT, LEFT, RIGHT;

    val isOnLeftSide: Boolean
        get() {
            return this == LEFT
                || this == ABOVE_LEFT
                || this == BELOW_LEFT
        }

    val isOnLowerSide: Boolean
        get() {
            return this == BELOW
                || this == BELOW_LEFT
                || this == BELOW_RIGHT
        }

    val isOnRightSide: Boolean
        get() {
            return this == RIGHT
                || this == ABOVE_RIGHT
                || this == BELOW_RIGHT
        }

    val isOnUpperSide: Boolean
        get() {
            return this == ABOVE
                || this == ABOVE_LEFT
                || this == ABOVE_RIGHT
        }

    companion object {

        @JvmStatic
        fun fromString(value: String?): Position {
            return when (value) {
                // auto positioning is implemented as below
                "auto" -> BELOW
                "center" -> CENTER
                "below" -> BELOW
                "below_left" -> BELOW_LEFT
                "below_right" -> BELOW_RIGHT
                "above" -> ABOVE
                "above_left" -> ABOVE_LEFT
                "above_right" -> ABOVE_RIGHT
                "left" -> LEFT
                "right" -> RIGHT
                else -> throw IllegalArgumentException("Invalid value for Position: $value")
            }
        }
    }
}
