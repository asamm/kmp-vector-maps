/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelGoldensTest {

    @Test
    fun tileWithinShareLimitPasses() {
        assertNull(LabelGoldens.verdict(tile = TILE, differingPixels = 524, totalPixels = TOTAL, updateTask = "update"))
        assertNotNull(LabelGoldens.verdict(tile = TILE, differingPixels = 525, totalPixels = TOTAL, updateTask = "update"))
    }

    @Test
    fun labelGoldensAreLabelledTilesAndDumps() {
        assertTrue(LabelGoldens.isLabelGolden(TILE))
        assertTrue(LabelGoldens.isLabelGolden("theme_car_ds2.625_ts1.0_separate_labels.txt"))
        assertFalse(LabelGoldens.isLabelGolden("theme_car_ds2.625_ts1.0_separate_z15_17052_11960.png"))
        assertFalse(LabelGoldens.isLabelGolden("reader.txt"))
    }

    @Test
    fun channelDistanceIsTheLargestChannelDifference() {
        assertEquals(0, LabelGoldens.channelDistance(0xFF102030.toInt(), 0xFF102030.toInt()))
        assertEquals(7, LabelGoldens.channelDistance(0xFF102030.toInt(), 0xFF10192A.toInt()))
        assertEquals(255, LabelGoldens.channelDistance(0xFF000000.toInt(), 0x00000000))
    }

    private companion object {

        private const val TILE = "theme_car_ds2.625_ts1.0_z15_17052_11960.png"

        private const val TOTAL = 512 * 512
    }
}
