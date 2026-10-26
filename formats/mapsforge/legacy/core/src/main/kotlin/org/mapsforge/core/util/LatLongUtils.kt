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
package org.mapsforge.core.util

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import java.util.StringTokenizer

/**
 * Geographic helpers on WGS84 coordinates.
 */
object LatLongUtils {

    /**
     * The equatorial radius of the WGS84 ellipsoid in meters.
     */
    const val EQUATORIAL_RADIUS = 6378137.0

    /**
     * The flattening factor of the earth's ellipsoid, as a reciprocal.
     */
    const val INVERSE_FLATTENING = 298.257223563

    /**
     * The polar radius of the WGS84 ellipsoid in meters.
     */
    const val POLAR_RADIUS = 6356752.3142

    const val LATITUDE_MAX = 90.0

    const val LATITUDE_MIN = -LATITUDE_MAX

    const val LONGITUDE_MAX = 180.0

    const val LONGITUDE_MIN = -LONGITUDE_MAX

    // degrees per microdegree, inverted
    private const val CONVERSION_FACTOR = 1000000.0

    private const val DELIMITER = ","

    private val LOG_2 = Math.log(2.0)

    /**
     * True if `latLong` lies inside the polygon `latLongs` (ray casting); the polygon need not be
     * closed.
     */
    @JvmStatic
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
     * closed.
     */
    @JvmStatic
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

    @JvmStatic
    fun degreesToMicrodegrees(coordinate: Double): Int {
        return (coordinate * CONVERSION_FACTOR).toInt()
    }

