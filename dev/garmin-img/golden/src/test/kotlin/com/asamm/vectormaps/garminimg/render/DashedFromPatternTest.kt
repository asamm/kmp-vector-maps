/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.style.Bitmap
import com.asamm.vectormaps.garminimg.style.Color
import com.asamm.vectormaps.garminimg.style.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression tests for the dashed-from-pattern path of [AwtRenderTarget].
 * The fully-opaque 1-row pattern used to IOOB when the rebalance step
 * tried to mutate a one-element runs list — that's REVIEW.md C2.
 */
class DashedFromPatternTest {

    @Test
    fun fullyOpaqueOneRowPatternStrokesSolidWithoutCrash() {
        val width = 32
        val height = 1
        val rgba = ByteArray(width * height * 4)
        for (px in 0 until width) {
            val i = px * 4
            rgba[i] = 0xFF.toByte()          // r
            rgba[i + 1] = 0x00.toByte()      // g
            rgba[i + 2] = 0x00.toByte()      // b
            rgba[i + 3] = 0xFF.toByte()      // a — fully opaque
        }
        val bitmap = Bitmap(width = width, height = height, rgba = rgba)
        val style = Style.LineStyle(
            drawPriority = 0,
            strokeColor = Color.Black,
            borderColor = null,
            strokeWidth = 2f,
            borderWidth = 0f,
            pattern = bitmap,
        )
        val target = AwtRenderTarget(64, 64, antiAliased = false, renderScale = 1f)
        target.beginFrame(Color.White)
        // Diagonal line guaranteed to touch interior pixels.
        target.drawPolylineStroke(
            xs = floatArrayOf(8f, 56f),
            ys = floatArrayOf(8f, 56f),
            style = style,
        )
        target.endFrame()
        // Sample a midpoint pixel: the solid-strokes path means we should
        // see red here (the pattern's foreground colour). The pre-fix
        // version threw before this point.
        val argb = target.image.getRGB(32, 32)
        assertNotEquals(0xFFFFFFFF.toInt(), argb, "expected non-background pixel at line midpoint")
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        assertEquals(0xFF, red, "midpoint should carry the pattern's red foreground")
        assertEquals(0x00, green)
        assertEquals(0x00, blue)
    }

    @Test
    fun fullyTransparentOneRowPatternIsNoOp() {
        val width = 32
        val rgba = ByteArray(width * 4) // all zeros → all transparent
        val bitmap = Bitmap(width = width, height = 1, rgba = rgba)
        val style = Style.LineStyle(
            drawPriority = 0,
            strokeColor = Color.Black,
            borderColor = null,
            strokeWidth = 2f,
            borderWidth = 0f,
            pattern = bitmap,
        )
        val target = AwtRenderTarget(32, 32, antiAliased = false, renderScale = 1f)
        target.beginFrame(Color.White)
        target.drawPolylineStroke(
            xs = floatArrayOf(0f, 32f),
            ys = floatArrayOf(16f, 16f),
            style = style,
        )
        target.endFrame()
        // Whole canvas remains the background — no draw happened.
        assertEquals(0xFFFFFFFF.toInt(), target.image.getRGB(16, 16))
    }

    @Test
    fun whiteOneRowPatternIsDrawn() {
        val width = 32
        // all opaque white: its ARGB equals -1, which the AWT target once took for "no colour"
        val rgba = ByteArray(width * 4) { 0xFF.toByte() }
        val bitmap = Bitmap(width = width, height = 1, rgba = rgba)
        val style = Style.LineStyle(
            drawPriority = 0,
            strokeColor = Color.Black,
            borderColor = null,
            strokeWidth = 2f,
            borderWidth = 0f,
            pattern = bitmap,
        )
        val target = AwtRenderTarget(32, 32, antiAliased = false, renderScale = 1f)
        target.beginFrame(Color.Black)
        target.drawPolylineStroke(
            xs = floatArrayOf(0f, 32f),
            ys = floatArrayOf(16f, 16f),
            style = style,
        )
        target.endFrame()
        assertEquals(0xFFFFFFFF.toInt(), target.image.getRGB(16, 16))
    }
}
