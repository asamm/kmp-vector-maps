/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2016-2020 devemux86
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
import org.mapsforge.core.model.Rectangle
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
 * Represents an icon on the map.
 */
open class Symbol @Throws(IOException::class, XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    relativePathPrefix: String?,
    resourceProvider: XmlThemeResourceProvider?,
) : RenderInstruction(graphicFactory, displayModel) {

    private var bitmap: Bitmap? = null
    private var bitmapInvalid: Boolean = false
    val boundary: Rectangle?
    private var display: Display
    var id: String? = null
        private set
    private var position: Position
    private var priority: Int = 0
    private val relativePathPrefix: String?
    private val resourceProvider: XmlThemeResourceProvider?
    private var src: String? = null

    private val mySync = Any()

    init {
        this.relativePathPrefix = relativePathPrefix
        this.resourceProvider = resourceProvider
        this.display = Display.IFSPACE
        this.position = Position.CENTER
        extractValues(elementName, pullParser)

        val bitmap = getBitmap()
        if (bitmap != null) {
            this.boundary = computeBoundary(bitmap.width, bitmap.height, this.position)
        } else {
            this.boundary = null
        }
    }

    final override fun destroy() {
        synchronized(mySync) {
            this.bitmap?.decrementRefCount()
        }
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
            } else if (DISPLAY == name) {
                this.display = Display.fromString(value)
            } else if (ID == name) {
                this.id = value
            } else if (PRIORITY == name) {
                this.priority = Integer.parseInt(value)
            } else if (SYMBOL_HEIGHT == name) {
                this.height = XmlUtils.parseNonNegativeInteger(name, value) * displayModel.scaleFactor
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

    /**
     * The symbol bitmap, created on first use; null without a source, when it failed to load or
     * when the theme callback dropped it.
     */
    fun getBitmap(): Bitmap? {
        synchronized(mySync) {
            if (this.bitmap == null && !bitmapInvalid) {
                try {
                    this.bitmap = createBitmap(relativePathPrefix, src, resourceProvider)
                } catch (ioException: IOException) {
                    this.bitmapInvalid = true
                }
            }
            return this.bitmap
        }
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
            val bitmap = getBitmap()
            if (bitmap != null) {
                renderCallback.renderPointOfInterestSymbol(
                    renderContext,
                    this.display,
                    this.priority,
                    this.boundary,
                    bitmap,
                    poi,
                )
            }
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
            val bitmap = this.getBitmap()
            if (bitmap != null) {
                renderCallback.renderAreaSymbol(renderContext, this.display, this.priority, bitmap, way)
            }
        }
    }

    final override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }
}
