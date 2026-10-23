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
 * Renders the corpus with the vendored modules and requires pixel equality, and equal label dumps,
 * with the upstream 0.30.0 render produced by `:formats:mapsforge:golden-upstream:renderGoldens` on
 * the same machine.
 */
class GoldenRenderTest {

    @Test
    fun vendoredRenderMatchesUpstream() {
        val actualDir = rendered
        val expected = expectedDir.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
        val actual = actualDir.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
        assertTrue(expected.isNotEmpty(), "no expected renders in $expectedDir")
        assertEquals(expected.map { it.name }, actual.map { it.name }, "rendered tile sets differ")

        val mismatches = expected.mapNotNull { exp ->
            val diffPixels = compare(exp, File(actualDir, exp.name))
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
        assertTrue(expected.count { !isUniform(ImageIO.read(it)) } >= expected.size / 2, "corpus is mostly blank")
    }

    @Test
    fun vendoredLabelDumpsMatchUpstream() {
        val actualDir = rendered
        val expected = expectedDir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
        val actual = actualDir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
        assertTrue(expected.isNotEmpty(), "no expected label dumps in $expectedDir")
        assertEquals(expected.map { it.name }, actual.map { it.name }, "label dump sets differ")
        for (exp in expected) {
            val expLines = exp.readLines()
            val actLines = File(actualDir, exp.name).readLines()
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
            diffDir.mkdirs()
            ImageIO.write(diff, "png", File(diffDir, expectedFile.name))
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

    private companion object {

        val fixturesDir = dirProperty("golden.fixturesDir")
        val expectedDir = dirProperty("golden.expectedDir")
        val diffDir = dirProperty("golden.diffDir")

        /**
         * The vendored render, done once per test JVM so every test reads the same fresh output
         * whatever order JUnit runs them in.
         */
        val rendered: File by lazy {
            val actualDir = dirProperty("golden.actualDir")
            actualDir.deleteRecursively()
            diffDir.deleteRecursively()
            GoldenCorpus.renderAll(fixturesDir = fixturesDir, outDir = actualDir)
            actualDir
        }

        fun dirProperty(key: String): File {
            val path = requireNotNull(System.getProperty(key)) {
                "dirProperty($key), not set; run through Gradle: ./gradlew :formats:mapsforge:golden:test"
            }
            return File(path)
        }
    }
}
