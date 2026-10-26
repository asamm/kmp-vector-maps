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
package org.mapsforge.core.model

import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.core.util.Parameters
import java.io.Serializable

/**
 * An immutable geographic box in degrees; `extendCoordinates`, `extendDegrees`, `extendMargin` and
 * `extendMeters` clip to the Mercator latitude range and to ±180, `extendBoundingBox` does not.
 */
open class BoundingBox : Serializable {

    @JvmField
    val maxLatitude: Double

    @JvmField
    val maxLongitude: Double

    @JvmField
    val minLatitude: Double

    @JvmField
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

            minLatitude = Math.min(minLatitude, latitude)
            minLongitude = Math.min(minLongitude, longitude)
            maxLatitude = Math.max(maxLatitude, latitude)
            maxLongitude = Math.max(maxLongitude, longitude)
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
        return java.lang.Double.doubleToLongBits(maxLatitude) == java.lang.Double.doubleToLongBits(other.maxLatitude)
            && java.lang.Double.doubleToLongBits(maxLongitude) == java.lang.Double.doubleToLongBits(other.maxLongitude)
            && java.lang.Double.doubleToLongBits(minLatitude) == java.lang.Double.doubleToLongBits(other.minLatitude)
            && java.lang.Double.doubleToLongBits(minLongitude) == java.lang.Double.doubleToLongBits(other.minLongitude)
    }

    /**
     * The box covering this one and `boundingBox`.
     */
    fun extendBoundingBox(boundingBox: BoundingBox): BoundingBox {
        return BoundingBox(
            Math.min(minLatitude, boundingBox.minLatitude),
            Math.min(minLongitude, boundingBox.minLongitude),
            Math.max(maxLatitude, boundingBox.maxLatitude),
            Math.max(maxLongitude, boundingBox.maxLongitude),
        )
    }

    /**
     * The box also covering the point; this instance when it already does.
     */
    fun extendCoordinates(latitude: Double, longitude: Double): BoundingBox {
        if (contains(latitude, longitude)) {
            return this
        }

        val minLat = Math.max(MercatorProjection.LATITUDE_MIN, Math.min(minLatitude, latitude))
        val minLon = Math.max(-180.0, Math.min(minLongitude, longitude))
        val maxLat = Math.min(MercatorProjection.LATITUDE_MAX, Math.max(maxLatitude, latitude))
        val maxLon = Math.min(180.0, Math.max(maxLongitude, longitude))

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    fun extendCoordinates(latLong: LatLong): BoundingBox {
        return extendCoordinates(latLong.latitude, latLong.longitude)
    }

    /**
     * The box grown by the given degrees on each side; negative values throw
     * [IllegalArgumentException].
     */
    fun extendDegrees(verticalExpansion: Double, horizontalExpansion: Double): BoundingBox {
        if (verticalExpansion == 0.0
            && horizontalExpansion == 0.0
        ) {
            return this
        } else if (verticalExpansion < 0
            || horizontalExpansion < 0
        ) {
            throw IllegalArgumentException("BoundingBox extend operation does not accept negative values")
        }

        val minLat = Math.max(MercatorProjection.LATITUDE_MIN, minLatitude - verticalExpansion)
        val minLon = Math.max(-180.0, minLongitude - horizontalExpansion)
        val maxLat = Math.min(MercatorProjection.LATITUDE_MAX, maxLatitude + verticalExpansion)
        val maxLon = Math.min(180.0, maxLongitude + horizontalExpansion)

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    /**
     * The box scaled around its center by `margin`; 0 or less throws [IllegalArgumentException].
     */
    fun extendMargin(margin: Float): BoundingBox {
        if (margin == 1f) {
            return this
        } else if (margin <= 0) {
            throw IllegalArgumentException("BoundingBox extend operation does not accept negative or zero values")
        }

        val verticalExpansion = (latitudeSpan * margin - latitudeSpan) * 0.5
        val horizontalExpansion = (longitudeSpan * margin - longitudeSpan) * 0.5

        val minLat = Math.max(MercatorProjection.LATITUDE_MIN, minLatitude - verticalExpansion)
        val minLon = Math.max(-180.0, minLongitude - horizontalExpansion)
        val maxLat = Math.min(MercatorProjection.LATITUDE_MAX, maxLatitude + verticalExpansion)
        val maxLon = Math.min(180.0, maxLongitude + horizontalExpansion)

        return BoundingBox(minLat, minLon, maxLat, maxLon)
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
            Math.max(Math.abs(minLatitude), Math.abs(maxLatitude)),
        )

        val minLat = Math.max(MercatorProjection.LATITUDE_MIN, minLatitude - verticalExpansion)
        val minLon = Math.max(-180.0, minLongitude - horizontalExpansion)
        val maxLat = Math.min(MercatorProjection.LATITUDE_MAX, maxLatitude + verticalExpansion)
        val maxLon = Math.min(180.0, maxLongitude + horizontalExpansion)

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    val centerPoint: LatLong
        get() {
            val latitudeOffset = (maxLatitude - minLatitude) / 2
            val longitudeOffset = (maxLongitude - minLongitude) / 2
            return LatLong(minLatitude + latitudeOffset, minLongitude + longitudeOffset)
        }

    val latitudeSpan: Double
        get() {
            return maxLatitude - minLatitude
        }

    val longitudeSpan: Double
        get() {
            return maxLongitude - minLongitude
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
        var temp = java.lang.Double.doubleToLongBits(maxLatitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(maxLongitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(minLatitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(minLongitude)
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
     * True if any point is inside, else if the points' bounding box intersects this one.
     */
    fun intersectsArea(latLongs: Array<Array<LatLong>>): Boolean {
        if (latLongs.isEmpty()
            || latLongs[0].isEmpty()
        ) {
            return false
        }

        for (outer in latLongs) {
            for (latLong in outer) {
                if (contains(latLong)) {
                    // if any of the points is inside the bbox return early
                    return true
                }
            }
        }

        // no fast solution, so accumulate boundary points
        var tmpMinLat = latLongs[0][0].latitude
        var tmpMinLon = latLongs[0][0].longitude
        var tmpMaxLat = latLongs[0][0].latitude
        var tmpMaxLon = latLongs[0][0].longitude

        for (outer in latLongs) {
            for (latLong in outer) {
                tmpMinLat = Math.min(tmpMinLat, latLong.latitude)
                tmpMaxLat = Math.max(tmpMaxLat, latLong.latitude)
                tmpMinLon = Math.min(tmpMinLon, latLong.longitude)
                tmpMaxLon = Math.max(tmpMaxLon, latLong.longitude)
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
        @JvmStatic
        fun fromString(boundingBoxString: String): BoundingBox {
            val coordinates = LatLongUtils.parseCoordinateString(boundingBoxString, 4)
            return BoundingBox(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
        }
    }
}
