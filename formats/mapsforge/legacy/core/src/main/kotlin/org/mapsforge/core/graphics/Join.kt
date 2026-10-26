/*
 * Copyright 2014 Ludwig M Brinckmann
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

/**
 * The shape of the joints between line segments.
 */
enum class Join {
    BEVEL, MITER, ROUND;

    companion object {

        @JvmStatic
        fun fromString(value: String?): Join {
            return when (value) {
                "bevel" -> BEVEL
                "round" -> ROUND
                "miter" -> MITER
                else -> throw IllegalArgumentException("Invalid value for Join: $value")
            }
        }
    }
}