    /**
     * The point `distance` meters from `start` along the initial `bearing` in degrees from north.
     * See [latlon.js](http://www.movable-type.co.uk/scripts/latlon.js).
     */
    @JvmStatic
    fun destinationPoint(start: LatLong, distance: Double, bearing: Float): LatLong {
        val theta = Math.toRadians(bearing.toDouble())
        // angular distance in radians
        val delta = distance / EQUATORIAL_RADIUS

        val phi1 = Math.toRadians(start.latitude)
        val lambda1 = Math.toRadians(start.longitude)

        val phi2 = Math.asin(
            Math.sin(phi1) * Math.cos(delta) + Math.cos(phi1) * Math.sin(delta) * Math.cos(theta),
        )
        val lambda2 = lambda1 + Math.atan2(
            Math.sin(theta) * Math.sin(delta) * Math.cos(phi1),
            Math.cos(delta) - Math.sin(phi1) * Math.sin(phi2),
        )

        return LatLong(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }

    /**
     * The Euclidean distance in degrees.
     */
    @JvmStatic
    fun distance(latLong1: LatLong, latLong2: LatLong): Double {
        return Math.hypot(latLong1.longitude - latLong2.longitude, latLong1.latitude - latLong2.latitude)
    }

    /**
     * The distance from the point to the nearest point of the segment; libGDX (Apache 2.0).
     */
    @JvmStatic
    fun distanceSegmentPoint(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        pointX: Double,
        pointY: Double,
    ): Double {
        val nearest = nearestSegmentPoint(startX, startY, endX, endY, pointX, pointY)
        return Math.hypot(nearest.x - pointX, nearest.y - pointY)
    }

    /**
     * Parses "latitude,longitude" in degrees.
     */
    @JvmStatic
    fun fromString(latLongString: String): LatLong {
        val coordinates = parseCoordinateString(latLongString, 2)
        return LatLong(coordinates[0], coordinates[1])
    }

    /**
     * True if the first and last points coincide.
     */
    @JvmStatic
    fun isClosedWay(latLongs: Array<LatLong>): Boolean {
        return latLongs[0].distance(latLongs[latLongs.size - 1]) < 0.000000001
    }

    /**
     * `meters` in degrees of latitude, on the WGS84 sphere.
     */
    @JvmStatic
    fun latitudeDistance(meters: Int): Double {
        return (meters * 360) / (2 * Math.PI * EQUATORIAL_RADIUS)
    }

    /**
     * `meters` in degrees of longitude at `latitude`, on the WGS84 sphere.
     */
    @JvmStatic
    fun longitudeDistance(meters: Int, latitude: Double): Double {
        return (meters * 360) / (2 * Math.PI * EQUATORIAL_RADIUS * Math.cos(Math.toRadians(latitude)))
    }

    @JvmStatic
    fun microdegreesToDegrees(coordinate: Int): Double {
        return coordinate / CONVERSION_FACTOR
    }

    /**
     * The point of the segment nearest to the given point; libGDX (Apache 2.0).
     */
    @JvmStatic
    fun nearestSegmentPoint(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        pointX: Double,
        pointY: Double,
    ): Point {
        val xDiff = endX - startX
        val yDiff = endY - startY
        val length2 = xDiff * xDiff + yDiff * yDiff
        if (length2 == 0.0) {
            return Point(startX, startY)
        }
        val t = ((pointX - startX) * (endX - startX) + (pointY - startY) * (endY - startY)) / length2
        if (t < 0) {
            return Point(startX, startY)
        }
        if (t > 1) {
            return Point(endX, endY)
        }
        return Point(startX + t * (endX - startX), startY + t * (endY - startY))
    }

    /**
     * Parses `numberOfCoordinates` comma-separated values; throws [IllegalArgumentException] on a
     * wrong count or a stray delimiter.
     */
    @JvmStatic
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
     * The great-circle distance in meters (haversine); faster but less exact than
     * [vincentyDistance].
     */
    @JvmStatic
    fun sphericalDistance(latLong1: LatLong, latLong2: LatLong): Double {
        val dLat = Math.toRadians(latLong2.latitude - latLong1.latitude)
        val dLon = Math.toRadians(latLong2.longitude - latLong1.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latLong1.latitude)) *
            Math.cos(Math.toRadians(latLong2.latitude)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return c * EQUATORIAL_RADIUS
    }

    /**
     * `latitude` unchanged; throws [IllegalArgumentException] when it is NaN or out of range.
     */
    @JvmStatic
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
    @JvmStatic
    fun validateLongitude(longitude: Double): Double {
        if (java.lang.Double.isNaN(longitude)
            || longitude < LONGITUDE_MIN
            || longitude > LONGITUDE_MAX
        ) {
            throw IllegalArgumentException("invalid longitude: $longitude")
        }
        return longitude
    }

    /**
     * The ellipsoidal distance in meters (Vincenty); 0 for coincident points or when the formula
     * does not converge. See http://www.movable-type.co.uk/scripts/latlong-vincenty.html.
     */
    @JvmStatic
    fun vincentyDistance(latLong1: LatLong, latLong2: LatLong): Double {
        val f = 1 / INVERSE_FLATTENING
        val bigL = Math.toRadians(latLong2.getLongitude() - latLong1.getLongitude())
        val u1 = Math.atan((1 - f) * Math.tan(Math.toRadians(latLong1.getLatitude())))
        val u2 = Math.atan((1 - f) * Math.tan(Math.toRadians(latLong2.getLatitude())))
        val sinU1 = Math.sin(u1)
        val cosU1 = Math.cos(u1)
        val sinU2 = Math.sin(u2)
        val cosU2 = Math.cos(u2)

        var lambda = bigL
        var lambdaP: Double
        var iterLimit = 100.0
        var cosSqAlpha: Double
        var sinSigma: Double
        var cosSigma: Double
        var cos2SigmaM: Double
        var sigma: Double
        var sinLambda: Double
        var sinAlpha: Double
        var cosLambda: Double
        do {
            sinLambda = Math.sin(lambda)
            cosLambda = Math.cos(lambda)
            sinSigma = Math.sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda),
            )
            if (sinSigma == 0.0) {
                // co-incident points
                return 0.0
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = Math.atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) {
                cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            } else {
                0.0
            }
            val bigC = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            lambdaP = lambda
            lambda = bigL + (1 - bigC) * f * sinAlpha *
                (sigma + bigC * sinSigma * (cos2SigmaM + bigC * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        } while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0.0) {
            // formula failed to converge
            return 0.0
        }

        val uSq = cosSqAlpha * (Math.pow(EQUATORIAL_RADIUS, 2.0) - Math.pow(POLAR_RADIUS, 2.0)) /
            Math.pow(POLAR_RADIUS, 2.0)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        return POLAR_RADIUS * bigA * (sigma - deltaSigma)
    }

    /**
     * The highest zoom level at which `boundingBox` fits into `dimension`, 0 to 127.
     */
    @JvmStatic
    fun zoomForBounds(dimension: Dimension, boundingBox: BoundingBox, tileSize: Int): Byte {
        val mapSize = MercatorProjection.getMapSize(0.toByte(), tileSize)
        val pixelXMax = MercatorProjection.longitudeToPixelX(boundingBox.maxLongitude, mapSize)
        val pixelXMin = MercatorProjection.longitudeToPixelX(boundingBox.minLongitude, mapSize)
        val zoomX = -Math.log(Math.abs(pixelXMax - pixelXMin) / dimension.width) / LOG_2
        val pixelYMax = MercatorProjection.latitudeToPixelY(boundingBox.maxLatitude, mapSize)
        val pixelYMin = MercatorProjection.latitudeToPixelY(boundingBox.minLatitude, mapSize)
        val zoomY = -Math.log(Math.abs(pixelYMax - pixelYMin) / dimension.height) / LOG_2
        val zoom = Math.floor(Math.min(zoomX, zoomY))
        if (zoom < 0) {
            return 0
        }
        if (zoom > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE
        }
        return zoom.toInt().toByte()
    }
}
