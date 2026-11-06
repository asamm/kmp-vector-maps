/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.HillshadeHook
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapsforgeHillshadeTest {

    private val fixtures = File(System.getProperty("fixturesDir"))
    private val monaco = File(fixtures, "maps/monaco.osm.map")
    private val driver = MapsforgeDriver(AwtGraphicFactory.INSTANCE)

    // Monaco harbour at z14
    private val harbour = TileRequest(x = 8529, y = 5974, zoom = 14)

    // fills the whole tile, so what draws later shows over it
    private val green = HillshadeHook { canvas, _, _, _ ->
        val graphics = canvas as Graphics2D
        graphics.color = java.awt.Color(GREEN)
        graphics.fillRect(0, 0, 256, 256)
    }

    @Test
    fun themeElementPlacesTheHookBetweenItsRules() {
        val hillshading = "<hillshading/>"
        val plain = counts(theme = hillshading, hooks = RenderHooks.NONE)
        val shaded = counts(theme = hillshading, hooks = RenderHooks(hillshade = green))

        // without a hook the theme's areas and lines draw as they are
        assertEquals(0, plain.green)
        assertTrue(plain.red > 1_000, "$plain")
        assertTrue(plain.blue > 1_000, "$plain")
        // the areas before the element are under the fill, the lines after it over it
        assertTrue(shaded.green > 1_000, "$shaded")
        assertTrue(shaded.red < plain.red / 20, "$shaded")
        assertEquals(plain.blue, shaded.blue)
    }

    @Test
    fun hiddenCategoryDrawsNoHillshading() {
        val shaded = counts(theme = "<hillshading cat=\"relief\"/>", hooks = RenderHooks(hillshade = green), menu = true)

        assertEquals(0, shaded.green)
        assertTrue(shaded.red > 1_000, "$shaded")
    }

    @Test
    fun elementDrawsOnlyWithinItsZoomRange() {
        // the harbour tile is at zoom 14
        val above = counts(theme = "<hillshading zoom-min=\"15\"/>", hooks = RenderHooks(hillshade = green))
        val within = counts(theme = "<hillshading zoom-min=\"14\" zoom-max=\"14\"/>", hooks = RenderHooks(hillshade = green))

        assertEquals(0, above.green)
        assertTrue(within.green > 1_000, "$within")
    }

    @Test
    fun throwingHookLeavesTheTileUnshaded() {
        val throwing = HillshadeHook { _, _, _, _ ->
            throw IllegalStateException("no elevation data")
        }
        val plain = counts(theme = "<hillshading/>", hooks = RenderHooks.NONE)
        val shaded = counts(theme = "<hillshading/>", hooks = RenderHooks(hillshade = throwing))

        assertEquals(plain, shaded)
    }

    @Test
    fun canvasStateTheHookLeavesStaysWithIt() {
        // draws nothing and leaves a fully transparent composite behind
        val leaking = HillshadeHook { canvas, _, _, _ ->
            (canvas as Graphics2D).composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0f)
        }
        val plain = counts(theme = "<hillshading/>", hooks = RenderHooks.NONE)
        val shaded = counts(theme = "<hillshading/>", hooks = RenderHooks(hillshade = leaking))

        assertEquals(plain, shaded)
    }

    @Test
    fun themeWithoutElementGetsTheHookOverAllWays() {
        val shaded = counts(theme = "", hooks = RenderHooks(hillshade = green))

        assertEquals(256 * 256, shaded.green)
    }

    private fun counts(theme: String, hooks: RenderHooks, menu: Boolean = false): Colours {
        val file = createTempFile(suffix = ".xml").toFile()
        try {
            file.writeText(themeXml(hillshading = theme, menu = menu))
            driver.open(listOf(monaco)).use { dataset ->
                val config = RenderConfig(tileSizePx = 256)
                dataset.createRenderer(driver.loadStyle(file), StyleSelection.DEFAULT, config, hooks).use { renderer ->
                    val target = TileBitmapTarget(256)
                    assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                    val bitmap = target.bitmap!!
                    try {
                        val image = AwtGraphicFactory.getBitmap(bitmap)
                        val colours = Colours()
                        for (y in 0 until 256) {
                            for (x in 0 until 256) {
                                when (image.getRGB(x, y) and 0xFFFFFF) {
                                    GREEN -> colours.green++
                                    RED -> colours.red++
                                    BLUE -> colours.blue++
                                }
                            }
                        }
                        return colours
                    } finally {
                        bitmap.decrementRefCount()
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    // closed ways red, the element, open ways blue; the menu's only layer shows no `relief`
    private fun themeXml(hillshading: String, menu: Boolean): String {
        val styleMenu = if (menu) {
            """
                <stylemenu id="menu" defaultvalue="base" defaultlang="en">
                    <layer id="base" enabled="true" visible="true">
                        <name lang="en" value="base"/>
                        <cat id="ways"/>
                    </layer>
                </stylemenu>
            """
        } else {
            ""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5" map-background="#ffffff">
                $styleMenu
                <rule e="way" k="*" v="*" closed="yes">
                    <area fill="#ff0000"/>
                </rule>
                $hillshading
                <rule e="way" k="*" v="*" closed="no">
                    <line stroke="#0000ff" stroke-width="4"/>
                </rule>
            </rendertheme>
        """.trimIndent()
    }

    private data class Colours(
        var green: Int = 0,
        var red: Int = 0,
        var blue: Int = 0,
    )

    private companion object {

        const val GREEN = 0x00FF00

        const val RED = 0xFF0000

        const val BLUE = 0x0000FF
    }
}
