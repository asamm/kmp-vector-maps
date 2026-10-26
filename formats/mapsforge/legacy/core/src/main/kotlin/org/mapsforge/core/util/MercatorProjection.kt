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
package org.mapsforge.core.util

import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Tile

/**
 * Converts between WGS84 coordinates, pixels and tiles of the spherical Mercator projection, by
 * zoom level (with a tile size) or by a fractional scale factor.
 */
object MercatorProjection {

    /**
     * The circumference of the earth at the equator in meters.
     */
    const val EARTH_CIRCUMFERENCE = 40075016.686

    /**
     * The maximum latitude the projection covers.
     */
    const val LATITUDE_MAX = 85.05112877980659

    const val LATITUDE_MIN = -LATITUDE_MAX

    private val LOG_2 = Math.log(2.0)

    // upstream: some operations cancel the tile size out but are built from ones that need it
    private const val DUMMY_TILE_SIZE = 256

    /**
     * Meters per pixel at `latitude` and `scaleFactor`.
     */
    @JvmStatic
    fun calculateGroundResolutionWithScaleFactor(latitude: Double, scaleFactor: Double, tileSize: Int): Double {
        val mapSize = getMapSizeWithScaleFactor(scaleFactor, tileSize)
        return Math.cos(latitude * (Math.PI / 180)) * EARTH_CIRCUMFERENCE / mapSize
    }

    /**
     * Meters per pixel at `latitude` for a map of `mapSize` pixels.
     */
    @JvmStatic
    fun calculateGroundResolution(latitude: Double, mapSize: Long): Double {
        return Math.cos(latitude * (Math.PI / 180)) * EARTH_CIRCUMFERENCE / mapSize
    }

    @JvmStatic
    fun fromPixelsWithScaleFactor(pixelX: Double, pixelY: Double, scaleFactor: Double, tileSize: Int): LatLong {
        return LatLong(
            pixelYToLatitudeWithScaleFactor(pixelY, scaleFactor, tileSize),
            pixelXToLongitudeWithScaleFactor(pixelX, scaleFactor, tileSize),
        )
    }

    @JvmStatic
    fun fromPixels(pixelX: Double, pixelY: Double, mapSize: Long): LatLong {
        return LatLong(pixelYToLatitude(pixelY, mapSize), pixelXToLongitude(pixelX, mapSize))
    }

    /**
     * The map size in pixels; throws [IllegalArgumentException] for a scale factor below 1.
     */
    @JvmStatic
    fun getMapSizeWithScaleFactor(scaleFactor: Double, tileSize: Int): Long {
        if (scaleFactor < 1) {
            throw IllegalArgumentException("scale factor must not < 1 $scaleFactor")
        }
        return (tileSize * Math.pow(2.0, scaleFactorToZoomLevel(scaleFactor))).toLong()
    }

    /**
     * The map size in pixels; throws [IllegalArgumentException] for a negative zoom level.
     */
    @JvmStatic
    fun getMapSize(zoomLevel: Byte, tileSize: Int): Long {
        if (zoomLevel < 0) {
            throw IllegalArgumentException("zoom level must not be negative: $zoomLevel")
        }
        return tileSize.toLong() shl zoomLevel.toInt()
    }

    @JvmStatic
    fun getPixelWithScaleFactor(latLong: LatLong, scaleFactor: Double, tileSize: Int): Point {
        val pixelX = longitudeToPixelXWithScaleFactor(latLong.longitude, scaleFactor, tileSize)
        val pixelY = latitudeToPixelYWithScaleFactor(latLong.latitude, scaleFactor, tileSize)
        return Point(pixelX, pixelY)
    }

    @JvmStatic
    fun getPixel(latLong: LatLong, mapSize: Long): Point {
        val pixelX = longitudeToPixelX(latLong.longitude, mapSize)
        val pixelY = latitudeToPixelY(latLong.latitude, mapSize)
        return Point(pixelX, pixelY)
    }

    @JvmStatic
    fun getPixelAbsolute(latLong: LatLong, mapSize: Long): Point {
        return getPixelRelative(latLong, mapSize, 0.0, 0.0)
    }

    /**
     * The pixel of `latLong` relative to the pixel (`x`, `y`).
     */
    @JvmStatic
    fun getPixelRelative(latLong: LatLong, mapSize: Long, x: Double, y: Double): Point {
        val pixelX = longitudeToPixelX(latLong.longitude, mapSize) - x
        val pixelY = latitudeToPixelY(latLong.latitude, mapSize) - y
        return Point(pixelX, pixelY)
    }

