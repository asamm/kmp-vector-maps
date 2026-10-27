/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2019 devemux86
 * Copyright 2020 Adrian Batzill
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
package org.mapsforge.map.rendertheme.renderinstruction

import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.FontFamily
import org.mapsforge.core.graphics.FontStyle
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.graphics.TextTransform
import org.mapsforge.core.model.Rectangle
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Represents a text label on the map.
 *
 * If a bitmap symbol is present the caption position is calculated relative to the bitmap, the
 * center of which is at the point of the POI. The bitmap itself is never rendered.
 */
open class Caption @Throws(XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    symbols: Map<String, Symbol>,
) : RenderInstruction(graphicFactory, displayModel) {

    private var boundary: Rectangle? = null
    private var display: Display
    private var dy: Float = 0f
    private val dyScaled: MutableMap<Byte, Float>
    private val fill: Paint
    private val fills: MutableMap<Byte, Paint>
    private var fontSize: Float = 0f
    private val gap: Float
    private var maxTextWidth: Int
    private lateinit var position: Position
    private var priority: Int = 0
    private val stroke: Paint
    private val strokes: MutableMap<Byte, Paint>
    private var symbolId: String? = null
    private var textKey: TextKey? = null
    private var textTransform: TextTransform

    private val mySync = Any()

    init {
        this.fill = graphicFactory.createPaint()
        this.fill.setColor(Color.BLACK)
        this.fill.setStyle(Style.FILL)
        this.fills = HashMap()

        this.stroke = graphicFactory.createPaint()
        this.stroke.setColor(Color.BLACK)
        this.stroke.setStyle(Style.STROKE)
        this.strokes = HashMap()
        this.dyScaled = HashMap()

        this.display = Display.IFSPACE

        this.gap = DEFAULT_GAP * displayModel.scaleFactor
        this.textTransform = TextTransform.NONE
        this.maxTextWidth = displayModel.maxTextWidth

        extractValues(graphicFactory, displayModel, elementName, pullParser)

        val symbolId = this.symbolId
        if (symbolId != null) {
            val symbol = symbols[symbolId]
            if (symbol != null) {
                this.boundary = symbol.boundary
            }
        }

        if (!this::position.isInitialized) {
            // sensible defaults: below if symbolContainer is present, center if not
            if (this.boundary == null) {
                this.position = Position.CENTER
            } else {
                this.position = Position.BELOW
            }
        }
    }

    private fun computeHorizontalOffset(): Float {
        val boundary = this.boundary!!
        // compute only the offset required by the bitmap, not the text size,
        // because at this point we do not know the text boxing
        if (this.position.isOnRightSide) {
            return (boundary.right + this.gap).toFloat()
        } else if (this.position.isOnLeftSide) {
            return (boundary.left - this.gap).toFloat()
        }
        return ((boundary.right + boundary.left) / 2).toFloat()
    }

    private fun computeVerticalOffset(zoomLevel: Byte): Float {
        val boundary = this.boundary!!
        var verticalOffset: Float = this.dyScaled[zoomLevel]!!

        if (this.position.isOnUpperSide) {
            verticalOffset = (verticalOffset + (boundary.top - this.gap)).toFloat()
        } else if (this.position.isOnLowerSide) {
            verticalOffset = (verticalOffset + (boundary.bottom + this.gap)).toFloat()
        } else {
            verticalOffset = (verticalOffset + (boundary.top + boundary.bottom) / 2).toFloat()
        }
        return verticalOffset
    }

    final override fun destroy() {
        // no-op
    }

    @Throws(XmlPullParserException::class)
    private fun extractValues(
        graphicFactory: GraphicFactory,
        displayModel: DisplayModel,
        elementName: String,
        pullParser: XmlPullParser,
    ) {
        var fontFamily = FontFamily.DEFAULT
        var fontStyle = FontStyle.NORMAL

        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (K == name) {
                this.textKey = TextKey.getInstance(value)
            } else if (CAT == name) {
                this.category = value
            } else if (DISPLAY == name) {
                this.display = Display.fromString(value)
            } else if (DY == name) {
                this.dy = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (FILL == name) {
                this.fill.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (FONT_FAMILY == name) {
                fontFamily = FontFamily.fromString(value)
            } else if (FONT_SIZE == name) {
                this.fontSize = XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else if (FONT_STYLE == name) {
                fontStyle = FontStyle.fromString(value)
            } else if (POSITION == name) {
                this.position = Position.fromString(value)
            } else if (PRIORITY == name) {
                this.priority = Integer.parseInt(value)
            } else if (STROKE == name) {
                this.stroke.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (STROKE_WIDTH == name) {
                this.stroke.strokeWidth =
                    XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else if (SYMBOL_ID == name) {
                this.symbolId = value
            } else if (TEXT_TRANSFORM == name) {
                this.textTransform = TextTransform.fromString(value)
            } else if (TEXT_WRAP_WIDTH == name) {
                val maxWidth = (XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor).toInt()
                if (maxWidth == 0) {
                    maxTextWidth = Int.MAX_VALUE
                } else {
                    maxTextWidth = maxWidth
                }
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }

        this.fill.setTypeface(fontFamily, fontStyle)
        this.stroke.setTypeface(fontFamily, fontStyle)

        XmlUtils.checkMandatoryAttribute(elementName, K, this.textKey)
    }

    private fun getFillPaint(zoomLevel: Byte): Paint {
        var paint = fills[zoomLevel]
        if (paint == null) {
            paint = this.fill
        }
        return paint
    }

    private fun getStrokePaint(zoomLevel: Byte): Paint {
        var paint = strokes[zoomLevel]
        if (paint == null) {
            paint = this.stroke
        }
        return paint
    }

    final override fun renderNode(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        poi: PointOfInterest,
    ) {
        if (Display.NEVER == this.display) {
            return
        }

        synchronized(mySync) {
            var caption = this.textKey!!.getValue(poi.tags)
            if (caption == null) {
                return
            }
            val themeCallback = displayModel.themeCallback
            if (themeCallback != null) {
                caption = themeCallback.getText(poi, caption)
            }

            var horizontalOffset = 0f

            var verticalOffset: Float = this.dyScaled[renderContext.rendererJob.tile.zoomLevel] ?: this.dy

            if (this.boundary != null) {
                horizontalOffset = computeHorizontalOffset()
                verticalOffset = computeVerticalOffset(renderContext.rendererJob.tile.zoomLevel)
            }

            renderCallback.renderPointOfInterestCaption(
                renderContext,
                this.display,
                this.priority,
                transformText(caption, textTransform),
                horizontalOffset,
                verticalOffset,
                getFillPaint(renderContext.rendererJob.tile.zoomLevel),
                getStrokePaint(renderContext.rendererJob.tile.zoomLevel),
                this.position,
                this.maxTextWidth,
                poi,
            )
        }
    }

    final override fun renderWay(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        way: PolylineContainer,
    ) {
        if (Display.NEVER == this.display) {
            return
        }

        synchronized(mySync) {
            var caption = this.textKey!!.getValue(way.tags)
            if (caption == null) {
                return
            }
            val themeCallback = displayModel.themeCallback
            if (themeCallback != null) {
                caption = themeCallback.getText(way, caption)
            }

            var horizontalOffset = 0f
            var verticalOffset: Float = this.dyScaled[renderContext.rendererJob.tile.zoomLevel] ?: this.dy

            if (this.boundary != null) {
                horizontalOffset = computeHorizontalOffset()
                verticalOffset = computeVerticalOffset(renderContext.rendererJob.tile.zoomLevel)
            }

            renderCallback.renderAreaCaption(
                renderContext,
                this.display,
                this.priority,
                transformText(caption, textTransform),
                horizontalOffset,
                verticalOffset,
                getFillPaint(renderContext.rendererJob.tile.zoomLevel),
                getStrokePaint(renderContext.rendererJob.tile.zoomLevel),
                this.position,
                this.maxTextWidth,
                way,
            )
        }
    }

    final override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            val f = graphicFactory.createPaint(this.fill)
            f.setTextSize(this.fontSize * scaleFactor)
            this.fills[zoomLevel] = f

            val s = graphicFactory.createPaint(this.stroke)
            s.setTextSize(this.fontSize * scaleFactor)
            this.strokes[zoomLevel] = s

            this.dyScaled[zoomLevel] = this.dy * scaleFactor
        }
    }

    companion object {

        const val DEFAULT_GAP: Float = 5f
    }
}
