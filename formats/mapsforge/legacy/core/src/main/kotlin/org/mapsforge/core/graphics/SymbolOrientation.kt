/*
 * Copyright mapsforge.org
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

enum class SymbolOrientation {

    /**
     * Rotated along the line, always readable.
     */
    AUTO,

    /**
     * As [AUTO], upside down.
     */
    AUTO_DOWN,

    /**
     * Rotated up where the path segment faces east.
     */
    RIGHT,

    /**
     * Rotated up where the path segment faces west.
     */
    LEFT,

    /**
     * Always rotated up.
     */
    UP,

    /**
     * Always rotated down.
     */
    DOWN;

    companion object {

        /**
         * The orientation for `value`, [AUTO] for "auto" and any unknown value.
         */
        @JvmStatic
        fun fromString(value: String): SymbolOrientation {
            return when (value) {
                "auto_down" -> AUTO_DOWN
                "right" -> RIGHT
                "left" -> LEFT
                "up" -> UP
                "down" -> DOWN
                else -> AUTO
            }
        }
    }
}
