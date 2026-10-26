/*
 * Copyright 2015-2018 devemux86
 * Copyright 2018 Adrian Batzill
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
import com.kitfox.svg.app.beans.SVGIcon
import org.mapsforge.core.graphics.GraphicUtils
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

open class AwtSvgBitmap @Throws(IOException::class) constructor(
    inputStream: InputStream,
    hash: Int,
    scaleFactor: Float,
    width: Int,
    height: Int,
    percent: Int,
) : AwtResourceBitmap(getResourceBitmapImpl(inputStream, hash, scaleFactor, width, height, percent)) {

    companion object {

        /**
         * Default size is 20x20px (400px).
         */
        @JvmField
        var DEFAULT_SIZE = 400f

        @JvmStatic
        @Throws(IOException::class)
        fun getResourceBitmap(
            inputStream: InputStream,
            name: String,
            scaleFactor: Float,
            defaultSize: Float,
            width: Int,
            height: Int,
            percent: Int,
        ): BufferedImage {
            try {
                val uri = SVGCache.getSVGUniverse().loadSVG(inputStream, name)
                val diagram = SVGCache.getSVGUniverse().getDiagram(uri)

                val scale = scaleFactor /
                    Math.sqrt(((diagram.height * diagram.width) / defaultSize).toDouble())

                val bmpSize = GraphicUtils.imageSize(
                    diagram.width,
                    diagram.height,
                    scale.toFloat(),
                    width,
                    height,
                    percent,
                )

                val icon = SVGIcon()
                icon.setAntiAlias(true)
                icon.setAutosize(SVGIcon.AUTOSIZE_STRETCH)
                icon.setPreferredSize(Dimension(bmpSize[0].toInt(), bmpSize[1].toInt()))
                icon.setSvgURI(uri)
                val bufferedImage = BufferedImage(
                    icon.iconWidth,
                    icon.iconHeight,
                    BufferedImage.TYPE_INT_ARGB,
                )
                icon.paintIcon(null, bufferedImage.createGraphics(), 0, 0)

                return bufferedImage
            } catch (e: Exception) {
                throw IOException(e)
            }
        }

        @Throws(IOException::class)
        private fun getResourceBitmapImpl(
            inputStream: InputStream,
            hash: Int,
            scaleFactor: Float,
            width: Int,
            height: Int,
            percent: Int,
        ): BufferedImage {
            synchronized(SVGCache.getSVGUniverse()) {
                return getResourceBitmap(
                    inputStream,
                    Integer.toString(hash),
                    scaleFactor,
                    DEFAULT_SIZE,
                    width,
                    height,
                    percent,
                )
            }
        }
    }
}
