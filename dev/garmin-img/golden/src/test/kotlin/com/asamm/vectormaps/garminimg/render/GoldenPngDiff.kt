/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File
import java.io.InputStream

/**
 * Pixel-level diff helper for golden-image regression tests.
 *
 * Strict pixel-equality is too brittle: anti-aliasing, font hinting, and
 * Java AWT version drift produce minor 1-pixel differences that aren't
 * visually meaningful. Instead we measure the *fraction* of pixels whose
 * channel-distance exceeds a small threshold; the test fails only when the
 * fraction crosses a configurable tolerance.
 *
 * # Usage
 *
 * ```kotlin
 * val golden = GoldenPngDiff.loadFromClasspath("golden/otm-monaco-15-17059-11948.png")
 * val diff = GoldenPngDiff.compare(actual, golden, perChannelTolerance = 8)
 * assertTrue(diff.differingPixelFraction < 0.005, "render diverged: $diff")
 * ```
 */
object GoldenPngDiff {

    /**
     * Loads a PNG from the test classpath. Returns `null` if the resource
     * is missing — useful for "regenerate first golden image" workflows.
     */
    fun loadFromClasspath(path: String): BufferedImage? {
        val cl = GoldenPngDiff::class.java.classLoader
            ?: return null
        val stream: InputStream = cl.getResourceAsStream(path) ?: return null
        return stream.use { ImageIO.read(it) }
    }

    /**
     * Compares two images pixel-by-pixel.
     *
     * @param actual the freshly-rendered image
     * @param expected the golden reference
     * @param perChannelTolerance per-channel distance below which a pixel
     *   counts as "matching". 0 means strict; 8 ignores typical AA noise.
     */
    fun compare(
        actual: BufferedImage,
        expected: BufferedImage,
        perChannelTolerance: Int = 8,
    ): DiffResult {
        require(actual.width == expected.width && actual.height == expected.height) {
            "size mismatch: actual=${actual.width}x${actual.height}, " +
                "expected=${expected.width}x${expected.height}"
        }
        val w = actual.width
        val h = actual.height
        val total = w.toLong() * h
        var differing = 0L
        var maxChannelDelta = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = actual.getRGB(x, y)
                val e = expected.getRGB(x, y)
                if (a == e) {
                    continue
                }
                val dr = absChannel(a, e, 16)
                val dg = absChannel(a, e, 8)
                val db = absChannel(a, e, 0)
                val da = absChannel(a, e, 24)
                val maxD = maxOf(dr, dg, db, da)
                if (maxD > maxChannelDelta) {
                    maxChannelDelta = maxD
                }
                if (maxD > perChannelTolerance) {
                    differing++
                }
            }
        }
        return DiffResult(
            totalPixels = total,
            differingPixels = differing,
            maxChannelDelta = maxChannelDelta,
        )
    }

    private fun absChannel(a: Int, e: Int, shift: Int): Int {
        val ca = (a ushr shift) and 0xFF
        val ce = (e ushr shift) and 0xFF
        return kotlin.math.abs(ca - ce)
    }

    data class DiffResult(
        val totalPixels: Long,
        val differingPixels: Long,
        val maxChannelDelta: Int,
    ) {

        val differingPixelFraction: Double
            get() = if (totalPixels == 0L) 0.0 else differingPixels.toDouble() / totalPixels

        override fun toString(): String {
            val pct = differingPixelFraction * 100.0
            return "DiffResult(differingPixels=$differingPixels/$totalPixels (${"%.3f".format(pct)}%), " +
                "maxChannelDelta=$maxChannelDelta)"
        }
    }

    /**
     * Helper used during golden-image authoring: writes `actual` to a
     * candidate PNG path and prints instructions for promoting it to the
     * golden directory.
     */
    fun saveCandidate(actual: BufferedImage, name: String): File {
        val dir = File("build/test-output/render-candidates").apply { mkdirs() }
        val file = File(dir, "$name.png")
        ImageIO.write(actual, "PNG", file)
        return file
    }
}
