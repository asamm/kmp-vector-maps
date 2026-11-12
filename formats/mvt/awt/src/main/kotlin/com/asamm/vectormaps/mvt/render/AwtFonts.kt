/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.symbol.MvtFont
import com.asamm.vectormaps.mvt.symbol.TextMetrics
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.Point2D
import java.text.Bidi
import java.util.concurrent.ConcurrentHashMap

/**
 * The AWT fonts behind the style's `text-font` faces, shaping text for both the symbol layout's
 * measuring and the target's drawing: `sans` and `serif` at their regular weight, bold and italic
 * derived where the family has no own face.
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

    /**
     * `text` laid out by the font's shaper (joining, ligatures, reordering for complex scripts), in
     * the direction of its first strong character, `letterSpacingPx` added between glyphs.
     */
    fun shape(text: String, sizePx: Float, font: MvtFont, letterSpacingPx: Float): GlyphVector {
        val rightToLeft = !Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
        val flags = if (rightToLeft) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT
        val chars = text.toCharArray()
        val glyphs = font(font, sizePx).layoutGlyphVector(CONTEXT, chars, 0, chars.size, flags)
        if (letterSpacingPx != 0f) {
            for (i in 0..glyphs.numGlyphs) {
                val p = glyphs.getGlyphPosition(i)
                glyphs.setGlyphPosition(i, Point2D.Float(p.x.toFloat() + i * letterSpacingPx, p.y.toFloat()))
            }
            // the end position counts one gap less than the glyphs
            val end = glyphs.getGlyphPosition(glyphs.numGlyphs)
            glyphs.setGlyphPosition(glyphs.numGlyphs, Point2D.Float(end.x.toFloat() - letterSpacingPx, end.y.toFloat()))
        }
        return glyphs
    }

    override fun width(text: String, sizePx: Float, font: MvtFont, letterSpacingPx: Float): Float {
        return shape(text, sizePx, font, letterSpacingPx).logicalBounds.width.toFloat()
    }

    internal companion object {

        // the antialiased, fractional metrics the target draws with
        val CONTEXT = FontRenderContext(null, true, true)
    }
}
