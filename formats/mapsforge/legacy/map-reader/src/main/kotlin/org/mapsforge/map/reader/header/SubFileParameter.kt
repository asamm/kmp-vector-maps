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
package org.mapsforge.map.reader.header

import org.mapsforge.core.util.MercatorProjection

/**
 * The parameters of one sub-file; equal by start address, size and base zoom level.
 */
class SubFileParameter internal constructor(builder: SubFileParameterBuilder) {

    /**
     * Base zoom level of the sub-file, which equals to one block.
     */
    val baseZoomLevel: Byte = builder.baseZoomLevel

    /**
     * Absolute start address of the index in the enclosing file.
     */
    val indexStartAddress: Long = builder.indexStartAddress

    /**
     * Absolute start address of the sub-file in the enclosing file.
     */
    val startAddress: Long = builder.startAddress

    /**
     * Size of the sub-file in bytes.
     */
    val subFileSize: Long = builder.subFileSize

    /**
     * Maximum zoom level for which the block entries tables are made.
     */
    val zoomLevelMax: Byte = builder.zoomLevelMax

    /**
     * Minimum zoom level for which the block entries tables are made.
     */
    val zoomLevelMin: Byte = builder.zoomLevelMin

    private val hashCodeValue = calculateHashCode()

    /**
     * Y number of the tile at the bottom boundary in the grid.
     */
    val boundaryTileBottom: Long =
        MercatorProjection.latitudeToTileY(builder.boundingBox.minLatitude, baseZoomLevel).toLong()

    /**
     * X number of the tile at the left boundary in the grid.
     */
    val boundaryTileLeft: Long =
        MercatorProjection.longitudeToTileX(builder.boundingBox.minLongitude, baseZoomLevel).toLong()

    /**
     * Y number of the tile at the top boundary in the grid.
     */
    val boundaryTileTop: Long =
        MercatorProjection.latitudeToTileY(builder.boundingBox.maxLatitude, baseZoomLevel).toLong()

    /**
     * X number of the tile at the right boundary in the grid.
     */
    val boundaryTileRight: Long =
        MercatorProjection.longitudeToTileX(builder.boundingBox.maxLongitude, baseZoomLevel).toLong()

    /**
     * Horizontal amount of blocks in the grid.
     */
    val blocksWidth: Long = boundaryTileRight - boundaryTileLeft + 1

    /**
     * Vertical amount of blocks in the grid.
     */
    val blocksHeight: Long = boundaryTileBottom - boundaryTileTop + 1

    val numberOfBlocks: Long = blocksWidth * blocksHeight

    /**
     * Absolute end address of the index in the enclosing file.
     */
    val indexEndAddress: Long = indexStartAddress + numberOfBlocks * BYTES_PER_INDEX_ENTRY

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is SubFileParameter) {
            return false
        }
        return startAddress == other.startAddress
            && subFileSize == other.subFileSize
            && baseZoomLevel == other.baseZoomLevel
    }

    override fun hashCode(): Int {
        return hashCodeValue
    }

    private fun calculateHashCode(): Int {
        var result = 7
        result = 31 * result + (startAddress xor (startAddress ushr 32)).toInt()
        result = 31 * result + (subFileSize xor (subFileSize ushr 32)).toInt()
        result = 31 * result + baseZoomLevel
        return result
    }

    companion object {

        /**
         * Number of bytes a single index entry consists of.
         */
        const val BYTES_PER_INDEX_ENTRY: Byte = 5
    }
}
