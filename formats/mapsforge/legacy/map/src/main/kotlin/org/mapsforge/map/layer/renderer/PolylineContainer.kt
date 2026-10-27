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
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.datastore.Way

/**
 * The way data read from a map file. Absolute and tile-relative pixel coordinates are computed on
 * first use, as many ways are never drawn; the way is released once they are.
 */
open class PolylineContainer : ShapeContainer {

    private var center: Point? = null

    private var coordinatesAbsoluteValue: Array<Array<Point>>? = null

    private var coordinatesRelativeToTile: Array<Array<Point>>? = null

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
        this.isClosedWay = LatLongUtils.isClosedWay(way.latLongs[0])
        val labelPosition = way.labelPosition
        if (labelPosition != null) {
            this.center = MercatorProjection.getPixelAbsolute(labelPosition, this.upperLeft.mapSize)
        }
    }

    constructor(coordinates: Array<Point>, upperLeft: Tile, lowerRight: Tile, tags: List<Tag>) {
        this.coordinatesAbsoluteValue = arrayOf(coordinates.copyOf())
        this.tags = tags
        this.upperLeft = upperLeft
        this.lowerRight = lowerRight
        this.layer = 0
        this.isClosedWay = coordinates[0] == coordinates[coordinates.size - 1]
    }

    /**
     * The label position if the way has one, else the center of a closed way's bounding box or the
     * middle point of an open way.
     */
    val centerAbsolute: Point
        get() {
            if (center == null) {
                if (isClosedWay) {
                    center = GeometryUtils.calculateCenterOfBoundingBox(coordinatesAbsolute[0])
                } else {
                    center = GeometryUtils.calculateCenterPoint(coordinatesAbsolute[0])
                }
            }
            return center!!
        }

    val coordinatesAbsolute: Array<Array<Point>>
        get() {
            // deferred evaluation as some PolyLineContainers will never be drawn. However,
            // to save memory, after computing the absolute coordinates, the way is released.
            if (coordinatesAbsoluteValue == null) {
                val way = this.way!!
                coordinatesAbsoluteValue = Array(way.latLongs.size) { i ->
                    Array(way.latLongs[i].size) { j ->
                        MercatorProjection.getPixelAbsolute(way.latLongs[i][j], upperLeft.mapSize)
                    }
                }
                this.way = null
            }
            return coordinatesAbsoluteValue!!
        }

    val coordinatesRelativeToOrigin: Array<Array<Point>>
        get() {
            if (coordinatesRelativeToTile == null) {
                val tileOrigin = upperLeft.origin
                val absolute = coordinatesAbsolute
                coordinatesRelativeToTile = Array(absolute.size) { i ->
                    Array(absolute[i].size) { j ->
                        absolute[i][j].offset(-tileOrigin.x, -tileOrigin.y)
                    }
                }
            }
            return coordinatesRelativeToTile!!
        }

    final override val shapeType: ShapeType
        get() {
            return ShapeType.POLYLINE
        }
}
