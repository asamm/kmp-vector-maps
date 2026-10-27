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
package org.mapsforge.map.awt.graphics

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.mapelements.PointTextContainer
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import java.text.AttributedString

open class AwtPointTextContainer internal constructor(
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

    @JvmField
    protected val boundary: Rectangle

    @JvmField
    val textHeight: Int

    @JvmField
    val textWidth: Int

    @JvmField
    val boxWidth: Int

    @JvmField
    val boxHeight: Int

    @JvmField
    val isMultiline: Boolean

    @JvmField
    val textBounds: Rectangle

    @JvmField
    val fontPadding: Int

    init {
        val measurePaint: Paint = paintBack ?: paintFront

        this.textBounds = measurePaint.getTextBounds(text)
        this.fontPadding = AwtPaint.getFontPadding(Math.round(this.textBounds.height).toInt())

        this.textWidth = Math.round(this.textBounds.width).toInt()
        this.textHeight = Math.round(this.textBounds.height).toInt()

        this.boxWidth = this.textWidth + 2 * this.fontPadding
        this.boxHeight = this.textHeight + 2 * this.fontPadding

        this.isMultiline = this.boxWidth > this.maxTextWidth

        this.boundary = computeBoundary()
    }

    final override fun draw(canvas: Canvas?, origin: Point?, matrix: Matrix?, rotation: Rotation?) {
        if (this.isNotVisible) {
            return
        }

        val awtCanvas = canvas as AwtCanvas

        // a local, as the field of another module cannot be smart cast
        val paintBack = this.paintBack

        val rotRelPosition = getRotatedRelativePosition(origin!!.x, origin.y, rotation)

        var x = rotRelPosition.x
        var y = rotRelPosition.y

        x += fontPadding
        y += -fontPadding

        if (this.isMultiline) {
            if (DEBUG_CLASH_BOUNDS) {
                drawClashBounds(origin.x, origin.y, rotation, awtCanvas)
            }

            val attrString = AttributedString(this.text)
            val awtPaintFront = AwtGraphicFactory.getPaint(this.paintFront)
            attrString.addAttribute(TextAttribute.FOREGROUND, awtPaintFront.awtColor)
            attrString.addAttribute(TextAttribute.FONT, awtPaintFront.font)
            val paragraph = attrString.iterator
            val paragraphStart = paragraph.beginIndex
            val paragraphEnd = paragraph.endIndex
            val frc = awtCanvas.getGraphicObject()!!.fontRenderContext
            val lineMeasurer = LineBreakMeasurer(paragraph, frc)

            var layoutHeight = 0f
            lineMeasurer.position = paragraphStart
            while (lineMeasurer.position < paragraphEnd) {
                val layout = lineMeasurer.nextLayout(maxTextWidth.toFloat())
                layoutHeight += layout.ascent + layout.descent + layout.leading
            }

            x += boundary.width / 2

            // Because the origin of our text box is on top
            y += boundary.height / 2

            var drawPosY = y.toFloat()
            lineMeasurer.position = paragraphStart
            while (lineMeasurer.position < paragraphEnd) {
                val layout = lineMeasurer.nextLayout(maxTextWidth.toFloat())
                var posX = x.toFloat()
                var posY = drawPosY
                when (this.position) {
                    Position.CENTER -> {
                        posX += -layout.advance * 0.5f
                        posY += layout.ascent - layoutHeight * 0.5f
                    }

                    Position.BELOW -> {
                        posX += -layout.advance * 0.5f
                        posY += (layout.ascent + layout.descent + layout.leading) * 0.5f
                    }

                    Position.BELOW_LEFT -> {
                        posX += -layout.advance
                        posY += (layout.ascent + layout.descent + layout.leading) * 0.5f
                    }

                    Position.BELOW_RIGHT -> {
                        posY += (layout.ascent + layout.descent + layout.leading) * 0.5f
                    }

                    Position.ABOVE -> {
                        posX += -layout.advance * 0.5f
                        posY += layout.ascent + layout.descent + layout.leading - layoutHeight
                    }

                    Position.ABOVE_LEFT -> {
                        posX += -layout.advance
                        posY += layout.ascent + layout.descent + layout.leading - layoutHeight
                    }

                    Position.ABOVE_RIGHT -> {
                        posY += layout.ascent + layout.descent + layout.leading - layoutHeight
                    }

                    Position.LEFT -> {
                        posX += -layout.advance
                        posY += layout.ascent - layoutHeight * 0.5f
                    }

                    Position.RIGHT -> {
                        posY += layout.ascent - layoutHeight * 0.5f
                    }

                    else -> {
                        throw IllegalArgumentException("No position for drawing PointTextContainer")
                    }
                }
                if (paintBack != null) {
                    awtCanvas.setColorAndStroke(AwtGraphicFactory.getPaint(paintBack))
                    val affineTransform = AffineTransform()
                    affineTransform.translate(posX.toDouble(), posY.toDouble())
                    awtCanvas.getGraphicObject()!!.draw(layout.getOutline(affineTransform))
                }
                layout.draw(awtCanvas.getGraphicObject(), posX, posY)
                drawPosY += layout.ascent + layout.descent + layout.leading
            }
        } else {
            if (DEBUG_CLASH_BOUNDS) {
                drawClashBounds(origin.x, origin.y, rotation, awtCanvas)
            }

            x += -textBounds.left

            // Because the origin of our text box is on top
            y += boundary.height
            // Because the origin of our text is the baseline
            y += -textBounds.bottom

            if (paintBack != null) {
                canvas.drawText(this.text, Math.round(x).toInt(), Math.round(y).toInt(), paintBack)
            }
            canvas.drawText(this.text, Math.round(x).toInt(), Math.round(y).toInt(), this.paintFront)
        }
    }

    final override fun getBoundary(): Rectangle {
        return boundary
    }

    private fun computeBoundary(): Rectangle {
        val lines = this.textWidth / this.maxTextWidth + 1
        var boxWidth = this.boxWidth.toDouble()
        var boxHeight = this.boxHeight.toDouble()

        if (lines > 1) {
            // a crude approximation of the size of the text box
            boxWidth = this.maxTextWidth.toDouble()
            boxHeight = (this.textHeight * lines + 2 * fontPadding).toDouble()
        }

        return when (this.position) {
            Position.BELOW -> Rectangle(-boxWidth / 2, 0.0, boxWidth / 2, boxHeight)
            Position.BELOW_LEFT -> Rectangle(-boxWidth, 0.0, 0.0, boxHeight)
            Position.BELOW_RIGHT -> Rectangle(0.0, 0.0, boxWidth, boxHeight)
            Position.ABOVE -> Rectangle(-boxWidth / 2, -boxHeight, boxWidth / 2, 0.0)
            Position.ABOVE_LEFT -> Rectangle(-boxWidth, -boxHeight, 0.0, 0.0)
            Position.ABOVE_RIGHT -> Rectangle(0.0, -boxHeight, boxWidth, 0.0)
            Position.LEFT -> Rectangle(-boxWidth, -boxHeight / 2, 0.0, boxHeight / 2)
            Position.RIGHT -> Rectangle(0.0, -boxHeight / 2, boxWidth, boxHeight / 2)
            // CENTER, and the default for any other position
            else -> Rectangle(-boxWidth / 2, -boxHeight / 2, boxWidth / 2, boxHeight / 2)
        }
    }

    internal fun drawClashBounds(
        originX: Double,
        originY: Double,
        rotation: Rotation?,
        awtCanvas: AwtCanvas,
    ) {
        val transformed: Rectangle? = getClashRectangleTransformed(this, originX, originY, rotation)

        if (transformed != null) {
            awtCanvas
                .getGraphicObject()!!
                .drawRect(
                    Math.round(transformed.left).toInt(),
                    Math.round(transformed.top).toInt(),
                    Math.round(transformed.width).toInt(),
                    Math.round(transformed.height).toInt(),
                )
        }
    }
}
