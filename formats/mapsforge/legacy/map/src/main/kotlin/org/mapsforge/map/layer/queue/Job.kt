/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 mvglasow <michael -at- vonglasow.com>
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
package org.mapsforge.map.layer.queue

import org.mapsforge.core.model.Tile
import java.io.File

/**
 * A unit of tile work, equal by tile and alpha; a null tile throws [IllegalArgumentException].
 */
open class Job(tile: Tile?, hasAlpha: Boolean) {

    @JvmField
    val hasAlpha: Boolean

    @JvmField
    val tile: Tile

    /**
     * A unique identifier for the tile, `zoom/x/y` with the platform's path separator between the
     * parts, the de-facto standard for tile references.
     */
    val key: String

    init {
        if (tile == null) {
            throw IllegalArgumentException("tile must not be null")
        }

        this.tile = tile
        this.hasAlpha = hasAlpha
        this.key = composeKey(this.tile.zoomLevel, this.tile.tileX.toLong(), this.tile.tileY.toLong())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is Job) {
            return false
        }
        return hasAlpha == other.hasAlpha
            && tile == other.tile
    }

    override fun hashCode(): Int {
        return tile.hashCode()
    }

    companion object {

        private fun composeKey(z: Byte, x: Long, y: Long): String {
            return z.toString() + File.separatorChar + x + File.separatorChar + y
        }

        @JvmStatic
        fun composeKey(z: String, x: String, y: String): String {
            return z + File.separatorChar + x + File.separatorChar + y
        }
    }
}
