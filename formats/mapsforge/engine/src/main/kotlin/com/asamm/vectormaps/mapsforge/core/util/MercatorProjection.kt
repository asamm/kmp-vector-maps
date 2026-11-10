/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2013 Stephan Brandt <stephan@contagt.com>
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016-2017 devemux86
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
package com.asamm.vectormaps.mapsforge.core.util

import com.asamm.vectormaps.mapsforge.core.model.LatLong
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Tile
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Converts between WGS84 coordinates, pixels and tiles of the spherical Mercator projection, by
 * zoom level (with a tile size) or map size.
 */
object MercatorProjection {

    /**
     * The maximum latitude the projection covers.
     */
    const val LATITUDE_MAX = 85.05112877980659

    const val LATITUDE_MIN = -LATITUDE_MAX

    // upstream: some operations cancel the tile size out but are built from ones that need it
    private const val DUMMY_TILE_SIZE = 256

    /**
     * The map size in pixels; throws [IllegalArgumentException] for a negative zoom level.
     */
    fun getMapSize(zoomLevel: Byte, tileSize: Int): Long {
        if (zoomLevel < 0) {
            throw IllegalArgumentException("zoom level must not be negative: $zoomLevel")
        }
        return tileSize.toLong() shl zoomLevel.toInt()
    }

    fun getPixelAbsolute(latLong: LatLong, mapSize: Long): Point {
        return getPixelRelative(latLong, mapSize, 0.0, 0.0)
    }

    /**
     * The pixel of `latLong` relative to the pixel (`x`, `y`).
     */
    fun getPixelRelative(latLong: LatLong, mapSize: Long, x: Double, y: Double): Point {
        val pixelX = longitudeToPixelX(latLong.longitude, mapSize) - x
        val pixelY = latitudeToPixelY(latLong.latitude, mapSize) - y
        return Point(pixelX, pixelY)
    }

    fun getPixelRelative(latLong: LatLong, mapSize: Long, origin: Point): Point {
        return getPixelRelative(latLong, mapSize, origin.x, origin.y)
    }

    /**
     * The pixel of `latLong` relative to the upper left corner of `tile`.
     */
    fun getPixelRelativeToTile(latLong: LatLong, tile: Tile): Point {
        return getPixelRelative(latLong, tile.mapSize, tile.origin)
    }

    fun latitudeToPixelY(latitude: Double, zoomLevel: Byte, tileSize: Int): Double {
        val mapSize = getMapSize(zoomLevel, tileSize)
        return latitudeToPixelY(latitude, mapSize)
    }

    /**
     * The pixel Y of `latitude`, clipped to the map.
     */
    fun latitudeToPixelY(latitude: Double, mapSize: Long): Double {
        val sinLatitude = sin(Math.toRadians(latitude))
        // upstream: the formula needs the clipping to stay on the map
        val pixelY = (0.5 - ln((1 + sinLatitude) / (1 - sinLatitude)) / (4 * PI)) * mapSize
        return min(max(0.0, pixelY), mapSize.toDouble())
    }

    fun latitudeToTileY(latitude: Double, zoomLevel: Byte): Int {
        return pixelYToTileY(latitudeToPixelY(latitude, zoomLevel, DUMMY_TILE_SIZE), zoomLevel, DUMMY_TILE_SIZE)
    }

    fun longitudeToPixelX(longitude: Double, zoomLevel: Byte, tileSize: Int): Double {
        val mapSize = getMapSize(zoomLevel, tileSize)
        return (longitude + 180) / 360 * mapSize
    }

    fun longitudeToPixelX(longitude: Double, mapSize: Long): Double {
        return (longitude + 180) / 360 * mapSize
    }

    fun longitudeToTileX(longitude: Double, zoomLevel: Byte): Int {
        return pixelXToTileX(longitudeToPixelX(longitude, zoomLevel, DUMMY_TILE_SIZE), zoomLevel, DUMMY_TILE_SIZE)
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    fun pixelXToLongitude(pixelX: Double, mapSize: Long): Double {
        if (pixelX < 0
            || pixelX > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelX coordinate $mapSize: $pixelX")
        }
        return 360 * ((pixelX / mapSize) - 0.5)
    }

    fun pixelXToTileX(pixelX: Double, zoomLevel: Byte, tileSize: Int): Int {
        return min(max(pixelX / tileSize, 0.0), 2.0.pow(zoomLevel.toDouble()) - 1).toInt()
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    fun pixelYToLatitude(pixelY: Double, mapSize: Long): Double {
        if (pixelY < 0
            || pixelY > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelY coordinate $mapSize: $pixelY")
        }
        val y = 0.5 - (pixelY / mapSize)
        return 90 - 360 * atan(exp(-y * (2 * PI))) / PI
    }

    fun pixelYToTileY(pixelY: Double, zoomLevel: Byte, tileSize: Int): Int {
        return min(max(pixelY / tileSize, 0.0), 2.0.pow(zoomLevel.toDouble()) - 1).toInt()
    }

    fun tileToPixel(tileNumber: Long, tileSize: Int): Long {
        return tileNumber * tileSize
    }

    fun tileXToLongitude(tileX: Long, zoomLevel: Byte): Double {
        return pixelXToLongitude((tileX * DUMMY_TILE_SIZE).toDouble(), getMapSize(zoomLevel, DUMMY_TILE_SIZE))
    }

    fun tileYToLatitude(tileY: Long, zoomLevel: Byte): Double {
        return pixelYToLatitude((tileY * DUMMY_TILE_SIZE).toDouble(), getMapSize(zoomLevel, DUMMY_TILE_SIZE))
    }
}
