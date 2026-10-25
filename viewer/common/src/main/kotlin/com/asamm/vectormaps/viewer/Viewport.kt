/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.TileRequest
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

/**
 * What a screen shows of the Web Mercator tile grid: an integer zoom and the centre as a fraction
 * of the world, `x` growing east and `y` south, both in 0..1.
 */
data class Viewport(
    val zoom: Int,
    val x: Double,
    val y: Double,
    val tileSize: Int,
) {

    val lat: Double
        get() {
            return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))
        }

    val lon: Double
        get() = x * 360.0 - 180.0

    private val worldPx: Double
        get() {
            return worldPx(zoom)
        }

    /**
     * Moves the map content by `dx`, `dy` screen pixels, as a drag does; the centre stays on the
     * world.
     */
    fun dragged(dx: Double, dy: Double): Viewport {
        return copy(x = (x - dx / worldPx).coerceIn(0.0, 1.0), y = (y - dy / worldPx).coerceIn(0.0, 1.0))
    }

    /**
     * Zooms by `steps` levels; the map point `focusX`, `focusY` pixels from the screen centre stays
     * under the same pixel.
     */
    fun zoomed(steps: Int, focusX: Double, focusY: Double): Viewport {
        val target = (zoom + steps).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (target == zoom) {
            return this
        }
        val focusWorldX = x + focusX / worldPx
        val focusWorldY = y + focusY / worldPx
        val targetPx = worldPx(target)
        return copy(
            zoom = target,
            x = (focusWorldX - focusX / targetPx).coerceIn(0.0, 1.0),
            y = (focusWorldY - focusY / targetPx).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Screen x of the left edge of tile column `tileX` on a screen `width` pixels wide.
     */
    fun screenX(tileX: Int, width: Int): Float {
        return (tileX.toDouble() * tileSize - (x * worldPx - width / 2.0)).toFloat()
    }

    /**
     * Screen y of the top edge of tile row `tileY` on a screen `height` pixels high.
     */
    fun screenY(tileY: Int, height: Int): Float {
        return (tileY.toDouble() * tileSize - (y * worldPx - height / 2.0)).toFloat()
    }

    /**
     * Tiles on a `width` × `height` screen, those nearest the centre first.
     */
    fun tiles(width: Int, height: Int): List<TileRequest> {
        val columns = span(start = x * worldPx - width / 2.0, length = width)
        val rows = span(start = y * worldPx - height / 2.0, length = height)
        val centreColumn = x * worldPx / tileSize - 0.5
        val centreRow = y * worldPx / tileSize - 0.5
        val result = ArrayList<TileRequest>()
        for (row in rows) {
            for (column in columns) {
                result.add(TileRequest(x = column, y = row, zoom = zoom))
            }
        }
        result.sortBy { tile ->
            val dx = tile.x - centreColumn
            val dy = tile.y - centreRow
            dx * dx + dy * dy
        }
        return result
    }

    // tile indexes touched by `length` pixels from world pixel `start`, clamped to the grid
    private fun span(start: Double, length: Int): IntRange {
        val last = (1 shl zoom) - 1
        val first = floor(start / tileSize).toInt().coerceAtLeast(0)
        val end = (ceil((start + length) / tileSize).toInt() - 1).coerceAtMost(last)
        return first..end
    }

    private fun worldPx(zoom: Int): Double {
        return tileSize.toDouble() * (1L shl zoom)
    }

    companion object {

        const val MIN_ZOOM = 2

        const val MAX_ZOOM = 21

        /**
         * The view centred on `lat`, `lon`; the zoom and centre are clamped to the valid range.
         */
        fun at(lat: Double, lon: Double, zoom: Int, tileSize: Int): Viewport {
            return Viewport(
                zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
                x = worldX(lon).coerceIn(0.0, 1.0),
                y = worldY(lat),
                tileSize = tileSize,
            )
        }

        /**
         * The highest zoom that shows all of `bounds` on a `width` × `height` screen, centred.
         */
        fun fitting(bounds: GeoBounds, width: Int, height: Int, tileSize: Int): Viewport {
            val west = worldX(bounds.minLon)
            val east = worldX(bounds.maxLon)
            val north = worldY(bounds.maxLat)
            val south = worldY(bounds.minLat)
            // world widths in pixels at which each axis just fits
            val fitX = width / ((east - west) * tileSize)
            val fitY = height / ((south - north) * tileSize)
            val zoom = floor(log2(min(fitX, fitY))).let { if (it.isFinite()) it.toInt() else MAX_ZOOM }
            return Viewport(
                zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
                x = (west + east) / 2.0,
                y = (north + south) / 2.0,
                tileSize = tileSize,
            )
        }

        private fun worldX(lon: Double): Double {
            return (lon + 180.0) / 360.0
        }

        private fun worldY(lat: Double): Double {
            // Web Mercator stops short of the poles
            val radians = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
            return (1.0 - ln(tan(PI / 4.0 + radians / 2.0)) / PI) / 2.0
        }
    }
}
