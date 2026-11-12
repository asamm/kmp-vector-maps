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
 * Garmin after the polygons; MVT at each `hillshade` and `color-relief` layer active at the zoom,
 * in style order, not at all without one. Called on render threads, several at once.
 */
fun interface HillshadeHook {

    /**
     * Draws on [canvas] (`android.graphics.Canvas` on Android, `java.awt.Graphics2D` on desktop)
     * in tile pixels, with its own alpha and blending. Its matrix, clip and layers are restored
     * after (all of a `Graphics2D`); it must not change an Android canvas' bitmap, density or draw
     * filter. A throw is logged and leaves the tile unshaded.
     *
     * @param pixelsPerMeter tile pixels per ground metre at the tile's centre
     * @param layer the style layer the call draws, null for formats whose styles describe none
     */
    fun draw(canvas: Any, tile: TileRequest, bounds: GeoBounds, pixelsPerMeter: Double, layer: HillshadeLayer?)

    /**
     * Calls [draw] for [tile], drawn [tileSizePx] pixels wide.
     */
    fun drawTile(canvas: Any, tile: TileRequest, tileSizePx: Int, layer: HillshadeLayer? = null) {
        val bounds = tile.bounds()
        val centerLat = Math.toRadians(latitudeOf(y = (tile.y + 0.5) / (1 shl tile.zoom)))
        val tileMeters = cos(centerLat) * 2.0 * PI * EARTH_RADIUS_M / (1 shl tile.zoom)
        draw(
            canvas = canvas,
            tile = tile,
            bounds = bounds,
            pixelsPerMeter = tileSizePx / tileMeters,
            layer = layer,
        )
    }

    private companion object {

        // the Web Mercator sphere
        const val EARTH_RADIUS_M = 6378137.0
    }
}

/**
 * An elevation layer of an MVT style the host draws for [HillshadeHook]: its MapLibre `hillshade`
 * or `color-relief` layer and its `raster-dem` source. The host picks the layers it draws, such as
 * only those of its terrain source.
 *
 * @param source the style's id of the layer's source
 * @param sourceUrl the source's TileJSON `url`, null when it lists [tiles] or the style lacks it
 * @param encoding the source's elevation encoding, `mapbox` (the default) or `terrarium`
 * @param paint the layer's paint properties at the tile's zoom by their MapLibre names: colours as
 *  ARGB `Int`, numbers as `Double`, enums as `String`; `color-relief-color` as a
 *  `List<Pair<Double, Int>>` of elevation in metres to ARGB, interpolated linearly
 */
class HillshadeLayer(
    val id: String,
    val type: Type,
    val source: String,
    val sourceUrl: String?,
    val tiles: List<String>,
    val encoding: String,
    val paint: Map<String, Any>,
) {

    enum class Type {
        HILLSHADE,
        COLOR_RELIEF,
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
