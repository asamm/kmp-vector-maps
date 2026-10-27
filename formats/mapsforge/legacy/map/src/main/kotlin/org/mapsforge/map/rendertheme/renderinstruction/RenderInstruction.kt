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
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.TextTransform
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import org.mapsforge.map.rendertheme.XmlUtils
import java.io.IOException
import java.util.Locale

/**
 * A RenderInstruction is a basic graphical primitive to draw a map.
 */
abstract class RenderInstruction protected constructor(
    graphicFactory: GraphicFactory,
    displayModel: DisplayModel,
) {

    internal enum class Scale {
        ALL,
        NONE,
        STROKE,
    }

    var category: String? = null
        protected set

    @JvmField
    val displayModel: DisplayModel

    @JvmField
    val graphicFactory: GraphicFactory

    @JvmField
    protected var height: Float = 0f

    @JvmField
    protected var percent: Int = 100

    @JvmField
    protected var width: Float = 0f

    init {
        this.displayModel = displayModel
        this.graphicFactory = graphicFactory
    }

    protected fun computeBoundary(width: Int, height: Int, position: Position): Rectangle {
        // Center by default
        var xFactor = -0.5
        var yFactor = -0.5

        if (position == Position.ABOVE_LEFT
            || position == Position.LEFT
            || position == Position.BELOW_LEFT
        ) {
            xFactor = -1.0
        } else if (position == Position.ABOVE_RIGHT
            || position == Position.RIGHT
            || position == Position.BELOW_RIGHT
        ) {
            xFactor = 0.0
        }

        if (position == Position.ABOVE_LEFT
            || position == Position.ABOVE
            || position == Position.ABOVE_RIGHT
        ) {
            yFactor = -1.0
        } else if (position == Position.BELOW_LEFT
            || position == Position.BELOW
            || position == Position.BELOW_RIGHT
        ) {
            yFactor = 0.0
        }

        val left = xFactor * width
        // upstream bug, kept until the M5.6 fixes: top scales by width, not height
        val top = yFactor * width
        return Rectangle(left, top, left + width, top + height)
    }

    @Throws(IOException::class)
    protected fun createBitmap(
        relativePathPrefix: String?,
        src: String?,
        resourceProvider: XmlThemeResourceProvider?,
    ): Bitmap? {
        if (null == src
            || src.isEmpty()
        ) {
            return null
        }

        var symbolScale = 1f
        when (Parameters.SYMBOL_SCALING) {
            Parameters.SymbolScaling.ALL -> {
                if (this is Symbol || this is LineSymbol) {
                    symbolScale = displayModel.symbolScaleFactor
                }
            }
            Parameters.SymbolScaling.POI -> {
                if (this is Symbol) {
                    symbolScale = displayModel.symbolScaleFactor
                }
            }
        }
        return XmlUtils.createBitmap(
            graphicFactory,
            displayModel,
            relativePathPrefix,
            src,
            resourceProvider,
            width.toInt(),
            height.toInt(),
            (percent * symbolScale).toInt(),
        )
    }

    abstract fun destroy()

    /**
     * Renders this instruction for [poi] through [renderCallback], the receiver of all render
     * callbacks.
     */
    abstract fun renderNode(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        poi: PointOfInterest,
    )

    /**
     * Renders this instruction for [way] through [renderCallback], the receiver of all render
     * callbacks.
     */
    abstract fun renderWay(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        way: PolylineContainer,
    )

    internal fun scaleFromValue(value: String): Scale {
        if (value == ALL) {
            return Scale.ALL
        } else if (value == NONE) {
            return Scale.NONE
        }
        return Scale.STROKE
    }

    /**
     * Scales the stroke width of this RenderInstruction by [scaleFactor] for [zoomLevel].
     */
    abstract fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte)

    /**
     * Scales the text size of this RenderInstruction by [scaleFactor] for [zoomLevel].
     */
    abstract fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte)

    /**
     * Transform text before render based on the selected text transformation.
     */
    protected fun transformText(text: String, transformation: TextTransform): String {
        if (text.length == 0) {
            return text
        }
        if (transformation == TextTransform.UPPERCASE) {
            return text.uppercase(Locale.ROOT)
        } else if (transformation == TextTransform.LOWERCASE) {
            return text.lowercase(Locale.ROOT)
        } else if (transformation == TextTransform.CAPITALIZE) {
            return text.substring(0, 1).uppercase(Locale.ROOT) + text.substring(1)
        } else {
            return text
        }
    }

    companion object {

        internal const val ALIGN_CENTER = "align-center"
        internal const val ALL = "all"
        internal const val CAT = "cat"
        internal const val CURVE = "curve"
        internal const val DISPLAY = "display"
        internal const val DY = "dy"
        internal const val FILL = "fill"
        internal const val FONT_FAMILY = "font-family"
        internal const val FONT_SIZE = "font-size"
        internal const val FONT_STYLE = "font-style"
        internal const val ID = "id"
        internal const val K = "k"
        internal const val NONE = "none"
        internal const val POSITION = "position"
        internal const val PRIORITY = "priority"
        internal const val R = "r"
        internal const val RADIUS = "radius"
        internal const val REPEAT = "repeat"
        internal const val REPEAT_GAP = "repeat-gap"
        internal const val REPEAT_START = "repeat-start"

        @Deprecated("Use \"orientation\" parameters instead.")
        internal const val ROTATE = "rotate"
        internal const val SCALE = "scale"
        internal const val SCALE_RADIUS = "scale-radius"
        internal const val SRC = "src"
        internal const val STROKE = "stroke"
        internal const val STROKE_DASHARRAY = "stroke-dasharray"
        internal const val STROKE_LINECAP = "stroke-linecap"
        internal const val STROKE_LINEJOIN = "stroke-linejoin"
        internal const val STROKE_WIDTH = "stroke-width"
        internal const val SYMBOL_HEIGHT = "symbol-height"
        internal const val SYMBOL_ID = "symbol-id"
        internal const val SYMBOL_ORIENTATION = "symbol-orientation"
        internal const val SYMBOL_PERCENT = "symbol-percent"
        internal const val SYMBOL_SCALING = "symbol-scaling"
        internal const val SYMBOL_WIDTH = "symbol-width"
        internal const val TEXT_ORIENTATION = "text-orientation"
        internal const val TEXT_TRANSFORM = "text-transform"
        internal const val TEXT_WRAP_WIDTH = "text-wrap-width"
    }
}
