/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InternalStylesTest {

    @Test
    fun mapBackgroundIsLandNotSea() {
        // 0x4A definition area and 0x4B background cover the whole map
        for (type in listOf(0x4A, 0x4B)) {
            val style = InternalStyles.resolvePolygon(type)
            assertEquals(0, style.fillColor.alpha, "type 0x${type.toString(16)}")
            assertNull(style.strokeColor)
            assertNull(style.pattern)
            assertTrue(style.drawPriority < InternalStyles.resolvePolygon(0x01).drawPriority)
        }
    }

    @Test
    fun seaAndIntermittentWaterAreWater() {
        val water = InternalStyles.resolvePolygon(0x3C)
        // sea, rivers and intermittent water
        for (type in listOf(0x28, 0x32, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4C)) {
            assertEquals(water.fillColor, InternalStyles.resolvePolygon(type).fillColor, "type 0x${type.toString(16)}")
        }
    }

    @Test
    fun whiteStreetsHaveAVisibleCasing() {
        // arterial to residential, white on the light land
        for (type in 0x04..0x09) {
            val style = InternalStyles.resolveLine(type)
            if (style.strokeColor == Color.White) {
                assertTrue(style.borderWidth - style.strokeWidth >= 1.2f, "type 0x${type.toString(16)}")
            }
        }
    }
}
