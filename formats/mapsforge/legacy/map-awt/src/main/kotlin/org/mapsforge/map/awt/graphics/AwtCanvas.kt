/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2021 devemux86
 * Copyright 2017 usrusr
 * Copyright 2019 cpt1gl0
 * Copyright 2019 Adrian Batzill
 * Copyright 2019 Matthew Egeler
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
package org.mapsforge.map.awt.graphics

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Path
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.Parameters
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel

internal open class AwtCanvas : Canvas {

    private var bufferedImage: BufferedImage? = null
    private var graphics2D: Graphics2D? = null

    // throws without a bitmap
    private val graphics: Graphics2D
        get() {
            return graphics2D!!
        }

    constructor()

    constructor(graphics2D: Graphics2D) {
        this.graphics2D = graphics2D
        isAntiAlias = Parameters.ANTI_ALIASING
    }

    final override fun destroy() {
        // do nothing
    }

    final override fun drawBitmap(bitmap: Bitmap, left: Int, top: Int) {
        val awtBitmap = AwtGraphicFactory.getBitmap(bitmap)
        if (awtBitmap.colorModel is IndexColorModel) {
            // We need to clear the existing alpha to get a clean overwrite
            // (this is currently expected only for hill shading bitmaps)
            fillColor(Color.TRANSPARENT)
        }
        this.graphics.drawImage(awtBitmap, left, top, null)
    }

    final override fun drawBitmap(bitmap: Bitmap, left: Int, top: Int, alpha: Float) {
        val composite = this.graphics.composite
        if (alpha != 1f) {
            this.graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        }
        this.graphics.drawImage(AwtGraphicFactory.getBitmap(bitmap), left, top, null)
        if (alpha != 1f) {
            this.graphics.composite = composite
        }
    }

    final override fun drawBitmap(bitmap: Bitmap, matrix: Matrix) {
        this.graphics.drawRenderedImage(
            AwtGraphicFactory.getBitmap(bitmap),
            AwtGraphicFactory.getAffineTransform(matrix),
        )
    }

