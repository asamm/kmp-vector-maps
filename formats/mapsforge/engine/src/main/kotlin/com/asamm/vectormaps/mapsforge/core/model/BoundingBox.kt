/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Christian Pesch
 * Copyright 2015-2022 devemux86
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
package com.asamm.vectormaps.mapsforge.core.model

import com.asamm.vectormaps.mapsforge.core.util.LatLongUtils
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.core.util.Parameters
import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * An immutable geographic box in degrees; `extendMeters` clips to the Mercator latitude range and
 * to ±180, `extendBoundingBox` does not.
 */
open class BoundingBox : Serializable {

    val maxLatitude: Double

    val maxLongitude: Double

    val minLatitude: Double

    val minLongitude: Double

    /**
     * Throws [IllegalArgumentException] for invalid coordinates (while validated) or a min above
     * its max.
     */
    constructor(minLatitude: Double, minLongitude: Double, maxLatitude: Double, maxLongitude: Double) {
        if (Parameters.VALIDATE_COORDINATES) {
            LatLongUtils.validateLatitude(minLatitude)
            LatLongUtils.validateLongitude(minLongitude)
            LatLongUtils.validateLatitude(maxLatitude)
            LatLongUtils.validateLongitude(maxLongitude)
        }

        if (minLatitude > maxLatitude) {
            throw IllegalArgumentException("invalid latitude range: $minLatitude $maxLatitude")
        } else if (minLongitude > maxLongitude) {
            throw IllegalArgumentException("invalid longitude range: $minLongitude $maxLongitude")
        }

        this.minLatitude = minLatitude
        this.minLongitude = minLongitude
        this.maxLatitude = maxLatitude
        this.maxLongitude = maxLongitude
    }

    /**
     * The box around `latLongs`, unchecked; an empty list gives infinite bounds.
     */
    constructor(latLongs: List<LatLong>) {
        var minLatitude = Double.POSITIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        for (latLong in latLongs) {
            val latitude = latLong.latitude
            val longitude = latLong.longitude

            minLatitude = min(minLatitude, latitude)
            minLongitude = min(minLongitude, longitude)
            maxLatitude = max(maxLatitude, latitude)
            maxLongitude = max(maxLongitude, longitude)
        }

        this.minLatitude = minLatitude
        this.minLongitude = minLongitude
        this.maxLatitude = maxLatitude
        this.maxLongitude = maxLongitude
    }

    fun contains(latitude: Double, longitude: Double): Boolean {
        return minLatitude <= latitude
            && maxLatitude >= latitude
            && minLongitude <= longitude
            && maxLongitude >= longitude
    }

    fun contains(latLong: LatLong): Boolean {
        return contains(latLong.latitude, latLong.longitude)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BoundingBox) {
            return false
        }
        return maxLatitude.toBits() == other.maxLatitude.toBits()
            && maxLongitude.toBits() == other.maxLongitude.toBits()
            && minLatitude.toBits() == other.minLatitude.toBits()
            && minLongitude.toBits() == other.minLongitude.toBits()
    }

    /**
     * The box covering this one and `boundingBox`.
     */
    fun extendBoundingBox(boundingBox: BoundingBox): BoundingBox {
        return BoundingBox(
            min(minLatitude, boundingBox.minLatitude),
            min(minLongitude, boundingBox.minLongitude),
            max(maxLatitude, boundingBox.maxLatitude),
            max(maxLongitude, boundingBox.maxLongitude),
        )
    }

    /**
     * The box grown by `meters` on each side; negative throws [IllegalArgumentException].
     */
    fun extendMeters(meters: Int): BoundingBox {
        if (meters == 0) {
            return this
        } else if (meters < 0) {
            throw IllegalArgumentException("BoundingBox extend operation does not accept negative values")
        }

        val verticalExpansion = LatLongUtils.latitudeDistance(meters)
        val horizontalExpansion = LatLongUtils.longitudeDistance(
            meters,
            max(abs(minLatitude), abs(maxLatitude)),
        )

        val minLat = max(MercatorProjection.LATITUDE_MIN, minLatitude - verticalExpansion)
        val minLon = max(-180.0, minLongitude - horizontalExpansion)
        val maxLat = min(MercatorProjection.LATITUDE_MAX, maxLatitude + verticalExpansion)
        val maxLon = min(180.0, maxLongitude + horizontalExpansion)

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    val centerPoint: LatLong
        get() {
            val latitudeOffset = (maxLatitude - minLatitude) / 2
            val longitudeOffset = (maxLongitude - minLongitude) / 2
            return LatLong(minLatitude + latitudeOffset, minLongitude + longitudeOffset)
        }

    /**
     * This box in pixels relative to `tile`'s upper left corner.
     */
    fun getPositionRelativeToTile(tile: Tile): Rectangle {
        val upperLeft = MercatorProjection.getPixelRelativeToTile(LatLong(maxLatitude, minLongitude), tile)
        val lowerRight = MercatorProjection.getPixelRelativeToTile(LatLong(minLatitude, maxLongitude), tile)
        return Rectangle(upperLeft.x, upperLeft.y, lowerRight.x, lowerRight.y)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        var temp = maxLatitude.toBits()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = maxLongitude.toBits()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = minLatitude.toBits()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = minLongitude.toBits()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    fun intersects(boundingBox: BoundingBox): Boolean {
        if (this === boundingBox) {
            return true
        }
        return maxLatitude >= boundingBox.minLatitude
            && maxLongitude >= boundingBox.minLongitude
            && minLatitude <= boundingBox.maxLatitude
            && minLongitude <= boundingBox.maxLongitude
    }

    /**
     * True if any point is inside, else if the points' bounding box intersects this one; the points
     * of each ring as latitude, longitude pairs.
     */
    fun intersectsArea(latLongs: Array<DoubleArray>): Boolean {
        if (latLongs.isEmpty()
            || latLongs[0].isEmpty()
        ) {
            return false
        }

        for (outer in latLongs) {
            for (i in outer.indices step 2) {
                if (contains(outer[i], outer[i + 1])) {
                    // if any of the points is inside the bbox return early
                    return true
                }
            }
        }

        // no fast solution, so accumulate boundary points
        var tmpMinLat = latLongs[0][0]
        var tmpMinLon = latLongs[0][1]
        var tmpMaxLat = latLongs[0][0]
        var tmpMaxLon = latLongs[0][1]

        for (outer in latLongs) {
            for (i in outer.indices step 2) {
                tmpMinLat = min(tmpMinLat, outer[i])
                tmpMaxLat = max(tmpMaxLat, outer[i])
                tmpMinLon = min(tmpMinLon, outer[i + 1])
                tmpMaxLon = max(tmpMaxLon, outer[i + 1])
            }
        }

        return intersects(BoundingBox(tmpMinLat, tmpMinLon, tmpMaxLat, tmpMaxLon))
    }

    override fun toString(): String {
        return "minLatitude=$minLatitude, minLongitude=$minLongitude, maxLatitude=$maxLatitude, " +
            "maxLongitude=$maxLongitude"
    }

    companion object {

        private const val serialVersionUID = 1L

        /**
         * Parses "minLat,minLon,maxLat,maxLon" in degrees.
         */
        fun fromString(boundingBoxString: String): BoundingBox {
            val coordinates = LatLongUtils.parseCoordinateString(boundingBoxString, 4)
            return BoundingBox(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
        }
    }
}
