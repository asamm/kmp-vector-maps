/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.HillshadeHook
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mvt.render.AwtFonts
import com.asamm.vectormaps.mvt.render.AwtMvtGraphics
import com.asamm.vectormaps.mvt.source.MvtTileSource
import java.awt.Font
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MvtDriverTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    private val driver = MvtDriver(AwtMvtGraphics(AwtFonts(Font(Font.SANS_SERIF, Font.PLAIN, 12))))

    private val pmtiles = File(mvtDir, "tiles/fixtures.pmtiles")

    private val style by lazy {
        driver.loadStyle(File(mvtDir, "styles/hike_bike/style.json"))
    }

    @Test
    fun probesPmTilesThroughTheRegistry() {
        val probed = assertNotNull(MapFormats(listOf(driver)).probe(pmtiles))

        assertEquals("mvt", probed.driver.id)
        assertTrue(probed.info.bounds.minLon < 7.42 && probed.info.bounds.maxLat > 50.73, "${probed.info.bounds}")
    }

    @Test
    fun loadsTheStyleWithItsSprite() {
        assertTrue(style.id.endsWith("@${File(mvtDir, "styles/hike_bike/style.json").lastModified()}"))
        assertNotNull(style.sprite)
        assertEquals(455, style.style.layers.size)
    }

    @Test
    fun rendersDataOrReportsEmpty() {
        driver.open(listOf(pmtiles)).use { dataset ->
            dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 256)).use { renderer ->
                val target = MvtTileTarget(256)
                assertEquals(RenderResult.DRAWN, renderer.render(TileRequest(x = 8529, y = 5974, zoom = 14), target) { false })
                assertNotNull(target.canvas)
                // the tile beside it is not in the file
                assertEquals(RenderResult.EMPTY, renderer.render(TileRequest(x = 8530, y = 5974, zoom = 14), MvtTileTarget(256)) { false })
                assertEquals(RenderResult.CANCELLED, renderer.render(TileRequest(x = 8529, y = 5974, zoom = 14), MvtTileTarget(256)) { true })
                // above the file's deepest zoom, cut from it
                assertEquals(RenderResult.DRAWN, renderer.render(TileRequest(x = 34116, y = 23896, zoom = 16), MvtTileTarget(256)) { false })
                assertTrue(renderer.labelPass.storeCapacity > 0)
                assertEquals(emptySet(), renderer.failedLayers)
            }
            assertFalse(dataset.addMap(File(mvtDir, "tiles/planet.json")))
        }
    }

    @Test
    fun rendersAHostSourceAndCallsTheHillshadeHookOnce() {
        val reads = AtomicInteger()
        val source = object : MvtTileSource {
            override val minZoom = 0
            override val maxZoom = 14
            override val bounds: DoubleArray? = null

            override fun tile(zoom: Int, x: Int, y: Int): ByteArray? {
                reads.incrementAndGet()
                return File(mvtDir, "tiles/planet/$zoom/$x/$y.pbf").takeIf { it.isFile }?.readBytes()
            }
        }
        val hillshades = AtomicInteger()
        val hooks = RenderHooks(hillshade = HillshadeHook { _, _, _, _ -> hillshades.incrementAndGet() })
        driver.openSource(source).use { dataset ->
            dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 512), hooks).use { renderer ->
                // four children above the data zoom read their ancestor once
                for (dx in 0..1) {
                    for (dy in 0..1) {
                        val tile = TileRequest(x = 8529 * 2 + dx, y = 5974 * 2 + dy, zoom = 15)
                        assertEquals(RenderResult.DRAWN, renderer.render(tile, MvtTileTarget(512)) { false })
                    }
                }
            }
        }

        assertEquals(1, reads.get())
        assertEquals(4, hillshades.get())
    }

    @Test
    fun keepsSourcesOpenForRenderersAndAddsAFileOnce() {
        val dataset = driver.open(listOf(pmtiles))
        assertFalse(dataset.addMap(pmtiles))
        assertFailsWith<IllegalArgumentException> { driver.open(listOf(pmtiles, pmtiles)) }
        val renderer = dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 256))
        dataset.close()
        // a tile not decoded before the dataset closed
        assertEquals(RenderResult.DRAWN, renderer.render(TileRequest(x = 8529, y = 5974, zoom = 14), MvtTileTarget(256)) { false })
        renderer.close()
        assertFailsWith<IllegalStateException> {
            renderer.render(TileRequest(x = 8529, y = 5974, zoom = 14), MvtTileTarget(256)) { false }
        }
    }

    @Test
    fun keepsOddBoundsWithinTheWorld() {
        fun boundsOf(vararg b: Double) = MvtDriver.boundsOf(object : MvtTileSource {
            override val minZoom = 0
            override val maxZoom = 14
            override val bounds: DoubleArray = b

            override fun tile(zoom: Int, x: Int, y: Int): ByteArray? = null
        })

        // across the antimeridian: every longitude, its latitudes kept
        val across = boundsOf(170.0, -50.0, -170.0, -40.0)
        assertEquals(-180.0, across.minLon)
        assertEquals(-50.0, across.minLat)
        val beyond = boundsOf(-200.0, -95.0, 200.0, Double.NaN)
        assertEquals(180.0, beyond.maxLon)
        assertEquals(85.0511, beyond.maxLat)
    }

    @Test
    fun rejectsASpriteIndexAndToleratesABrokenSprite(@TempDir dir: File) {
        val hikeBike = File(mvtDir, "styles/hike_bike")
        assertFailsWith<IllegalArgumentException> { driver.loadStyle(File(hikeBike, "sprite.json")) }

        File(hikeBike, "style.json").copyTo(File(dir, "style.json"))
        File(dir, "sprite.json").writeText("[")
        File(dir, "sprite.png").writeText("not a png")
        val loaded = driver.loadStyle(File(dir, "style.json"))
        assertNull(loaded.sprite)
        assertEquals(455, loaded.style.layers.size)
    }
}
