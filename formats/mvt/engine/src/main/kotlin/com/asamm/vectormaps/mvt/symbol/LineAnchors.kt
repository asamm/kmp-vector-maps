/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Anchors along a line part, as MapLibre's `getAnchors` on a line clipped to its data tile: every
 * spacing from half a spacing in for a line that comes in over the tile's edge, from half the label
 * plus two text sizes for one that starts in the tile (both times the overscale, modulo the
 * spacing), where the label fits and the line turns little enough under it; the middle of a line
 * starting in the tile when no spacing fits it.
 */
internal object LineAnchors {

    class Anchor(
        val x: Float,
        val y: Float,
        val distance: Float,
        val angleDeg: Float,
    )

    /**
     * Anchors of a label of `labelLength` and text size `glyphSize` on the part
     * `coords[start, end)` of a tile cut `overscale` times from its data tile `bounds` (min x,
     * min y, max x, max y), the part clipped to them unless `center`: only the middle for `center`,
     * else every `spacing` (widened for a label longer than it); kept only inside `region`, whose
     * segments alone are walked. With a positive `angleWindow`, the line may turn at most
     * `maxAngleDeg` within any window under the label.
     */
    fun find(
        coords: FloatArray,
        start: Int,
        end: Int,
        spacing: Float,
        labelLength: Float,
        glyphSize: Float,
        overscale: Int,
        center: Boolean,
        maxAngleDeg: Double,
        angleWindow: Float,
        region: FloatArray,
        bounds: FloatArray,
    ): List<Anchor> {
        val total = length(coords, start, end)
        if (total <= 0f
            || total < labelLength
        ) {
            return emptyList()
        }
        val turns = if (angleWindow > 0f) turns(coords, start, end) else null
        fun fits(d: Double): Boolean {
            val from = (d - labelLength / 2.0).toFloat()
            val to = (d + labelLength / 2.0).toFloat()
            return from >= 0f
                && to <= total
                && (turns == null || angleFits(turns, from, to, Math.toRadians(maxAngleDeg), angleWindow))
        }
        val out = ArrayList<Anchor>()
        if (center) {
            middle(coords, start, end, total, region, bounds, ::fits)?.let { out += it }
            return out
        }
        // MapLibre widens a spacing the label nearly fills
        val step = if (spacing - labelLength < spacing / 4f) labelLength + spacing / 4f else spacing
        val continued = onEdge(coords[start], coords[start + 1], bounds)
        // MapLibre measures the offset in the data tile's units, so it grows with the overscale
        val first = if (continued) {
            step / 2.0 * overscale % step
        } else {
            (labelLength / 2.0 + glyphSize * 2.0) * overscale % step
        }
        var at = 0.0
        var i = start + 2
        while (i < end
            && out.size < MAX_ANCHORS
        ) {
            val x0 = coords[i - 2]
            val y0 = coords[i - 1]
            val dx = coords[i] - x0
            val dy = coords[i + 1] - y0
            val segment = hypot(dx, dy).toDouble()
            if (segment > 0.0
                && meets(x0, y0, coords[i], coords[i + 1], region)
            ) {
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val firstK = ceil((at - first) / step).toLong().coerceAtLeast(0L)
                val lastK = floor((at + segment - first) / step).toLong()
                var k = firstK
                while (k <= lastK
                    && out.size < MAX_ANCHORS
                ) {
                    val d = first + k * step
                    val t = ((d - at) / segment).toFloat()
                    val x = x0 + dx * t
                    val y = y0 + dy * t
                    if (inside(x, y, region)
                        && inside(x, y, bounds)
                        && fits(d)
                    ) {
                        out += Anchor(x, y, d.toFloat(), angle)
                    }
                    k++
                }
            }
            at += segment
            i += 2
        }
        // MapLibre's second try, for a line starting in the tile with no spaced anchor anywhere in
        // the tile, a region the spaced anchors only miss showing none: they are its neighbours'
        if (out.isNotEmpty()
            || continued
            || spacedFits(coords, start, end, first, step, total, bounds, ::fits)
        ) {
            return out
        }
        middle(coords, start, end, total, region, bounds, ::fits)?.let { out += it }
        return out
    }

