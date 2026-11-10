/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015-2022 devemux86
 * Copyright 2025 Sublimis
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
import com.asamm.vectormaps.mapsforge.core.util.Parameters
import java.util.Objects
import java.util.regex.Pattern

/**
 * An immutable WGS84 coordinate in degrees; validated while [Parameters.VALIDATE_COORDINATES]
 * is set, out of range values throw [IllegalArgumentException].
 */
open class LatLong : Comparable<LatLong> {

    val latitude: Double

    val longitude: Double

    @Throws(IllegalArgumentException::class)
    constructor(latitude: Double, longitude: Double) {
        this.latitude = if (Parameters.VALIDATE_COORDINATES) LatLongUtils.validateLatitude(latitude) else latitude
        this.longitude = if (Parameters.VALIDATE_COORDINATES) LatLongUtils.validateLongitude(longitude) else longitude
    }

    /**
     * Parses a Well-Known-Text point in degrees, `POINT(longitude latitude)`.
     */
    constructor(wellKnownText: String) {
        val m = WKT_POINT_PATTERN.matcher(wellKnownText)
        m.matches()
        val longitude = java.lang.Double.parseDouble(m.group(1))
        this.longitude = if (Parameters.VALIDATE_COORDINATES) LatLongUtils.validateLongitude(longitude) else longitude
        val latitude = java.lang.Double.parseDouble(m.group(2))
        this.latitude = if (Parameters.VALIDATE_COORDINATES) LatLongUtils.validateLatitude(latitude) else latitude
    }

    /**
     * Orders by longitude, then latitude, for tree structures.
     */
    override fun compareTo(other: LatLong): Int {
        return Point.compareCoord(longitude, latitude, other.longitude, other.latitude)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LatLong) {
            return false
        }
        return java.lang.Double.compare(latitude, other.latitude) == 0
            && java.lang.Double.compare(longitude, other.longitude) == 0
    }

    override fun hashCode(): Int {
        return Objects.hash(latitude, longitude)
    }

    /**
     * The Euclidean distance in degrees.
     */
    fun distance(other: LatLong): Double {
        return LatLongUtils.distance(this, other)
    }

    override fun toString(): String {
        return "latitude=$latitude, longitude=$longitude"
    }

    companion object {

        private val WKT_POINT_PATTERN: Pattern = Pattern.compile(".*POINT\\s?\\(([\\d\\.]+)\\s([\\d\\.]+)\\).*")

        private val SEPARATOR_PATTERN: Pattern = Pattern.compile("[,;:\\s]")

        /**
         * Parses "latitude,longitude" in degrees; ';', ':' and whitespace also separate.
         */
        fun fromString(latLonString: String): LatLong {
            val split = SEPARATOR_PATTERN.split(latLonString)
            if (split.size != 2) {
                throw IllegalArgumentException("cannot read coordinate, not a valid format")
            }
            val latitude = java.lang.Double.parseDouble(split[0])
            val longitude = java.lang.Double.parseDouble(split[1])
            return LatLong(latitude, longitude)
        }
    }
}
