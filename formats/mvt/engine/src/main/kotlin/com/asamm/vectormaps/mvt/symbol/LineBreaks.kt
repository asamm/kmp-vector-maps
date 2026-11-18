/*
 * Copyright 2026 Asamm Software
 * Portions Copyright (c) 2023, MapLibre contributors; Copyright (c) 2020, Mapbox (MapLibre GL JS
 * `determineLineBreaks`), BSD-3-Clause licence:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of MapLibre GL JS nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * SPDX-License-Identifier: Apache-2.0 AND BSD-3-Clause
 */
package com.asamm.vectormaps.mvt.symbol

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Point text broken into lines as MapLibre's `determineLineBreaks`, translated from MapLibre GL
 * JS.
 */
internal object LineBreaks {

    // where a line may break after: newline, space, &, ), +, -, /, soft hyphen, middle dot,
    // zero width space, hyphen, en dash, hyphenation point, as MapLibre's
    private val BREAKABLE = setOf(0x0a, 0x20, 0x26, 0x29, 0x2b, 0x2d, 0x2f, 0xad, 0xb7, 0x200b, 0x2010, 0x2013, 0x2027)

    private val WHITESPACE = setOf(0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20)

    // a longer text breaks at its newlines only, as the search is quadratic in its break points
    private const val MAX_POINTS = 1000

    /**
     * `text` broken into as few lines as fit `maxWidth`, at the breaks that make them least ragged
     * against their average width, a last line rather shorter than longer, a newline always;
     * `advance` measures one character, `spacing` adds after each.
     */
    fun breakLines(text: String, maxWidth: Float, spacing: Float, advance: (String) -> Float): List<String> {
        val points = text.codePoints().toArray()
        if (points.isEmpty()) {
            return emptyList()
        }
        if (points.size > MAX_POINTS) {
            return text.split('\n').map { it.trim() }
        }
        val widths = HashMap<Int, Float>()
        fun advanceOf(point: Int): Float {
            return widths.getOrPut(point) { advance(String(Character.toChars(point))).coerceAtLeast(0f) } + spacing
        }
        var total = 0f
        for (point in points) {
            total += advanceOf(point)
        }
        val lineCount = maxOf(1f, ceil(total / maxWidth))
        val target = (total / lineCount).toDouble()
        val breaks = ArrayList<LineBreak>()
        var x = 0f
        for (i in points.indices) {
            val point = points[i]
            if (point !in WHITESPACE) {
                x += advanceOf(point)
            }
            if (i < points.size - 1
                && (point in BREAKABLE || ideographic(point) || breaksBefore(points, i + 1))
            ) {
                val penalty = penalty(point = point, next = points[i + 1])
                breaks += evaluate(index = i + 1, x = x, target = target, prior = breaks, penalty = penalty, last = false)
            }
        }
        val chosen = ArrayList<Int>()
        var at: LineBreak? = evaluate(index = points.size, x = x, target = target, prior = breaks, penalty = 0.0, last = true)
        while (at != null) {
            chosen += at.index
            at = at.prior
        }
        chosen.reverse()
        val lines = ArrayList<String>()
        var start = 0
        // an empty line between two newlines keeps its line height, as MapLibre's
        for (end in chosen) {
            lines += String(points, start, end - start).trim()
            start = end
        }
        return lines.ifEmpty { listOf(text.trim()) }
    }

    private class LineBreak(val index: Int, val x: Float, val prior: LineBreak?, val badness: Double)

    // the least bad way to end a line at `index`, over every earlier break
    private fun evaluate(index: Int, x: Float, target: Double, prior: List<LineBreak>, penalty: Double, last: Boolean): LineBreak {
        var best: LineBreak? = null
        var bestBadness = badness(width = x.toDouble(), target = target, penalty = penalty, last = last)
        for (p in prior) {
            val b = badness(width = (x - p.x).toDouble(), target = target, penalty = penalty, last = last) + p.badness
            if (b <= bestBadness) {
                best = p
                bestBadness = b
            }
        }
        return LineBreak(index = index, x = x, prior = best, badness = bestBadness)
    }

    private fun badness(width: Double, target: Double, penalty: Double, last: Boolean): Double {
        val raggedness = (width - target) * (width - target)
        if (last) {
            // a last line shorter than the average rather than longer
            return if (width < target) raggedness / 2.0 else raggedness * 2.0
        }
        return raggedness + abs(penalty) * penalty
    }

    // MapLibre also breaks before an open parenthesis, unless it ends the text
    private fun breaksBefore(points: IntArray, i: Int): Boolean {
        return points[i] == 0x28
            && i != points.size - 1
    }

    private fun penalty(point: Int, next: Int): Double {
        var penalty = 0.0
        if (point == 0x0a) {
            penalty -= 10000.0
        }
        // an open parenthesis ends a line, a closing one starts it, only reluctantly
        if (point == 0x28
            || point == 0xff08
        ) {
            penalty += 50.0
        }
        if (next == 0x29
            || next == 0xff09
        ) {
            penalty += 50.0
        }
        return penalty
    }

    // scripts written without spaces, which break between any characters, as MapLibre's
    // ideographic breaking: CJK and Yi, not Hangul
    private fun ideographic(point: Int): Boolean {
        return point in 0x2e80..0x312f
            || point in 0x3190..0x9fff
            || point in 0xa000..0xa4cf
            || point in 0xf900..0xfaff
            || point in 0xff00..0xffef
            || point in 0x20000..0x2fa1f
    }
}
