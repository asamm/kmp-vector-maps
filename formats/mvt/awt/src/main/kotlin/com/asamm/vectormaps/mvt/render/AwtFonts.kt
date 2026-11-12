/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.symbol.MvtFont
import com.asamm.vectormaps.mvt.symbol.TextMetrics
import java.awt.Font
import java.awt.font.FontRenderContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The AWT fonts behind the style's `text-font` faces, measuring for the symbol layout what the
 * target draws: `sans` and `serif` at their regular weight, bold and italic derived where the
 * family has no own face.
 */
class AwtFonts(
    private val sans: Font,
    private val serif: Font = sans,
) : TextMetrics {

    private val faces = ConcurrentHashMap<MvtFont, Font>()

    /**
     * The face of `font` at `sizePx`.
     */
    fun font(font: MvtFont, sizePx: Float): Font {
        val face = faces.getOrPut(font) {
            val style = (if (font.bold) Font.BOLD else 0) or (if (font.italic) Font.ITALIC else 0)
            (if (font.serif) serif else sans).deriveFont(style)
        }
        return face.deriveFont(sizePx)
    }

    override fun width(text: String, sizePx: Float, font: MvtFont): Float {
        return font(font, sizePx).getStringBounds(text, CONTEXT).width.toFloat()
    }

    internal companion object {

        // the antialiased, fractional metrics the target draws with
        val CONTEXT = FontRenderContext(null, true, true)
    }
}
