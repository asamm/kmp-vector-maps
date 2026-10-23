/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares the vendored render (`:formats:mapsforge:golden:renderVendored`) with the upstream 0.30.0
 * render (`:formats:mapsforge:golden-upstream:renderGoldens`), both made on this machine: pixels
 * and label dumps must be equal.
 */
class GoldenRenderTest {

    @Test
    fun vendoredRenderMatchesUpstream() {
        val expected = TestDirs.expected.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
        val actual = TestDirs.actual.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
        assertTrue(expected.isNotEmpty(), "no expected renders in ${TestDirs.expected}")
        assertEquals(expected.map { it.name }, actual.map { it.name }, "rendered tile sets differ")

        val mismatches = expected.mapNotNull { exp ->
            val diffPixels = compare(exp, File(TestDirs.actual, exp.name))
            if (diffPixels == 0) {
                null
            } else {
                "${exp.name}: $diffPixels px"
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of ${expected.size} tiles differ, diffs in ${TestDirs.diff}\n" + mismatches.joinToString("\n"))
        }
        // guard against a corpus of empty tiles passing trivially
        assertTrue(expected.count { !isUniform(ImageIO.read(it)) } >= expected.size / 2, "corpus is mostly blank")
    }

    @Test
    fun vendoredLabelDumpsMatchUpstream() {
        val expected = TestDirs.expected.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
        val actual = TestDirs.actual.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
        assertTrue(expected.isNotEmpty(), "no expected label dumps in ${TestDirs.expected}")
        assertEquals(expected.map { it.name }, actual.map { it.name }, "label dump sets differ")
        for (exp in expected) {
            val expLines = exp.readLines()
            val actLines = File(TestDirs.actual, exp.name).readLines()
            if (expLines != actLines) {
                val line = expLines.indices.firstOrNull { it >= actLines.size || expLines[it] != actLines[it] }
                    ?: expLines.size
                fail(
                    "${exp.name} differs at line ${line + 1}: " +
                        "expected '${expLines.getOrNull(line)}', actual '${actLines.getOrNull(line)}'",
                )
            }
            // guard against an empty store passing trivially
            assertTrue(expLines.count { it.startsWith("  ") } > 0, "${exp.name} holds no labels")
        }
    }

    /**
     * Returns the count of differing pixels and writes a diff image when there are any.
     */
    private fun compare(expectedFile: File, actualFile: File): Int {
        val exp = ImageIO.read(expectedFile)
        val act = ImageIO.read(actualFile)
        if (exp.width != act.width
            || exp.height != act.height
        ) {
            return exp.width * exp.height
        }
        val diff = BufferedImage(exp.width, exp.height, BufferedImage.TYPE_INT_ARGB)
        var count = 0
        for (y in 0 until exp.height) {
            for (x in 0 until exp.width) {
                if (exp.getRGB(x, y) != act.getRGB(x, y)) {
                    count++
                    diff.setRGB(x, y, 0xFFFF0000.toInt())
                } else {
                    diff.setRGB(x, y, (exp.getRGB(x, y) and 0x00FFFFFF) or 0x40000000)
                }
            }
        }
        if (count > 0) {
            TestDirs.diff.mkdirs()
            ImageIO.write(diff, "png", File(TestDirs.diff, expectedFile.name))
        }
        return count
    }

    private fun isUniform(image: BufferedImage): Boolean {
        val first = image.getRGB(0, 0)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != first) {
                    return false
                }
            }
        }
        return true
    }
}
