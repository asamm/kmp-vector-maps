/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
package org.mapsforge.map.reader

import org.mapsforge.core.model.Tile

/**
 * The 16-bit masks that select a tile's part of a base tile, one bit per second-level sub-tile.
 */
internal object QueryCalculations {

    fun calculateTileBitmask(tile: Tile, zoomLevelDifference: Int): Int {
        if (zoomLevelDifference == 1) {
            return getFirstLevelTileBitmask(tile)
        }

        // calculate the XY numbers of the second level sub-tile
        val subtileX = (tile.tileX ushr (zoomLevelDifference - 2)).toLong()
        val subtileY = (tile.tileY ushr (zoomLevelDifference - 2)).toLong()

        // calculate the XY numbers of the parent tile
        val parentTileX = subtileX ushr 1
        val parentTileY = subtileY ushr 1

        // determine the correct bitmask for all 16 sub-tiles
        return if (parentTileX % 2 == 0L && parentTileY % 2 == 0L) {
            getSecondLevelTileBitmaskUpperLeft(subtileX, subtileY)
        } else if (parentTileX % 2 == 1L && parentTileY % 2 == 0L) {
            getSecondLevelTileBitmaskUpperRight(subtileX, subtileY)
        } else if (parentTileX % 2 == 0L && parentTileY % 2 == 1L) {
            getSecondLevelTileBitmaskLowerLeft(subtileX, subtileY)
        } else {
            getSecondLevelTileBitmaskLowerRight(subtileX, subtileY)
        }
    }

    fun calculateTileBitmask(upperLeft: Tile, lowerRight: Tile, zoomLevelDifference: Int): Int {
        var bitmask = 0
        for (x in upperLeft.tileX..lowerRight.tileX) {
            for (y in upperLeft.tileY..lowerRight.tileY) {
                val current = Tile(x, y, upperLeft.zoomLevel, upperLeft.tileSize)
                bitmask = bitmask or calculateTileBitmask(current, zoomLevelDifference)
            }
        }
        return bitmask
    }

    private fun getFirstLevelTileBitmask(tile: Tile): Int {
        return if (tile.tileX % 2 == 0 && tile.tileY % 2 == 0) {
            // upper left quadrant
            0xcc00
        } else if (tile.tileX % 2 == 1 && tile.tileY % 2 == 0) {
            // upper right quadrant
            0x3300
        } else if (tile.tileX % 2 == 0 && tile.tileY % 2 == 1) {
            // lower left quadrant
            0xcc
        } else {
            // lower right quadrant
            0x33
        }
    }

    private fun getSecondLevelTileBitmaskLowerLeft(subtileX: Long, subtileY: Long): Int {
        return secondLevel(subtileX, subtileY, upperLeft = 0x80, upperRight = 0x40, lowerLeft = 0x8, lowerRight = 0x4)
    }

    private fun getSecondLevelTileBitmaskLowerRight(subtileX: Long, subtileY: Long): Int {
        return secondLevel(subtileX, subtileY, upperLeft = 0x20, upperRight = 0x10, lowerLeft = 0x2, lowerRight = 0x1)
    }

    private fun getSecondLevelTileBitmaskUpperLeft(subtileX: Long, subtileY: Long): Int {
        return secondLevel(
            subtileX,
            subtileY,
            upperLeft = 0x8000,
            upperRight = 0x4000,
            lowerLeft = 0x800,
            lowerRight = 0x400,
        )
    }

    private fun getSecondLevelTileBitmaskUpperRight(subtileX: Long, subtileY: Long): Int {
        return secondLevel(
            subtileX,
            subtileY,
            upperLeft = 0x2000,
            upperRight = 0x1000,
            lowerLeft = 0x200,
            lowerRight = 0x100,
        )
    }

    // the bit of the sub-tile within its parent's quadrant
    private fun secondLevel(
        subtileX: Long,
        subtileY: Long,
        upperLeft: Int,
        upperRight: Int,
        lowerLeft: Int,
        lowerRight: Int,
    ): Int {
        return if (subtileX % 2 == 0L && subtileY % 2 == 0L) {
            upperLeft
        } else if (subtileX % 2 == 1L && subtileY % 2 == 0L) {
            upperRight
        } else if (subtileX % 2 == 0L && subtileY % 2 == 1L) {
            lowerLeft
        } else {
            lowerRight
        }
    }
}
