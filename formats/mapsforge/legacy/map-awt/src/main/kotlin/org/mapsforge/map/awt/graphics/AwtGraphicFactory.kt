/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 Christian Pesch
 * Copyright 2014 Develar
 * Copyright 2015-2017 devemux86
 * Copyright 2017 usrusr
 * Copyright 2018 Adrian Batzill
 * Copyright 2024-2025 Sublimis
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

import com.kitfox.svg.SVGCache
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Path
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.ResourceBitmap
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.mapelements.PointTextContainer
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.model.Point
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

/**
 * The AWT [GraphicFactory], a singleton: use [INSTANCE].
 */
open class AwtGraphicFactory private constructor() : GraphicFactory {

    final override fun createBitmap(width: Int, height: Int): Bitmap {
        return AwtBitmap(width, height)
    }

    final override fun createBitmap(width: Int, height: Int, isTransparent: Boolean): Bitmap {
        return AwtBitmap(width, height, isTransparent)
    }

    final override fun createCanvas(): Canvas {
        return AwtCanvas()
    }

    final override fun createColor(color: Color): Int {
        return getColor(color).rgb
    }

    final override fun createColor(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return java.awt.Color(red, green, blue, alpha).rgb
    }

    final override fun createMatrix(): Matrix {
        return AwtMatrix()
    }

    final override fun createPaint(): Paint {
        return AwtPaint()
    }

    final override fun createPaint(paint: Paint): Paint {
        return AwtPaint(paint)
    }

    final override fun createPath(): Path {
        return AwtPath()
    }

    final override fun createPointTextContainer(
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
    ): PointTextContainer {
        return AwtPointTextContainer(
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
        )
    }

    @Throws(IOException::class)
    final override fun createResourceBitmap(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap {
        return AwtResourceBitmap(inputStream, scaleFactor, width, height, percent)
    }

    @Throws(IOException::class)
    final override fun createTileBitmap(
        inputStream: InputStream,
        tileSize: Int,
        isTransparent: Boolean,
    ): TileBitmap {
        return AwtTileBitmap(inputStream)
    }

    final override fun createTileBitmap(tileSize: Int, isTransparent: Boolean): TileBitmap {
        return AwtTileBitmap(tileSize, isTransparent)
    }

    @Throws(IOException::class)
    final override fun platformSpecificSources(relativePathPrefix: String?, src: String): InputStream? {
        return null
    }

    @Throws(IOException::class)
    final override fun renderSvg(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap {
        return AwtSvgBitmap(inputStream, hash, scaleFactor, width, height, percent)
    }

    companion object {

        @JvmField
        val INSTANCE: GraphicFactory = AwtGraphicFactory()

        @JvmField
        val TRANSPARENT = java.awt.Color(0, 0, 0, 0)

        @JvmStatic
        fun createGraphicContext(graphics: Graphics): Canvas {
            return AwtCanvas(graphics as Graphics2D)
        }

        @JvmStatic
        internal fun getAffineTransform(matrix: Matrix): AffineTransform {
            return (matrix as AwtMatrix).affineTransform
        }

        @JvmStatic
        fun getGraphics(canvas: Canvas): Graphics2D? {
            return (canvas as AwtCanvas).getGraphicObject()
        }

        @JvmStatic
        fun getPaint(paint: Paint): AwtPaint {
            return paint as AwtPaint
        }

        @JvmStatic
        internal fun getPath(path: Path): AwtPath {
            return path as AwtPath
        }

        @JvmStatic
        internal fun getColor(color: Color): java.awt.Color {
            return when (color) {
                Color.BLACK -> java.awt.Color.BLACK
                Color.BLUE -> java.awt.Color.BLUE
                Color.GREEN -> java.awt.Color.GREEN
                Color.RED -> java.awt.Color.RED
                Color.TRANSPARENT -> TRANSPARENT
                Color.WHITE -> java.awt.Color.WHITE
            }
        }

        @JvmStatic
        fun clearResourceFileCache() {
            // We don't use a resource file cache
        }

        @JvmStatic
        fun clearResourceMemoryCache() {
            SVGCache.getSVGUniverse().clear()
        }

        /**
         * Returns the internal image representation.
         *
         * @param bitmap Mapsforge Bitmap
         * @return platform specific image.
         */
        @JvmStatic
        fun getBitmap(bitmap: Bitmap): BufferedImage {
            return (bitmap as AwtBitmap).bufferedImage
        }
    }
}
