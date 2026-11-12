/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import kotlin.math.sqrt

/**
 * `line-offset`: shifts each part of a path by `offset` pixels, positive to the right of its
 * direction as in MapLibre; a mitred vertex moves at most [MAX_MITER] times the offset, and a
 * closed ring is offset around its closing vertex too.
 */
internal object LineOffset {

    private const val MAX_MITER = 4f

    fun offset(path: MvtPath, offset: Float): MvtPath {
        val c = path.coords
        val out = FloatArray(c.size)
        var start = 0
        for (end in path.partEnds) {
            val n = (end - start) / 2
            for (k in 0 until n) {
                val i = start + k * 2
                // the segments before and after the vertex; a ring wraps around its ends
                val prev = when {
                    k > 0 -> i - 2
                    path.closed && n > 2 -> start + (n - 1) * 2
                    else -> -1
                }
                val next = when {
                    k < n - 1 -> i + 2
                    path.closed && n > 2 -> start
                    else -> -1
                }
                var ax = 0f
                var ay = 0f
                if (prev >= 0) {
                    val dx = c[i] - c[prev]
                    val dy = c[i + 1] - c[prev + 1]
                    val len = sqrt(dx * dx + dy * dy)
                    if (len > 1e-6f) {
                        // right-hand normal in screen space, y down
                        ax = -dy / len
                        ay = dx / len
                    }
                }
                var bx = 0f
                var by = 0f
                if (next >= 0) {
                    val dx = c[next] - c[i]
                    val dy = c[next + 1] - c[i + 1]
                    val len = sqrt(dx * dx + dy * dy)
                    if (len > 1e-6f) {
                        bx = -dy / len
                        by = dx / len
                    }
                }
                val hasA = ax != 0f || ay != 0f
                val hasB = bx != 0f || by != 0f
                val nx: Float
                val ny: Float
                val scale: Float
                if (hasA && hasB) {
                    val sx = ax + bx
                    val sy = ay + by
                    val len2 = sx * sx + sy * sy
                    if (len2 < 1e-6f) {
                        // a U-turn: keep the incoming side
                        nx = ax
                        ny = ay
                        scale = 1f
                    } else {
                        // the summed unit normals scaled to the mitre, its length capped
                        nx = sx
                        ny = sy
                        scale = minOf(2f / len2, MAX_MITER / sqrt(len2))
                    }
                } else {
                    nx = ax + bx
                    ny = ay + by
                    scale = 1f
                }
                out[i] = c[i] + nx * scale * offset
                out[i + 1] = c[i + 1] + ny * scale * offset
            }
            start = end
        }
        return MvtPath(out, path.partEnds, path.closed)
    }
}
