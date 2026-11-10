/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.coord

import kotlin.math.round

/**
 * Converts between Garmin's 24-bit fixed-point coordinates and WGS84 degrees.
 *
 * Garmin coordinates are signed 24-bit fixed-point fractions of a full circle: a
 * coordinate value `c` represents `c * 360.0 / 2^24` degrees. This means any IMG
 * file's lat/lon precision is roughly 2.4 m at the equator.
 */
object CoordUtils {

    private const val COORD_FACTOR: Double = 360.0 / (1 shl 24)

    /**
     * Converts a Garmin fixed-point coordinate to WGS84 degrees.
     */
    fun toWGS84(coord: Int): Double {
        return coord * COORD_FACTOR
    }

    /**
     * Converts WGS84 degrees to the nearest Garmin fixed-point coordinate.
     */
    fun fromWGS84(coord: Double): Int {
        return round(coord / COORD_FACTOR).toInt()
    }
}