    @JvmStatic
    fun getPixelRelative(latLong: LatLong, mapSize: Long, origin: Point): Point {
        return getPixelRelative(latLong, mapSize, origin.x, origin.y)
    }

    /**
     * The pixel of `latLong` relative to the upper left corner of `tile`.
     */
    @JvmStatic
    fun getPixelRelativeToTile(latLong: LatLong, tile: Tile): Point {
        return getPixelRelative(latLong, tile.mapSize, tile.origin)
    }

    @JvmStatic
    fun latitudeToPixelYWithScaleFactor(latitude: Double, scaleFactor: Double, tileSize: Int): Double {
        val mapSize = getMapSizeWithScaleFactor(scaleFactor, tileSize)
        return latitudeToPixelY(latitude, mapSize)
    }

    @JvmStatic
    fun latitudeToPixelY(latitude: Double, zoomLevel: Byte, tileSize: Int): Double {
        val mapSize = getMapSize(zoomLevel, tileSize)
        return latitudeToPixelY(latitude, mapSize)
    }

    /**
     * The pixel Y of `latitude`, clipped to the map.
     */
    @JvmStatic
    fun latitudeToPixelY(latitude: Double, mapSize: Long): Double {
        val sinLatitude = Math.sin(Math.toRadians(latitude))
        // upstream: the formula needs the clipping to stay on the map
        val pixelY = (0.5 - Math.log((1 + sinLatitude) / (1 - sinLatitude)) / (4 * Math.PI)) * mapSize
        return Math.min(Math.max(0.0, pixelY), mapSize.toDouble())
    }

    @JvmStatic
    fun latitudeToTileYWithScaleFactor(latitude: Double, scaleFactor: Double): Int {
        return pixelYToTileYWithScaleFactor(
            latitudeToPixelYWithScaleFactor(latitude, scaleFactor, DUMMY_TILE_SIZE),
            scaleFactor,
            DUMMY_TILE_SIZE,
        )
    }

    @JvmStatic
    fun latitudeToTileY(latitude: Double, zoomLevel: Byte): Int {
        return pixelYToTileY(latitudeToPixelY(latitude, zoomLevel, DUMMY_TILE_SIZE), zoomLevel, DUMMY_TILE_SIZE)
    }

    @JvmStatic
    fun longitudeToPixelXWithScaleFactor(longitude: Double, scaleFactor: Double, tileSize: Int): Double {
        val mapSize = getMapSizeWithScaleFactor(scaleFactor, tileSize)
        return (longitude + 180) / 360 * mapSize
    }

    @JvmStatic
    fun longitudeToPixelX(longitude: Double, zoomLevel: Byte, tileSize: Int): Double {
        val mapSize = getMapSize(zoomLevel, tileSize)
        return (longitude + 180) / 360 * mapSize
    }

    @JvmStatic
    fun longitudeToPixelX(longitude: Double, mapSize: Long): Double {
        return (longitude + 180) / 360 * mapSize
    }

    @JvmStatic
    fun longitudeToTileXWithScaleFactor(longitude: Double, scaleFactor: Double): Int {
        return pixelXToTileXWithScaleFactor(
            longitudeToPixelXWithScaleFactor(longitude, scaleFactor, DUMMY_TILE_SIZE),
            scaleFactor,
            DUMMY_TILE_SIZE,
        )
    }

    @JvmStatic
    fun longitudeToTileX(longitude: Double, zoomLevel: Byte): Int {
        return pixelXToTileX(longitudeToPixelX(longitude, zoomLevel, DUMMY_TILE_SIZE), zoomLevel, DUMMY_TILE_SIZE)
    }

    @JvmStatic
    fun metersToPixelsWithScaleFactor(meters: Float, latitude: Double, scaleFactor: Double, tileSize: Int): Double {
        return meters / calculateGroundResolutionWithScaleFactor(latitude, scaleFactor, tileSize)
    }

