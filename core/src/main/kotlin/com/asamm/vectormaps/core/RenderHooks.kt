/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sinh

/**
 * Host code a renderer calls while it draws tiles, fixed for the renderer's life. It changes the
 * tiles but is not part of [RenderKey], so a host keys its tile cache on it itself.
 */
class RenderHooks(
    val hillshade: HillshadeHook? = null,
) {

    companion object {

        val NONE = RenderHooks()
    }
}

/**
 * Draws the host's hillshading into a tile at the format's `HILLSHADE` anchor (D13): Mapsforge at
 * its theme's `<hillshading>` layer and level, or after all features when the theme has none;
 * Garmin after the polygons; MVT at the style's first `hillshade` or `color-relief` layer active at
 * the zoom, not at all without one. Called on render threads, several at once.
 */
fun interface HillshadeHook {

    /**
     * Draws on [canvas] (`android.graphics.Canvas` on Android, `java.awt.Graphics2D` on desktop)
     * in tile pixels, with its own alpha and blending. Its matrix, clip and layers are restored
     * after (all of a `Graphics2D`); it must not change an Android canvas' bitmap, density or draw
     * filter. A throw is logged and leaves the tile unshaded.
     *
     * @param pixelsPerMeter tile pixels per ground metre at the tile's centre
     */
    fun draw(canvas: Any, tile: TileRequest, bounds: GeoBounds, pixelsPerMeter: Double)

    /**
     * Calls [draw] for [tile], drawn [tileSizePx] pixels wide.
     */
    fun drawTile(canvas: Any, tile: TileRequest, tileSizePx: Int) {
        val bounds = tile.bounds()
        val centerLat = Math.toRadians(latitudeOf(y = (tile.y + 0.5) / (1 shl tile.zoom)))
        val tileMeters = cos(centerLat) * 2.0 * PI * EARTH_RADIUS_M / (1 shl tile.zoom)
        draw(canvas = canvas, tile = tile, bounds = bounds, pixelsPerMeter = tileSizePx / tileMeters)
    }

    private companion object {

        // the Web Mercator sphere
        const val EARTH_RADIUS_M = 6378137.0
    }
}

/**
 * The tile's area in WGS84 degrees.
 */
fun TileRequest.bounds(): GeoBounds {
    val tiles = (1 shl zoom).toDouble()
    return GeoBounds(
        minLat = latitudeOf(y = (y + 1) / tiles),
        minLon = x / tiles * 360.0 - 180.0,
        maxLat = latitudeOf(y = y / tiles),
        maxLon = (x + 1) / tiles * 360.0 - 180.0,
    )
}

// y from 0 at the top to 1 at the bottom of the Mercator square
private fun latitudeOf(y: Double): Double {
    return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))
}
