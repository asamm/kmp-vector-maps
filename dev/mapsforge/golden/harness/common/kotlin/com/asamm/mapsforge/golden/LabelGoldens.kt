/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The committed label goldens (ROADMAP D37): tiles with labels and the label dumps, rendered by the
 * vendored side in the bundled font and compared with its own output instead of upstream's. A dump
 * must be equal; a tile passes when at most [MAX_DIFFERING_SHARE] of its pixels differ by more than
 * [CHANNEL_TOLERANCE] in a channel, which absorbs text rasterised by another system's JDK.
 */
object LabelGoldens {

    // as the Garmin golden
    const val CHANNEL_TOLERANCE = 8

    const val MAX_DIFFERING_SHARE = 0.002

    const val LABEL_DUMP_SUFFIX = "_labels.txt"

    /**
     * True for the files the label goldens hold: tiles with labels and the label dumps.
     */
    fun isLabelGolden(fileName: String): Boolean {
        return (fileName.endsWith(".png") && !DrawingCheck.isDrawingTile(fileName))
            || fileName.endsWith(LABEL_DUMP_SUFFIX)
    }

    /**
     * Returns null when the tile passes, else why it fails.
     */
    fun verdict(tile: String, differingPixels: Int, totalPixels: Int, updateTask: String): String? {
        val share = differingPixels.toDouble() / totalPixels
        if (share <= MAX_DIFFERING_SHARE) {
            return null
        }
        return "$tile: ${percent(share)} differ from its label golden, over ${percent(MAX_DIFFERING_SHARE)};" +
            " an intended change: $updateTask"
    }

    /**
     * Counts the pixels with a channel differing by more than `channelTolerance`, all of them for
     * images of another size; writes a diff image to `diffFile` when any differ.
     */
    fun differingPixels(expectedFile: File, actualFile: File, channelTolerance: Int, diffFile: File): Int {
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
                if (channelDistance(exp.getRGB(x, y), act.getRGB(x, y)) > channelTolerance) {
                    count++
                    diff.setRGB(x, y, 0xFFFF0000.toInt())
                } else {
                    diff.setRGB(x, y, (exp.getRGB(x, y) and 0x00FFFFFF) or 0x40000000)
                }
            }
        }
        if (count > 0) {
            diffFile.parentFile.mkdirs()
            ImageIO.write(diff, "png", diffFile)
        }
        return count
    }

    /**
     * Where two text dumps first differ, as a message; null when equal.
     */
    fun firstDifference(expected: File, actual: File): String? {
        val expLines = expected.readLines()
        val actLines = actual.readLines()
        if (expLines == actLines) {
            return null
        }
        val line = expLines.indices.firstOrNull { it >= actLines.size || expLines[it] != actLines[it] }
            ?: expLines.size
        return "${expected.name} differs at line ${line + 1}: " +
            "expected '${expLines.getOrNull(line)}', actual '${actLines.getOrNull(line)}'"
    }

    /**
     * The largest difference of the ARGB channels of two pixels.
     */
    fun channelDistance(a: Int, b: Int): Int {
        var max = 0
        for (shift in 0..24 step 8) {
            max = maxOf(max, abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)))
        }
        return max
    }

}
