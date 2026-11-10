/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
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
package com.asamm.vectormaps.mapsforge.map.reader

import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.reader.header.SubFileParameter

/**
 * The base tiles and sub-file blocks one read covers.
 */
internal class QueryParameters {

    var fromBaseTileX = 0L
    var fromBaseTileY = 0L
    var fromBlockX = 0L
    var fromBlockY = 0L
    var queryTileBitmask = 0
    var queryZoomLevel = 0
    var toBaseTileX = 0L
    var toBaseTileY = 0L
    var toBlockX = 0L
    var toBlockY = 0L
    var useTileBitmask = false

    fun calculateBaseTiles(upperLeft: Tile, lowerRight: Tile, subFileParameter: SubFileParameter) {
        if (upperLeft.zoomLevel < subFileParameter.baseZoomLevel) {
            // here we need to combine multiple base tiles
            val zoomLevelDifference = subFileParameter.baseZoomLevel - upperLeft.zoomLevel
            fromBaseTileX = (upperLeft.tileX shl zoomLevelDifference).toLong()
            fromBaseTileY = (upperLeft.tileY shl zoomLevelDifference).toLong()
            toBaseTileX = ((lowerRight.tileX shl zoomLevelDifference) + (1 shl zoomLevelDifference) - 1).toLong()
            toBaseTileY = ((lowerRight.tileY shl zoomLevelDifference) + (1 shl zoomLevelDifference) - 1).toLong()
            useTileBitmask = false
        } else if (upperLeft.zoomLevel > subFileParameter.baseZoomLevel) {
            // we might have more than just one base tile as we might span boundaries
            val zoomLevelDifference = upperLeft.zoomLevel - subFileParameter.baseZoomLevel
            fromBaseTileX = (upperLeft.tileX ushr zoomLevelDifference).toLong()
            fromBaseTileY = (upperLeft.tileY ushr zoomLevelDifference).toLong()
            toBaseTileX = (lowerRight.tileX ushr zoomLevelDifference).toLong()
            toBaseTileY = (lowerRight.tileY ushr zoomLevelDifference).toLong()
            // upstream: unclear how one bitmask can select from a set of base tiles
            useTileBitmask = true
            queryTileBitmask = QueryCalculations.calculateTileBitmask(upperLeft, lowerRight, zoomLevelDifference)
        } else {
            // we are on the base zoom level, so we just need all tiles in range
            fromBaseTileX = upperLeft.tileX.toLong()
            fromBaseTileY = upperLeft.tileY.toLong()
            toBaseTileX = lowerRight.tileX.toLong()
            toBaseTileY = lowerRight.tileY.toLong()
            useTileBitmask = false
        }
    }

    fun calculateBlocks(subFileParameter: SubFileParameter) {
        // calculate the blocks in the file which need to be read
        fromBlockX = maxOf(fromBaseTileX - subFileParameter.boundaryTileLeft, 0)
        fromBlockY = maxOf(fromBaseTileY - subFileParameter.boundaryTileTop, 0)
        toBlockX = minOf(toBaseTileX - subFileParameter.boundaryTileLeft, subFileParameter.blocksWidth - 1)
        toBlockY = minOf(toBaseTileY - subFileParameter.boundaryTileTop, subFileParameter.blocksHeight - 1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is QueryParameters) {
            return false
        }
        return fromBaseTileX == other.fromBaseTileX
            && fromBlockX == other.fromBlockX
            && fromBaseTileY == other.fromBaseTileY
            && fromBlockY == other.fromBlockY
            && queryTileBitmask == other.queryTileBitmask
            && queryZoomLevel == other.queryZoomLevel
            && toBaseTileX == other.toBaseTileX
            && toBaseTileY == other.toBaseTileY
            && toBlockX == other.toBlockX
            && toBlockY == other.toBlockY
            && useTileBitmask == other.useTileBitmask
    }

    override fun hashCode(): Int {
        var result = 7
        result = 31 * result + (fromBaseTileX xor (fromBaseTileX ushr 16)).toInt()
        result = 31 * result + (fromBaseTileY xor (fromBaseTileY ushr 16)).toInt()
        result = 31 * result + (toBaseTileX xor (toBaseTileX ushr 16)).toInt()
        result = 31 * result + (toBaseTileY xor (toBaseTileY ushr 16)).toInt()
        result = 31 * result + (fromBlockX xor (fromBlockX ushr 16)).toInt()
        result = 31 * result + (fromBlockY xor (fromBlockY ushr 16)).toInt()
        result = 31 * result + (toBlockX xor (toBlockX ushr 16)).toInt()
        result = 31 * result + (toBlockY xor (toBlockY ushr 16)).toInt()
        result = 31 * result + queryZoomLevel
        result = 31 * result + queryTileBitmask
        return result
    }
}
