/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.HillshadeHook
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarminHillshadeTest {

    private val fixtures = File(System.getProperty("fixturesDir"))
    private val otm = File(fixtures, "maps/otm-monaco/otm-monaco.img")
    private val driver = GarminDriver { size, antiAliased, scale ->
        AwtRenderTarget(widthPx = size, heightPx = size, antiAliased = antiAliased, renderScale = scale)
    }

    // Monaco harbour at z14
    private val harbour = TileRequest(x = 8529, y = 5974, zoom = 14)

    @Test
    fun hookDrawsOverThePolygonsAndUnderTheLines() {
        val green = HillshadeHook { canvas, _, _, _, _ ->
            val graphics = canvas as Graphics2D
            graphics.color = java.awt.Color(GREEN)
            graphics.fillRect(0, 0, 256, 256)
        }
        val shaded = render(
            objectKinds = ObjectKind.POLYGON or ObjectKind.POLYLINE,
            hooks = RenderHooks(hillshade = green),
        )
        // the same lines over the bare background
        val lines = render(objectKinds = ObjectKind.POLYLINE, hooks = RenderHooks.NONE)
        val background = lines.getRGB(0, 0)

        var filled = 0
        var lined = 0
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                val shadedPixel = shaded.getRGB(x, y)
                val linePixel = lines.getRGB(x, y)
                if (linePixel == background) {
                    assertEquals(GREEN, shadedPixel and 0xFFFFFF, "pixel $x, $y")
                    filled++
                } else {
                    assertEquals(linePixel, shadedPixel, "pixel $x, $y")
                    lined++
                }
            }
        }
        assertTrue(filled > 1_000, "$filled filled")
        assertTrue(lined > 1_000, "$lined under lines")
    }

    @Test
    fun canvasStateTheHookLeavesStaysWithIt() {
        // draws nothing and leaves a fully transparent composite behind
        val leaking = HillshadeHook { canvas, _, _, _, _ ->
            (canvas as Graphics2D).composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0f)
        }
        val kinds = ObjectKind.POLYGON or ObjectKind.POLYLINE
        val plain = render(objectKinds = kinds, hooks = RenderHooks.NONE)
        val shaded = render(objectKinds = kinds, hooks = RenderHooks(hillshade = leaking))

        for (y in 0 until 256) {
            for (x in 0 until 256) {
                assertEquals(plain.getRGB(x, y), shaded.getRGB(x, y), "pixel $x, $y")
            }
        }
    }

    @Test
    fun throwingHookLeavesTheTileUnshaded() {
        val throwing = HillshadeHook { _, _, _, _, _ ->
            throw IllegalStateException("no elevation data")
        }
        val kinds = ObjectKind.POLYGON or ObjectKind.POLYLINE
        val plain = render(objectKinds = kinds, hooks = RenderHooks.NONE)
        val shaded = render(objectKinds = kinds, hooks = RenderHooks(hillshade = throwing))

        for (y in 0 until 256) {
            for (x in 0 until 256) {
                assertEquals(plain.getRGB(x, y), shaded.getRGB(x, y), "pixel $x, $y")
            }
        }
    }

    private fun render(objectKinds: Int, hooks: RenderHooks): BufferedImage {
        driver.open(listOf(otm)).use { dataset ->
            val options = GarminOptions(antiAliasing = false, objectKinds = objectKinds)
            dataset.createRenderer(
                style = dataset.styles[0],
                selection = StyleSelection.DEFAULT,
                config = RenderConfig(tileSizePx = 256),
                options = options,
                hooks = hooks,
            ).use { renderer ->
                val target = GarminTileTarget(256)
                assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                return (target.canvas as AwtRenderTarget).image
            }
        }
    }

    private companion object {

        const val GREEN = 0x00FF00
    }
}
