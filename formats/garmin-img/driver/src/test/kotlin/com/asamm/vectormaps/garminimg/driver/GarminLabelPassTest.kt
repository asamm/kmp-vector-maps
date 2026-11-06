/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.garminimg.render.MapLabel
import com.asamm.vectormaps.garminimg.style.Color
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class GarminLabelPassTest {

    @Test
    fun storedRoadKeepsTheStretchItsTextCovers() {
        // an L of 100 + 100 px, its middle at the corner; the 50 px text with 8 px each side
        val label = line(xs = floatArrayOf(0f, 100f, 100f), ys = floatArrayOf(0f, 0f, 100f), widthPx = 50f)

        val trimmed = GarminLabelPass.trimmedToText(label)

        assertContentEquals(floatArrayOf(67f, 100f, 100f), trimmed.pathXs)
        assertContentEquals(floatArrayOf(0f, 0f, 33f), trimmed.pathYs)
    }

    @Test
    fun shortRoadsAndPointsStayAsTheyAre() {
        // the text does not fit, so the targets draw it straight at the middle vertex
        val short = line(xs = floatArrayOf(0f, 40f), ys = floatArrayOf(0f, 0f), widthPx = 50f)
        // fits, but the margins reach both ends
        val snug = line(xs = floatArrayOf(0f, 60f), ys = floatArrayOf(0f, 0f), widthPx = 50f)
        // a corrupt vertex, which the pass then skips
        val corrupt = line(xs = floatArrayOf(0f, Float.NaN, 200f), ys = floatArrayOf(0f, 0f, 0f), widthPx = 50f)
        val point = MapLabel(
            kind = MapLabel.Kind.POINT,
            x = 10f,
            y = 10f,
            offsetYPx = 0f,
            widthPx = 50f,
            heightPx = 12f,
            priority = 1,
            text = "point",
            color = Color.rgb(0, 0, 0),
            outlineColor = null,
            fontSize = 10f,
        )

        assertSame(short, GarminLabelPass.trimmedToText(short))
        assertSame(snug, GarminLabelPass.trimmedToText(snug))
        assertSame(corrupt, GarminLabelPass.trimmedToText(corrupt))
        assertSame(point, GarminLabelPass.trimmedToText(point))
    }

    private fun line(xs: FloatArray, ys: FloatArray, widthPx: Float): MapLabel {
        return MapLabel(
            kind = MapLabel.Kind.LINE,
            x = xs[xs.size / 2],
            y = ys[ys.size / 2],
            offsetYPx = 0f,
            widthPx = widthPx,
            heightPx = 12f,
            priority = 1,
            text = "road",
            color = Color.rgb(0, 0, 0),
            outlineColor = null,
            fontSize = 10f,
            pathXs = xs,
            pathYs = ys,
        )
    }
}
