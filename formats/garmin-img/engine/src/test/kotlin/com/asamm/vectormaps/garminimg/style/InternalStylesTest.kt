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
        for (type in listOf(0x28, 0x32, 0x4C)) {
            assertEquals(water.fillColor, InternalStyles.resolvePolygon(type).fillColor, "type 0x${type.toString(16)}")
        }
    }
}
