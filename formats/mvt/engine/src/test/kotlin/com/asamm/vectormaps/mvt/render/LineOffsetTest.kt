/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

class LineOffsetTest {

    @Test
    fun offsetsEveryCornerOfAClosedRing() {
        // a clockwise square in screen space; offset 1 to the right moves it inwards
        val ring = MvtPath(
            floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f),
            intArrayOf(8),
            closed = true,
        )

        val out = LineOffset.offset(ring, 1f).coords

        val expected = floatArrayOf(1f, 1f, 9f, 1f, 9f, 9f, 1f, 9f)
        for (i in expected.indices) {
            assertTrue(abs(out[i] - expected[i]) < 1e-4f, "coordinate $i: ${out[i]} vs ${expected[i]}")
        }
    }

    @Test
    fun capsTheMitreAtAHairpin() {
        // a near U-turn: the vertex moves at most 4 offsets and never back onto the centre line
        val line = MvtPath(floatArrayOf(0f, 0f, 100f, 0f, 0f, 1f), intArrayOf(6), closed = false)

        val out = LineOffset.offset(line, 2f).coords

        val moved = hypot(out[2] - 100f, out[3])
        assertTrue(moved > 1.99f && moved <= 8.001f, "hairpin vertex moved $moved")
    }
}
