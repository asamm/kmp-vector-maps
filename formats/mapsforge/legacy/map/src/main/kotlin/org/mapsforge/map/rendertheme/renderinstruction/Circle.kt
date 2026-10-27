/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
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

import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Represents a round area on the map.
 */
open class Circle @Throws(XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
    level: Int,
) : RenderInstruction(graphicFactory, displayModel) {

    private val fill: Paint
    private val fills: MutableMap<Byte, Paint>
    private val level: Int
    private var radius: Float = 0f
    private var renderRadius: Float = 0f
    private val renderRadiusScaled: MutableMap<Byte, Float>
    private var scaleRadius: Boolean = false
    private val stroke: Paint
    private val strokes: MutableMap<Byte, Paint>
    private var strokeWidth: Float = 0f

    private val mySync = Any()

    init {
        this.level = level

        this.fill = graphicFactory.createPaint()
        this.fill.setColor(Color.TRANSPARENT)
        this.fill.setStyle(Style.FILL)
        this.fills = HashMap()

        this.stroke = graphicFactory.createPaint()
        this.stroke.setColor(Color.TRANSPARENT)
        this.stroke.setStyle(Style.STROKE)
        this.strokes = HashMap()
        this.renderRadiusScaled = HashMap()

        extractValues(graphicFactory, displayModel, elementName, pullParser)

        if (!this.scaleRadius) {
            this.renderRadius = this.radius
            this.stroke.strokeWidth = this.strokeWidth
        }
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
        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (RADIUS == name || R == name) {
                this.radius = XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else if (CAT == name) {
                this.category = value
            } else if (FILL == name) {
                this.fill.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (SCALE_RADIUS == name) {
                this.scaleRadius = java.lang.Boolean.parseBoolean(value)
            } else if (STROKE == name) {
                this.stroke.color = XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, this)
            } else if (STROKE_WIDTH == name) {
                this.strokeWidth = XmlUtils.parseNonNegativeFloat(name, value) * displayModel.scaleFactor
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }

        XmlUtils.checkMandatoryAttribute(elementName, RADIUS, this.radius)
    }

    private fun getFillPaint(zoomLevel: Byte): Paint {
        var paint = fills[zoomLevel]
        if (paint == null) {
            paint = this.fill
        }
        return paint
    }

    private fun getRenderRadius(zoomLevel: Byte): Float {
        var radius = renderRadiusScaled[zoomLevel]
        if (radius == null) {
            radius = this.renderRadius
        }
        return radius
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
        synchronized(mySync) {
            renderCallback.renderPointOfInterestCircle(
                renderContext,
                getRenderRadius(renderContext.rendererJob.tile.zoomLevel),
                getFillPaint(renderContext.rendererJob.tile.zoomLevel),
                getStrokePaint(renderContext.rendererJob.tile.zoomLevel),
                this.level,
                poi,
            )
        }
    }

    final override fun renderWay(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        way: PolylineContainer,
    ) {
        // do nothing
    }

    final override fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            if (this.scaleRadius) {
                this.renderRadiusScaled[zoomLevel] = this.radius * scaleFactor
                val paint = graphicFactory.createPaint(stroke)
                paint.strokeWidth = this.strokeWidth * scaleFactor
                strokes[zoomLevel] = paint
            }
        }
    }

    final override fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        // do nothing
    }
}
