/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer.common

import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import kotlin.math.floor

/**
 * Immutable map view: integer zoom and the centre in world pixels at that zoom.
 */
data class Viewport(
    val zoom: Int,
    val centerX: Double,
    val centerY: Double,
    val tileSize: Int,
) {

    val center: LatLong
        get() {
            val mapSize = mapSize()
            return LatLong(
                MercatorProjection.pixelYToLatitude(centerY, mapSize),
                MercatorProjection.pixelXToLongitude(centerX, mapSize),
            )
        }

    fun panBy(dx: Double, dy: Double): Viewport {
        return copy(centerX = centerX - dx, centerY = centerY - dy)
    }

    /**
     * Zooms by `delta` levels keeping the world point under (`anchorX`, `anchorY`) in place; the
     * anchor is relative to the view centre.
     */
    fun zoomBy(delta: Int, anchorX: Double, anchorY: Double): Viewport {
        val newZoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) {
            return this
        }
        val factor = Math.pow(2.0, (newZoom - zoom).toDouble())
        return copy(
            zoom = newZoom,
            centerX = (centerX + anchorX) * factor - anchorX,
            centerY = (centerY + anchorY) * factor - anchorY,
        )
    }

    /**
     * Tiles covering a view of `width` × `height` pixels, nearest to the centre first.
     */
    fun visibleTiles(width: Int, height: Int): List<Tile> {
        val maxTile = (1 shl zoom) - 1
        val left = floor((centerX - width / 2.0) / tileSize).toInt().coerceAtLeast(0)
        val right = floor((centerX + width / 2.0) / tileSize).toInt().coerceAtMost(maxTile)
        val top = floor((centerY - height / 2.0) / tileSize).toInt().coerceAtLeast(0)
        val bottom = floor((centerY + height / 2.0) / tileSize).toInt().coerceAtMost(maxTile)
        val cx = centerX / tileSize
        val cy = centerY / tileSize
        val result = mutableListOf<Tile>()
        for (y in top..bottom) {
            for (x in left..right) {
                result += Tile(x, y, zoom.toByte(), tileSize)
            }
        }
        return result.sortedBy { (it.tileX + 0.5 - cx) * (it.tileX + 0.5 - cx) + (it.tileY + 0.5 - cy) * (it.tileY + 0.5 - cy) }
    }

    private fun mapSize(): Long {
        return MercatorProjection.getMapSize(zoom.toByte(), tileSize)
    }

    companion object {
        const val MIN_ZOOM = 2
        const val MAX_ZOOM = 21

        fun of(center: LatLong, zoom: Int, tileSize: Int): Viewport {
            val mapSize = MercatorProjection.getMapSize(zoom.toByte(), tileSize)
            return Viewport(
                zoom = zoom,
                centerX = MercatorProjection.longitudeToPixelX(center.longitude, mapSize),
                centerY = MercatorProjection.latitudeToPixelY(center.latitude, mapSize),
                tileSize = tileSize,
            )
        }
    }
}
