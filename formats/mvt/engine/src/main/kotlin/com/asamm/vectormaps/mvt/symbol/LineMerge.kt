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
        // each line as its pieces in order, joined into one array once at the end
        val lines = arrayOfNulls<ArrayDeque<Int>>(parts.size)
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
                val joined = lines[before]!!
                joined.addLast(k)
                joined.addAll(lines[after]!!)
                ends.remove(startKey)
                starts.remove(endKey)
                val last = parts[joined.last()]
                ends[key(last[last.size - 2], last[last.size - 1])] = before
                lines[after] = null
            } else if (before != null) {
                lines[before]!!.addLast(k)
                ends.remove(startKey)
                ends[endKey] = before
            } else if (after != null) {
                lines[after]!!.addFirst(k)
                starts.remove(endKey)
                starts[startKey] = after
            } else {
                lines[k] = ArrayDeque(listOf(k))
                starts[startKey] = k
                ends[endKey] = k
            }
        }
        return lines.indices.mapNotNull { i -> lines[i]?.let { Chain(coordsOf(parts, it), i) } }
    }

    // the pieces end to end, each after the first without the point it shares with the one before
    private fun coordsOf(parts: List<FloatArray>, pieces: ArrayDeque<Int>): FloatArray {
        if (pieces.size == 1) {
            return parts[pieces.first()]
        }
        var size = parts[pieces.first()].size
        for (j in 1 until pieces.size) {
            size += parts[pieces[j]].size - 2
        }
        val out = FloatArray(size)
        var at = 0
        for ((j, piece) in pieces.withIndex()) {
            val part = parts[piece]
            val from = if (j == 0) 0 else 2
            part.copyInto(out, destinationOffset = at, startIndex = from)
            at += part.size - from
        }
        return out
    }

    private fun key(x: Float, y: Float): Long {
        return (x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xffffffffL)
    }
}
