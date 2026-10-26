/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2016 devemux86
 * Copyright 2024 Sublimis
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

import android.graphics.Bitmap.CompressFormat
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.os.Build
import org.mapsforge.core.graphics.BaseBitmap
import org.mapsforge.core.graphics.Bitmap
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.SoftReference
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

open class AndroidBitmap protected constructor() : BaseBitmap(), Bitmap {

    /**
     * The Android bitmap, null once destroyed.
     */
    @Volatile
    @JvmField
    internal var bitmap: android.graphics.Bitmap? = null

    private val refCount = AtomicInteger()

    init {
        if (AndroidGraphicFactory.DEBUG_BITMAPS) {
            BITMAP_INSTANCES!!.incrementAndGet()
            synchronized(BITMAP_LIST!!) {
                BITMAP_LIST.add(this)
            }
        }
    }

    constructor(bitmap: android.graphics.Bitmap) : this() {
        if (bitmap.isRecycled) {
            throw IllegalArgumentException("bitmap is already recycled")
        }
        this.bitmap = bitmap
    }

    internal constructor(width: Int, height: Int, config: Config) : this() {
        this.bitmap = getBitmapFromReusableSet(width, height, config)
        if (this.bitmap == null) {
            this.bitmap = createAndroidBitmap(width, height, config)
        }
    }

    final override val height: Int
        get() {
            return bitmap!!.height
        }

    final override val isDestroyed: Boolean
        get() {
            return bitmap == null
        }

    final override val width: Int
        get() {
            return bitmap!!.width
        }

    @Throws(IOException::class)
    final override fun compress(outputStream: OutputStream) {
        if (!bitmap!!.compress(CompressFormat.PNG, 100, outputStream)) {
            throw IOException("Failed to write bitmap to output stream")
        }
    }

    final override fun decrementRefCount() {
        if (refCount.decrementAndGet() < 0) {
            destroy()
        }
    }

    final override fun incrementRefCount() {
        refCount.incrementAndGet()
    }

    final override fun scaleTo(width: Int, height: Int) {
        if (this.width != width
            || this.height != height
        ) {
            // The effect of the filter argument to createScaledBitmap is not well documented in the
            // official android docs, but according to
            // http://stackoverflow.com/questions/2895065/what-does-the-filter-parameter-to-createscaledbitmap-do
            // passing true results in smoother edges, less pixellation.
            // If smoother corners improve the readability of map labels is perhaps debatable.
            val myBitmap = bitmap
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                myBitmap!!,
                width,
                height,
                true,
            )
            destroy()
            bitmap = scaledBitmap
        }
    }

    final override fun setBackgroundColor(color: Int) {
        bitmap!!.eraseColor(color)
    }

    override fun toString(): String {
        val info: String
        val myBitmap = bitmap
        if (myBitmap != null) {
            if (myBitmap.hasAlpha()) {
                info = " has alpha"
            } else {
                info = " no alpha"
            }
        } else {
            info = " is recycled"
        }
        return super.toString() + " rC " + refCount.get() + info
    }

    protected fun canUseBitmap(
        candidate: android.graphics.Bitmap,
        width: Int,
        height: Int,
    ): Boolean {
        return candidate.width == width
            && candidate.height == height
    }

    protected open fun destroy() {
        if (AndroidGraphicFactory.DEBUG_BITMAPS) {
            synchronized(BITMAP_LIST!!) {
                val i = BITMAP_INSTANCES!!.decrementAndGet()
                if (BITMAP_LIST.contains(this)) {
                    BITMAP_LIST.remove(this)
                } else {
                    LOGGER.severe("BITMAP ALREADY REMOVED $this")
                }
                LOGGER.info("BITMAP COUNT " + i + " " + BITMAP_LIST.size)
            }
        }
        destroyBitmap()
    }

    protected open fun destroyBitmap() {
        val myBitmap = bitmap
        bitmap = null

        if (myBitmap != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                synchronized(REUSABLE_BITMAPS) {
                    REUSABLE_BITMAPS.add(SoftReference(myBitmap))
                }
            } else {
                myBitmap.recycle()
            }
        }
    }

    protected fun getBitmapFromReusableSet(
        width: Int,
        height: Int,
        config: Config,
    ): android.graphics.Bitmap? {
        var result: android.graphics.Bitmap? = null

        if (!REUSABLE_BITMAPS.isEmpty()) {
            synchronized(REUSABLE_BITMAPS) {
                val iterator = REUSABLE_BITMAPS.iterator()
                var candidate: android.graphics.Bitmap?

                while (iterator.hasNext()) {
                    candidate = iterator.next().get()
                    if (null != candidate
                        && candidate.isMutable
                    ) {
                        // Check to see it the item can be used for inBitmap.
                        if (canUseBitmap(candidate, width, height)) {
                            result = candidate
                            // Remove from reusable set so it can't be used again.
                            iterator.remove()
                            break
                        }
                    } else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove()
                    }
                }
            }
        }
        return result
    }

    companion object {

        private val LOGGER = Logger.getLogger(AndroidBitmap::class.java.name)

        private val REUSABLE_BITMAPS: MutableSet<SoftReference<android.graphics.Bitmap>> =
            HashSet()

        private val BITMAP_INSTANCES: AtomicInteger? = if (AndroidGraphicFactory.DEBUG_BITMAPS) {
            AtomicInteger()
        } else {
            null
        }

        private val BITMAP_LIST: MutableList<AndroidBitmap>? =
            if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                LinkedList()
            } else {
                null
            }

        @JvmStatic
        protected fun createAndroidBitmap(
            width: Int,
            height: Int,
            config: Config,
        ): android.graphics.Bitmap {
            return android.graphics.Bitmap.createBitmap(width, height, config)
        }

        @JvmStatic
        protected fun createBitmapFactoryOptions(config: Config): BitmapFactory.Options {
            val bitmapFactoryOptions = BitmapFactory.Options()
            bitmapFactoryOptions.inPreferredConfig = config
            return bitmapFactoryOptions
        }
    }
}
