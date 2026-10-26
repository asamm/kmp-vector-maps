/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016 devemux86
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
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tag

/**
 * The immutable metadata of a map file.
 */
class MapFileInfo internal constructor(builder: MapFileInfoBuilder) {

    val comment: String? = builder.optionalFields.comment

    val createdBy: String? = builder.optionalFields.createdBy

    /**
     * True if the map file includes debug information.
     */
    val debugFile: Boolean = builder.optionalFields.isDebugFile

    /**
     * The size of the map file in bytes.
     */
    val fileSize: Long = builder.fileSize

    val fileVersion: Int = builder.fileVersion

    /**
     * The preferred languages for names, ISO 639-1 or 639-2, separated by ','.
     */
    val languagesPreference: String? = builder.optionalFields.languagesPreference

    val boundingBox: BoundingBox = builder.boundingBox

    /**
     * The date of the map data in milliseconds since January 1, 1970.
     */
    val mapDate: Long = builder.mapDate

    val numberOfSubFiles: Byte = builder.numberOfSubFiles

    val poiTags: Array<Tag> = builder.poiTags

    val projectionName: String = builder.projectionName

    val startPosition: LatLong? = builder.optionalFields.startPosition

    val startZoomLevel: Byte? = builder.optionalFields.startZoomLevel

    /**
     * The size of the tiles in pixels.
     */
    val tilePixelSize: Int = builder.tilePixelSize

    val wayTags: Array<Tag> = builder.wayTags

    val zoomLevelMax: Byte = builder.zoomLevelMax

    val zoomLevelMin: Byte = builder.zoomLevelMin
}
