/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import android.graphics.Paint
import android.graphics.Typeface
import com.asamm.vectormaps.mvt.symbol.MvtFont
import com.asamm.vectormaps.mvt.symbol.TextMetrics
import java.util.concurrent.ConcurrentHashMap

/**
 * Text faces for [AndroidMvtRenderTarget], and [TextMetrics] that match its drawing; Android
 * shapes complex scripts and right-to-left text itself.
 */
class AndroidFonts(
    private val sans: Typeface = Typeface.SANS_SERIF,
    private val serif: Typeface = Typeface.SERIF,
) : TextMetrics {

    private val faces = ConcurrentHashMap<MvtFont, Typeface>()

    // measuring runs on several render threads at once
    private val paints = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    }

    /**
     * The face of `font`.
     */
    fun typeface(font: MvtFont): Typeface {
        return faces.getOrPut(font) {
            val style = when {
                font.bold && font.italic -> Typeface.BOLD_ITALIC
                font.bold -> Typeface.BOLD
                font.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(if (font.serif) serif else sans, style)
        }
    }

    /**
     * Sets `paint` up to draw `font` at `sizePx`, `letterSpacingPx` added between glyphs.
     */
    fun apply(paint: Paint, font: MvtFont, sizePx: Float, letterSpacingPx: Float) {
        paint.typeface = typeface(font)
        paint.textSize = sizePx
        // Android counts letter spacing in ems
        paint.letterSpacing = if (sizePx > 0f) letterSpacingPx / sizePx else 0f
    }

    override fun width(text: String, sizePx: Float, font: MvtFont, letterSpacingPx: Float): Float {
        val paint = paints.get()
        apply(paint = paint, font = font, sizePx = sizePx, letterSpacingPx = letterSpacingPx)
        return paint.measureText(text).coerceAtLeast(0f)
    }
}
