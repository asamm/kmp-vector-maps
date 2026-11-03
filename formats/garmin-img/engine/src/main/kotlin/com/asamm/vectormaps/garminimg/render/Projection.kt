/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.utils.coord.CoordUtils
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Projects Garmin fixed-point coordinates onto screen pixels for a given
 * [Viewport]. Only one projection is implemented for now — Web Mercator —
 * which is what Locus / OpenTopoMap / OSM-style tiles use. An equirectangular
 * projection helper is provided for unit tests where Mercator's polar
 * blow-up is inconvenient.
 */
class Projection(
    val viewport: Viewport,
    val mercator: Boolean = true,
) {

    private val widthPx: Float = viewport.widthPx.toFloat()
    private val heightPx: Float = viewport.heightPx.toFloat()

    private val minLonDeg: Double = viewport.minLonDeg
    private val maxLonDeg: Double = viewport.maxLonDeg
    private val minLatDeg: Double = viewport.minLatDeg
    private val maxLatDeg: Double = viewport.maxLatDeg

    private val minXMercator: Double = mercatorX(minLonDeg)
    private val maxXMercator: Double = mercatorX(maxLonDeg)
    private val minYMercator: Double = mercatorY(minLatDeg)
    private val maxYMercator: Double = mercatorY(maxLatDeg)

    /**
     * Projects a single Garmin coordinate to screen pixels.
     */
    fun project(longitude: Int, latitude: Int): FloatPair {
        return FloatPair(x = projectX(longitude), y = projectY(latitude))
    }

    /**
     * Screen x of a Garmin longitude, without the [FloatPair] of [project].
     */
    fun projectX(longitude: Int): Float {
        val lonDeg = CoordUtils.toWGS84(longitude)
        val nx = if (mercator) {
            (mercatorX(lonDeg) - minXMercator) / (maxXMercator - minXMercator)
        } else {
            (lonDeg - minLonDeg) / (maxLonDeg - minLonDeg)
        }
        return (nx * widthPx).toFloat()
    }

    /**
     * Screen y of a Garmin latitude, without the [FloatPair] of [project].
     */
    fun projectY(latitude: Int): Float {
        val latDeg = CoordUtils.toWGS84(latitude)
        val ny = if (mercator) {
            (mercatorY(latDeg) - minYMercator) / (maxYMercator - minYMercator)
        } else {
            (latDeg - minLatDeg) / (maxLatDeg - minLatDeg)
        }
        // Y is flipped because screen Y grows down while Mercator Y grows up.
        return ((1.0 - ny) * heightPx).toFloat()
    }

    /**
     * Bulk projection — fills `xsOut` / `ysOut` from parallel
     * `longitudes` / `latitudes`.
     */
    fun projectBulk(
        longitudes: IntArray,
        latitudes: IntArray,
        xsOut: FloatArray,
        ysOut: FloatArray,
    ) {
        val n = longitudes.size
        for (i in 0 until n) {
            xsOut[i] = projectX(longitudes[i])
            ysOut[i] = projectY(latitudes[i])
        }
    }

    private fun mercatorX(lonDeg: Double): Double {
        return lonDeg * PI / 180.0
    }

    private fun mercatorY(latDeg: Double): Double {
        // Clamp near the poles to avoid Infinity.
        val clamped = latDeg.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val rad = clamped * PI / 180.0
        return ln(tan(PI / 4.0 + rad / 2.0))
    }

    companion object {

        /** Web Mercator caps near the poles to avoid Infinity. */
        private const val MAX_MERCATOR_LAT: Double = 85.05112878
    }
}

/**
 * The pair of `(x, y)` floats returned by [Projection.project]. Avoids
 * importing a third-party `Pair<Float, Float>` and the boxing it implies.
 */
data class FloatPair(val x: Float, val y: Float)

/**
 * The viewable rectangle: a screen-pixel size and a Garmin-coord bounding
 * box. The renderer projects features into this viewport.
 *
 * @property minLon western boundary in Garmin fixed-point coords; selects the map objects.
 * @property maxLon eastern boundary.
 * @property minLat southern boundary.
 * @property maxLat northern boundary.
 * @property minLonDeg western boundary in degrees the projection maps to the left edge; a Garmin
 *   unit is ~2.4 m, too coarse for adjacent tiles at high zoom, see [ofDegrees].
 * @property maxLonDeg eastern boundary in degrees.
 * @property minLatDeg southern boundary in degrees.
 * @property maxLatDeg northern boundary in degrees.
 */
data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val minLon: Int,
    val maxLon: Int,
    val minLat: Int,
    val maxLat: Int,
    val minLonDeg: Double = CoordUtils.toWGS84(minLon),
    val maxLonDeg: Double = CoordUtils.toWGS84(maxLon),
    val minLatDeg: Double = CoordUtils.toWGS84(minLat),
    val maxLatDeg: Double = CoordUtils.toWGS84(maxLat),
) {

    init {
        require(widthPx > 0 && heightPx > 0) { "viewport has zero dimension" }
        // Strict `>` (not `>=`) so a zero-extent viewport — minLon ==
        // maxLon or matching latitude — can't slip through. The
        // projection's normalisation `(lon - minLon) / (maxLon - minLon)`
        // would otherwise divide by zero and produce NaN coordinates;
        // AWT silently renders blank, Android may throw.
        require(maxLon > minLon) { "maxLon ($maxLon) must be > minLon ($minLon)" }
        require(maxLat > minLat) { "maxLat ($maxLat) must be > minLat ($minLat)" }
        require(maxLonDeg > minLonDeg) { "maxLonDeg ($maxLonDeg) must be > minLonDeg ($minLonDeg)" }
        require(maxLatDeg > minLatDeg) { "maxLatDeg ($maxLatDeg) must be > minLatDeg ($minLatDeg)" }
    }

    companion object {

        /**
         * Builds a [Viewport] projecting exactly the given degree bounds, such as a map tile's;
         * the Garmin-unit bounds round outwards, so objects on the edge are still read.
         */
        fun ofDegrees(
            widthPx: Int,
            heightPx: Int,
            minLonDeg: Double,
            maxLonDeg: Double,
            minLatDeg: Double,
            maxLatDeg: Double,
        ): Viewport {
            return Viewport(
                widthPx = widthPx,
                heightPx = heightPx,
                minLon = CoordUtils.fromWGS84(minLonDeg) - 1,
                maxLon = CoordUtils.fromWGS84(maxLonDeg) + 1,
                minLat = CoordUtils.fromWGS84(minLatDeg) - 1,
                maxLat = CoordUtils.fromWGS84(maxLatDeg) + 1,
                minLonDeg = minLonDeg,
                maxLonDeg = maxLonDeg,
                minLatDeg = minLatDeg,
                maxLatDeg = maxLatDeg,
            )
        }

        /**
         * Builds a [Viewport] tightly fitting an IMG's TRE bounding box. Used
         * by the simple "render the whole map at this size" entry point.
         */
        fun ofTreBounds(
            widthPx: Int,
            heightPx: Int,
            northBoundary: Int,
            eastBoundary: Int,
            southBoundary: Int,
            westBoundary: Int,
        ): Viewport {
            return Viewport(
                widthPx = widthPx,
                heightPx = heightPx,
                minLon = westBoundary,
                maxLon = eastBoundary,
                minLat = southBoundary,
                maxLat = northBoundary,
            )
        }
    }
}
