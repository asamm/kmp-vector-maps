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
 * Anchors along a line part, as MapLibre's `getAnchors`: every spacing from half a spacing in,
 * the middle when none fits, where the label fits and the line turns little enough under it.
 */
internal object LineAnchors {

    class Anchor(
        val x: Float,
        val y: Float,
        val distance: Float,
        val angleDeg: Float,
    )

    /**
     * Anchors of a label of `labelLength` on the part `coords[start, end)`: only the middle for
     * `center`, else every `spacing` (widened for a label longer than it); kept only inside
     * `region` (min x, min y, max x, max y), whose segments alone are walked. With a positive
     * `angleWindow`, the line may turn at most `maxAngleDeg` within any window under the label.
     */
    fun find(
        coords: FloatArray,
        start: Int,
        end: Int,
        spacing: Float,
        labelLength: Float,
        center: Boolean,
        maxAngleDeg: Double,
        angleWindow: Float,
        region: FloatArray,
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
        // MapLibre widens a spacing the label nearly fills
        val step = if (spacing - labelLength < spacing / 4f) labelLength + spacing / 4f else spacing
        if (!center
            && total >= step
        ) {
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
                    val firstK = ceil((at - step / 2.0) / step).toLong().coerceAtLeast(0L)
                    val lastK = floor((at + segment - step / 2.0) / step).toLong()
                    var k = firstK
                    while (k <= lastK
                        && out.size < MAX_ANCHORS
                    ) {
                        val d = step / 2.0 + k * step
                        val t = ((d - at) / segment).toFloat()
                        val x = x0 + dx * t
                        val y = y0 + dy * t
                        if (inside(x, y, region)
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
            if (out.isNotEmpty()) {
                return out
            }
        }
        // the middle, for `line-center`, a line shorter than its spacing, or one no spacing fits
        val middle = total / 2.0
        val (x, y, angle) = pointAt(coords, start, end, middle.toFloat())
        if (inside(x, y, region)
            && fits(middle)
        ) {
            out += Anchor(x, y, middle.toFloat(), angle)
        }
        return out
    }

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
