/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2019 devemux86
 * Copyright 2017 usrusr
 * Copyright 2019 cpt1gl0
 * Copyright 2019 Adrian Batzill
 * Copyright 2019 mg4gh
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
package org.mapsforge.map.android.graphics

import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Path
import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.layer.renderer.RendererUtils

internal open class AndroidCanvas(canvas: android.graphics.Canvas) : Canvas {

    /**
     * The Android canvas, null once destroyed.
     */
    @JvmField
    internal var canvas: android.graphics.Canvas? = canvas

    @JvmField
    protected val bitmapPaint = android.graphics.Paint()

    init {
        bitmapPaint.isAntiAlias = true
        bitmapPaint.isFilterBitmap = true
    }

    constructor() : this(android.graphics.Canvas())

    final override val dimension: Dimension
        get() {
            return Dimension(width, height)
        }

    final override val height: Int
        get() {
            return canvas!!.height
        }

    final override val width: Int
        get() {
            return canvas!!.width
        }

    final override var isAntiAlias: Boolean
        get() {
            return bitmapPaint.isAntiAlias
        }
        set(aa) {
            bitmapPaint.isAntiAlias = aa
        }

    final override var isFilterBitmap: Boolean
        get() {
            return bitmapPaint.isFilterBitmap
        }
        set(filter) {
            bitmapPaint.isFilterBitmap = filter
        }

    final override fun destroy() {
        canvas = null
    }

    final override fun drawBitmap(bitmap: Bitmap, left: Int, top: Int) {
        val androidBitmap = AndroidGraphicFactory.getBitmap(bitmap)
        if (AndroidGraphicFactory.MONO_ALPHA_BITMAP == androidBitmap!!.config) {
            // we need to clear the existing alpha to get a clean overwrite
            canvas!!.drawColor(android.graphics.Color.argb(0, 0, 0, 0), PorterDuff.Mode.SRC)
        }
        canvas!!.drawBitmap(androidBitmap, left.toFloat(), top.toFloat(), bitmapPaint)
    }

    final override fun drawBitmap(bitmap: Bitmap, left: Int, top: Int, alpha: Float) {
        val oldAlpha = bitmapPaint.alpha
        if (alpha != 1f) {
            bitmapPaint.alpha = (alpha * 255).toInt()
        }
        canvas!!.drawBitmap(
            AndroidGraphicFactory.getBitmap(bitmap)!!,
            left.toFloat(),
            top.toFloat(),
            bitmapPaint,
        )
        if (alpha != 1f) {
            bitmapPaint.alpha = oldAlpha
        }
    }

    final override fun drawBitmap(bitmap: Bitmap, matrix: Matrix) {
        canvas!!.drawBitmap(
            AndroidGraphicFactory.getBitmap(bitmap)!!,
            AndroidGraphicFactory.getMatrix(matrix),
            bitmapPaint,
        )
    }

