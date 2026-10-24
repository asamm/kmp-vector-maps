/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ValueTypesTest {

    @Test
    fun renderKeyEqualForEqualInputs() {
        val config = RenderConfig(tileSizePx = 512, deviceScale = 2.625f)
        val selection = StyleSelection(layerId = "hike_bike", overlayIds = setOf("contours"))

        val first = RenderKey(styleId = "lomaps@1", selection = selection, config = config)
        val second = RenderKey(styleId = "lomaps@1", selection = selection.copy(), config = config.copy())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, second.copy(config = config.copy(textScale = 1.5f)))
        assertNotEquals(first, second.copy(selection = selection.copy(overlayIds = emptySet())))
    }

    @Test
    fun renderConfigRejectsNonPositiveSizes() {
        assertFailsWith<IllegalArgumentException> {
            RenderConfig(tileSizePx = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            RenderConfig(tileSizePx = 256, deviceScale = 0.0f)
        }
        assertFailsWith<IllegalArgumentException> {
            RenderConfig(tileSizePx = 256, textScale = -1.0f)
        }
    }

    @Test
    fun tileRequestStaysInsideItsZoom() {
        TileRequest(x = 0, y = 0, zoom = 0)
        TileRequest(x = 8529, y = 5974, zoom = 14)
        assertFailsWith<IllegalArgumentException> {
            TileRequest(x = 1, y = 0, zoom = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TileRequest(x = 0, y = -1, zoom = 3)
        }
        assertFailsWith<IllegalArgumentException> {
            TileRequest(x = 0, y = 0, zoom = TileRequest.MAX_ZOOM + 1)
        }
    }

    @Test
    fun geoBoundsUnionCoversBoth() {
        val monaco = GeoBounds(minLat = 43.72, minLon = 7.40, maxLat = 43.76, maxLon = 7.44)
        val prague = GeoBounds(minLat = 49.94, minLon = 14.22, maxLat = 50.18, maxLon = 14.71)

        assertEquals(GeoBounds(minLat = 43.72, minLon = 7.40, maxLat = 50.18, maxLon = 14.71), monaco.union(prague))
    }

    @Test
    fun geoBoundsRejectsInvertedOrOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            GeoBounds(minLat = 50.0, minLon = 7.0, maxLat = 43.0, maxLon = 8.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GeoBounds(minLat = 43.0, minLon = 170.0, maxLat = 44.0, maxLon = 190.0)
        }
    }

    @Test
    fun styleMenuFindsLayersById() {
        val hike = StyleLayer(
            id = "hike_bike",
            titles = mapOf("en" to "Hike & bike"),
            isVisible = true,
            isEnabled = true,
            overlayIds = listOf("contours"),
        )
        val menu = StyleMenu(id = "lomaps", defaultLayerId = "hike_bike", defaultLanguage = "en", layers = listOf(hike))

        assertEquals(hike, menu.layer("hike_bike"))
        assertNull(menu.layer("car"))
    }
}
