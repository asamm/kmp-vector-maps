/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.asamm.mapsforge.golden.AndroidCorpus
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
 * Renders the golden corpus with the vendored `map-android` module and compares it pixel by pixel
 * with the upstream 0.30.0 render of `:formats:mapsforge:golden-android-upstream`, both made on
 * this machine under Robolectric's native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AndroidGoldenRenderTest {

    private val expectedDir = dirProperty("golden.expectedDir")
    private val actualDir = dirProperty("golden.actualDir")
    private val diffDir = dirProperty("golden.diffDir")

    @Test
    fun vendoredRenderMatchesUpstream() {
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
        val mismatches = expected.mapNotNull { exp ->
            val expBitmap = decode(exp)
            if (!isUniform(expBitmap)) {
                nonUniform++
            }
            val diffPixels = compare(expected = expBitmap, actual = decode(File(actualDir, exp.name)), name = exp.name)
            if (diffPixels == 0) {
                null
            } else {
                "${exp.name}: $diffPixels px"
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of ${expected.size} tiles differ, diffs in $diffDir\n" + mismatches.joinToString("\n"))
        }
        // guard against a corpus of empty tiles passing trivially
        assertTrue("corpus is mostly blank", nonUniform >= expected.size / 2)
    }

    /**
     * Returns the count of differing pixels and writes a diff image when there are any.
     */
    private fun compare(expected: Bitmap, actual: Bitmap, name: String): Int {
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
            if (exp[i] != act[i]) {
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
            "dirProperty($key), not set; run through Gradle: ./gradlew :formats:mapsforge:golden-android:test"
        }
        return File(path)
    }
}
