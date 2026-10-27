/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2016 Ludwig M Brinckmann
 * Copyright 2016-2019 devemux86
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

import org.mapsforge.core.graphics.Align
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.FontFamily
import org.mapsforge.core.graphics.FontStyle
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.graphics.TextTransform
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Represents a text along a polyline on the map.
 */
open class PathText @Throws(XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
) : RenderInstruction(graphicFactory, displayModel) {

    private var display: Display
    private var dy: Float = 0f
    private val dyScaled: MutableMap<Byte, Float>
    private val fill: Paint
    private val fills: MutableMap<Byte, Paint>
    private var fontSize: Float = 0f
    private var priority: Int = 0
    private var scale: Scale = Scale.STROKE
    private val stroke: Paint
    private val strokes: MutableMap<Byte, Paint>
    private var repeat: Boolean
    private var repeatGap: Float = 0f
    private var repeatStart: Float = 0f
    private var textKey: TextKey? = null
    private var textTransform: TextTransform
    private var textOrientation: TextOrientation

    private val mySync = Any()

    init {
        this.fill = createPaint()
        this.fill.setColor(Color.BLACK)
        this.fill.setStyle(Style.FILL)
        this.fill.setTextAlign(Align.CENTER)
        this.fills = HashMap()
        this.repeat = true

        this.stroke = createPaint()
        this.stroke.setColor(Color.BLACK)
        this.stroke.setStyle(Style.STROKE)
        this.stroke.setTextAlign(Align.CENTER)
        this.strokes = HashMap()
        this.dyScaled = HashMap()
        this.display = Display.IFSPACE
        this.textTransform = TextTransform.NONE
        this.textOrientation = TextOrientation.AUTO

        extractValues(graphicFactory, displayModel, elementName, pullParser)
    }

    final override fun evictZoom(zoomLevel: Byte) {
        synchronized(mySync) {
            this.fills.remove(zoomLevel)
            this.strokes.remove(zoomLevel)
            this.dyScaled.remove(zoomLevel)
        }
    }

    final override fun destroy() {
        // no-op
    }

    @Suppress("DEPRECATION")
    @Throws(XmlPullParserException::class)
    private fun extractValues(
        graphicFactory: GraphicFactory,
        displayModel: DisplayModel,
        elementName: String,
        pullParser: XmlPullParser,
    ) {
        this.repeatGap = REPEAT_GAP_DEFAULT * displayModel.scaleFactor
        this.repeatStart = REPEAT_START_DEFAULT * displayModel.scaleFactor

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
            } else if (REPEAT == name) {
                this.repeat = java.lang.Boolean.parseBoolean(value)
            } else if (REPEAT_GAP == name) {
                this.repeatGap = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (REPEAT_START == name) {
                this.repeatStart = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (PRIORITY == name) {
                this.priority = Integer.parseInt(value)
            } else if (ROTATE == name) {
                if (!java.lang.Boolean.parseBoolean(value)) {
                    this.textOrientation = TextOrientation.RIGHT
                }
            } else if (SCALE == name) {
                this.scale = scaleFromValue(value)
            } else if (STROKE == name) {
                this.stroke.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (STROKE_WIDTH == name) {
                this.stroke.strokeWidth =
                    XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else if (TEXT_ORIENTATION == name) {
                this.textOrientation = TextOrientation.fromString(value)
            } else if (TEXT_TRANSFORM == name) {
                this.textTransform = TextTransform.fromString(value)
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
        // do nothing
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

            val dyScale: Float = this.dyScaled[renderContext.rendererJob.tile.zoomLevel] ?: this.dy

            renderCallback.renderWayText(
                renderContext,
                this.display,
                this.priority,
                transformText(caption, textTransform),
                dyScale,
                getFillPaint(renderContext.rendererJob.tile.zoomLevel),
                getStrokePaint(renderContext.rendererJob.tile.zoomLevel),
                this.repeat,
                this.repeatGap,
                this.repeatStart,
                this.textOrientation,
                way,
            )
        }
    }

    final override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            var factor = scaleFactor
            if (this.scale == Scale.NONE) {
                factor = 1f
            }
            this.dyScaled[zoomLevel] = this.dy * factor
        }
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            val zlPaint = graphicFactory.createPaint(this.fill)
            zlPaint.setTextSize(this.fontSize * scaleFactor)
            this.fills[zoomLevel] = zlPaint

            val zlStroke = graphicFactory.createPaint(this.stroke)
            zlStroke.setTextSize(this.fontSize * scaleFactor)
            this.strokes[zoomLevel] = zlStroke
        }
    }

    private companion object {

        private const val REPEAT_GAP_DEFAULT = 100f
        private const val REPEAT_START_DEFAULT = 10f
    }
}