    @JvmStatic
    fun metersToPixels(meters: Float, latitude: Double, mapSize: Long): Double {
        return meters / calculateGroundResolution(latitude, mapSize)
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    @JvmStatic
    fun pixelXToLongitudeWithScaleFactor(pixelX: Double, scaleFactor: Double, tileSize: Int): Double {
        val mapSize = getMapSizeWithScaleFactor(scaleFactor, tileSize)
        if (pixelX < 0
            || pixelX > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelX coordinate at scale $scaleFactor: $pixelX")
        }
        return 360 * ((pixelX / mapSize) - 0.5)
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    @JvmStatic
    fun pixelXToLongitude(pixelX: Double, mapSize: Long): Double {
        if (pixelX < 0
            || pixelX > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelX coordinate $mapSize: $pixelX")
        }
        return 360 * ((pixelX / mapSize) - 0.5)
    }

    @JvmStatic
    fun pixelXToTileXWithScaleFactor(pixelX: Double, scaleFactor: Double, tileSize: Int): Int {
        return Math.min(Math.max(pixelX / tileSize, 0.0), scaleFactor - 1).toInt()
    }

    @JvmStatic
    fun pixelXToTileX(pixelX: Double, zoomLevel: Byte, tileSize: Int): Int {
        return Math.min(Math.max(pixelX / tileSize, 0.0), Math.pow(2.0, zoomLevel.toDouble()) - 1).toInt()
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    @JvmStatic
    fun pixelYToLatitudeWithScaleFactor(pixelY: Double, scaleFactor: Double, tileSize: Int): Double {
        val mapSize = getMapSizeWithScaleFactor(scaleFactor, tileSize)
        if (pixelY < 0
            || pixelY > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelY coordinate at scale $scaleFactor: $pixelY")
        }
        val y = 0.5 - (pixelY / mapSize)
        return 90 - 360 * Math.atan(Math.exp(-y * (2 * Math.PI))) / Math.PI
    }

    /**
     * Throws [IllegalArgumentException] for a pixel off the map.
     */
    @JvmStatic
    fun pixelYToLatitude(pixelY: Double, mapSize: Long): Double {
        if (pixelY < 0
            || pixelY > mapSize
        ) {
            throw IllegalArgumentException("invalid pixelY coordinate $mapSize: $pixelY")
        }
        val y = 0.5 - (pixelY / mapSize)
        return 90 - 360 * Math.atan(Math.exp(-y * (2 * Math.PI))) / Math.PI
    }

    @JvmStatic
    fun pixelYToTileYWithScaleFactor(pixelY: Double, scaleFactor: Double, tileSize: Int): Int {
        return Math.min(Math.max(pixelY / tileSize, 0.0), scaleFactor - 1).toInt()
    }

    @JvmStatic
    fun pixelYToTileY(pixelY: Double, zoomLevel: Byte, tileSize: Int): Int {
        return Math.min(Math.max(pixelY / tileSize, 0.0), Math.pow(2.0, zoomLevel.toDouble()) - 1).toInt()
    }

    @JvmStatic
    fun scaleFactorToZoomLevel(scaleFactor: Double): Double {
        return Math.log(scaleFactor) / LOG_2
    }

    @JvmStatic
    fun tileToPixel(tileNumber: Long, tileSize: Int): Long {
        return tileNumber * tileSize
    }

    @JvmStatic
    fun tileXToLongitudeWithScaleFactor(tileX: Long, scaleFactor: Double): Double {
        return pixelXToLongitudeWithScaleFactor((tileX * DUMMY_TILE_SIZE).toDouble(), scaleFactor, DUMMY_TILE_SIZE)
    }

    @JvmStatic
    fun tileXToLongitude(tileX: Long, zoomLevel: Byte): Double {
        return pixelXToLongitude((tileX * DUMMY_TILE_SIZE).toDouble(), getMapSize(zoomLevel, DUMMY_TILE_SIZE))
    }

    @JvmStatic
    fun tileYToLatitudeWithScaleFactor(tileY: Long, scaleFactor: Double): Double {
        return pixelYToLatitudeWithScaleFactor((tileY * DUMMY_TILE_SIZE).toDouble(), scaleFactor, DUMMY_TILE_SIZE)
    }

    @JvmStatic
    fun tileYToLatitude(tileY: Long, zoomLevel: Byte): Double {
        return pixelYToLatitude((tileY * DUMMY_TILE_SIZE).toDouble(), getMapSize(zoomLevel, DUMMY_TILE_SIZE))
    }

    @JvmStatic
    fun zoomLevelToScaleFactor(zoomLevel: Byte): Double {
        return Math.pow(2.0, zoomLevel.toDouble())
    }
}
