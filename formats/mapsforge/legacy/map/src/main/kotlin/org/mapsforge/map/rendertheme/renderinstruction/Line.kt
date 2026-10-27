/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2019 devemux86
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

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import org.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.regex.Pattern

/**
 * Represents a polyline on the map.
 */
open class Line @Throws(IOException::class, XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    level: Int,
    relativePathPrefix: String?,
    resourceProvider: XmlThemeResourceProvider?,
) : RenderInstruction(graphicFactory, displayModel) {

    private var bitmapCreated: Boolean = false
    private var dy: Float = 0f
    private val dyScaled: MutableMap<Byte, Float>
    private val level: Int
    private val relativePathPrefix: String?
    private val resourceProvider: XmlThemeResourceProvider?
    private var scale: Scale = Scale.STROKE
    private var shaderBitmap: Bitmap? = null
    private var src: String? = null
    private val stroke: Paint
    private var strokeDasharray: FloatArray? = null
    private val strokes: MutableMap<Byte, Paint>
    private var strokeWidth: Float = 0f
    private var curve: Curve

    private val mySync = Any()

    init {
        this.level = level
        this.relativePathPrefix = relativePathPrefix
        this.resourceProvider = resourceProvider

        this.stroke = graphicFactory.createPaint()
        this.stroke.setColor(Color.BLACK)
        this.stroke.setStyle(Style.STROKE)
        this.stroke.setStrokeCap(Cap.ROUND)
        this.stroke.setStrokeJoin(Join.ROUND)
        this.strokes = HashMap()
        this.dyScaled = HashMap()
        this.curve = Curve.NO

        extractValues(graphicFactory, displayModel, elementName, pullParser)
    }

    final override fun destroy() {
        // no.op
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun extractValues(
        graphicFactory: GraphicFactory,
        displayModel: DisplayModel,
        elementName: String,
        pullParser: XmlPullParser,
    ) {
        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (SRC == name) {
                this.src = value
            } else if (CAT == name) {
                this.category = value
            } else if (CURVE == name) {
                curve = Curve.fromString(value)
            } else if (DY == name) {
                this.dy = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (SCALE == name) {
                this.scale = scaleFromValue(value)
            } else if (STROKE == name) {
                this.stroke.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (STROKE_DASHARRAY == name) {
                val strokeDasharray = parseFloatArray(name, value)
                this.strokeDasharray = strokeDasharray
                for (f in 0 until strokeDasharray.size) {
                    strokeDasharray[f] = strokeDasharray[f] * displayModel.scaleFactor
                }
                this.stroke.setDashPathEffect(strokeDasharray)
            } else if (STROKE_LINECAP == name) {
                this.stroke.setStrokeCap(Cap.fromString(value))
            } else if (STROKE_LINEJOIN == name) {
                this.stroke.setStrokeJoin(Join.fromString(value))
            } else if (STROKE_WIDTH == name) {
                this.strokeWidth = XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else if (SYMBOL_HEIGHT == name) {
                this.height = XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor
            } else if (SYMBOL_PERCENT == name) {
                this.percent = XmlUtils.parseNonNegativeInteger(name, value)
            } else if (SYMBOL_SCALING == name) {
                // no-op
            } else if (SYMBOL_WIDTH == name) {
                this.width = XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }
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

    @Synchronized
    final override fun renderWay(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        way: PolylineContainer,
    ) {
        synchronized(mySync) {
            if (!bitmapCreated) {
                try {
                    shaderBitmap = createBitmap(relativePathPrefix, src, resourceProvider)
                } catch (ioException: IOException) {
                    // no-op
                }
                bitmapCreated = true
            }

            val strokePaint = getStrokePaint(renderContext.rendererJob.tile.zoomLevel)

            val shaderBitmap = this.shaderBitmap
            if (shaderBitmap != null) {
                strokePaint.setBitmapShader(shaderBitmap)
                strokePaint.setBitmapShaderShift(way.upperLeft.origin)
            }

            val dyScale: Float = this.dyScaled[renderContext.rendererJob.tile.zoomLevel] ?: this.dy
            renderCallback.renderWay(renderContext, strokePaint, dyScale, curve, this.level, way)
        }
    }

    final override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            var factor = scaleFactor
            if (this.scale == Scale.NONE) {
                factor = 1f
            }
            val paint = graphicFactory.createPaint(stroke)
            paint.strokeWidth = this.strokeWidth * factor
            if (this.scale == Scale.ALL) {
                // throws without a stroke-dasharray, as upstream
                val strokeDasharray = this.strokeDasharray!!
                val strokeDasharrayScaled = FloatArray(strokeDasharray.size)
                for (i in 0 until strokeDasharray.size) {
                    strokeDasharrayScaled[i] = strokeDasharray[i] * factor
                }
                paint.setDashPathEffect(strokeDasharrayScaled)
            }
            strokes[zoomLevel] = paint

            this.dyScaled[zoomLevel] = this.dy * factor
        }
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }

    private companion object {

        private val SPLIT_PATTERN: Pattern = Pattern.compile(",")

        @Throws(XmlPullParserException::class)
        private fun parseFloatArray(name: String, dashString: String): FloatArray {
            val dashEntries = SPLIT_PATTERN.split(dashString)
            val dashIntervals = FloatArray(dashEntries.size)
            for (i in 0 until dashEntries.size) {
                dashIntervals[i] = XmlUtils.parseNonNegativeFloat(name, dashEntries[i])
            }
            return dashIntervals
        }
    }
}
