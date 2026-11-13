/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.HillshadeHook
import com.asamm.vectormaps.core.HillshadeLayer
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
    fun rendersAHostSourceAndCallsTheHillshadeHookPerTile() {
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
        val hooks = RenderHooks(hillshade = HillshadeHook { _, _, _, _, _ -> hillshades.incrementAndGet() })
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
    fun readsADataTileOnceForConcurrentChildren() {
        val reads = AtomicInteger()
        val bytes = File(mvtDir, "tiles/planet/14/8529/5974.pbf").readBytes()
        val source = object : MvtTileSource {
            override val minZoom = 0
            override val maxZoom = 14
            override val bounds: DoubleArray? = null

            override fun tile(zoom: Int, x: Int, y: Int): ByteArray {
                reads.incrementAndGet()
                // slow, as a download, so the children's misses overlap
                Thread.sleep(200)
                return bytes
            }
        }
        val executor = Executors.newFixedThreadPool(4)
        try {
            driver.openSource(source).use { dataset ->
                dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 256)).use { renderer ->
                    val tasks = (0..3).map { i ->
                        Callable {
                            val tile = TileRequest(x = 8529 * 2 + i % 2, y = 5974 * 2 + i / 2, zoom = 15)
                            renderer.render(tile, MvtTileTarget(256)) { false }
                        }
                    }
                    assertEquals(List(4) { RenderResult.DRAWN }, executor.invokeAll(tasks).map { it.get() })
                }
            }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, reads.get())
    }

    @Test
    fun callsTheHillshadeHookAtEachElevationLayer() {
        val bytes = File(mvtDir, "tiles/planet/10/533/373.pbf").readBytes()
        // any tile's data, as only the elevation layers matter here
        val source = object : MvtTileSource {
            override val minZoom = 0
            override val maxZoom = 14
            override val bounds: DoubleArray? = null

            override fun tile(zoom: Int, x: Int, y: Int): ByteArray = bytes
        }
        val layers = ArrayList<HillshadeLayer>()
        val hooks = RenderHooks(hillshade = HillshadeHook { _, _, _, _, layer -> layers.add(requireNotNull(layer)) })
        driver.openSource(source).use { dataset ->
            dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 256), hooks).use { renderer ->
                renderer.render(TileRequest(x = 16, y = 11, zoom = 5), MvtTileTarget(256)) { false }
            }
        }

        // bathymetry under the land, terrain over it, in style order
        assertEquals(listOf("bathymetry_colored", "bathymetry", "hillshade"), layers.map { it.id })
        val relief = layers[0]
        assertEquals(HillshadeLayer.Type.COLOR_RELIEF, relief.type)
        assertEquals("bathymetry-rgb", relief.source)
        assertEquals("terrarium", relief.encoding)
        assertEquals(-11000.0 to 0xff0d3d5f.toInt(), (relief.paint["color-relief-color"] as List<*>).first())
        val terrain = layers[2]
        assertEquals(HillshadeLayer.Type.HILLSHADE, terrain.type)
        assertEquals("terrain-rgb", terrain.source)
        assertEquals("https://tiles.locusmap.app/data/terrain-rgb.json", terrain.sourceUrl)
        assertEquals(0xff3c3228.toInt(), terrain.paint["hillshade-shadow-color"])
        // 0.5 at z2 to 0.3 at z5
        assertEquals(0.3, terrain.paint["hillshade-exaggeration"] as Double, 1e-9)
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
