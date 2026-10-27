/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2016 devemux86
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
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.SymbolOrientation
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
 * Represents an icon along a polyline on the map.
 */
open class LineSymbol @Throws(IOException::class, XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    relativePathPrefix: String?,
    resourceProvider: XmlThemeResourceProvider?,
) : RenderInstruction(graphicFactory, displayModel) {

    private var bitmap: Bitmap? = null
    private var bitmapInvalid: Boolean = false
    private var display: Display
    private var dy: Float = 0f
    private val dyScaled: MutableMap<Byte, Float>
    private var position: Position
    private var priority: Int = 0
    private val relativePathPrefix: String?
    private var repeat: Boolean = false
    private var repeatGap: Float = 0f
    private var repeatStart: Float = 0f
    private val resourceProvider: XmlThemeResourceProvider?
    private var scale: Scale = Scale.STROKE
    private var src: String? = null
    private var symbolOrientation: SymbolOrientation

    private val mySync = Any()

    init {
        this.display = Display.IFSPACE
        this.relativePathPrefix = relativePathPrefix
        this.resourceProvider = resourceProvider
        this.dyScaled = HashMap()
        // Probably not a good default, but backwards compatible
        this.position = Position.BELOW_RIGHT
        this.symbolOrientation = SymbolOrientation.LEFT

        extractValues(elementName, pullParser)
    }

    final override fun evictZoom(zoomLevel: Byte) {
        synchronized(mySync) {
            this.dyScaled.remove(zoomLevel)
        }
    }

    final override fun destroy() {
        synchronized(mySync) {
            this.bitmap?.decrementRefCount()
        }
    }

    @Suppress("DEPRECATION")
    @Throws(IOException::class, XmlPullParserException::class)
    private fun extractValues(elementName: String, pullParser: XmlPullParser) {
        this.repeatGap = REPEAT_GAP_DEFAULT * displayModel.scaleFactor
        this.repeatStart = REPEAT_START_DEFAULT * displayModel.scaleFactor

        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (SRC == name) {
                this.src = value
            } else if (ALIGN_CENTER == name) {
                // Deprecated way to say position="center"
                val alignCenter = java.lang.Boolean.parseBoolean(value)
                if (alignCenter) {
                    this.position = Position.CENTER
                }
            } else if (CAT == name) {
                this.category = value
            } else if (DISPLAY == name) {
                this.display = Display.fromString(value)
            } else if (DY == name) {
                this.dy = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (PRIORITY == name) {
                this.priority = Integer.parseInt(value)
            } else if (REPEAT == name) {
                this.repeat = java.lang.Boolean.parseBoolean(value)
            } else if (REPEAT_GAP == name) {
                this.repeatGap = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (REPEAT_START == name) {
                this.repeatStart = java.lang.Float.parseFloat(value) * displayModel.scaleFactor
            } else if (ROTATE == name) {
                if (!java.lang.Boolean.parseBoolean(value)) {
                    this.symbolOrientation = SymbolOrientation.UP
                }
            } else if (SCALE == name) {
                this.scale = scaleFromValue(value)
            } else if (SYMBOL_HEIGHT == name) {
                this.height = XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor
            } else if (SYMBOL_ORIENTATION == name) {
                this.symbolOrientation = SymbolOrientation.fromString(value)
            } else if (SYMBOL_PERCENT == name) {
                this.percent = XmlUtils.parseNonNegativeInteger(name, value)
            } else if (SYMBOL_SCALING == name) {
                // no-op
            } else if (SYMBOL_WIDTH == name) {
                this.width = XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor
            } else if (POSITION == name) {
                this.position = Position.fromString(value)
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }
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
            if (this.bitmap == null && !this.bitmapInvalid) {
                try {
                    this.bitmap = createBitmap(relativePathPrefix, src, resourceProvider)
                } catch (ioException: IOException) {
                    this.bitmapInvalid = true
                }
            }

            val dyScale: Float = this.dyScaled[renderContext.rendererJob.tile.zoomLevel] ?: this.dy

            val bitmap = this.bitmap
            if (bitmap != null) {
                val boundary = computeBoundary(bitmap.width, bitmap.height, this.position)
                renderCallback.renderWaySymbol(
                    renderContext,
                    this.display,
                    this.priority,
                    bitmap,
                    dyScale,
                    boundary,
                    this.repeat,
                    this.repeatGap,
                    this.repeatStart,
                    this.symbolOrientation,
                    way,
                )
            }
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
        // do nothing
    }

    private companion object {

        private const val REPEAT_GAP_DEFAULT = 200f
        private const val REPEAT_START_DEFAULT = 30f
    }
}
