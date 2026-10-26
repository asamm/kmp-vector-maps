/*
 * Copyright 2014 Ludwig M Brinckmann
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
package org.mapsforge.core.graphics

/**
 * Whether and how a map element is displayed.
 *
 * - [IFSPACE], the default: displayed if there is space for it, also by priority.
 * - [ORDER]: always displayed at the expense of [IFSPACE] elements; of two colliding [ORDER]
 *   elements one is chosen, by priority, so they never overlap.
 * - [ALWAYS]: always displayed, overlap or not, so others can cover it.
 * - [NEVER]: never displayed.
 */
enum class Display {
    IFSPACE, ORDER, ALWAYS, NEVER;

    companion object {

        @JvmStatic
        fun fromString(value: String?): Display {
            return when (value) {
                "ifspace" -> IFSPACE
                "order" -> ORDER
                "always" -> ALWAYS
                "never" -> NEVER
                else -> throw IllegalArgumentException("Invalid value for Display: $value")
            }
        }
    }
}
