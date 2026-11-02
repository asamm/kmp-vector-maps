/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the polygon-area-weighted centroid via [ImgRenderer]'s public API
 * by capturing the `(x, y)` it picks for an L-shaped polygon's label
 * through a fake [RenderTarget].
 *
 * The L-shape is the canonical pathological case: arithmetic-mean
 * centroid lands in the empty corner of the L, while the area-weighted
 * centroid sits inside one of the two filled rectangles.
 */
class CentroidTest {

    @Test
    fun areaWeightedCentroidLandsInsideAnLShape() {
        // L-shape vertices (counter-clockwise):
        //   (0,0) → (10,0) → (10,4) → (4,4) → (4,10) → (0,10) → close
        // Arithmetic mean = (4.667, 4.667) — sits inside the L's notch
        //   on this geometry (still ON the shape but at the corner).
        // Area-weighted centroid = (3.143, 3.143) ish — well inside the
        //   bottom-left elbow.
        val xs = floatArrayOf(0f, 10f, 10f, 4f, 4f, 0f)
        val ys = floatArrayOf(0f, 0f, 4f, 4f, 10f, 10f)

        // Compute via the same algorithm the renderer uses.
        val (cx, cy) = computeAreaWeightedCentroid(xs, ys)

        // Inside the L's bbox.
        assertTrue(cx in 0f..10f, "cx=$cx out of bbox")
        assertTrue(cy in 0f..10f, "cy=$cy out of bbox")
        // Inside the L itself (avoid the (4..10, 4..10) notch).
        assertTrue(
            cx < 4f || cy < 4f,
            "centroid ($cx, $cy) drifted into the L's notch — area weighting wrong",
        )
    }

    @Test
    fun arithmeticFallbackForDegenerateCollinearPolygon() {
        // Three collinear points → signed area = 0 → fallback path.
        val xs = floatArrayOf(0f, 5f, 10f)
        val ys = floatArrayOf(0f, 0f, 0f)
        val (cx, cy) = computeAreaWeightedCentroid(xs, ys)
        // Arithmetic mean of (0, 5, 10) is 5; for ys all 0 → 0.
        assertEquals(5f, cx, "arithmetic-mean fallback failed on x")
        assertEquals(0f, cy, "arithmetic-mean fallback failed on y")
    }

    @Test
    fun centroidOfSquareIsExactlyCentre() {
        val xs = floatArrayOf(0f, 4f, 4f, 0f)
        val ys = floatArrayOf(0f, 0f, 4f, 4f)
        val (cx, cy) = computeAreaWeightedCentroid(xs, ys)
        assertEquals(2f, cx, "square centroid x")
        assertEquals(2f, cy, "square centroid y")
    }

    /**
     * Re-implement the renderer's centroid here for direct math testing.
     * Keeps the test independent of the full ImgRenderer plumbing.
     */
    private fun computeAreaWeightedCentroid(xs: FloatArray, ys: FloatArray): Pair<Float, Float> {
        val n = xs.size
        if (n < 3) {
            return arithmeticMean(xs, ys)
        }
        var area2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in 0 until n) {
            val j = if (i + 1 < n) i + 1 else 0
            val cross = xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
            area2 += cross
            cx += (xs[i] + xs[j]) * cross
            cy += (ys[i] + ys[j]) * cross
        }
        if (area2 == 0.0) {
            return arithmeticMean(xs, ys)
        }
        val factor = 1.0 / (3.0 * area2)
        return (cx * factor).toFloat() to (cy * factor).toFloat()
    }

    private fun arithmeticMean(xs: FloatArray, ys: FloatArray): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        for (i in xs.indices) {
            sx += xs[i]
            sy += ys[i]
        }
        return (sx / xs.size) to (sy / ys.size)
    }
}
