/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleMenu
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import java.awt.image.BufferedImage
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminDriverTest {

    private val fixtures = File(System.getProperty("fixturesDir"))
    private val otm = File(fixtures, "maps/otm-monaco/otm-monaco.img")
    private val mkgmap = File(fixtures, "maps/mkgmap-monaco/monaco.img")
    private val driver = GarminDriver { size, antiAliased, scale ->
        AwtRenderTarget(widthPx = size, heightPx = size, antiAliased = antiAliased, renderScale = scale)
    }
    private val config = RenderConfig(tileSizePx = 256)

    // Monaco harbour at z14
    private val harbour = TileRequest(x = 8529, y = 5974, zoom = 14)

    @Test
    fun probeReadsBoundsFromTre() {
        val bounds = driver.probe(otm).bounds

        assertTrue(bounds.minLat < 43.735 && bounds.maxLat > 43.735)
        assertTrue(bounds.minLon < 7.423 && bounds.maxLon > 7.423)
    }

    @Test
    fun probeFailsOnOtherFiles() {
        val file = createTempFile(suffix = ".img").toFile()
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
    fun datasetListsEmbeddedTypFirst() {
        driver.open(listOf(otm)).use { dataset ->
            assertEquals(2, dataset.styles.size)
            assertIs<GarminStyle.Embedded>(dataset.styles[0])
            assertEquals(GarminStyle.BuiltIn, dataset.styles[1])
        }
        driver.open(listOf(mkgmap)).use { dataset ->
            assertEquals(listOf<GarminStyle>(GarminStyle.BuiltIn), dataset.styles)
        }
    }

    @Test
    fun datasetHoldsOneFile() {
        assertFailsWith<IllegalArgumentException> {
            driver.open(listOf(otm, mkgmap))
        }
        driver.open(listOf(otm)).use { dataset ->
            assertFalse(dataset.addMap(mkgmap))
        }
    }

    @Test
    fun rendersHarbourWithEachStyle() {
        driver.open(listOf(otm)).use { dataset ->
            for (style in dataset.styles) {
                val image = render(dataset, style, harbour)
                assertTrue(colourCount(image) > 10, "${style.id} drew too few colours")
            }
        }
    }

    @Test
    fun tileOutsideMapIsEmpty() {
        driver.open(listOf(otm)).use { dataset ->
            dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config).use { renderer ->
                val target = GarminTileTarget(256)
                // Prague
                val result = renderer.render(TileRequest(x = 8846, y = 5555, zoom = 14), target) { false }

                assertEquals(RenderResult.EMPTY, result)
                assertNull(target.canvas)
            }
        }
    }

    @Test
    fun cancelledBeforeDrawing() {
        driver.open(listOf(otm)).use { dataset ->
            dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config).use { renderer ->
                val target = GarminTileTarget(256)

                assertEquals(RenderResult.CANCELLED, renderer.render(harbour, target) { true })
                assertNull(target.canvas)
            }
        }
    }

    @Test
    fun optionsReachTheEngine() {
        driver.open(listOf(otm)).use { dataset ->
            val nothing = GarminOptions(objectKinds = 0)
            val image = render(dataset, dataset.styles[0], harbour, options = nothing)

            assertEquals(1, colourCount(image), "no object kinds leave the background only")
        }
    }

    @Test
    fun rejectsUnsupportedSetups() {
        driver.open(listOf(otm)).use { dataset ->
            val foreign = object : MapStyle {
                override val id: String = "foreign"
                override val menu: StyleMenu? = null
            }
            assertFailsWith<IllegalArgumentException> {
                dataset.createRenderer(foreign, StyleSelection.DEFAULT, config)
            }
            assertFailsWith<IllegalArgumentException> {
                dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config.copy(labelMode = LabelMode.SEPARATE))
            }
            val otherMapTyp = driver.open(listOf(otm)).styles[0]
            driver.open(listOf(mkgmap)).use { other ->
                assertFailsWith<IllegalArgumentException> {
                    other.createRenderer(otherMapTyp, StyleSelection.DEFAULT, config)
                }
            }
        }
    }

    @Test
    fun loadsExternalTyp() {
        val typ = createTempFile(suffix = ".typ").toFile()
        try {
            typ.writeBytes(extractTyp(otm))
            val style = driver.loadStyle(typ)
            driver.open(listOf(mkgmap)).use { dataset ->
                val image = render(dataset, style, harbour)

                assertTrue(colourCount(image) > 10)
            }
        } finally {
            typ.delete()
        }
    }

    /**
     * A z17 tile at 512 px against its four z18 tiles at 256 px: same resolution, so the stitched
     * quarters match the whole when the tile bounds are exact; Garmin-unit bounds shift them by
     * pixels at this zoom.
     */
    @Test
    fun adjacentTilesMeetWithoutSeams() {
        // geometry only, no labels or icons, which stop at tile edges
        val options = GarminOptions(antiAliasing = false, objectKinds = ObjectKind.POLYGON or ObjectKind.POLYLINE)
        driver.open(listOf(otm)).use { dataset ->
            val style = dataset.styles[0]
            val whole = render(dataset, style, TileRequest(x = 68232, y = 47792, zoom = 17), options, sizePx = 512)
            val quarters = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
            for (dy in 0..1) {
                for (dx in 0..1) {
                    val tile = TileRequest(x = 68232 * 2 + dx, y = 47792 * 2 + dy, zoom = 18)
                    val quarter = render(dataset, style, tile, options)
                    quarters.graphics.drawImage(quarter, dx * 256, dy * 256, null)
                }
            }

            var differing = 0
            for (y in 0 until 512) {
                for (x in 0 until 512) {
                    if (whole.getRGB(x, y) != quarters.getRGB(x, y)) {
                        differing++
                    }
                }
            }
            assertTrue(differing < 512 * 512 / 100, "$differing of ${512 * 512} pixels differ")
        }
    }

    private fun render(
        dataset: GarminDataset,
        style: MapStyle,
        tile: TileRequest,
        options: GarminOptions = GarminOptions(),
        sizePx: Int = 256,
    ): BufferedImage {
        val config = RenderConfig(tileSizePx = sizePx)
        dataset.createRenderer(style, StyleSelection.DEFAULT, config, options).use { renderer ->
            val target = GarminTileTarget(sizePx)
            assertEquals(RenderResult.DRAWN, renderer.render(tile, target) { false })
            return (target.canvas as AwtRenderTarget).image
        }
    }

    private fun colourCount(image: BufferedImage): Int {
        val colours = HashSet<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                colours.add(image.getRGB(x, y))
            }
        }
        return colours.size
    }

    private fun extractTyp(img: File): ByteArray {
        FileImgRandomAccessReader(img).use { reader ->
            val archive = ImgArchive.open(reader)
            val stream = archive.openSubFile(checkNotNull(archive.findFirst("TYP")), reader)
            val bytes = ByteArray(stream.size.toInt())
            stream.seek(0L)
            stream.readBlock(bytes)
            return bytes
        }
    }
}
