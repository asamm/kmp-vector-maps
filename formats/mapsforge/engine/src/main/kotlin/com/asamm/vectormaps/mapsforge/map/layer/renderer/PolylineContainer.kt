/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015-2018 devemux86
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
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Tag
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.core.util.LatLongUtils
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.map.datastore.Way

/**
 * The way data read from a map file. Absolute pixel coordinates are computed on first use, as many
 * ways are never drawn, as x, y pairs per ring; the way is released once they are.
 */
open class PolylineContainer : ShapeContainer {

    private var center: Point? = null

    private var coordinatesAbsoluteValue: Array<DoubleArray>? = null

    val tags: List<Tag>

    val layer: Byte

    val upperLeft: Tile

    val lowerRight: Tile

    val isClosedWay: Boolean

    private var way: Way? = null

    constructor(way: Way, upperLeft: Tile, lowerRight: Tile) {
        this.tags = way.tags
        this.upperLeft = upperLeft
        this.lowerRight = lowerRight
        this.layer = way.layer
        this.way = way
        this.isClosedWay = LatLongUtils.isClosedWay(way.coordinates[0])
        val labelPosition = way.labelPosition
        if (labelPosition != null) {
            this.center = MercatorProjection.getPixelAbsolute(labelPosition, this.upperLeft.mapSize)
        }
    }

    /**
     * A way of one ring, `coordinates` as absolute x, y pixel pairs.
     */
    constructor(coordinates: DoubleArray, upperLeft: Tile, lowerRight: Tile, tags: List<Tag>) {
        this.coordinatesAbsoluteValue = arrayOf(coordinates.copyOf())
        this.tags = tags
        this.upperLeft = upperLeft
        this.lowerRight = lowerRight
        this.layer = 0
        // as Point.equals
        val last = coordinates.size - 2
        this.isClosedWay = coordinates[0].compareTo(coordinates[last]) == 0
            && coordinates[1].compareTo(coordinates[last + 1]) == 0
    }

    /**
     * The label position if the way has one, else the center of a closed way's bounding box or the
     * middle point of an open way.
     */
    val centerAbsolute: Point
        get() {
            if (center == null) {
                center = if (isClosedWay) {
                    GeometryUtils.calculateCenterOfBoundingBox(coordinatesAbsolute[0])
                } else {
                    GeometryUtils.calculateCenterPoint(coordinatesAbsolute[0])
                }
            }
            return center!!
        }

    val coordinatesAbsolute: Array<DoubleArray>
        get() {
            // deferred evaluation as some PolyLineContainers will never be drawn. However,
            // to save memory, after computing the absolute coordinates, the way is released.
            if (coordinatesAbsoluteValue == null) {
                val way = this.way!!
                val mapSize = upperLeft.mapSize
                coordinatesAbsoluteValue = Array(way.coordinates.size) { i ->
                    val latLongs = way.coordinates[i]
                    val ring = DoubleArray(latLongs.size)
                    for (j in latLongs.indices step 2) {
                        ring[j] = MercatorProjection.longitudeToPixelX(latLongs[j + 1], mapSize)
                        ring[j + 1] = MercatorProjection.latitudeToPixelY(latLongs[j], mapSize)
                    }
                    ring
                }
                this.way = null
            }
            return coordinatesAbsoluteValue!!
        }

    final override val shapeType: ShapeType
        get() {
            return ShapeType.POLYLINE
        }
}
