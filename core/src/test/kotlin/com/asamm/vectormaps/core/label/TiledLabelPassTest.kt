/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import com.asamm.vectormaps.core.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TiledLabelPassTest {

    @Test
    fun collectsTheTilesUnderTheTurnedScreen() {
        val store = TileLabelStore<String>(capacity = 100)
        for (x in 0..3) {
            for (y in 0..3) {
                store.store(tile = TileRequest(x = x, y = y, zoom = 2), labels = listOf("$x,$y"))
            }
        }
        val read = ArrayList<String>()
        val pass = object : TiledLabelPass<String>(source = store, tileSize = 256) {
            override fun candidateOf(label: String, tile: TileRequest): LabelCandidate? {
                read += label
                return null
            }
        }

        // 100 x 100 px around the corner of tiles 1,1 and 2,2: four tiles
        pass.prepareNow(LabelViewport(zoom = 2, centerX = 512.0, centerY = 512.0, widthPx = 100, heightPx = 100))
        assertEquals(setOf("1,1", "2,1", "1,2", "2,2"), read.toSet())

        // a screen past the map's edge reads only the tiles there are
        read.clear()
        pass.prepareNow(LabelViewport(zoom = 2, centerX = 1024.0, centerY = 0.0, widthPx = 100, heightPx = 100))
        assertEquals(setOf("3,0"), read.toSet())

        // turned 45°, the 400 x 20 screen's bounds reach 149 px around its centre: tiles 1..3
        read.clear()
        pass.prepareNow(
            LabelViewport(zoom = 2, centerX = 640.0, centerY = 640.0, widthPx = 400, heightPx = 20, rotationDeg = 45.0),
        )
        assertEquals(9, read.toSet().size)
        // not turned, it stays within tile row 2
        read.clear()
        pass.prepareNow(LabelViewport(zoom = 2, centerX = 640.0, centerY = 640.0, widthPx = 400, heightPx = 20))
        assertEquals(setOf("1,2", "2,2", "3,2"), read.toSet())
    }

    @Test
    fun storeDropsTheLeastRecentlyUsedTile() {
        val store = TileLabelStore<Int>(capacity = 2)
        val a = TileRequest(x = 0, y = 0, zoom = 1)
        val b = TileRequest(x = 1, y = 0, zoom = 1)
        val c = TileRequest(x = 0, y = 1, zoom = 1)
        store.store(tile = a, labels = listOf(1))
        store.store(tile = b, labels = listOf(2))
        // reading a keeps it, so b goes
        store.labelsOf(a)
        store.store(tile = c, labels = listOf(3))

        assertEquals(listOf(1), store.labelsOf(a))
        assertNull(store.labelsOf(b))
        assertEquals(listOf(3), store.labelsOf(c))
    }

    @Test
    fun screenTransformTurnsAboutTheCentre() {
        val view = LabelViewport(zoom = 5, centerX = 1000.0, centerY = 2000.0, widthPx = 200, heightPx = 100, rotationDeg = 90.0)
        val screen = ScreenTransform(view)

        assertEquals(100.0, screen.x(1000.0, 2000.0), 1e-9)
        assertEquals(50.0, screen.y(1000.0, 2000.0), 1e-9)
        // east of the centre turns to below it, as the map turns clockwise
        assertEquals(100.0, screen.x(1010.0, 2000.0), 1e-9)
        assertEquals(60.0, screen.y(1010.0, 2000.0), 1e-9)
    }

    @Test
    fun labelIdMixesBytesLowestFirst() {
        var bytes = LabelId.START
        for (byte in listOf(0x34, 0x12, 0, 0, 0, 0, 0, 0)) {
            bytes = LabelId.mixByte(hash = bytes, byte = byte)
        }

        assertEquals(bytes, LabelId.mix(hash = LabelId.START, value = 0x1234L))
        assertEquals(
            LabelId.mix(hash = LabelId.mix(hash = LabelId.START, value = 'a'.code.toLong()), value = 'b'.code.toLong()),
            LabelId.mix(hash = LabelId.START, text = "ab"),
        )
    }
}
