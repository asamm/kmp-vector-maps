/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.coord

import kotlin.math.PI
import kotlin.math.round

/**
 * Static helpers for converting between Garmin's 24-bit fixed-point coordinate
 * representation and WGS84 (degrees / radians), plus bounding-box geometry helpers.
 *
 * Garmin coordinates are signed 24-bit fixed-point fractions of a full circle: a
 * coordinate value `c` represents `c * 360.0 / 2^24` degrees. This means any IMG
 * file's lat/lon precision is roughly 2.4 m at the equator.
 */
object CoordUtils {

    private const val COORD_FACTOR: Double = 360.0 / (1 shl 24)
    private val RAD_COORD_FACTOR: Double = 2.0 * PI / (1 shl 24)

    //********************************
    // CONVERSIONS
    //********************************

    /**
     * Converts a Garmin fixed-point coordinate to WGS84 degrees.
     */
    fun toWGS84(coord: Int): Double {
        return coord * COORD_FACTOR
    }

    /**
     * Converts a Garmin fixed-point coordinate to WGS84 radians.
     */
    fun toWGS84Rad(coord: Int): Double {
        return coord * RAD_COORD_FACTOR
    }

    /**
     * Converts WGS84 degrees to the nearest Garmin fixed-point coordinate.
     */
    fun fromWGS84(coord: Double): Int {
        return round(coord / COORD_FACTOR).toInt()
    }

    /**
     * Converts WGS84 radians to the nearest Garmin fixed-point coordinate.
     */
    fun fromWGS84Rad(coord: Double): Int {
        return round(coord / RAD_COORD_FACTOR).toInt()
    }

    /**
     * Formats a Garmin fixed-point coordinate as `[-]Dd MM'SS.ss` for human display.
     */
    fun toDMS(coord: Int): String {
        val negative = coord < 0
        val abs = if (negative) {
            -coord.toLong()
        } else {
            coord.toLong()
        }
        // Same scaling as the Java original: degrees * 3600 * 100 expressed in
        // hundredths-of-a-second since 0.
        var tmp = abs * 360L * 3600L * 100L / (1 shl 24)
        val deg = tmp / (3600L * 100L)
        tmp %= 3600L * 100L
        val min = tmp / (60L * 100L)
        val sec = tmp % (60L * 100L)
        return buildString {
            if (negative) {
                append('-')
            }
            append(deg)
            append('d')
            append(min.toString().padStart(2, '0'))
            append('\'')
            append(formatSeconds(sec))
        }
    }

    //********************************
    // BOUNDING-BOX TESTS
    //********************************

    /**
     * Returns true when the two axis-aligned bounding boxes overlap (edge-touching counts).
     */
    fun matchesCoordinates(
        minLong1: Int, maxLong1: Int, minLat1: Int, maxLat1: Int,
        minLong2: Int, maxLong2: Int, minLat2: Int, maxLat2: Int,
    ): Boolean {
        return !(minLong1 > maxLong2
            || maxLong1 < minLong2
            || minLat1 > maxLat2
            || maxLat1 < minLat2)
    }

    /**
     * Returns true when box 1 is fully contained in box 2.
     */
    fun includedInCoordinates(
        minLong1: Int, maxLong1: Int, minLat1: Int, maxLat1: Int,
        minLong2: Int, maxLong2: Int, minLat2: Int, maxLat2: Int,
    ): Boolean {
        return minLong1 >= minLong2
            && maxLong1 <= maxLong2
            && minLat1 >= minLat2
            && maxLat1 <= maxLat2
    }

    /**
     * Returns true when the (longitude, latitude) point lies inside the box.
     */
    fun includedInCoordinates(
        longitude: Int,
        latitude: Int,
        minLon: Int,
        maxLon: Int,
        minLat: Int,
        maxLat: Int,
    ): Boolean {
        return longitude in minLon..maxLon
            && latitude in minLat..maxLat
    }

    /**
     * Returns true when the polyline's bounding box is fully contained in the given box.
     */
    fun includedInCoordinates(
        longitudes: IntArray,
        latitudes: IntArray,
        minLon: Int,
        maxLon: Int,
        minLat: Int,
        maxLat: Int,
    ): Boolean {
        val (mnLo, mxLo, mnLa, mxLa) = boundsOf(longitudes, latitudes, longitudes.size)
        return includedInCoordinates(mnLo, mxLo, mnLa, mxLa, minLon, maxLon, minLat, maxLat)
    }

    /**
     * Returns true when the polyline's bounding box overlaps the given box.
     */
    fun matchesCoordinates(
        longitudes: IntArray,
        latitudes: IntArray,
        nbPoints: Int,
        minLon: Int,
        maxLon: Int,
        minLat: Int,
        maxLat: Int,
    ): Boolean {
        val (mnLo, mxLo, mnLa, mxLa) = boundsOf(longitudes, latitudes, nbPoints)
        return matchesCoordinates(mnLo, mxLo, mnLa, mxLa, minLon, maxLon, minLat, maxLat)
    }

    //********************************
    // INTERNAL
    //********************************

    private data class Bounds(
        val minLon: Int,
        val maxLon: Int,
        val minLat: Int,
        val maxLat: Int,
    )

    private fun boundsOf(longitudes: IntArray, latitudes: IntArray, nbPoints: Int): Bounds {
        var minLon = Int.MAX_VALUE
        var maxLon = Int.MIN_VALUE
        var minLat = Int.MAX_VALUE
        var maxLat = Int.MIN_VALUE
        for (i in 0 until nbPoints) {
            val lon = longitudes[i]
            val lat = latitudes[i]
            if (lon < minLon) {
                minLon = lon
            }
            if (lon > maxLon) {
                maxLon = lon
            }
            if (lat < minLat) {
                minLat = lat
            }
            if (lat > maxLat) {
                maxLat = lat
            }
        }
        return Bounds(minLon, maxLon, minLat, maxLat)
    }

    /**
     * Formats `secInHundredths` (range 0..5_999) as `SS.ss` with zero-padded seconds,
     * independent of the default locale.
     */
    private fun formatSeconds(secInHundredths: Long): String {
        val whole = (secInHundredths / 100L).toString().padStart(2, '0')
        val frac = (secInHundredths % 100L).toString().padStart(2, '0')
        return "$whole.$frac"
    }
}