    // the line's middle, where it is in both the region and the tile and the label fits
    private fun middle(
        coords: FloatArray,
        start: Int,
        end: Int,
        total: Float,
        region: FloatArray,
        bounds: FloatArray,
        fits: (Double) -> Boolean,
    ): Anchor? {
        val middle = total / 2.0
        val (x, y, angle) = pointAt(coords, start, end, middle.toFloat())
        if (!inside(x, y, region)
            || !inside(x, y, bounds)
            || !fits(middle)
        ) {
            return null
        }
        return Anchor(x, y, middle.toFloat(), angle)
    }

    // a spaced anchor fits somewhere along the whole line within its tile
    private fun spacedFits(
        coords: FloatArray,
        start: Int,
        end: Int,
        first: Double,
        step: Float,
        total: Float,
        bounds: FloatArray,
        fits: (Double) -> Boolean,
    ): Boolean {
        var k = 0L
        while (k < MAX_ANCHORS) {
            val d = first + k * step
            if (d > total) {
                return false
            }
            if (fits(d)) {
                val (x, y) = pointAt(coords, start, end, d.toFloat())
                if (inside(x, y, bounds)) {
                    return true
                }
            }
            k++
        }
        return false
    }

    /**
     * `coords` (x, y pairs) clipped to `bounds` (min x, min y, max x, max y) as MapLibre's
     * `clipLine`: the pieces inside, each cut exactly at the edge it crosses.
     */
    fun clip(coords: FloatArray, bounds: FloatArray): List<FloatArray> {
        // most lines lie inside their tile, kept as they are
        if (inside(coords, bounds)) {
            return listOf(coords)
        }
        val pieces = ArrayList<FloatArray>()
        val piece = FloatArray(coords.size)
        var size = 0
        fun close() {
            if (size >= 4) {
                pieces += piece.copyOf(size)
            }
            size = 0
        }
        var i = 2
        while (i < coords.size) {
            val x0 = coords[i - 2]
            val y0 = coords[i - 1]
            val dx = coords[i] - x0
            val dy = coords[i + 1] - y0
            // Liang-Barsky: the share of the segment within each of the four edges
            var t0 = 0f
            var t1 = 1f
            var visible = true
            for (edge in 0 until 4) {
                val p = when (edge) {
                    0 -> -dx
                    1 -> dx
                    2 -> -dy
                    else -> dy
                }
                val q = when (edge) {
                    0 -> x0 - bounds[0]
                    1 -> bounds[2] - x0
                    2 -> y0 - bounds[1]
                    else -> bounds[3] - y0
                }
                if (p == 0f) {
                    if (q < 0f) {
                        visible = false
                    }
                } else {
                    val r = q / p
                    if (p < 0f) {
                        if (r > t1) {
                            visible = false
                        } else if (r > t0) {
                            t0 = r
                        }
                    } else {
                        if (r < t0) {
                            visible = false
                        } else if (r < t1) {
                            t1 = r
                        }
                    }
                }
            }
            if (visible) {
                val ax = snap(x0 + dx * t0, bounds[0], bounds[2])
                val ay = snap(y0 + dy * t0, bounds[1], bounds[3])
                if (t0 > 0f
                    || size == 0
                ) {
                    close()
                    piece[size++] = ax
                    piece[size++] = ay
                }
                piece[size++] = snap(x0 + dx * t1, bounds[0], bounds[2])
                piece[size++] = snap(y0 + dy * t1, bounds[1], bounds[3])
                if (t1 < 1f) {
                    close()
                }
            } else {
                close()
            }
            i += 2
        }
        close()
        return pieces
    }

    private fun inside(coords: FloatArray, bounds: FloatArray): Boolean {
        for (i in coords.indices step 2) {
            if (coords[i] <= bounds[0]
                || coords[i] >= bounds[2]
                || coords[i + 1] <= bounds[1]
                || coords[i + 1] >= bounds[3]
            ) {
                return false
            }
        }
        return true
    }

    // a value within a hair of an edge put on it, as the clipped ends must be found there
    private fun snap(value: Float, min: Float, max: Float): Float {
        return when {
            abs(value - min) < EDGE -> min
            abs(value - max) < EDGE -> max
            else -> value
        }
    }

    private fun onEdge(x: Float, y: Float, bounds: FloatArray): Boolean {
        return x <= bounds[0] + EDGE
            || x >= bounds[2] - EDGE
            || y <= bounds[1] + EDGE
            || y >= bounds[3] - EDGE
    }

