/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013-2014 Ludwig M Brinckmann
 * Copyright 2014 devemux86
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
import android.graphics.BitmapFactory
import android.util.Pair
import org.mapsforge.core.graphics.GraphicUtils
import org.mapsforge.core.graphics.ResourceBitmap
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * @param hash identifies the resource, so it is loaded only once
 */
open class AndroidResourceBitmap protected constructor(
    private val hash: Int,
) : AndroidBitmap(), ResourceBitmap {

    @Throws(IOException::class)
    internal constructor(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ) : this(hash) {
        this.bitmap = getResourceBitmap(inputStream, scaleFactor, width, height, percent, hash)
    }

    // destroy is the super method here, which will take care of bitmap accounting
    // and call down into destroyBitmap when the resource bitmap needs to be destroyed

    override fun destroyBitmap() {
        if (this.bitmap != null) {
            if (removeBitmap(this.hash)) {
                this.bitmap!!.recycle()
            }
            this.bitmap = null
        }
    }

    companion object {

        @JvmField
        protected val LOGGER: Logger = Logger.getLogger(AndroidResourceBitmap::class.java.name)

        @JvmField
        protected var rBitmaps: MutableSet<Int>? = null

        @JvmField
        protected val RESOURCE_BITMAPS: MutableMap<Int, Pair<Bitmap, Int>> = HashMap()

        // used for debug bitmap accounting
        @JvmField
        protected var rInstances: AtomicInteger? = null

        init {
            if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                rInstances = AtomicInteger()
                rBitmaps = HashSet()
            }
        }

        // if AndroidGraphicFactory.KEEP_RESOURCE_BITMAPS is set, the bitmaps are kept in
        // a dictionary for faster retrieval and are not deleted or recycled until
        // clearResourceBitmaps is called

        @JvmStatic
        fun clearResourceBitmaps() {
            if (!AndroidGraphicFactory.KEEP_RESOURCE_BITMAPS) {
                return
            }
            synchronized(RESOURCE_BITMAPS) {
                for (p in RESOURCE_BITMAPS.values) {
                    p.first.recycle()
                    if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                        rInstances!!.decrementAndGet()
                    }
                }
                if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                    rBitmaps!!.clear()
                }
                RESOURCE_BITMAPS.clear()
            }
        }

        @Throws(IOException::class)
        private fun getResourceBitmap(
            inputStream: InputStream,
            scaleFactor: Float,
            width: Int,
            height: Int,
            percent: Int,
            hash: Int,
        ): Bitmap {
            return synchronized(RESOURCE_BITMAPS) {
                val data = RESOURCE_BITMAPS[hash]
                if (data != null) {
                    val updated = Pair<Bitmap, Int>(data.first, data.second + 1)
                    RESOURCE_BITMAPS[hash] = updated
                    data.first
                } else {
                    var bitmap: Bitmap? = BitmapFactory.decodeStream(
                        inputStream,
                        null,
                        createBitmapFactoryOptions(AndroidGraphicFactory.TRANSPARENT_BITMAP),
                    )
                    if (bitmap == null) {
                        throw IOException("BitmapFactory failed to decodeStream")
                    }
                    val newSize = GraphicUtils.imageSize(
                        bitmap.width.toFloat(),
                        bitmap.height.toFloat(),
                        scaleFactor,
                        width,
                        height,
                        percent,
                    )
                    if (newSize[0].toInt() != bitmap.width
                        || newSize[1].toInt() != bitmap.height
                    ) {
                        bitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            newSize[0].toInt(),
                            newSize[1].toInt(),
                            true,
                        )
                    }
                    val updated = Pair<Bitmap, Int>(bitmap, 1)
                    RESOURCE_BITMAPS[hash] = updated
                    if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                        LOGGER.info("RESOURCE BITMAP CREATE $hash")
                        rInstances!!.incrementAndGet()
                        synchronized(rBitmaps!!) {
                            rBitmaps!!.add(hash)
                        }
                        LOGGER.info(
                            "RESOURCE BITMAP ACC COUNT " + rInstances!!.get() + " " +
                                rBitmaps!!.size,
                        )
                        LOGGER.info("RESOURCE BITMAP COUNT " + RESOURCE_BITMAPS.size)
                    }
                    bitmap
                }
            }
        }

        private fun removeBitmap(hash: Int): Boolean {
            if (AndroidGraphicFactory.KEEP_RESOURCE_BITMAPS) {
                return false
            }
            synchronized(RESOURCE_BITMAPS) {
                val data = RESOURCE_BITMAPS[hash]
                if (data != null) {
                    if (data.second > 1) {
                        val updated = Pair<Bitmap, Int>(data.first, data.second - 1)
                        RESOURCE_BITMAPS[hash] = updated
                        return false
                    }
                    RESOURCE_BITMAPS.remove(hash)
                    if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                        synchronized(rBitmaps!!) {
                            LOGGER.info("RESOURCE BITMAP DELETE $hash")
                            val i = rInstances!!.decrementAndGet()
                            if (rBitmaps!!.contains(hash)) {
                                rBitmaps!!.remove(hash)
                            } else {
                                LOGGER.severe("RESOURCE BITMAP ALREADY REMOVED $hash")
                            }
                            LOGGER.info("RESOURCE BITMAP ACC COUNT " + i + " " + rBitmaps!!.size)
                        }
                        LOGGER.info("RESOURCE BITMAP COUNT " + RESOURCE_BITMAPS.size)
                    }
                    return true
                }
            }
            throw IllegalStateException("Bitmap should have been here $hash")
        }
    }
}
