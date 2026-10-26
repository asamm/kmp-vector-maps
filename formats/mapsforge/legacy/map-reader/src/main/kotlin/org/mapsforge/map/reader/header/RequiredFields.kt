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

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Tag
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.reader.ReadBuffer
import java.io.IOException

/**
 * Reads and checks the required header fields.
 */
internal object RequiredFields {

    // magic bytes at the beginning of a valid binary map file
    private const val BINARY_OSM_MAGIC_BYTE = "mapsforge binary OSM"

    private const val HEADER_SIZE_MAX = 1000000

    private const val HEADER_SIZE_MIN = 70

    // the name of the Mercator projection as stored in the file header
    private const val MERCATOR = "Mercator"

    private const val SUPPORTED_FILE_VERSION_MIN = 3

    private const val SUPPORTED_FILE_VERSION_MAX = 5

    fun readBoundingBox(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        val minLatitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())
        val minLongitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())
        val maxLatitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())
        val maxLongitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())

        try {
            mapFileInfoBuilder.boundingBox = BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude)
        } catch (e: IllegalArgumentException) {
            throw MapFileException(e.toString())
        }
    }

    fun readFileSize(readBuffer: ReadBuffer, fileSize: Long, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get and check the file size (8 bytes)
        val headerFileSize = readBuffer.readLong()
        if (headerFileSize != fileSize) {
            throw MapFileException("invalid file size: $headerFileSize")
        }
        mapFileInfoBuilder.fileSize = fileSize
    }

    fun readFileVersion(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get and check the file version (4 bytes)
        val fileVersion = readBuffer.readInt()
        if (fileVersion < SUPPORTED_FILE_VERSION_MIN
            || fileVersion > SUPPORTED_FILE_VERSION_MAX
        ) {
            throw MapFileException("unsupported file version: $fileVersion")
        }
        mapFileInfoBuilder.fileVersion = fileVersion
    }

    @Throws(IOException::class)
    fun readMagicByte(readBuffer: ReadBuffer) {
        // read the the magic byte and the file header size into the buffer
        val magicByteLength = BINARY_OSM_MAGIC_BYTE.length
        if (!readBuffer.readFromFile(magicByteLength + 4)) {
            throw MapFileException("reading magic byte has failed")
        }

        // get and check the magic byte
        val magicByte = readBuffer.readUTF8EncodedString(magicByteLength)
        if (BINARY_OSM_MAGIC_BYTE != magicByte) {
            throw MapFileException("invalid magic byte: $magicByte")
        }
    }

    fun readMapDate(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get and check the the map date (8 bytes)
        val mapDate = readBuffer.readLong()
        // is the map date before 2010-01-10 ?
        if (mapDate < 1200000000000L) {
            throw MapFileException("invalid map date: $mapDate")
        }
        mapFileInfoBuilder.mapDate = mapDate
    }

    fun readPoiTags(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        mapFileInfoBuilder.poiTags = readTags(readBuffer = readBuffer, kind = "POI")
    }

    fun readProjectionName(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get and check the projection name
        val projectionName = readBuffer.readUTF8EncodedString()
        if (MERCATOR != projectionName) {
            throw MapFileException("unsupported projection: $projectionName")
        }
        mapFileInfoBuilder.projectionName = projectionName
    }

    @Throws(IOException::class)
    fun readRemainingHeader(readBuffer: ReadBuffer) {
        // get and check the size of the remaining file header (4 bytes)
        val remainingHeaderSize = readBuffer.readInt()
        if (remainingHeaderSize < HEADER_SIZE_MIN
            || remainingHeaderSize > HEADER_SIZE_MAX
        ) {
            throw MapFileException("invalid remaining header size: $remainingHeaderSize")
        }

        // read the header data into the buffer
        if (!readBuffer.readFromFile(remainingHeaderSize)) {
            throw MapFileException("reading header data has failed: $remainingHeaderSize")
        }
    }

    fun readTilePixelSize(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        // get the tile pixel size (2 bytes), not checked
        mapFileInfoBuilder.tilePixelSize = readBuffer.readShort()
    }

    fun readWayTags(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
        mapFileInfoBuilder.wayTags = readTags(readBuffer = readBuffer, kind = "way")
    }

    // the count (2 bytes), then each tag as a string
    private fun readTags(readBuffer: ReadBuffer, kind: String): Array<Tag> {
        val numberOfTags = readBuffer.readShort()
        if (numberOfTags < 0) {
            throw MapFileException("invalid number of $kind tags: $numberOfTags")
        }
        return Array(numberOfTags) { currentTagId ->
            val tag = readBuffer.readUTF8EncodedString()
                ?: throw MapFileException("$kind tag must not be null: $currentTagId")
            Tag(tag)
        }
    }
}
