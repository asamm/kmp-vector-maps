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
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.IndexColorModel
import java.awt.image.Raster
import java.awt.image.SampleModel
import java.awt.image.WritableRaster
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

/**
 * The AWT [GraphicFactory], a singleton: use [INSTANCE].
 */
open class AwtGraphicFactory private constructor() : GraphicFactory {

    @JvmField
    protected val monoColorModelSync = Any()

    @Volatile
    @JvmField
    protected var monoColorModel: MonoColorModel? = null

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

    final override fun createMonoBitmap(
        width: Int,
        height: Int,
        buffer: ByteArray?,
        padding: Int,
        area: BoundingBox?,
        color: Int,
    ): AwtHillshadingBitmap {
        var myMonoColorModel = this.monoColorModel

        if (myMonoColorModel == null
            || color != myMonoColorModel.color
        ) {
            myMonoColorModel = synchronized(this.monoColorModelSync) {
                val current = this.monoColorModel

                if (current == null
                    || color != current.color
                ) {
                    val awtColor = java.awt.Color(color, true)

                    val alpha = awtColor.alpha

                    val reds = ByteArray(256)
                    val greens = ByteArray(256)
                    val blues = ByteArray(256)
                    val alphas = ByteArray(256)

                    Arrays.fill(reds, awtColor.red.toByte())
                    Arrays.fill(greens, awtColor.green.toByte())
                    Arrays.fill(blues, awtColor.blue.toByte())

                    // use a lookup color model on the AWT side so that the android implementation
                    // can take the bytes without any twiddling (the only 8 bit bitmaps android
                    // knows are alpha masks, so we have to define our mono bitmap bytes in a way
                    // that are easy for android to understand)
                    for (i in 0 until 256) {
                        alphas[i] = (i * alpha / 255).toByte()
                    }

                    val created = MonoColorModel(
                        IndexColorModel(8, 256, reds, greens, blues, alphas),
                        color,
                    )
                    this.monoColorModel = created
                    created
                } else {
                    current
                }
            }
        }

        val dataBuffer: DataBuffer = DataBufferByte(buffer, buffer!!.size)
        val singleByteSampleModel: SampleModel = myMonoColorModel.model.createCompatibleSampleModel(
            width + 2 * padding,
            height + 2 * padding,
        )
        val writableRaster: WritableRaster =
            Raster.createWritableRaster(singleByteSampleModel, dataBuffer, null)
        val bufferedImage = BufferedImage(myMonoColorModel.model, writableRaster, false, null)

        return AwtHillshadingBitmap(bufferedImage, padding, area)
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

    // the constructor is not protected as upstream: Kotlin hides it from the outer class then
    protected open class MonoColorModel(
        @JvmField internal val model: ColorModel,
        @JvmField internal val color: Int,
    )

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
