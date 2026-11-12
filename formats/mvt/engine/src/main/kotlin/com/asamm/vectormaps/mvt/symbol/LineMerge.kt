/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

/**
 * Joins line pieces whose ends meet, as MapLibre's `mergeLines` does before it anchors the
 * symbols of one road or route: a symbol then repeats along the whole line, not per piece.
 */
internal object LineMerge {

    /**
     * A joined line and the index of the piece it started from, whose symbol it shows.
     */
    class Chain(
        val coords: FloatArray,
        val first: Int,
    )

    /**
     * `parts` (x, y pairs each) joined where one piece ends at another's start, through indexes of
     * the free ends, in one pass in piece order.
     */
    fun merge(parts: List<FloatArray>): List<Chain> {
        val lines = arrayOfNulls<FloatArray>(parts.size)
        // a free end's point to the index of the line it starts or ends
        val starts = HashMap<Long, Int>()
        val ends = HashMap<Long, Int>()
        for ((k, part) in parts.withIndex()) {
            val startKey = key(part[0], part[1])
            val endKey = key(part[part.size - 2], part[part.size - 1])
            val before = ends[startKey]
            val after = starts[endKey]
            if (before != null
                && after != null
                && before != after
            ) {
                // the piece joins two lines into one
                lines[before] = lines[before]!! + part.copyOfRange(2, part.size) +
                    lines[after]!!.copyOfRange(2, lines[after]!!.size)
                ends.remove(startKey)
                starts.remove(endKey)
                val joined = lines[before]!!
                ends[key(joined[joined.size - 2], joined[joined.size - 1])] = before
                lines[after] = null
            } else if (before != null) {
                lines[before] = lines[before]!! + part.copyOfRange(2, part.size)
                ends.remove(startKey)
                ends[endKey] = before
            } else if (after != null) {
                lines[after] = part + lines[after]!!.copyOfRange(2, lines[after]!!.size)
                starts.remove(endKey)
                starts[startKey] = after
            } else {
                lines[k] = part
                starts[startKey] = k
                ends[endKey] = k
            }
        }
        return lines.indices.mapNotNull { i -> lines[i]?.let { Chain(it, i) } }
    }

    private fun key(x: Float, y: Float): Long {
        return (x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xffffffffL)
    }
}
