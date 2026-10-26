/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015 devemux86
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

import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.reader.ReadBuffer

/**
 * The optional header fields, present as the flags byte says.
 */
internal class OptionalFields private constructor(flags: Byte) {

    var comment: String? = null
    var createdBy: String? = null
    val hasComment = flags.toInt() and HEADER_BITMASK_COMMENT != 0
    val hasCreatedBy = flags.toInt() and HEADER_BITMASK_CREATED_BY != 0
    val hasLanguagesPreference = flags.toInt() and HEADER_BITMASK_LANGUAGES_PREFERENCE != 0
    val hasStartPosition = flags.toInt() and HEADER_BITMASK_START_POSITION != 0
    val hasStartZoomLevel = flags.toInt() and HEADER_BITMASK_START_ZOOM_LEVEL != 0
    val isDebugFile = flags.toInt() and HEADER_BITMASK_DEBUG != 0
    var languagesPreference: String? = null
    var startPosition: LatLong? = null
    var startZoomLevel: Byte? = null

    private fun readLanguagesPreference(readBuffer: ReadBuffer) {
        if (hasLanguagesPreference) {
            languagesPreference = readBuffer.readUTF8EncodedString()
        }
    }

    private fun readMapStartPosition(readBuffer: ReadBuffer) {
        if (hasStartPosition) {
            val mapStartLatitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())
            val mapStartLongitude = LatLongUtils.microdegreesToDegrees(readBuffer.readInt())
            try {
                startPosition = LatLong(mapStartLatitude, mapStartLongitude)
            } catch (e: IllegalArgumentException) {
                throw MapFileException(e.toString())
            }
        }
    }

    private fun readMapStartZoomLevel(readBuffer: ReadBuffer) {
        if (hasStartZoomLevel) {
            // get and check the start zoom level (1 byte)
            val mapStartZoomLevel = readBuffer.readByte()
            if (mapStartZoomLevel < 0
                || mapStartZoomLevel > START_ZOOM_LEVEL_MAX
            ) {
                throw MapFileException("invalid map start zoom level: $mapStartZoomLevel")
            }
            startZoomLevel = mapStartZoomLevel
        }
    }

    private fun readOptionalFields(readBuffer: ReadBuffer) {
        readMapStartPosition(readBuffer)

        readMapStartZoomLevel(readBuffer)

        readLanguagesPreference(readBuffer)

        if (hasComment) {
            comment = readBuffer.readUTF8EncodedString()
        }

        if (hasCreatedBy) {
            createdBy = readBuffer.readUTF8EncodedString()
        }
    }

    companion object {

        private const val HEADER_BITMASK_COMMENT = 0x08

        private const val HEADER_BITMASK_CREATED_BY = 0x04

        private const val HEADER_BITMASK_DEBUG = 0x80

        private const val HEADER_BITMASK_LANGUAGES_PREFERENCE = 0x10

        private const val HEADER_BITMASK_START_POSITION = 0x40

        private const val HEADER_BITMASK_START_ZOOM_LEVEL = 0x20

        // maximum valid start zoom level
        private const val START_ZOOM_LEVEL_MAX = 22

        fun readOptionalFields(readBuffer: ReadBuffer, mapFileInfoBuilder: MapFileInfoBuilder) {
            val optionalFields = OptionalFields(readBuffer.readByte())
            mapFileInfoBuilder.optionalFields = optionalFields

            optionalFields.readOptionalFields(readBuffer)
        }
    }
}
