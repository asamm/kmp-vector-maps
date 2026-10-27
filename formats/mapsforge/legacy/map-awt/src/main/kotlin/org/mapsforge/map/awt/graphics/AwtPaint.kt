/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2017 devemux86
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2024-2025 Sublimis
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.awt.graphics

import org.mapsforge.core.graphics.Align
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.FontFamily
import org.mapsforge.core.graphics.FontStyle
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.Point
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Rectangle
import java.awt.Stroke
import java.awt.TexturePaint
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.text.AttributedCharacterIterator

open class AwtPaint : Paint {

    internal var awtColor: java.awt.Color
    internal var font: Font? = null
    internal var stroke: Stroke? = null
    internal var style: Style
    internal var texturePaint: TexturePaint? = null
    private var cap: Int
    private var fontName: String? = null
    private var fontStyle = 0
    private var join: Int
    private var strokeDasharray: FloatArray? = null
    private var strokeWidthValue = 0f
    private var textSize = 0f

    // needed to record size of bitmap shader to compute the shift
    private var shaderWidth = 0
    private var shaderHeight = 0

    // Avoid creating unnecessary objects
    private val bufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    internal constructor() {
        this.cap = getCap(Cap.ROUND)
        this.awtColor = java.awt.Color.BLACK
        this.style = Style.FILL
        this.join = getJoin(Join.ROUND)
    }

    internal constructor(paint: Paint) {
        val ap = paint as AwtPaint
        this.cap = ap.cap
        this.awtColor = ap.awtColor
        this.style = ap.style
        this.join = ap.join
        this.stroke = ap.stroke
        this.fontStyle = ap.fontStyle
        this.font = ap.font
        this.fontName = ap.fontName
        this.strokeWidthValue = ap.strokeWidthValue
        this.textSize = ap.textSize
        this.strokeDasharray = ap.strokeDasharray
    }

    final override var color: Int
        get() {
            return this.awtColor.rgb
        }
        set(color) {
            this.awtColor = java.awt.Color(color, true)
        }

    final override var strokeWidth: Float
        get() {
            return this.strokeWidthValue
        }
        set(strokeWidth) {
            this.strokeWidthValue = strokeWidth
            createStroke()
        }

    final override fun getTextBounds(text: String): org.mapsforge.core.model.Rectangle {
        val graphics2d = bufferedImage.createGraphics()
        val fontMetrics = graphics2d.getFontMetrics(this.font)
        graphics2d.dispose()

        val bounds: Rectangle = this.font!!
            .createGlyphVector(fontMetrics.fontRenderContext, text)
            .visualBounds
            .bounds

        return org.mapsforge.core.model.Rectangle(
            bounds.x.toDouble(),
            bounds.y.toDouble(),
            (bounds.x + bounds.width).toDouble(),
            (bounds.y + bounds.height).toDouble(),
        )
    }

    final override fun getTextHeight(text: String): Int {
        val graphics2d = bufferedImage.createGraphics()
        val fontMetrics = graphics2d.getFontMetrics(this.font)
        graphics2d.dispose()
        return this.font!!
            .createGlyphVector(fontMetrics.fontRenderContext, text)
            .visualBounds
            .height
            .toInt()
    }

    final override fun getTextWidth(text: String): Int {
        val graphics2d = bufferedImage.createGraphics()
        val fontMetrics = graphics2d.getFontMetrics(this.font)
        graphics2d.dispose()
        return fontMetrics.stringWidth(text)
    }

    final override val isTransparent: Boolean
        get() {
            return this.texturePaint == null
                && this.awtColor.alpha == 0
        }

    final override val isComplexStyle: Boolean
        get() {
            return true
        }

    final override fun setBitmapShader(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        this.shaderWidth = bitmap.width
        this.shaderHeight = bitmap.height
        val rectangle = Rectangle(0, 0, bitmap.width, bitmap.height)
        this.texturePaint = TexturePaint(AwtGraphicFactory.getBitmap(bitmap), rectangle)
    }

