/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

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
