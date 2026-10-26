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
package org.mapsforge.map.reader.header

import org.mapsforge.map.datastore.MapCallback
import org.mapsforge.map.reader.ReadBuffer
import java.io.IOException

/**
 * Reads and validates the header of a binary map file.
 */
class MapFileHeader {

    /**
     * The header data; set by [readHeader].
     */
    lateinit var mapFileInfo: MapFileInfo
        private set

    private lateinit var subFileParameters: Array<SubFileParameter?>
    private var zoomLevelMaximum: Byte = 0
    private var zoomLevelMinimum: Byte = 0

    /**
     * The zoom level closest to `zoomLevel` that a sub-file covers, adjusted by `mapCallback`
     * within the range.
     */
    fun getQueryZoomLevel(zoomLevel: Byte, mapCallback: MapCallback?): Byte {
        if (zoomLevel > zoomLevelMaximum) {
            return zoomLevelMaximum
        } else if (zoomLevel < zoomLevelMinimum) {
            return zoomLevelMinimum
        }
        if (mapCallback != null) {
            return mapCallback.getQueryZoomLevel(zoomLevel)
        }
        return zoomLevel
    }

    /**
     * The sub-file for `queryZoomLevel`, null when none covers that in-range level.
     */
    fun getSubFileParameter(queryZoomLevel: Int): SubFileParameter? {
        return subFileParameters[queryZoomLevel]
    }

    /**
     * Reads and validates the header; throws [MapFileException] on invalid data.
     */
    @Throws(IOException::class)
    fun readHeader(readBuffer: ReadBuffer, fileSize: Long) {
        RequiredFields.readMagicByte(readBuffer)
        RequiredFields.readRemainingHeader(readBuffer)

        val mapFileInfoBuilder = MapFileInfoBuilder()

        RequiredFields.readFileVersion(readBuffer, mapFileInfoBuilder)

        RequiredFields.readFileSize(readBuffer, fileSize, mapFileInfoBuilder)

        RequiredFields.readMapDate(readBuffer, mapFileInfoBuilder)

        RequiredFields.readBoundingBox(readBuffer, mapFileInfoBuilder)

        RequiredFields.readTilePixelSize(readBuffer, mapFileInfoBuilder)

        RequiredFields.readProjectionName(readBuffer, mapFileInfoBuilder)

        OptionalFields.readOptionalFields(readBuffer, mapFileInfoBuilder)

        RequiredFields.readPoiTags(readBuffer, mapFileInfoBuilder)

        RequiredFields.readWayTags(readBuffer, mapFileInfoBuilder)

        readSubFileParameters(readBuffer, fileSize, mapFileInfoBuilder)

        mapFileInfo = mapFileInfoBuilder.build()
    }

    private fun readSubFileParameters(readBuffer: ReadBuffer, fileSize: Long, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get and check the number of sub-files (1 byte)
        val numberOfSubFiles = readBuffer.readByte()
        if (numberOfSubFiles < 1) {
            throw MapFileException("invalid number of sub-files: $numberOfSubFiles")
        }
        mapFileInfoBuilder.numberOfSubFiles = numberOfSubFiles

        val tempSubFileParameters = arrayOfNulls<SubFileParameter>(numberOfSubFiles.toInt())
        zoomLevelMinimum = Byte.MAX_VALUE
        zoomLevelMaximum = Byte.MIN_VALUE

        // get and check the information for each sub-file
        for (currentSubFile in 0 until numberOfSubFiles) {
            val subFileParameterBuilder = SubFileParameterBuilder()

            // get and check the base zoom level (1 byte)
            val baseZoomLevel = readBuffer.readByte()
            if (baseZoomLevel < 0
                || baseZoomLevel > BASE_ZOOM_LEVEL_MAX
            ) {
                throw MapFileException("invalid base zoom level: $baseZoomLevel")
            }
            subFileParameterBuilder.baseZoomLevel = baseZoomLevel

            // get and check the minimum zoom level (1 byte)
            val zoomLevelMin = readBuffer.readByte()
            if (zoomLevelMin < 0
                || zoomLevelMin > 22
            ) {
                throw MapFileException("invalid minimum zoom level: $zoomLevelMin")
            }
            subFileParameterBuilder.zoomLevelMin = zoomLevelMin

            // get and check the maximum zoom level (1 byte)
            val zoomLevelMax = readBuffer.readByte()
            if (zoomLevelMax < 0
                || zoomLevelMax > 22
            ) {
                throw MapFileException("invalid maximum zoom level: $zoomLevelMax")
            }
            subFileParameterBuilder.zoomLevelMax = zoomLevelMax

            // check for valid zoom level range
            if (zoomLevelMin > zoomLevelMax) {
                throw MapFileException("invalid zoom level range: $zoomLevelMin $zoomLevelMax")
            }

            // get and check the start address of the sub-file (8 bytes)
            val startAddress = readBuffer.readLong()
            if (startAddress < HEADER_SIZE_MIN
                || startAddress >= fileSize
            ) {
                throw MapFileException("invalid start address: $startAddress")
            }
            subFileParameterBuilder.startAddress = startAddress

            var indexStartAddress = startAddress
            if (mapFileInfoBuilder.optionalFields.isDebugFile) {
                // the sub-file has an index signature before the index
                indexStartAddress += SIGNATURE_LENGTH_INDEX
            }
            subFileParameterBuilder.indexStartAddress = indexStartAddress

            // get and check the size of the sub-file (8 bytes)
            val subFileSize = readBuffer.readLong()
            if (subFileSize < 1) {
                throw MapFileException("invalid sub-file size: $subFileSize")
            }
            subFileParameterBuilder.subFileSize = subFileSize

            subFileParameterBuilder.boundingBox = mapFileInfoBuilder.boundingBox

            // add the current sub-file to the list of sub-files
            val subFileParameter = subFileParameterBuilder.build()
            tempSubFileParameters[currentSubFile] = subFileParameter

            // update the global minimum and maximum zoom level information
            if (zoomLevelMinimum > subFileParameter.zoomLevelMin) {
                zoomLevelMinimum = subFileParameter.zoomLevelMin
                mapFileInfoBuilder.zoomLevelMin = zoomLevelMinimum
            }
            if (zoomLevelMaximum < subFileParameter.zoomLevelMax) {
                zoomLevelMaximum = subFileParameter.zoomLevelMax
                mapFileInfoBuilder.zoomLevelMax = zoomLevelMaximum
            }
        }

        // create and fill the lookup table for the sub-files
        val lookup = arrayOfNulls<SubFileParameter>(zoomLevelMaximum + 1)
        for (subFileParameter in tempSubFileParameters) {
            checkNotNull(subFileParameter)
            for (zoomLevel in subFileParameter.zoomLevelMin..subFileParameter.zoomLevelMax) {
                lookup[zoomLevel] = subFileParameter
            }
        }
        subFileParameters = lookup
    }

    private companion object {

        // maximum valid base zoom level of a sub-file
        const val BASE_ZOOM_LEVEL_MAX = 20

        // minimum size of the file header in bytes
        const val HEADER_SIZE_MIN = 70

        // length of the debug signature at the beginning of the index
        const val SIGNATURE_LENGTH_INDEX: Byte = 16
    }
}
