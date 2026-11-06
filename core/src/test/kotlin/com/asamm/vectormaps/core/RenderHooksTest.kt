/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderHooksTest {

    @Test
    fun tileBoundsFollowWebMercator() {
        val world = TileRequest(x = 0, y = 0, zoom = 0).bounds()
        assertEquals(-180.0, world.minLon, 1e-9)
        assertEquals(180.0, world.maxLon, 1e-9)
        assertEquals(85.0511287798, world.maxLat, 1e-9)
        assertEquals(-85.0511287798, world.minLat, 1e-9)

        // the north-east quarter at zoom 1
        val northEast = TileRequest(x = 1, y = 0, zoom = 1).bounds()
        assertEquals(0.0, northEast.minLon, 1e-9)
        assertEquals(0.0, northEast.minLat, 1e-9)
    }

    @Test
    fun drawTileGivesBoundsAndGroundScale() {
        var scale = 0.0
        var bounds: GeoBounds? = null
        val hook = HillshadeHook { _, _, tileBounds, pixelsPerMeter ->
            bounds = tileBounds
            scale = pixelsPerMeter
        }

        // the zoom-1 tile touching the equator: its pixel centre sits at 66.5° N
        val tile = TileRequest(x = 0, y = 0, zoom = 1)
        hook.drawTile(canvas = Any(), tile = tile, tileSizePx = 256)

        assertEquals(tile.bounds(), bounds)
        val centerLat = Math.atan(Math.sinh(PI * 0.5))
        assertEquals(66.513, Math.toDegrees(centerLat), 1e-3)
        val tileMeters = Math.cos(centerLat) * 2.0 * PI * 6378137.0 / 2.0
        assertEquals(256.0 / tileMeters, scale, 1e-12)
    }
}
