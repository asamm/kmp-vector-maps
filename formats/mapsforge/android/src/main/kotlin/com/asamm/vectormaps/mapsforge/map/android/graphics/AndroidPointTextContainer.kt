/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2016 devemux86
 * Copyright 2024 Sublimis
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
package com.asamm.vectormaps.mapsforge.map.android.graphics

import android.graphics.RectF
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.asamm.vectormaps.mapsforge.core.graphics.Canvas
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.Matrix
import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.core.graphics.Position
import com.asamm.vectormaps.mapsforge.core.mapelements.PointTextContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.SymbolContainer
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.core.model.Rotation

open class AndroidPointTextContainer internal constructor(
    xy: Point,
    horizontalOffset: Double,
    verticalOffset: Double,
    display: Display,
    priority: Int,
    text: String,
    paintFront: Paint,
    paintBack: Paint?,
    symbolContainer: SymbolContainer?,
    position: Position,
    maxTextWidth: Int,
) : PointTextContainer(
    xy,
    horizontalOffset,
    verticalOffset,
    display,
    priority,
    text,
    paintFront,
    paintBack,
    symbolContainer,
    position,
    maxTextWidth,
) {

    private val boundary: Rectangle

    private val backLayout: StaticLayout?

    private val frontLayout: StaticLayout?

    val textHeight: Int

    val textWidth: Int

    val isMultiline: Boolean

    val textBounds: Rectangle

    val fontPadding: Int

    protected val debugClashBoundsPaint: android.graphics.Paint?

    init {
        val measurePaint: Paint = paintBack ?: paintFront

        this.textBounds = measurePaint.getTextBounds(text)
        this.fontPadding = AndroidPaint.getFontPadding(Math.round(this.textBounds.height).toInt())

        val myTextWidth = Math.round(this.textBounds.width).toInt() + 2 * this.fontPadding

        val boxWidth: Float
        val boxHeight: Float
        if (myTextWidth > this.maxTextWidth) {
            // if the text is too wide its layout is done by the Android StaticLayout class,
            // which automatically inserts line breaks. There is not a whole lot of useful
            // documentation of this class.
            // For below and above placements the text is center-aligned, for left on the right
            // and for right on the left.
            // One disadvantage is that it will always keep the text within the maxWidth,
            // even if that means breaking text mid-word.

            this.isMultiline = true

            val frontTextPaint = TextPaint(AndroidGraphicFactory.getPaint(this.paintFront))
            var backTextPaint: TextPaint? = null
            if (paintBack != null) {
                backTextPaint = TextPaint(AndroidGraphicFactory.getPaint(paintBack))
            }

            // strange Android behaviour: if alignment is set to center, then
            // text is rendered with right alignment if using StaticLayout
            frontTextPaint.textAlign = android.graphics.Paint.Align.LEFT
            if (backTextPaint != null) {
                backTextPaint.textAlign = android.graphics.Paint.Align.LEFT
            }

            val front = createTextLayout(frontTextPaint)
            this.frontLayout = front
            val back = if (backTextPaint != null) {
                createTextLayout(backTextPaint)
            } else {
                null
            }
            this.backLayout = back

            this.textWidth = AndroidPaint.getTextWidth(back ?: front)
            this.textHeight = (back ?: front).height

            boxWidth = (this.textWidth + 2 * this.fontPadding).toFloat()
            boxHeight = (this.textHeight + 2 * this.fontPadding).toFloat()
        } else {
            this.isMultiline = false
            this.frontLayout = null
            this.backLayout = null

            this.textWidth = myTextWidth - 2 * this.fontPadding
            this.textHeight = Math.round(this.textBounds.height).toInt()

            boxWidth = myTextWidth.toFloat()
            boxHeight = (this.textHeight + 2 * this.fontPadding).toFloat()
        }

        this.boundary = when (this.position) {
            Position.BELOW -> Rectangle(
                (-boxWidth / 2f).toDouble(),
                0.0,
                (boxWidth / 2f).toDouble(),
                boxHeight.toDouble(),
            )
            Position.BELOW_LEFT -> Rectangle(
                (-boxWidth).toDouble(),
                0.0,
                0.0,
                boxHeight.toDouble(),
            )
            Position.BELOW_RIGHT -> Rectangle(
                0.0,
                0.0,
                boxWidth.toDouble(),
                boxHeight.toDouble(),
            )
            Position.ABOVE -> Rectangle(
                (-boxWidth / 2f).toDouble(),
                (-boxHeight).toDouble(),
                (boxWidth / 2f).toDouble(),
                0.0,
            )
            Position.ABOVE_LEFT -> Rectangle(
                (-boxWidth).toDouble(),
                (-boxHeight).toDouble(),
                0.0,
                0.0,
            )
            Position.ABOVE_RIGHT -> Rectangle(
                0.0,
                (-boxHeight).toDouble(),
                boxWidth.toDouble(),
                0.0,
            )
            Position.LEFT -> Rectangle(
                (-boxWidth).toDouble(),
                (-boxHeight / 2f).toDouble(),
                0.0,
                (boxHeight / 2f).toDouble(),
            )
            Position.RIGHT -> Rectangle(
                0.0,
                (-boxHeight / 2f).toDouble(),
                boxWidth.toDouble(),
                (boxHeight / 2f).toDouble(),
            )
            // CENTER and any other
            else -> Rectangle(
                (-boxWidth / 2f).toDouble(),
                (-boxHeight / 2f).toDouble(),
                (boxWidth / 2f).toDouble(),
                (boxHeight / 2f).toDouble(),
            )
        }

        if (DEBUG_CLASH_BOUNDS) {
            val debugPaint = AndroidGraphicFactory.getPaint(
                AndroidGraphicFactory.INSTANCE.createPaint(measurePaint),
            )
            debugPaint.color = android.graphics.Color.BLACK
            this.debugClashBoundsPaint = debugPaint
        } else {
            this.debugClashBoundsPaint = null
        }
    }

    @Suppress("DEPRECATION")
    private fun createTextLayout(paint: TextPaint): StaticLayout {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API29+, A10+
            return StaticLayout.Builder
                .obtain(this.text, 0, this.text.length, paint, this.maxTextWidth)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true)
                .build()
        } else {
            return StaticLayout(
                this.text,
                paint,
                this.maxTextWidth,
                Layout.Alignment.ALIGN_CENTER,
                1f,
                0f,
                true,
            )
        }
    }

    override fun getBoundary(): Rectangle {
        return boundary
    }

    final override fun draw(canvas: Canvas?, origin: Point?, matrix: Matrix?, rotation: Rotation?) {
        if (this.isNotVisible) {
            return
        }

        val androidCanvas = AndroidGraphicFactory.getCanvas(canvas!!)!!

        val rotRelPosition = getRotatedRelativePosition(origin!!.x, origin.y, rotation)

        var x = rotRelPosition.x
        var y = rotRelPosition.y

        if (isMultiline) {
            // in this case we draw the precomputed staticLayout onto the canvas by translating
            // the canvas.

            androidCanvas.save()

            if (!Rotation.noRotation(rotation)) {
                androidCanvas.rotate(-rotation!!.degrees, rotation.px, rotation.py)
            }

            if (DEBUG_CLASH_BOUNDS) {
                drawClashBounds(origin.x, origin.y, rotation, androidCanvas)
            }

            x -= (this.maxTextWidth - boundary.width) / 2f

            y += fontPadding

            androidCanvas.translate(x.toFloat(), y.toFloat())

            this.backLayout?.draw(androidCanvas)
            this.frontLayout!!.draw(androidCanvas)

            androidCanvas.restore()
        } else {
            if (!Rotation.noRotation(rotation)) {
                androidCanvas.save()
                androidCanvas.rotate(-rotation!!.degrees, rotation.px, rotation.py)
            }

            if (DEBUG_CLASH_BOUNDS) {
                drawClashBounds(origin.x, origin.y, rotation, androidCanvas)
            }

            x += fontPadding - textBounds.left

            y += boundary.height
            y += -fontPadding - textBounds.bottom

            // a local, as the field of another module cannot be smart cast
            val paintBack = this.paintBack
            if (paintBack != null) {
                androidCanvas.drawText(
                    this.text,
                    x.toFloat(),
                    y.toFloat(),
                    AndroidGraphicFactory.getPaint(paintBack),
                )
            }

            androidCanvas.drawText(
                this.text,
                x.toFloat(),
                y.toFloat(),
                AndroidGraphicFactory.getPaint(this.paintFront),
            )

            if (!Rotation.noRotation(rotation)) {
                androidCanvas.restore()
            }
        }
    }

    protected open fun drawClashBounds(
        originX: Double,
        originY: Double,
        rotation: Rotation?,
        androidCanvas: android.graphics.Canvas,
    ) {
        val transformed: Rectangle? =
            getClashRectangleTransformed(this, originX, originY, rotation)

        if (transformed != null) {
            androidCanvas.drawRect(
                RectF(
                    transformed.left.toFloat(),
                    transformed.top.toFloat(),
                    transformed.right.toFloat(),
                    transformed.bottom.toFloat(),
                ),
                debugClashBoundsPaint!!,
            )
        }
    }
}
