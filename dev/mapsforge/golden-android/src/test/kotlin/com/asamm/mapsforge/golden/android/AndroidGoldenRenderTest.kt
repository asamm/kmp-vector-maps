/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.asamm.mapsforge.golden.AndroidCorpus
import com.asamm.mapsforge.golden.DrawingCheck
import com.asamm.mapsforge.golden.LabelGoldens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the golden corpus with the vendored `:formats:mapsforge:android` module under Robolectric's
 * native graphics (D37): tiles drawn without labels close to the upstream 0.30.0 render of
 * `:dev:mapsforge:golden-android-upstream`, made on this machine ([DrawingCheck]); tiles with labels
 * against the committed label goldens ([LabelGoldens]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AndroidGoldenRenderTest {

    private val expectedDir = dirProperty("golden.expectedDir")
    private val actualDir = dirProperty("golden.actualDir")
    private val diffDir = dirProperty("golden.diffDir")
    private val acceptedFile = dirProperty("golden.acceptedFile")
    private val upstreamDoc = dirProperty("golden.upstreamDoc")
    private val labelsDir = dirProperty("golden.labelsDir")

    @Test
    fun vendoredRenderMatchesUpstream() {
        // parsed first, so a malformed entry fails before the corpus renders
        val drawingCheck = DrawingCheck(acceptedFile = acceptedFile, upstreamDoc = upstreamDoc, backend = "android")
        AndroidCorpus.renderAll(
            application = RuntimeEnvironment.getApplication(),
            fixturesDir = dirProperty("golden.fixturesDir"),
            outDir = actualDir,
        )

        val expected = pngs(expectedDir)
        val actual = pngs(actualDir)
        assertTrue("no expected renders in $expectedDir", expected.isNotEmpty())
        assertEquals("rendered tile sets differ", expected.map { it.name }, actual.map { it.name })

        var nonUniform = 0
        val drawingMismatches = expected.mapNotNull { exp ->
            val expBitmap = decode(exp)
            if (!isUniform(expBitmap)) {
                nonUniform++
            }
            if (!DrawingCheck.isDrawingTile(exp.name)) {
                return@mapNotNull null
            }
            drawingCheck.verdict(
                tile = exp.name,
                differingPixels = compare(expBitmap, decode(File(actualDir, exp.name)), exp.name, channelTolerance = 0),
                totalPixels = expBitmap.width * expBitmap.height,
            )
        } + drawingCheck.staleEntries()
        val mismatches = drawingMismatches + labelMismatches(actual)
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} problems in ${expected.size} tiles, diffs in $diffDir\n" + mismatches.joinToString("\n"))
        }
        // guard against a corpus of empty tiles passing trivially
        assertTrue("corpus is mostly blank", nonUniform >= expected.size / 2)
    }

    /**
     * Compares the tiles with labels with the committed label goldens, unless `updateLabelGoldens`
     * is replacing them.
     */
    private fun labelMismatches(actual: List<File>): List<String> {
        if (System.getProperty("golden.updatingLabels").toBoolean()) {
            return emptyList()
        }
        val goldens = pngs(labelsDir)
        assertEquals(
            "label golden tiles differ, $UPDATE_TASK",
            actual.map { it.name }.filter { LabelGoldens.isLabelGolden(it) },
            goldens.map { it.name },
        )
        return goldens.mapNotNull { golden ->
            val goldenBitmap = decode(golden)
            LabelGoldens.verdict(
                tile = golden.name,
                differingPixels = compare(
                    goldenBitmap,
                    decode(File(actualDir, golden.name)),
                    golden.name,
                    LabelGoldens.CHANNEL_TOLERANCE,
                ),
                totalPixels = goldenBitmap.width * goldenBitmap.height,
                updateTask = UPDATE_TASK,
            )
        }
    }

    /**
     * Returns the count of pixels with a channel differing by more than `channelTolerance` and writes
     * a diff image when there are any.
     */
    private fun compare(expected: Bitmap, actual: Bitmap, name: String, channelTolerance: Int): Int {
        if (expected.width != actual.width
            || expected.height != actual.height
        ) {
            return expected.width * expected.height
        }
        val exp = pixels(expected)
        val act = pixels(actual)
        val diff = IntArray(exp.size)
        var count = 0
        for (i in exp.indices) {
            if (LabelGoldens.channelDistance(exp[i], act[i]) > channelTolerance) {
                count++
                diff[i] = 0xFFFF0000.toInt()
            } else {
                diff[i] = (exp[i] and 0x00FFFFFF) or 0x40000000
            }
        }
        if (count > 0) {
            diffDir.mkdirs()
            val image = Bitmap.createBitmap(diff, expected.width, expected.height, Bitmap.Config.ARGB_8888)
            File(diffDir, name).outputStream().use { out ->
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return count
    }

    private fun isUniform(bitmap: Bitmap): Boolean {
        val pixels = pixels(bitmap)
        return pixels.all { it == pixels[0] }
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun decode(file: File): Bitmap {
        return checkNotNull(BitmapFactory.decodeFile(file.absolutePath)) {
            "decode($file), not a readable PNG"
        }
    }

    private fun pngs(dir: File): List<File> {
        return dir.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
    }

    private fun dirProperty(key: String): File {
        val path = requireNotNull(System.getProperty(key)) {
            "dirProperty($key), not set; run through Gradle: ./gradlew :dev:mapsforge:golden-android:test"
        }
        return File(path)
    }

    private companion object {

        private const val UPDATE_TASK = "./gradlew :dev:mapsforge:golden-android:updateLabelGoldens"
    }
}
