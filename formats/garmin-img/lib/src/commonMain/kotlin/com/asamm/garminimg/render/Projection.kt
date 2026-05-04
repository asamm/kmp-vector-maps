/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import com.asamm.garminimg.coord.CoordUtils
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

    private val minLonDeg: Double = CoordUtils.toWGS84(viewport.minLon)
    private val maxLonDeg: Double = CoordUtils.toWGS84(viewport.maxLon)
    private val minLatDeg: Double = CoordUtils.toWGS84(viewport.minLat)
    private val maxLatDeg: Double = CoordUtils.toWGS84(viewport.maxLat)

    private val minXMercator: Double = mercatorX(minLonDeg)
    private val maxXMercator: Double = mercatorX(maxLonDeg)
    private val minYMercator: Double = mercatorY(minLatDeg)
    private val maxYMercator: Double = mercatorY(maxLatDeg)

    /**
     * Projects a single Garmin coordinate to screen pixels.
     */
    fun project(longitude: Int, latitude: Int): FloatPair {
        val lonDeg = CoordUtils.toWGS84(longitude)
        val latDeg = CoordUtils.toWGS84(latitude)
        return if (mercator) {
            val mx = mercatorX(lonDeg)
            val my = mercatorY(latDeg)
            val nx = (mx - minXMercator) / (maxXMercator - minXMercator)
            val ny = (my - minYMercator) / (maxYMercator - minYMercator)
            // Y is flipped because screen Y grows down while Mercator Y grows up.
            FloatPair(
                x = (nx * widthPx).toFloat(),
                y = ((1.0 - ny) * heightPx).toFloat(),
            )
        } else {
            val nx = (lonDeg - minLonDeg) / (maxLonDeg - minLonDeg)
            val ny = (latDeg - minLatDeg) / (maxLatDeg - minLatDeg)
            FloatPair(
                x = (nx * widthPx).toFloat(),
                y = ((1.0 - ny) * heightPx).toFloat(),
            )
        }
    }

    /**
     * Bulk projection — fills `xsOut` / `ysOut` from parallel
     * `longitudes` / `latitudes`. Skips per-call object allocation.
     */
    fun projectBulk(
        longitudes: IntArray,
        latitudes: IntArray,
        xsOut: FloatArray,
        ysOut: FloatArray,
    ) {
        val n = longitudes.size
        for (i in 0 until n) {
            val p = project(longitudes[i], latitudes[i])
            xsOut[i] = p.x
            ysOut[i] = p.y
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
 * @property minLon western boundary in Garmin fixed-point coords.
 * @property maxLon eastern boundary.
 * @property minLat southern boundary.
 * @property maxLat northern boundary.
 */
data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val minLon: Int,
    val maxLon: Int,
    val minLat: Int,
    val maxLat: Int,
) {

    init {
        require(widthPx > 0 && heightPx > 0) { "viewport has zero dimension" }
        require(maxLon >= minLon) { "maxLon < minLon" }
        require(maxLat >= minLat) { "maxLat < minLat" }
    }

    companion object {

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