    final override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, alpha: Float) {
        val composite = this.graphics.composite
        if (alpha != 1f) {
            this.graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        }
        this.graphics.drawRenderedImage(
            AwtGraphicFactory.getBitmap(bitmap),
            AwtGraphicFactory.getAffineTransform(matrix),
        )
        if (alpha != 1f) {
            this.graphics.composite = composite
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
        this.graphics.drawImage(
            AwtGraphicFactory.getBitmap(bitmap),
            dstLeft, dstTop, dstRight, dstBottom,
            srcLeft, srcTop, srcRight, srcBottom,
            null,
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
        val composite = this.graphics.composite
        if (alpha != 1f) {
            this.graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        }
        this.graphics.drawImage(
            AwtGraphicFactory.getBitmap(bitmap),
            dstLeft, dstTop, dstRight, dstBottom,
            srcLeft, srcTop, srcRight, srcBottom,
            null,
        )
        if (alpha != 1f) {
            this.graphics.composite = composite
        }
    }

    final override fun drawCircle(x: Int, y: Int, radius: Int, paint: Paint) {
        if (paint.isTransparent) {
            return
        }

        val awtPaint = AwtGraphicFactory.getPaint(paint)
        setColorAndStroke(awtPaint)
        val doubleRadius = radius * 2

        val style = awtPaint.style
        when (style) {
            Style.FILL -> {
                this.graphics.fillOval(x - radius, y - radius, doubleRadius, doubleRadius)
            }

            Style.STROKE -> {
                this.graphics.drawOval(x - radius, y - radius, doubleRadius, doubleRadius)
            }
        }
    }

    final override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, paint: Paint) {
        if (paint.isTransparent) {
            return
        }

        setColorAndStroke(AwtGraphicFactory.getPaint(paint))
        this.graphics.drawLine(x1, y1, x2, y2)
    }

    final override fun drawPath(path: Path, paint: Paint) {
        if (paint.isTransparent) {
            return
        }

        val awtPaint = AwtGraphicFactory.getPaint(paint)
        val awtPath = AwtGraphicFactory.getPath(path)

        setColorAndStroke(awtPaint)
        this.graphics.paint = awtPaint.texturePaint

        val style = awtPaint.style
        when (style) {
            Style.FILL -> {
                this.graphics.fill(awtPath.path2D)
            }

            Style.STROKE -> {
                this.graphics.draw(awtPath.path2D)
            }
        }
    }

    final override fun drawLines(coordinates: Array<Array<Point>>, dy: Float, paint: Paint) {
        // Not used
    }

    final override fun drawPathText(text: String, path: Path, paint: Paint) {
        // Java's String.trim() semantics: strips every char up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }

        val awtPaint = AwtGraphicFactory.getPaint(paint)
        val awtPath = AwtGraphicFactory.getPath(path)

        if (awtPaint.stroke == null) {
            this.graphics.color = awtPaint.awtColor
            this.graphics.font = awtPaint.font
        } else {
            setColorAndStroke(awtPaint)
        }

        val textStroke = TextStroke(text, awtPaint.font, this.graphics.fontRenderContext, false, false)
        val style = awtPaint.style
        when (style) {
            Style.FILL -> {
                this.graphics.fill(textStroke.createStrokedShape(awtPath.path2D))
            }

            Style.STROKE -> {
                this.graphics.draw(textStroke.createStrokedShape(awtPath.path2D))
            }
        }
    }

    final override fun drawText(text: String, x: Int, y: Int, paint: Paint) {
        // Java's String.trim() semantics: strips every char up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }

        val awtPaint = AwtGraphicFactory.getPaint(paint)

        if (awtPaint.stroke == null) {
            this.graphics.color = awtPaint.awtColor
            this.graphics.font = awtPaint.font
            this.graphics.drawString(text, x, y)
        } else {
            setColorAndStroke(awtPaint)
            val textLayout = TextLayout(text, awtPaint.font, this.graphics.fontRenderContext)
            val affineTransform = AffineTransform()
            affineTransform.translate(x.toDouble(), y.toDouble())
            this.graphics.draw(textLayout.getOutline(affineTransform))
        }
    }

    final override fun drawTextRotated(text: String, x1: Int, y1: Int, x2: Int, y2: Int, paint: Paint) {
        // Java's String.trim() semantics: strips every char up to U+0020
        if (text.trim { it <= ' ' }.isEmpty()) {
            return
        }
        if (paint.isTransparent) {
            return
        }

        val affineTransform = this.graphics.transform

        val theta = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        this.graphics.rotate(theta, x1.toDouble(), y1.toDouble())

        val lineLength = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
        val textWidth = paint.getTextWidth(text)
        val dx = (lineLength - textWidth).toInt() / 2
        val xy = paint.getTextHeight(text) / 3
        drawText(text, x1 + dx, y1 + xy, paint)

        this.graphics.transform = affineTransform
    }

    final override fun fillColor(color: Color) {
        fillColor(AwtGraphicFactory.getColor(color))
    }

    final override fun fillColor(color: Int) {
        fillColor(java.awt.Color(color, true))
    }

    final override val dimension: Dimension
        get() {
            return Dimension(width, height)
        }

    fun getGraphicObject(): Graphics2D? {
        return graphics2D
    }

    final override val height: Int
        get() {
            return this.bufferedImage?.height ?: 0
        }

    final override val width: Int
        get() {
            return this.bufferedImage?.width ?: 0
        }

    final override var isAntiAlias: Boolean
        get() {
            return this.graphics.renderingHints.containsValue(RenderingHints.VALUE_ANTIALIAS_ON)
        }
        set(aa) {
            this.graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                if (aa) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF,
            )
            this.graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                if (aa) {
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                } else {
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
                },
            )
            this.graphics.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                if (aa) {
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON
                } else {
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF
                },
            )
        }

    final override var isFilterBitmap: Boolean
        get() {
            return this.graphics.renderingHints
                .containsValue(RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                || this.graphics.renderingHints
                    .containsValue(RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        set(filter) {
            this.graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                if (filter) {
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                } else {
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                },
            )
        }

    final override fun resetClip() {
        this.graphics.setClip(null)
    }

    final override fun restore() {
        // no-op here
    }

    final override fun rotate(degrees: Float, px: Float, py: Float) {
        if (degrees != 0f) {
            this.graphics.rotate(degrees.toDouble(), px.toDouble(), py.toDouble())
        }
    }

    final override fun rotate(rotation: Rotation) {
        if (!Rotation.noRotation(rotation)) {
            rotate(rotation.degrees, rotation.px, rotation.py)
        }
    }

    final override fun save() {
        // no-op here
    }

    final override fun setBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            this.bufferedImage = null
            this.graphics2D = null
        } else {
            val image = AwtGraphicFactory.getBitmap(bitmap)
            this.bufferedImage = image
            this.graphics2D = image.createGraphics()
            isAntiAlias = Parameters.ANTI_ALIASING
            this.graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            this.graphics.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE,
            )
        }
    }

    final override fun setBitmap(
        bitmap: Bitmap?,
        dx: Float,
        dy: Float,
        degrees: Float,
        px: Float,
        py: Float,
    ) {
        translate(dx, dy)
        rotate(degrees, px, py)
        setBitmap(bitmap)
    }

    final override fun setClip(left: Int, top: Int, width: Int, height: Int) {
        setClip(left, top, width, height, false)
    }

    final override fun setClip(left: Int, top: Int, width: Int, height: Int, intersect: Boolean) {
        this.graphics.setClip(left, top, width, height)
    }

    final override fun setClipDifference(left: Float, top: Float, width: Float, height: Float) {
        val clip = Area(Rectangle2D.Double(0.0, 0.0, this.width.toDouble(), this.height.toDouble()))
        clip.subtract(
            Area(
                Rectangle2D.Double(left.toDouble(), top.toDouble(), width.toDouble(), height.toDouble()),
            ),
        )
        this.graphics.setClip(clip)
    }

    final override fun translate(dx: Float, dy: Float) {
        this.graphics.translate(dx.toDouble(), dy.toDouble())
    }

    private fun fillColor(color: java.awt.Color) {
        val originalComposite = this.graphics.composite
        this.graphics.composite = AlphaComposite.getInstance(
            if (color.alpha == 0) AlphaComposite.CLEAR else AlphaComposite.SRC_OVER,
        )
        this.graphics.color = color
        this.graphics.fillRect(0, 0, width, height)
        this.graphics.composite = originalComposite
    }

    fun setColorAndStroke(awtPaint: AwtPaint) {
        this.graphics.color = awtPaint.awtColor
        if (awtPaint.stroke != null) {
            this.graphics.stroke = awtPaint.stroke
        }
    }
}
