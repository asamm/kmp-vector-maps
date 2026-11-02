/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.parser.TreLevel
import com.asamm.vectormaps.garminimg.parser.TreLevels
import kotlin.test.Test
import kotlin.test.assertEquals

class PickRenderLevelTest {

    // mkgmap-style levels: 24, 22, 20 bits
    private val levels = TreLevels(
        levels = listOf(
            TreLevel(level = 0, bitsPerCoord = 24, subDivisionCount = 1, inherited = false),
            TreLevel(level = 1, bitsPerCoord = 22, subDivisionCount = 1, inherited = false),
            TreLevel(level = 2, bitsPerCoord = 20, subDivisionCount = 1, inherited = false),
        ),
        totalSubDivisions = 4,
    )

    /**
     * A z17 tile at 256 px resolves 24 bits; the padded Garmin-unit bounds of `ofDegrees` must not
     * push it to the 22-bit level.
     */
    @Test
    fun tileViewportPicksLevelOfItsExactSpan() {
        val span = 360.0 / (1 shl 17)
        val viewport = Viewport.ofDegrees(
            widthPx = 256,
            heightPx = 256,
            minLonDeg = 7.0,
            maxLonDeg = 7.0 + span,
            minLatDeg = 43.0,
            maxLatDeg = 43.0 + span,
        )

        assertEquals(0, ImgRenderer.pickRenderLevel(viewport, levels))
    }
}