    final override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, alpha: Float) {
        val oldAlpha = bitmapPaint.alpha
        if (alpha != 1f) {
            bitmapPaint.alpha = (alpha * 255).toInt()
        }
        canvas!!.drawBitmap(
            AndroidGraphicFactory.getBitmap(bitmap)!!,
            AndroidGraphicFactory.getMatrix(matrix),
            bitmapPaint,
        )
        if (alpha != 1f) {
            bitmapPaint.alpha = oldAlpha
        }
    }

    final override fun drawBitmap(
        bitmap: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Int,
        dstTop: Int,
        dstRight: Int,
        dstBottom: Int,
    ) {
        canvas!!.drawBitmap(
            AndroidGraphicFactory.getBitmap(bitmap)!!,
            Rect(srcLeft, srcTop, srcRight, srcBottom),
            Rect(dstLeft, dstTop, dstRight, dstBottom),
            bitmapPaint,
        )
    }

    final override fun drawBitmap(
        bitmap: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Int,
        dstTop: Int,
        dstRight: Int,
        dstBottom: Int,
        alpha: Float,
    ) {
        val oldAlpha = bitmapPaint.alpha
        if (alpha != 1f) {
            bitmapPaint.alpha = (alpha * 255).toInt()
        }
        canvas!!.drawBitmap(
            AndroidGraphicFactory.getBitmap(bitmap)!!,
            Rect(srcLeft, srcTop, srcRight, srcBottom),
            Rect(dstLeft, dstTop, dstRight, dstBottom),
            bitmapPaint,
        )
        if (alpha != 1f) {
            bitmapPaint.alpha = oldAlpha
        }
    }

    final override fun drawCircle(x: Int, y: Int, radius: Int, paint: Paint) {
        if (paint.isTransparent) {
            return
        }
        canvas!!.drawCircle(
            x.toFloat(),
            y.toFloat(),
            radius.toFloat(),
            AndroidGraphicFactory.getPaint(paint),
        )
    }

    final override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, paint: Paint) {
        if (paint.isTransparent) {
            return
        }

        canvas!!.drawLine(
            x1.toFloat(),
            y1.toFloat(),
            x2.toFloat(),
            y2.toFloat(),
            AndroidGraphicFactory.getPaint(paint),
        )
    }

    final override fun drawPath(path: Path, paint: Paint) {
        if (paint.isTransparent) {
            return
        }
        canvas!!.drawPath(
            AndroidGraphicFactory.getPath(path),
            AndroidGraphicFactory.getPaint(paint),
        )
    }

    final override fun drawLines(coordinates: Array<Array<Point>>, dy: Float, paint: Paint) {
        if (paint.isTransparent) {
            return
        }

        var pointsCount = 0
        for (points in coordinates) {
            if (points.size >= 2) {
                pointsCount += 4 * (points.size - 1)
            }
        }
        val lines = FloatArray(pointsCount)

        var ptr = 0

        for (innerList in coordinates) {
            val points = if (dy == 0f) {
                innerList
            } else {
                RendererUtils.parallelPath(innerList, dy.toDouble())
            }
            if (points.size >= 2) {
                lines[ptr] = points[0].x.toFloat()
                lines[ptr + 1] = points[0].y.toFloat()
                lines[ptr + 2] = points[1].x.toFloat()
                lines[ptr + 3] = points[1].y.toFloat()

                ptr += 4

                for (i in 1 until points.size - 1) {
                    lines[ptr] = lines[ptr - 2]
                    lines[ptr + 1] = lines[ptr - 1]
                    lines[ptr + 2] = points[i + 1].x.toFloat()
                    lines[ptr + 3] = points[i + 1].y.toFloat()

                    ptr += 4
                }
            }
        }

        canvas!!.drawLines(lines, AndroidGraphicFactory.getPaint(paint))
    }

    final override fun drawPathText(text: String, path: Path, paint: Paint) {
        // Java's String.trim(), which strips chars up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }

        val androidPaint = AndroidGraphicFactory.getPaint(paint)
        // Way text container was made larger by text height
        canvas!!.drawTextOnPath(
            text,
            AndroidGraphicFactory.getPath(path),
            0f,
            androidPaint.textSize / 4,
            androidPaint,
        )
    }

    final override fun drawText(text: String, x: Int, y: Int, paint: Paint) {
        // Java's String.trim(), which strips chars up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }
        canvas!!.drawText(text, x.toFloat(), y.toFloat(), AndroidGraphicFactory.getPaint(paint))
    }

    final override fun drawTextRotated(
        text: String,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        paint: Paint,
    ) {
        // Java's String.trim(), which strips chars up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }

        val path = android.graphics.Path()
        path.moveTo(x1.toFloat(), y1.toFloat())
        path.lineTo(x2.toFloat(), y2.toFloat())
        canvas!!.drawTextOnPath(text, path, 0f, 3f, AndroidGraphicFactory.getPaint(paint))
    }

    final override fun fillColor(color: Color) {
        fillColor(AndroidGraphicFactory.getColor(color))
    }

    final override fun fillColor(color: Int) {
        val alpha = (color shr 24) and 0xff
        canvas!!.drawColor(
            color,
            if (alpha == 0) PorterDuff.Mode.CLEAR else PorterDuff.Mode.SRC_OVER,
        )
    }

    @Suppress("DEPRECATION")
    final override fun resetClip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            canvas!!.clipRect(0f, 0f, width.toFloat(), height.toFloat(), Region.Op.REPLACE)
        }
    }

    final override fun restore() {
        canvas!!.restore()
    }

    final override fun rotate(degrees: Float, px: Float, py: Float) {
        if (degrees != 0f) {
            canvas!!.rotate(degrees, px, py)
        }
    }

    final override fun rotate(rotation: Rotation) {
        if (!Rotation.noRotation(rotation)) {
            rotate(rotation.degrees, rotation.px, rotation.py)
        }
    }

    final override fun save() {
        canvas!!.save()
    }

    final override fun setBitmap(bitmap: Bitmap?) {
        canvas!!.setBitmap(AndroidGraphicFactory.getBitmap(bitmap!!))
    }

    final override fun setBitmap(
        bitmap: Bitmap?,
        dx: Float,
        dy: Float,
        degrees: Float,
        px: Float,
        py: Float,
    ) {
        setBitmap(bitmap)
        val matrix = android.graphics.Matrix()
        if (dx != 0f
            || dy != 0f
        ) {
            matrix.preTranslate(dx, dy)
        }
        if (degrees != 0f) {
            matrix.preRotate(degrees, px, py)
        }
        canvas!!.setMatrix(matrix)
    }

    final override fun setClip(left: Int, top: Int, width: Int, height: Int) {
        setClip(left, top, width, height, false)
    }

    @Suppress("DEPRECATION")
    final override fun setClip(left: Int, top: Int, width: Int, height: Int, intersect: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (intersect) {
                canvas!!.clipRect(left, top, left + width, top + height)
            }
        } else {
            canvas!!.clipRect(
                left.toFloat(),
                top.toFloat(),
                (left + width).toFloat(),
                (top + height).toFloat(),
                Region.Op.REPLACE,
            )
        }
    }

    @Suppress("DEPRECATION")
    final override fun setClipDifference(left: Float, top: Float, width: Float, height: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas!!.clipOutRect(left, top, left + width, top + height)
        } else {
            canvas!!.clipRect(left, top, left + width, top + height, Region.Op.DIFFERENCE)
        }
    }

    final override fun translate(dx: Float, dy: Float) {
        canvas!!.translate(dx, dy)
    }
}
