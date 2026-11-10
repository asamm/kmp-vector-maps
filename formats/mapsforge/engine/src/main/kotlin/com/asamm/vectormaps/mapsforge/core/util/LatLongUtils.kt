/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 Christian Pesch
 * Copyright 2014-2017 devemux86
 * Copyright 2015 Andreas Schildbach
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
import java.util.StringTokenizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Geographic helpers on WGS84 coordinates.
 */
object LatLongUtils {

    /**
     * The equatorial radius of the WGS84 ellipsoid in meters.
     */
    const val EQUATORIAL_RADIUS = 6378137.0

    const val LATITUDE_MAX = 90.0

    const val LATITUDE_MIN = -LATITUDE_MAX

    const val LONGITUDE_MAX = 180.0

    const val LONGITUDE_MIN = -LONGITUDE_MAX

    // degrees per microdegree, inverted
    private const val CONVERSION_FACTOR = 1000000.0

    private const val DELIMITER = ","

    /**
     * True if `latLong` lies inside the polygon `latLongs` (ray casting); the polygon need not be
     * closed. See http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html.
     */
    fun contains(latLongs: Array<LatLong>, latLong: LatLong): Boolean {
        var result = false
        var i = 0
        var j = latLongs.size - 1
        while (i < latLongs.size) {
            if ((latLongs[i].latitude > latLong.latitude) != (latLongs[j].latitude > latLong.latitude)
                && (latLong.longitude < (latLongs[j].longitude - latLongs[i].longitude) *
                    (latLong.latitude - latLongs[i].latitude) /
                    (latLongs[j].latitude - latLongs[i].latitude) + latLongs[i].longitude)
            ) {
                result = !result
            }
            j = i++
        }
        return result
    }

    /**
     * True if `latLong` lies inside the polygon `latLongs` (ray casting); the polygon need not be
     * closed. See http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html.
     */
    fun contains(latLongs: List<LatLong>, latLong: LatLong): Boolean {
        var result = false
        var i = 0
        var j = latLongs.size - 1
        while (i < latLongs.size) {
            if ((latLongs[i].latitude > latLong.latitude) != (latLongs[j].latitude > latLong.latitude)
                && (latLong.longitude < (latLongs[j].longitude - latLongs[i].longitude) *
                    (latLong.latitude - latLongs[i].latitude) /
                    (latLongs[j].latitude - latLongs[i].latitude) + latLongs[i].longitude)
            ) {
                result = !result
            }
            j = i++
        }
        return result
    }

    /**
     * The Euclidean distance in degrees.
     */
    fun distance(latLong1: LatLong, latLong2: LatLong): Double {
        return hypot(latLong1.longitude - latLong2.longitude, latLong1.latitude - latLong2.latitude)
    }

    /**
     * Parses "latitude,longitude" in degrees.
     */
    fun fromString(latLongString: String): LatLong {
        val coordinates = parseCoordinateString(latLongString, 2)
        return LatLong(coordinates[0], coordinates[1])
    }

    /**
     * True if the first and last points of `latLongs`, latitude, longitude pairs, coincide.
     */
    fun isClosedWay(latLongs: DoubleArray): Boolean {
        val last = latLongs.size - 2
        // as LatLong.distance
        return hypot(latLongs[1] - latLongs[last + 1], latLongs[0] - latLongs[last]) < 0.000000001
    }

    /**
     * `meters` in degrees of latitude, on the WGS84 sphere.
     */
    fun latitudeDistance(meters: Int): Double {
        return (meters * 360) / (2 * PI * EQUATORIAL_RADIUS)
    }

    /**
     * `meters` in degrees of longitude at `latitude`, on the WGS84 sphere.
     */
    fun longitudeDistance(meters: Int, latitude: Double): Double {
        return (meters * 360) / (2 * PI * EQUATORIAL_RADIUS * cos(Math.toRadians(latitude)))
    }

    fun microdegreesToDegrees(coordinate: Int): Double {
        return coordinate / CONVERSION_FACTOR
    }

    /**
     * Parses `numberOfCoordinates` comma-separated values; throws [IllegalArgumentException] on a
     * wrong count or a stray delimiter.
     */
    fun parseCoordinateString(coordinatesString: String, numberOfCoordinates: Int): DoubleArray {
        val stringTokenizer = StringTokenizer(coordinatesString, DELIMITER, true)
        var isDelimiter = true
        val tokens = ArrayList<String>(numberOfCoordinates)

        while (stringTokenizer.hasMoreTokens()) {
            val token = stringTokenizer.nextToken()
            isDelimiter = !isDelimiter
            if (isDelimiter) {
                continue
            }
            tokens.add(token)
        }

        if (isDelimiter) {
            throw IllegalArgumentException("invalid coordinate delimiter: $coordinatesString")
        } else if (tokens.size != numberOfCoordinates) {
            throw IllegalArgumentException("invalid number of coordinate values: $coordinatesString")
        }

        return DoubleArray(numberOfCoordinates) { java.lang.Double.parseDouble(tokens[it]) }
    }

    /**
     * `latitude` unchanged; throws [IllegalArgumentException] when it is NaN or out of range.
     */
    fun validateLatitude(latitude: Double): Double {
        if (java.lang.Double.isNaN(latitude)
            || latitude < LATITUDE_MIN
            || latitude > LATITUDE_MAX
        ) {
            throw IllegalArgumentException("invalid latitude: $latitude")
        }
        return latitude
    }

    /**
     * `longitude` unchanged; throws [IllegalArgumentException] when it is NaN or out of range.
     */
    fun validateLongitude(longitude: Double): Double {
        if (java.lang.Double.isNaN(longitude)
            || longitude < LONGITUDE_MIN
            || longitude > LONGITUDE_MAX
        ) {
            throw IllegalArgumentException("invalid longitude: $longitude")
        }
        return longitude
    }

}