    /**
     * Shifts the bitmap pattern so that it will always start at a multiple of itself for any tile
     * the pattern is used. This ensures that regardless of size of the pattern it tiles correctly.
     *
     * @param origin the reference point
     */
    final override fun setBitmapShaderShift(origin: Point) {
        val texturePaint = this.texturePaint
        if (texturePaint != null) {
            val relativeDx = (-origin.x).toInt() % this.shaderWidth
            val relativeDy = (-origin.y).toInt() % this.shaderHeight

            val rectangle = Rectangle(relativeDx, relativeDy, this.shaderWidth, this.shaderHeight)
            this.texturePaint = TexturePaint(texturePaint.image, rectangle)
        }
    }

    final override fun setAntiAlias(antiAlias: Boolean) {
        // the canvas anti-aliases
    }

    final override fun setColor(color: Color) {
        this.awtColor = AwtGraphicFactory.getColor(color)
    }

    final override fun setDashPathEffect(strokeDasharray: FloatArray?) {
        this.strokeDasharray = strokeDasharray
        createStroke()
    }

    final override fun setStrokeCap(cap: Cap) {
        this.cap = getCap(cap)
        createStroke()
    }

    final override fun setStrokeJoin(join: Join) {
        this.join = getJoin(join)
        createStroke()
    }

    final override fun setStyle(style: Style) {
        this.style = style
    }

    final override fun setTextAlign(align: Align) {
        // this.align = align; //never read
    }

    final override fun setTextSize(textSize: Float) {
        this.textSize = textSize
        createFont()
    }

    final override fun setTypeface(fontFamily: FontFamily, fontStyle: FontStyle) {
        this.fontName = getFontName(fontFamily)
        this.fontStyle = getFontStyle(fontStyle)
        createFont()
    }

    private fun createFont() {
        if (this.textSize > 0) {
            this.font = Font(this.fontName, this.fontStyle, this.textSize.toInt())
                .deriveFont(TEXT_ATTRIBUTES)
        } else {
            this.font = null
        }
    }

    private fun createStroke() {
        if (this.strokeWidthValue <= 0) {
            return
        }
        this.stroke = BasicStroke(
            this.strokeWidthValue,
            this.cap,
            this.join,
            if (this.join == BasicStroke.JOIN_MITER) 1.0f else 0f,
            this.strokeDasharray,
            0f,
        )
    }

    fun setStroke(stroke: Stroke?) {
        this.stroke = stroke
    }

    companion object {

        private val TEXT_ATTRIBUTES: MutableMap<AttributedCharacterIterator.Attribute, Any> = HashMap()

        init {
            TEXT_ATTRIBUTES[TextAttribute.KERNING] = TextAttribute.KERNING_ON
        }

        private fun getCap(cap: Cap): Int {
            return when (cap) {
                Cap.BUTT -> BasicStroke.CAP_BUTT
                Cap.ROUND -> BasicStroke.CAP_ROUND
                Cap.SQUARE -> BasicStroke.CAP_SQUARE
            }
        }

        private fun getFontName(fontFamily: FontFamily): String? {
            return when (fontFamily) {
                FontFamily.MONOSPACE -> Font.MONOSPACED
                FontFamily.DEFAULT -> null
                FontFamily.SANS_SERIF -> Font.SANS_SERIF
                FontFamily.SERIF -> Font.SERIF
            }
        }

        private fun getFontStyle(fontStyle: FontStyle): Int {
            return when (fontStyle) {
                FontStyle.BOLD -> Font.BOLD
                FontStyle.BOLD_ITALIC -> Font.BOLD or Font.ITALIC
                FontStyle.ITALIC -> Font.ITALIC
                FontStyle.NORMAL -> Font.PLAIN
            }
        }

        private fun getJoin(join: Join): Int {
            return when (join) {
                Join.ROUND -> BasicStroke.JOIN_ROUND
                Join.BEVEL -> BasicStroke.JOIN_BEVEL
                Join.MITER -> BasicStroke.JOIN_MITER
            }
        }

        /**
         * [Paint.FONT_PADDING_FACTOR] times `fontHeight`, at least 1.
         */
        @JvmStatic
        fun getFontPadding(fontHeight: Int): Int {
            return Math.max(1.0, Paint.FONT_PADDING_FACTOR * fontHeight).toInt()
        }
    }
}
