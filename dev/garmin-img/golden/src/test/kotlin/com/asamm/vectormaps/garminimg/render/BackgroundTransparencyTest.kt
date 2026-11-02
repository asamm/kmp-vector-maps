/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.utils.coord.CoordUtils
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.style.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the rendered tile's alpha channel.
 *
 * The tile renderer must support `Color.Transparent` as the background
 * so that Locus / similar consumers can stack the IMG render on top of
 * a base map without the IMG's background colour blocking out
 * underlying content.
 *
 * The Java predecessor's last meaningful fix in 2020
 * (`f9bc4d4536`) was about exactly this; we make it explicit and
 * regression-test it here so the Phase C rewrite doesn't silently
 * break what the old code relied on.
 */
class BackgroundTransparencyTest {

    @Test
    fun transparentBackgroundLeavesUnpaintedPixelsTransparent() {
        val img = openMonaco()
        val widthPx = 256
        val heightPx = 256
        // Tight viewport over a corner of Monaco so most of the image has no
        // map data — those pixels must stay alpha=0 with a transparent
        // background fill.
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = CoordUtils.fromWGS84(7.40),
            maxLon = CoordUtils.fromWGS84(7.50),
            minLat = CoordUtils.fromWGS84(43.72),
            maxLat = CoordUtils.fromWGS84(43.85),
        )
        val target = AwtRenderTarget(widthPx, heightPx, antiAliased = false)
        ImgRenderer(img).render(viewport, target, backgroundColor = Color.Transparent)

        // Top-right corner is well outside Monaco proper — should stay
        // transparent. Sample a 4x4 block to avoid hitting the occasional
        // contour line that may stray near the edge.
        var transparentSamples = 0
        for (dx in 0 until 4) {
            for (dy in 0 until 4) {
                val argb = target.image.getRGB(widthPx - 1 - dx, dy)
                if ((argb ushr 24) and 0xFF == 0) {
                    transparentSamples++
                }
            }
        }
        assertTrue(
            transparentSamples >= 14,
            "Expected at least 14/16 transparent pixels in the top-right " +
                "no-data corner, got $transparentSamples. backgroundColor=Transparent " +
                "is not actually clearing alpha.",
        )
    }

    @Test
    fun opaqueBackgroundProducesAlphaOpaqueAtNonDataCorner() {
        // Sanity: the OPPOSITE case. A non-transparent background must
        // leave the no-data corners fully opaque (alpha=0xFF), so a
        // future regression that drops the SrcOver fillRect would surface.
        val img = openMonaco()
        val widthPx = 256
        val heightPx = 256
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = CoordUtils.fromWGS84(7.40),
            maxLon = CoordUtils.fromWGS84(7.50),
            minLat = CoordUtils.fromWGS84(43.72),
            maxLat = CoordUtils.fromWGS84(43.85),
        )
        val target = AwtRenderTarget(widthPx, heightPx, antiAliased = false)
        val opaqueBeige = Color.rgb(0xF8, 0xF6, 0xF0, 0xFF)
        ImgRenderer(img).render(viewport, target, backgroundColor = opaqueBeige)

        val argb = target.image.getRGB(widthPx - 1, 0)
        assertEquals(0xFF, (argb ushr 24) and 0xFF, "opaque background must render alpha=0xFF")
    }

    private fun openMonaco(): ImgFile {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
        return ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
    }
}
