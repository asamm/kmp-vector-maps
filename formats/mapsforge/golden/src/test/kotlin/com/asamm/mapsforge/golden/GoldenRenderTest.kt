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
 * and text dumps (labels, reader, theme callbacks) must be equal. The Paint counts differ on
 * purpose, as the vendored theme keeps scaled Paints for a window of zoom levels only (D29).
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
    fun vendoredTextDumpsMatchUpstream() {
        val expected = textDumps(TestDirs.expected)
        val actual = textDumps(TestDirs.actual)
        assertTrue(expected.isNotEmpty(), "no expected text dumps in ${TestDirs.expected}")
        assertEquals(expected.map { it.name }, actual.map { it.name }, "text dump sets differ")
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
            // guard against an empty dump passing trivially
            assertTrue(expLines.count { it.startsWith("  ") } > 0, "${exp.name} holds no entries")
        }
    }

    @Test
    fun vendoredPaintCachesStayBounded() {
        val expected = File(TestDirs.expected, PAINT_COUNTS).readLines()
        val actual = File(TestDirs.actual, PAINT_COUNTS).readLines()
        assertEquals(expected.size, actual.size, "$PAINT_COUNTS line count")
        assertEquals(expected.take(2), actual.take(2), "$PAINT_COUNTS theme parse")

        // the first sweep creates the same Paints, the second creates them again after eviction
        val firstSweep = expected.indexOf("sweep 2")
        assertEquals(
            expected.take(firstSweep).map { it.substringBefore(" live=") },
            actual.take(firstSweep).map { it.substringBefore(" live=") },
            "$PAINT_COUNTS first sweep",
        )

        // theme Paints, the window, the rendering job's zoom, and one zoom as slack for stored labels
        val parseLive = live(expected[1])
        val perZoom = expected.take(firstSweep).filter { it.startsWith("  z") }.maxOf { created(it) }
        val bound = parseLive + (ZOOM_WINDOW + 2) * perZoom
        val maxLive = actual.filter { it.startsWith("  ") }.maxOf { live(it) }
        assertTrue(maxLive <= bound, "$PAINT_COUNTS live $maxLive above $bound")
        assertTrue(live(expected.last()) > bound, "$PAINT_COUNTS upstream stays below $bound")
    }

    private fun textDumps(dir: File): List<File> {
        return dir.listFiles { f -> f.extension == "txt" && f.name != PAINT_COUNTS }
            .orEmpty()
            .sortedBy { it.name }
    }

    private fun created(line: String): Int {
        return line.substringAfter("created=").substringBefore(' ').toInt()
    }

    private fun live(line: String): Int {
        return line.substringAfter("live=").toInt()
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

    private companion object {

        private const val PAINT_COUNTS = "paint_counts.txt"

        // RenderTheme's window of zoom levels with scaled Paints no job holds
        private const val ZOOM_WINDOW = 4
    }
}
