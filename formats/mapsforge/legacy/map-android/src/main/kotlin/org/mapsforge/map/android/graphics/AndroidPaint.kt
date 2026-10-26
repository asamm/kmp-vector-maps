/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015-2020 devemux86
 * Copyright 2019 Matthew Egeler
 * Copyright 2024-2025 Sublimis / Urban Biker
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
package org.mapsforge.map.android.graphics

import android.graphics.BitmapShader
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Shader.TileMode
import android.graphics.Typeface
import android.text.StaticLayout
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
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.android.util.AndroidParameters

internal open class AndroidPaint : Paint {

    @JvmField
    internal val paint: android.graphics.Paint

    // needed to record size of bitmap shader to compute the shift
    private var shaderWidth = 0
    private var shaderHeight = 0

    // Avoid creating unnecessary objects
    private val rect = Rect()

    constructor() {
        paint = android.graphics.Paint()
        paint.isAntiAlias = Parameters.ANTI_ALIASING
        paint.strokeCap = getAndroidCap(Cap.ROUND)
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        paint.style = getAndroidStyle(Style.FILL)
    }

    constructor(paint: Paint) {
        this.paint = android.graphics.Paint((paint as AndroidPaint).paint)
    }

    final override var color: Int
        get() {
            return paint.color
        }
        set(color) {
            paint.color = color
        }

    final override val isComplexStyle: Boolean
        get() {
            return !AndroidParameters.ANDROID_LIBHWUI_FIX
                || paint.style != android.graphics.Paint.Style.STROKE
                || paint.pathEffect != null
        }

    final override val isTransparent: Boolean
        get() {
            return paint.shader == null
                && paint.alpha == 0
        }

    final override var strokeWidth: Float
        get() {
            return paint.strokeWidth
        }
        set(strokeWidth) {
            paint.strokeWidth = strokeWidth
        }

    final override fun getTextBounds(text: String): Rectangle {
        paint.getTextBounds(text, 0, text.length, rect)
        return Rectangle(
            rect.left.toDouble(),
            rect.top.toDouble(),
            rect.right.toDouble(),
            rect.bottom.toDouble(),
        )
    }

    final override fun getTextHeight(text: String): Int {
        paint.getTextBounds(text, 0, text.length, rect)
        return rect.height()
    }

    final override fun getTextWidth(text: String): Int {
        return paint.measureText(text).toInt()
    }

    final override fun setBitmapShader(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        val androidBitmap = AndroidGraphicFactory.getBitmap(bitmap)
        if (androidBitmap == null) {
            return
        }

        shaderWidth = bitmap.width
        shaderHeight = bitmap.height
        if (!AndroidGraphicFactory.KEEP_RESOURCE_BITMAPS) {
            // there is an problem when bitmaps are recycled too early on honeycomb and up,
            // where shaders are corrupted. This problem does of course not arise if
            // the bitmaps are cached for future use.
            // incrementing the refcount stops the recycling, but leaks the bitmap.
            bitmap.incrementRefCount()
        }
        paint.color = AndroidGraphicFactory.getColor(Color.WHITE)
        paint.shader = BitmapShader(androidBitmap, TileMode.REPEAT, TileMode.REPEAT)
    }

    /**
     * Shifts the bitmap pattern so that it always starts at a multiple of itself for any tile that
     * uses it, so a pattern of any size tiles correctly.
     *
     * @param origin the reference point
     */
    final override fun setBitmapShaderShift(origin: Point) {
        val shader = paint.shader
        if (shader != null) {
            val relativeDx = (-origin.x).toInt() % shaderWidth
            val relativeDy = (-origin.y).toInt() % shaderHeight

            val localMatrix = Matrix()
            localMatrix.setTranslate(relativeDx.toFloat(), relativeDy.toFloat())
            shader.setLocalMatrix(localMatrix)
        }
    }

    final override fun setColor(color: Color) {
        paint.color = AndroidGraphicFactory.getColor(color)
    }

