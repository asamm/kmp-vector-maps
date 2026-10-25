/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ViewerStateTest {

    private val fixtures = File(System.getProperty("fixturesDir"))

    private val map = File(fixtures, "maps/monaco.osm.map")

    private val theme = File(fixtures, "themes/lomaps/theme.xml")

    private val formats = MapFormats(listOf(MapsforgeDriver(AwtGraphicFactory.INSTANCE)))

    private val temp = createTempDirectory().toFile()

    @AfterTest
    fun deleteTemp() {
        temp.deleteRecursively()
    }

    @Test
    fun rendersTheScreenAndCapturesIt() {
        ViewerState(formats = formats, session = Session(tileSize = 256), capturesDir = temp).use { state ->
            state.openMap(map)
            state.openStyle(theme)
            state.resize(width = 600, height = 400)

            val viewport = assertNotNull(state.viewport)
            val engine = assertNotNull(state.engine)
            val tiles = viewport.tiles(600, 400)
            waitFor { tiles.all { engine[it] != null } }
            assertNull(state.error)
            assertNull(engine.error)

            val png = assertNotNull(state.capture())
            assertEquals(600, ImageIO.read(png).width)
            val view = Properties()
            File(temp, png.nameWithoutExtension + ".properties").inputStream().use { view.load(it) }
            assertEquals(viewport.zoom.toString(), view.getProperty("zoom"))
            assertEquals(viewport.lat, view.getProperty("lat").toDouble(), 1e-6)
            assertNotNull(state.setupMs)
        }
    }

    @Test
    fun captureThatCannotWriteReportsAnError() {
        val blocked = File(temp, "captures").apply { writeText("a file, not a folder") }
        ViewerState(formats = formats, session = Session(tileSize = 256), capturesDir = blocked).use { state ->
            state.openMap(map)
            state.openStyle(theme)
            state.resize(width = 600, height = 400)

            assertNull(state.capture())
            assertNotNull(state.error)
            assertNull(state.notice)
        }
    }

    @Test
    fun sessionBringsBackTheView() {
        val first = ViewerState(formats = formats, session = Session(tileSize = 256), capturesDir = temp)
        val saved = first.use { state ->
            state.openMap(map)
            state.openStyle(theme)
            state.resize(width = 600, height = 400)
            state.zoom(steps = 2, x = 100f, y = 100f)
            state.selectLayer("car")
            state.session()
        }
        val file = File(temp, "viewer.properties")
        saved.write(file)
        val restored = Session.read(file)

        ViewerState(formats = formats, session = restored, capturesDir = temp).use { state ->
            state.openMap(map)
            state.openStyle(theme)
            state.resize(width = 600, height = 400)

            assertEquals("car", state.layerId)
            assertEquals(saved.place?.zoom, state.viewport?.zoom)
            assertEquals(assertNotNull(saved.place).lat, assertNotNull(state.viewport).lat, 1e-9)
        }
    }

    @Test
    fun styleEditedOnDiskAppliesOnTheNextSetting() {
        val styleDir = File(temp, "lomaps")
        theme.parentFile.copyRecursively(styleDir)
        val style = File(styleDir, theme.name)
        ViewerState(formats = formats, session = Session(tileSize = 256), capturesDir = temp).use { state ->
            state.openMap(map)
            state.openStyle(style)
            style.setLastModified(style.lastModified() + 10_000)

            state.selectLayer("car")

            assertNull(state.error)
            assertNotNull(state.engine)
            assertEquals("car", state.layerId)
        }
    }

    @Test
    fun styleBrokenOnDiskStopsTheOldRendering() {
        val styleDir = File(temp, "lomaps")
        theme.parentFile.copyRecursively(styleDir)
        val style = File(styleDir, theme.name)
        ViewerState(formats = formats, session = Session(tileSize = 256), capturesDir = temp).use { state ->
            state.openMap(map)
            state.openStyle(style)
            state.resize(width = 600, height = 400)
            style.writeText("<rendertheme")

            state.changeSettings(tileSize = 512)

            assertNotNull(state.error)
            assertNull(state.engine)
            assertEquals(512, state.viewport?.tileSize)
        }
    }

    @Test
    fun unreadableFileKeepsTheViewerUsable() {
        ViewerState(formats = formats, session = Session(), capturesDir = temp).use { state ->
            state.openMap(theme)

            assertNull(state.mapFile)
            assertNotNull(state.error)
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "waitFor(), timed out"
            }
            Thread.sleep(50)
        }
    }
}
