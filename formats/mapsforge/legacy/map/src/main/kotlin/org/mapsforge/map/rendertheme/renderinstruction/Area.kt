/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2020 devemux86
 * Copyright 2020 Adrian Batzill
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
import org.mapsforge.core.graphics.GraphicFactory
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

/**
 * Represents a closed polygon on the map.
 */
open class Area @Throws(IOException::class, XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    level: Int,
    relativePathPrefix: String?,
    resourceProvider: XmlThemeResourceProvider?,
) : RenderInstruction(graphicFactory, displayModel) {

    private var bitmapInvalid: Boolean = false
    private val fill: Paint
    private val level: Int
    private val relativePathPrefix: String?
    private val resourceProvider: XmlThemeResourceProvider?
    private var scale: Scale = Scale.STROKE
    private var shaderBitmap: Bitmap? = null
    private var src: String? = null
    private val stroke: Paint
    private val strokes: MutableMap<Byte, Paint>
    private var strokeWidth: Float = 0f

    private val mySync = Any()

    init {
        this.level = level
        this.relativePathPrefix = relativePathPrefix
        this.resourceProvider = resourceProvider

        this.fill = createPaint()
        this.fill.setColor(Color.TRANSPARENT)
        this.fill.setStyle(Style.FILL)
        this.fill.setStrokeCap(Cap.ROUND)

        this.stroke = createPaint()
        this.stroke.setColor(Color.TRANSPARENT)
        this.stroke.setStyle(Style.STROKE)
        this.stroke.setStrokeCap(Cap.ROUND)

        this.strokes = HashMap()

        extractValues(elementName, pullParser)
    }

    final override fun evictZoom(zoomLevel: Byte) {
        synchronized(mySync) {
            this.strokes.remove(zoomLevel)
        }
    }

    final override fun destroy() {
        // no-op
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun extractValues(elementName: String, pullParser: XmlPullParser) {
        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (SRC == name) {
                this.src = value
            } else if (CAT == name) {
                this.category = value
            } else if (FILL == name) {
                this.fill.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (SCALE == name) {
                this.scale = scaleFromValue(value)
            } else if (STROKE == name) {
                this.stroke.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
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

    private fun getFillPaint(): Paint {
        return this.fill
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
        synchronized(mySync) {
            // this needs to be synchronized as we potentially set a shift in the shader and
            // the shift is particular to the tile when rendered in multi-thread mode
            val fillPaint = getFillPaint()
            if (shaderBitmap == null && !bitmapInvalid) {
                try {
                    val bitmap = createBitmap(relativePathPrefix, src, resourceProvider)
                    shaderBitmap = bitmap
                    if (bitmap != null) {
                        fillPaint.setBitmapShader(bitmap)
                        bitmap.decrementRefCount()
                    }
                } catch (ioException: IOException) {
                    bitmapInvalid = true
                }
            }

            if (renderContext.rendererJob.displayModel.numberOfThreads == 1) {
                fillPaint.setBitmapShaderShift(way.upperLeft.origin)
            }

            val themeCallback = displayModel.themeCallback
            if (themeCallback != null) {
                fillPaint.color = themeCallback.getColor(way, fillPaint.color)
                stroke.color = themeCallback.getColor(way, stroke.color)
                for (strokePaint in strokes.values) {
                    strokePaint.color = themeCallback.getColor(way, strokePaint.color)
                }
            }
            renderCallback.renderArea(
                renderContext,
                fillPaint,
                getStrokePaint(renderContext.rendererJob.tile.zoomLevel),
                this.level,
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
            val paint = graphicFactory.createPaint(this.stroke)
            paint.strokeWidth = this.strokeWidth * factor
            this.strokes[zoomLevel] = paint
        }
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }
}
