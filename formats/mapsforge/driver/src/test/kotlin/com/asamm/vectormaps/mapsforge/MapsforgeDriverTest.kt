/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapsforgeDriverTest {

    private val fixtures = File(System.getProperty("fixturesDir"))
    private val monaco = File(fixtures, "maps/monaco.osm.map")
    private val theme = File(fixtures, "themes/lomaps/theme.xml")
    private val driver = MapsforgeDriver(AwtGraphicFactory.INSTANCE)
    private val config = RenderConfig(tileSizePx = 256)

    // Monaco harbour at z14
    private val harbour = TileRequest(x = 8529, y = 5974, zoom = 14)

    @Test
    fun probeReadsBoundsFromHeader() {
        val info = assertNotNull(driver.probe(monaco))

        assertTrue(info.bounds.minLat < 43.735 && info.bounds.maxLat > 43.735)
        assertTrue(info.bounds.minLon < 7.423 && info.bounds.maxLon > 7.423)
    }

    @Test
    fun probeFailsOnOtherFiles() {
        val file = createTempFile(suffix = ".map").toFile()
        try {
            file.writeText("not a map")
            assertFails {
                driver.probe(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun loadStyleReadsMenuWithMapsforgeIds() {
        val menu = assertNotNull(driver.loadStyle(theme).menu)

        assertEquals("r4menu", menu.id)
        assertEquals("hike_bike", menu.defaultLayerId)
        assertEquals("en", menu.defaultLanguage)
        val hikeBike = assertNotNull(menu.layer("hike_bike"))
        assertTrue(hikeBike.isVisible)
        assertTrue(hikeBike.overlayIds.isNotEmpty())
        assertFalse(assertNotNull(menu.layer("hiking_trails")).isVisible)
    }

    @Test
    fun renderDrawsIntoTarget() {
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                val target = TileBitmapTarget(256)

                assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                val bitmap = assertNotNull(target.bitmap)
                assertEquals(256, bitmap.width)
                bitmap.decrementRefCount()
                assertNull(renderer.labelStore)
            }
        }
    }

    @Test
    fun textWrapsLaterWithTextScale() {
        val scaled = config.copy(textScale = 1.5f)
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, scaled).use { renderer ->
                assertEquals((256 * 0.7f * 1.5f).toInt(), renderer.displayModel.maxTextWidth)
            }
        }
    }

    @Test
    fun renderStopsWhenCancelled() {
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                val target = TileBitmapTarget(256)

                assertEquals(RenderResult.CANCELLED, renderer.render(harbour, target) { true })
                assertNull(target.bitmap)
            }
        }
    }

    @Test
    fun multiMapRendersFromEveryFile() {
        val copy = createTempFile(suffix = ".map").toFile()
        try {
            monaco.copyTo(copy, overwrite = true)
            driver.open(listOf(monaco, copy)).use { dataset ->
                dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                    val target = TileBitmapTarget(256)

                    assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                    target.bitmap?.decrementRefCount()
                }
            }
        } finally {
            copy.delete()
        }
    }

    @Test
    fun separateLabelsGoToTheLabelStore() {
        val separate = config.copy(labelMode = LabelMode.SEPARATE)
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, separate).use { renderer ->
                val target = TileBitmapTarget(256)
                renderer.render(harbour, target) { false }
                target.bitmap?.decrementRefCount()

                val labelStore = assertNotNull(renderer.labelStore)
                assertTrue(labelStore.getVisibleItems(harbour.toMapsforge(), harbour.toMapsforge()).isNotEmpty())
            }
        }
    }

    @Test
    fun renderRejectsForeignOrMisfitTargets() {
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                val foreign = object : RenderTarget {
                    override val widthPx = 256
                    override val heightPx = 256
                }
                assertFailsWith<IllegalArgumentException> {
                    renderer.render(harbour, foreign) { false }
                }
                assertFailsWith<IllegalArgumentException> {
                    renderer.render(harbour, TileBitmapTarget(512)) { false }
                }
            }
        }
    }

    @Test
    fun unknownLayerRendersEveryCategory() {
        val style = driver.loadStyle(theme)
        val menu = XmlRenderThemeStyleMenu("r4menu", "en", "hike_bike")
        menu.createLayer("hike_bike", true, true).addCategory("hike")

        assertNull(style.menuCallback(StyleSelection(layerId = "boat")).getCategories(menu))
        assertEquals(setOf("hike"), style.menuCallback(StyleSelection.DEFAULT).getCategories(menu))
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection(layerId = "boat"), config).use { renderer ->
                val target = TileBitmapTarget(256)

                assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                target.bitmap?.decrementRefCount()
            }
        }
    }

    @Test
    fun themeWithoutMenuLoadsAndRenders() {
        val style = driver.loadStyle(File(fixtures, "themes/lomaps/theme_car.poi.xml"))

        assertNull(style.menu)
        driver.open(listOf(monaco)).use { dataset ->
            dataset.createRenderer(style, StyleSelection.DEFAULT, config).use { renderer ->
                val target = TileBitmapTarget(256)

                assertEquals(RenderResult.DRAWN, renderer.render(harbour, target) { false })
                target.bitmap?.decrementRefCount()
            }
        }
    }

    @Test
    fun createRendererRefusesChangedTheme() {
        val copy = createTempFile(suffix = ".xml").toFile()
        try {
            theme.copyTo(copy, overwrite = true)
            val style = driver.loadStyle(copy)
            copy.setLastModified(copy.lastModified() + 2_000)
            driver.open(listOf(monaco)).use { dataset ->
                assertFailsWith<IllegalStateException> {
                    dataset.createRenderer(style, StyleSelection.DEFAULT, config)
                }
            }
        } finally {
            copy.delete()
        }
    }

    @Test
    fun renderFailsAfterClose() {
        driver.open(listOf(monaco)).use { dataset ->
            val renderer = dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config)
            renderer.close()
            renderer.close()

            assertFailsWith<IllegalStateException> {
                renderer.render(harbour, TileBitmapTarget(256)) { false }
            }
        }
    }

    @Test
    fun failedReadThrowsInsteadOfBlankData() {
        val store = CheckedMapFile(file = monaco, language = null)
        store.close()

        assertFailsWith<IOException> {
            store.readMapData(harbour.toMapsforge())
        }
    }

    @Test
    fun createRendererIgnoresGlobalDeviceScale() {
        val original = DisplayModel.getDeviceScaleFactor()
        val originalLineScale = DisplayModel.lineScale
        val originalSymbolScale = DisplayModel.symbolScale
        val originalTextScale = DisplayModel.textScale
        DisplayModel.setDeviceScaleFactor(2.0f)
        DisplayModel.lineScale = 2.0f
        DisplayModel.symbolScale = 2.0f
        DisplayModel.textScale = 2.0f
        try {
            driver.open(listOf(monaco)).use { dataset ->
                dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                    assertEquals(config.deviceScale, renderer.displayModel.scaleFactor)
                    assertEquals(1.0f, renderer.displayModel.lineScaleFactor)
                    assertEquals(1.0f, renderer.displayModel.symbolScaleFactor)
                    assertEquals(1.0f, renderer.displayModel.textScaleFactor)
                }
            }
        } finally {
            DisplayModel.setDeviceScaleFactor(original)
            DisplayModel.lineScale = originalLineScale
            DisplayModel.symbolScale = originalSymbolScale
            DisplayModel.textScale = originalTextScale
        }
    }

    @Test
    fun createRendererIgnoresGlobalParameters() {
        val originalZoomMin = Parameters.POLYGON_ZOOM_MIN
        val originalThreads = Parameters.NUMBER_OF_THREADS
        val originalAntiAliasing = Parameters.ANTI_ALIASING
        Parameters.POLYGON_ZOOM_MIN = 20
        Parameters.ANTI_ALIASING = false
        // a value the renderer's parallelism cannot have
        Parameters.NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors() + 7
        try {
            driver.open(listOf(monaco)).use { dataset ->
                dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                    assertEquals(-1, renderer.displayModel.polygonZoomMin)
                    assertTrue(renderer.displayModel.antiAliasing)
                    assertEquals(renderer.maxParallelism, renderer.displayModel.numberOfThreads)
                }
            }
        } finally {
            Parameters.POLYGON_ZOOM_MIN = originalZoomMin
            Parameters.NUMBER_OF_THREADS = originalThreads
            Parameters.ANTI_ALIASING = originalAntiAliasing
        }
    }

    @Test
    fun addMapRejectsDuplicatesAndOtherFiles() {
        val other = createTempFile(suffix = ".map").toFile()
        try {
            other.writeText("not a map")
            driver.open(listOf(monaco)).use { dataset ->
                assertFalse(dataset.addMap(monaco))
                assertFalse(dataset.addMap(other))
                assertEquals(driver.probe(monaco)?.bounds, dataset.bounds)
            }
            assertFailsWith<IllegalArgumentException> {
                driver.open(listOf(other))
            }
        } finally {
            other.delete()
        }
    }

    private fun TileRequest.toMapsforge(): org.mapsforge.core.model.Tile {
        return org.mapsforge.core.model.Tile(x, y, zoom.toByte(), 256)
    }
}
