/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import kotlin.math.abs

/**
 * Joins line pieces whose ends meet, as MapLibre's `mergeLines` does before it anchors the
 * symbols of one road or route: a symbol then repeats along the whole line, not per piece.
 */
internal object LineMerge {

    /**
     * `parts` (x, y pairs each) with every piece that starts where another ends appended to it.
     */
    fun merge(parts: List<FloatArray>): List<FloatArray> {
        if (parts.size < 2) {
            return parts
        }
        val lines = parts.toMutableList<FloatArray?>()
        var joined = true
        while (joined) {
            joined = false
            for (a in lines.indices) {
                val first = lines[a] ?: continue
                for (b in lines.indices) {
                    val second = lines[b] ?: continue
                    if (a == b
                        || !meets(first, second)
                    ) {
                        continue
                    }
                    // the shared point once
                    lines[a] = first + second.copyOfRange(2, second.size)
                    lines[b] = null
                    joined = true
                    break
                }
            }
        }
        return lines.filterNotNull()
    }

    // the end of `first` is the start of `second`
    private fun meets(first: FloatArray, second: FloatArray): Boolean {
        return abs(first[first.size - 2] - second[0]) < EPSILON
            && abs(first[first.size - 1] - second[1]) < EPSILON
    }

    private const val EPSILON = 0.01f
}
