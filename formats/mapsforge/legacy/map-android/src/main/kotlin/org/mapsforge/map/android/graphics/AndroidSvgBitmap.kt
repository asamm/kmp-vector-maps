/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013-2014 Ludwig M Brinckmann
 * Copyright 2014-2016 devemux86
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
package org.mapsforge.map.android.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Pair
import com.caverock.androidsvg.SVG
import org.mapsforge.core.graphics.GraphicUtils
import java.io.IOException
import java.io.InputStream

open class AndroidSvgBitmap @Throws(IOException::class) constructor(
    inputStream: InputStream,
    hash: Int,
    scaleFactor: Float,
    width: Int,
    height: Int,
    percent: Int,
) : AndroidResourceBitmap(hash) {

    init {
        this.bitmap = getResourceBitmapImpl(inputStream, hash, scaleFactor, width, height, percent)
    }

    companion object {

        /**
         * Default size is 20x20px (400px) at baseline mdpi (160dpi).
         */
        @JvmField
        var DEFAULT_SIZE = 400f

        @JvmStatic
        @Throws(IOException::class)
        fun getResourceBitmap(
            inputStream: InputStream,
            scaleFactor: Float,
            defaultSize: Float,
            width: Int,
            height: Int,
            percent: Int,
        ): Bitmap {
            try {
                // not in any cache, so need to render svg
                val svg = SVG.getFromInputStream(inputStream)
                val picture = svg.renderToPicture()

                val scale = scaleFactor /
                    Math.sqrt(((picture.height * picture.width) / defaultSize).toDouble())

                val bmpSize = GraphicUtils.imageSize(
                    picture.width.toFloat(),
                    picture.height.toFloat(),
                    scale.toFloat(),
                    width,
                    height,
                    percent,
                )

                val bitmap = Bitmap.createBitmap(
                    Math.ceil(bmpSize[0].toDouble()).toInt(),
                    Math.ceil(bmpSize[1].toDouble()).toInt(),
                    AndroidGraphicFactory.TRANSPARENT_BITMAP,
                )
                val canvas = Canvas(bitmap)
                canvas.drawPicture(picture, RectF(0f, 0f, bmpSize[0], bmpSize[1]))

                return bitmap
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
        ): Bitmap {
            return synchronized(RESOURCE_BITMAPS) {
                val data = RESOURCE_BITMAPS[hash]
                if (data != null) {
                    val updated = Pair<Bitmap, Int>(data.first, data.second + 1)
                    RESOURCE_BITMAPS[hash] = updated
                    return data.first
                }

                var bitmap = AndroidSvgBitmapStore.get(hash)

                if (bitmap == null) {
                    bitmap = getResourceBitmap(
                        inputStream,
                        scaleFactor,
                        DEFAULT_SIZE,
                        width,
                        height,
                        percent,
                    )

                    // save to disk for faster future retrieval
                    AndroidSvgBitmapStore.put(hash, bitmap)
                }

                // save in in-memory cache
                val updated = Pair<Bitmap, Int>(bitmap, 1)
                RESOURCE_BITMAPS[hash] = updated

                if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                    rInstances!!.incrementAndGet()
                    synchronized(rBitmaps!!) {
                        rBitmaps!!.add(hash)
                    }
                }
                bitmap
            }
        }
    }
}
