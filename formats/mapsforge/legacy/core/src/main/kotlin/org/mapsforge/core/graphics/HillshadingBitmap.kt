/*
 * Copyright 2017 usrusr
 * Copyright 2024 Sublimis
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

import org.mapsforge.core.model.BoundingBox

interface HillshadingBitmap : Bitmap {

    enum class Border(@JvmField val vertical: Boolean) {
        WEST(true), NORTH(false), EAST(true), SOUTH(false)
    }

    /**
     * The geographic bounds of the area inside the padding.
     */
    val areaRect: BoundingBox?

    /**
     * Optional padding, outside [areaRect].
     */
    val padding: Int

    /**
     * The size of this bitmap in bytes.
     */
    val sizeBytes: Long
}
