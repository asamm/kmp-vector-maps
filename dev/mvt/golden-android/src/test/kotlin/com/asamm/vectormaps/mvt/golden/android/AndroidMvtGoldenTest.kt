/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AndroidMvtGraphics
import com.asamm.vectormaps.mvt.render.AndroidMvtRenderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Renders every MapLibre reference tile of `dev/fixtures/mvt` through the MVT driver on Android
 * graphics and checks it against MapLibre GL JS's render without symbols and elevation, within the
 * JVM golden's limits; the label pass, drawn over each tile into one reused target, must draw the
 * labels it places. Tiles and labelled screens go to `build/renders` for inspection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AndroidMvtGoldenTest {

    private val fixturesDir = File(requireNotNull(System.getProperty("golden.fixturesDir")))

    private val rendersDir = File(requireNotNull(System.getProperty("golden.rendersDir")))

    private val mvtDir = File(fixturesDir, "mvt")

    @Test
    fun rendersTheReferenceTilesAsMapLibre() {
        val driver = MvtDriver(AndroidMvtGraphics())
        val style = driver.loadStyle(File(mvtDir, "styles/hike_bike/style.json"))
        val references = File(mvtDir, "reference/maplibre/base").walk()
            .filter { it.extension == "png" }
            .sortedBy { it.path }
            .toList()
        assertEquals("MapLibre references", TILES, references.size)
        rendersDir.mkdirs()
        val failures = ArrayList<String>()
        driver.open(listOf(File(mvtDir, "tiles/fixtures.pmtiles"))).use { dataset ->
            val config = RenderConfig(tileSizePx = SIZE)
            dataset.createRenderer(style, StyleSelection.DEFAULT, config).use { renderer ->
                val pass = renderer.labelPass
                val labels = pass.newTarget(SIZE, SIZE) as AndroidMvtRenderTarget
                for (file in references) {
                    val z = file.parentFile.parentFile.name.toInt()
                    val x = file.parentFile.name.toInt()
                    val y = file.nameWithoutExtension.toInt()
                    val name = "${z}_${x}_$y"
                    val target = MvtTileTarget(SIZE)
                    val result = renderer.render(TileRequest(x = x, y = y, zoom = z), target) { false }
                    assertEquals(name, RenderResult.DRAWN, result)
                    val tile = (target.canvas as AndroidMvtRenderTarget).bitmap
                    write(tile, name)
                    val (mean, off) = difference(tile, decode(file))
                    println("android $name vs MapLibre mean %.1f, %.1f %% off".format(mean, off))
                    if (mean > MAX_MEAN
                        || off > MAX_OFF_PERCENT
                    ) {
                        failures.add("$name: mean %.1f, %.1f %% off".format(mean, off))
                    }

                    val view = LabelViewport(
                        zoom = z,
                        centerX = (x + 0.5) * SIZE,
                        centerY = (y + 0.5) * SIZE,
                        widthPx = SIZE,
                        heightPx = SIZE,
                        rotationDeg = 0.0,
                    )
                    val prepared = pass.prepareNow(view)
                    labels.clear()
                    assertEquals("$name: cleared label target", 0, drawnPixels(labels.bitmap))
                    pass.draw(labels, view, prepared)
                    val screen = tile.copy(Bitmap.Config.ARGB_8888, true)
                    Canvas(screen).drawBitmap(labels.bitmap, 0f, 0f, null)
                    write(screen, "labels_$name")
                    if (prepared.placed.isNotEmpty()) {
                        assertTrue("$name: labels placed but none drawn", drawnPixels(labels.bitmap) > 0)
                    }
                }
                assertEquals("layers that threw", emptySet<String>(), renderer.failedLayers)
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    private fun decode(file: File): Bitmap {
        return requireNotNull(BitmapFactory.decodeFile(file.path)) {
            "decode($file), not an image"
        }
    }

    private fun write(bitmap: Bitmap, name: String) {
        File(rendersDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun drawnPixels(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it ushr 24 != 0 }
    }

    // mean absolute RGB difference (0-255) and the share of pixels off by more than TOLERANCE in a
    // channel, as the JVM golden's MvtFixtures.difference
    private fun difference(a: Bitmap, b: Bitmap): Pair<Double, Double> {
        val pa = IntArray(SIZE * SIZE)
        val pb = IntArray(SIZE * SIZE)
        a.getPixels(pa, 0, SIZE, 0, 0, SIZE, SIZE)
        b.getPixels(pb, 0, SIZE, 0, 0, SIZE, SIZE)
        var sum = 0L
        var off = 0
        for (i in pa.indices) {
            var worst = 0
            for (shift in intArrayOf(16, 8, 0)) {
                val d = abs((pa[i] ushr shift and 0xff) - (pb[i] ushr shift and 0xff))
                sum += d
                worst = maxOf(worst, d)
            }
            if (worst > TOLERANCE) {
                off++
            }
        }
        return sum / (pa.size * 3.0) to off * 100.0 / pa.size
    }

    private companion object {

        const val SIZE = 512

        // the MapLibre references of dev/fixtures/mvt, as the JVM golden
        const val TILES = 8

        // the JVM golden's limits (GoldenRenderTest)
        const val MAX_MEAN = 4.0
        const val MAX_OFF_PERCENT = 6.0

        const val TOLERANCE = 32
    }
}