    private const val EDGE = 1e-3f

    /**
     * The part of the line between distances `from` and `to`, as x, y pairs; null when empty.
     */
    fun stretch(coords: FloatArray, start: Int, end: Int, from: Float, to: Float): FloatArray? {
        val out = ArrayList<Float>()
        val (fx, fy) = pointAt(coords, start, end, from)
        out += fx
        out += fy
        var at = 0f
        var i = start + 2
        while (i < end) {
            at += hypot(coords[i] - coords[i - 2], coords[i + 1] - coords[i - 1])
            if (at > from
                && at < to
            ) {
                out += coords[i]
                out += coords[i + 1]
            }
            i += 2
        }
        val (tx, ty) = pointAt(coords, start, end, to)
        out += tx
        out += ty
        return if (out.size >= 4) out.toFloatArray() else null
    }

    private const val MAX_ANCHORS = 4096

    private fun length(coords: FloatArray, start: Int, end: Int): Float {
        var total = 0f
        var i = start + 2
        while (i < end) {
            total += hypot(coords[i] - coords[i - 2], coords[i + 1] - coords[i - 1])
            i += 2
        }
        return total
    }

    // the point at `distance` along the line and its segment's angle in degrees
    private fun pointAt(coords: FloatArray, start: Int, end: Int, distance: Float): Triple<Float, Float, Float> {
        var at = 0f
        var angle = 0f
        var i = start + 2
        while (i < end) {
            val x0 = coords[i - 2]
            val y0 = coords[i - 1]
            val dx = coords[i] - x0
            val dy = coords[i + 1] - y0
            val segment = hypot(dx, dy)
            if (segment > 0f) {
                angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (distance <= at + segment) {
                    val t = ((distance - at) / segment).coerceIn(0f, 1f)
                    return Triple(x0 + dx * t, y0 + dy * t, angle)
                }
            }
            at += segment
            i += 2
        }
        return Triple(coords[end - 2], coords[end - 1], angle)
    }

    private fun inside(x: Float, y: Float, region: FloatArray): Boolean {
        return x >= region[0]
            && y >= region[1]
            && x < region[2]
            && y < region[3]
    }

    // the segment's bounds overlap the region
    private fun meets(x0: Float, y0: Float, x1: Float, y1: Float, region: FloatArray): Boolean {
        return maxOf(x0, x1) >= region[0]
            && maxOf(y0, y1) >= region[1]
            && minOf(x0, x1) < region[2]
            && minOf(y0, y1) < region[3]
    }

    // the corners of the line as distance along it and turn in radians
    private fun turns(coords: FloatArray, start: Int, end: Int): List<Pair<Float, Double>> {
        val out = ArrayList<Pair<Float, Double>>()
        var at = 0f
        var previous = Double.NaN
        var i = start + 2
        while (i < end) {
            val dx = coords[i] - coords[i - 2]
            val dy = coords[i + 1] - coords[i - 1]
            val segment = hypot(dx, dy)
            if (segment > 0f) {
                val angle = atan2(dy.toDouble(), dx.toDouble())
                if (!previous.isNaN()) {
                    var turn = angle - previous
                    while (turn > Math.PI) {
                        turn -= 2 * Math.PI
                    }
                    while (turn < -Math.PI) {
                        turn += 2 * Math.PI
                    }
                    out += Pair(at, turn)
                }
                previous = angle
            }
            at += segment
            i += 2
        }
        return out
    }

    // MapLibre's checkMaxAngle: the corners within any window under the label, as absolute turns,
    // stay below the limit
    private fun angleFits(
        turns: List<Pair<Float, Double>>,
        from: Float,
        to: Float,
        max: Double,
        window: Float,
    ): Boolean {
        var a = turns.binarySearch { it.first.compareTo(from) }.let { if (it < 0) -it - 1 else it }
        while (a < turns.size
            && turns[a].first < to
        ) {
            var sum = 0.0
            var b = a
            while (b < turns.size
                && turns[b].first < to
                && turns[b].first - turns[a].first <= window
            ) {
                sum += abs(turns[b].second)
                if (sum > max) {
                    return false
                }
                b++
            }
            a++
        }
        return true
    }
}
