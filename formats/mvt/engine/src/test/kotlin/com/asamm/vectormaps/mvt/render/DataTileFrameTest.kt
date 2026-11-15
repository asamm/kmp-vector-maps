/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.tile.Geometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataTileFrameTest {

    @Test
    fun overzoomedTileMeetsOnlyNearbyGeometry() {
        // the top-left quarter of its z14 data tile, 512 px, so 1 extent unit is 0.25 px
        val frame = DataTileFrame(zoom = 15, x = 0, y = 0, widthPx = 512, heightPx = 512, maxDataZoom = 14)
        val inside = Geometry(floatArrayOf(100f, 100f, 200f, 200f), intArrayOf(4))
        // the bottom-right quarter, 1024 px away
        val outside = Geometry(floatArrayOf(3000f, 3000f, 4000f, 4000f), intArrayOf(4))
        // 8 px right of the tile
        val beside = Geometry(floatArrayOf(2080f, 100f, 2100f, 200f), intArrayOf(4))

        assertTrue(frame.meets(inside, extent = 4096, marginPx = 0f))
        assertFalse(frame.meets(outside, extent = 4096, marginPx = 0f))
        assertFalse(frame.meets(beside, extent = 4096, marginPx = 4f))
        assertTrue(frame.meets(beside, extent = 4096, marginPx = 10f))
    }

    @Test
    fun startsPatternsAtTheWorldOrigin() {
        assertEquals(0f, patternOffset(origin = 1024, period = 32f))
        assertEquals(16f, patternOffset(origin = 1040, period = 32f))
        // a scaled period need not divide the tile origin
        assertEquals(12f, patternOffset(origin = 100, period = 22f))
        assertEquals(10f, patternOffset(origin = -12, period = 22f))
    }
}