    final override fun setDashPathEffect(strokeDasharray: FloatArray?) {
        paint.pathEffect = if (strokeDasharray != null) {
            DashPathEffect(strokeDasharray, 0f)
        } else {
            null
        }
    }

    final override fun setStrokeCap(cap: Cap) {
        paint.strokeCap = getAndroidCap(cap)
    }

    final override fun setStrokeJoin(join: Join) {
        paint.strokeJoin = getAndroidJoin(join)
    }

    final override fun setStyle(style: Style) {
        paint.style = getAndroidStyle(style)
    }

    final override fun setTextAlign(align: Align) {
        paint.textAlign = getAndroidAlign(align)
    }

    final override fun setTextSize(textSize: Float) {
        paint.textSize = textSize
    }

    final override fun setTypeface(fontFamily: FontFamily, fontStyle: FontStyle) {
        paint.typeface = Typeface.create(getTypeface(fontFamily), getFontStyle(fontStyle))
    }

    companion object {

        private fun getAndroidAlign(align: Align): android.graphics.Paint.Align {
            return when (align) {
                Align.CENTER -> android.graphics.Paint.Align.CENTER
                Align.LEFT -> android.graphics.Paint.Align.LEFT
                Align.RIGHT -> android.graphics.Paint.Align.RIGHT
            }
        }

        private fun getAndroidCap(cap: Cap): android.graphics.Paint.Cap {
            return when (cap) {
                Cap.BUTT -> android.graphics.Paint.Cap.BUTT
                Cap.ROUND -> android.graphics.Paint.Cap.ROUND
                Cap.SQUARE -> android.graphics.Paint.Cap.SQUARE
            }
        }

        private fun getAndroidJoin(join: Join): android.graphics.Paint.Join {
            return when (join) {
                Join.BEVEL -> android.graphics.Paint.Join.BEVEL
                Join.ROUND -> android.graphics.Paint.Join.ROUND
                Join.MITER -> android.graphics.Paint.Join.MITER
            }
        }

        private fun getAndroidStyle(style: Style): android.graphics.Paint.Style {
            return when (style) {
                Style.FILL -> android.graphics.Paint.Style.FILL
                Style.STROKE -> android.graphics.Paint.Style.STROKE
            }
        }

        private fun getFontStyle(fontStyle: FontStyle): Int {
            return when (fontStyle) {
                FontStyle.BOLD -> Typeface.BOLD
                FontStyle.BOLD_ITALIC -> Typeface.BOLD_ITALIC
                FontStyle.ITALIC -> Typeface.ITALIC
                FontStyle.NORMAL -> Typeface.NORMAL
            }
        }

        private fun getTypeface(fontFamily: FontFamily): Typeface {
            return when (fontFamily) {
                FontFamily.DEFAULT -> Typeface.DEFAULT
                FontFamily.MONOSPACE -> Typeface.MONOSPACE
                FontFamily.SANS_SERIF -> Typeface.SANS_SERIF
                FontFamily.SERIF -> Typeface.SERIF
            }
        }

        /**
         * [Paint.FONT_PADDING_FACTOR] times `fontHeight`, at least 1.
         */
        @JvmStatic
        fun getFontPadding(fontHeight: Int): Int {
            return Math.max(1.0, Paint.FONT_PADDING_FACTOR * fontHeight).toInt()
        }

        /**
         * The width of the widest line, measured accurately by the layout, which respects spans.
         */
        @JvmStatic
        fun getTextWidth(layout: StaticLayout?): Int {
            var retVal = 0

            if (layout != null) {
                val lineCount = layout.lineCount
                var maxWidth = 0.0

                for (i in 0 until lineCount) {
                    val lw = layout.getLineWidth(i)

                    if (maxWidth < lw) {
                        maxWidth = lw.toDouble()
                    }
                }

                retVal = maxWidth.toInt()
            }

            return retVal
        }
    }
}
