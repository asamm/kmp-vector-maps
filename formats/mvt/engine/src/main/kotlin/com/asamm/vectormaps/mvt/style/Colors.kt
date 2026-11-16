/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * CSS colour strings as the style spec accepts them: `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`,
 * `rgb()`, `rgba()`, `hsl()`, `hsla()` and the CSS named colours the Asamm styles use.
 */
object Colors {

    private const val MAX_CACHE = 4096

    private const val MAX_CACHED_LENGTH = 64

    private val cache = ConcurrentHashMap<String, Color>()

    fun parse(text: String): Color? {
        cache[text]?.let {
            return it
        }
        val color = parseUncached(text.trim().lowercase()) ?: return null
        // style constants fill it; colours read from tile data are bounded by the caps
        if (cache.size < MAX_CACHE
            && text.length <= MAX_CACHED_LENGTH
        ) {
            cache[text] = color
        }
        return color
    }

    /**
     * Interpolates the straight (not premultiplied) channels, as MapLibre's `rgb` interpolation.
     */
    fun interpolate(a: Color, b: Color, t: Double): Color {
        if (t.isNaN()) {
            return a
        }
        val f = t.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val ca = a.argb ushr shift and 0xff
            val cb = b.argb ushr shift and 0xff
            return (ca + (cb - ca) * f).roundToInt().coerceIn(0, 255)
        }
        return Color((channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0))
    }

    fun format(color: Color): String {
        val c = color.argb
        return "rgba(${c ushr 16 and 0xff},${c ushr 8 and 0xff},${c and 0xff},${color.alpha / 255.0})"
    }

    private fun parseUncached(s: String): Color? {
        if (s.startsWith("#")) {
            return parseHex(s.substring(1))
        }
        val open = s.indexOf('(')
        if (open > 0
            && s.endsWith(")")
        ) {
            val name = s.substring(0, open)
            val parts = s.substring(open + 1, s.length - 1).split(',', ' ', '/').filter { it.isNotBlank() }
            return when (name) {
                "rgb", "rgba" -> rgba(parts)
                "hsl", "hsla" -> hsla(parts)
                else -> null
            }
        }
        if (s == "transparent") {
            return Color(0)
        }
        return NAMED[s]?.let { Color(0xff000000.toInt() or it) }
    }

    private fun parseHex(h: String): Color? {
        if (!h.all { it in '0'..'9' || it in 'a'..'f' }) {
            return null
        }
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            3 -> rgb(((v shr 8) and 0xf) * 17, ((v shr 4) and 0xf) * 17, (v and 0xf) * 17, 255)
            4 -> rgb(
                ((v shr 12) and 0xf) * 17,
                ((v shr 8) and 0xf) * 17,
                ((v shr 4) and 0xf) * 17,
                (v and 0xf) * 17,
            )
            6 -> rgb((v shr 16) and 0xff, (v shr 8) and 0xff, v and 0xff, 255)
            8 -> rgb((v shr 24) and 0xff, (v shr 16) and 0xff, (v shr 8) and 0xff, v and 0xff)
            else -> null
        }
    }

    private fun rgb(r: Long, g: Long, b: Long, a: Long): Color {
        return Color(((a shl 24) or (r shl 16) or (g shl 8) or b).toInt())
    }

    private fun rgba(parts: List<String>): Color? {
        if (parts.size < 3) {
            return null
        }
        fun channel(p: String): Int? {
            return if (p.endsWith("%")) {
                p.dropLast(1).toDoubleOrNull()?.let { (it * 2.55).roundToInt() }
            } else {
                p.toDoubleOrNull()?.roundToInt()
            }
        }
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        val a = alpha(parts.getOrNull(3)) ?: return null
        val rgb = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        return Color((a shl 24) or rgb)
    }

    private fun hsla(parts: List<String>): Color? {
        if (parts.size < 3) {
            return null
        }
        val h = (parts[0].removeSuffix("deg").toDoubleOrNull() ?: return null).mod(360.0) / 360.0
        val s = (parts[1].removeSuffix("%").toDoubleOrNull() ?: return null).coerceIn(0.0, 100.0) / 100
        val l = (parts[2].removeSuffix("%").toDoubleOrNull() ?: return null).coerceIn(0.0, 100.0) / 100
        if (h.isNaN()) {
            return null
        }
        val a = alpha(parts.getOrNull(3)) ?: return null
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(t0: Double): Int {
            val t = t0.mod(1.0)
            val v = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (v * 255).roundToInt().coerceIn(0, 255)
        }
        return Color((a shl 24) or (hue(h + 1.0 / 3) shl 16) or (hue(h) shl 8) or hue(h - 1.0 / 3))
    }

    private fun alpha(p: String?): Int? {
        if (p == null) {
            return 255
        }
        val v = if (p.endsWith("%")) p.dropLast(1).toDoubleOrNull()?.div(100) else p.toDoubleOrNull()
        return v?.let { (it.coerceIn(0.0, 1.0) * 255).roundToInt() }
    }

    private val NAMED = mapOf(
        "black" to 0x000000, "white" to 0xffffff, "red" to 0xff0000, "green" to 0x008000,
        "blue" to 0x0000ff, "yellow" to 0xffff00, "gray" to 0x808080, "grey" to 0x808080,
        "orange" to 0xffa500, "purple" to 0x800080, "brown" to 0xa52a2a, "pink" to 0xffc0cb,
        "cyan" to 0x00ffff, "magenta" to 0xff00ff, "lime" to 0x00ff00, "navy" to 0x000080,
        "teal" to 0x008080, "olive" to 0x808000, "maroon" to 0x800000, "silver" to 0xc0c0c0,
    )
}
