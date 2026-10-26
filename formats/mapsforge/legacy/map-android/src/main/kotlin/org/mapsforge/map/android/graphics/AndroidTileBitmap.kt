/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016 ksaihtam
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
import android.os.Build
import org.mapsforge.core.graphics.CorruptedInputStreamException
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.util.IOUtils
import java.io.InputStream
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * On Android, managing and recycling the memory for bitmaps is important, but varies significantly
 * between versions: before Honeycomb (3.0), bitmaps require a call to recycle() to clear the
 * memory, for higher versions it is possible to reuse the bitmap storage as long as it fits
 * exactly. For older Android versions, bitmaps are recycled when not in use any more. For new
 * Android versions, the bitmap memory gets stored as a SoftReference, so that the memory can get
 * reclaimed if the GC so decides. For every new TileBitmap, it is attempted to reuse one of the
 * older bitmaps from the cache, only if that fails, a new bitmap is allocated.
 */
open class AndroidTileBitmap : AndroidBitmap, TileBitmap {

    private var expiration: Long = 0

    final override var timestamp: Long = System.currentTimeMillis()

    /*
     * THIS CAN THROW AN IllegalArgumentException or SocketTimeoutException The inputStream can be
     * corrupt for various reasons (slow download or slow access to file system) and will then raise
     * an exception. This exception must be caught by client classes. We do not catch it here to
     * allow proper handling in the higher levels (like redownload or reload from file storage).
     */
    internal constructor(
        inputStream: InputStream,
        tileSize: Int,
        isTransparent: Boolean,
    ) : super() {
        try {
            if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                tileInstances!!.incrementAndGet()
            }
            this.bitmap = BitmapFactory.decodeStream(
                inputStream,
                null,
                createTileBitmapFactoryOptions(tileSize, isTransparent),
            )
            // somehow on Android the decode stream can succeed, but the bitmap remains invalid.
            // Asking for the width forces the bitmap to be fully loaded and a NullPointerException
            // is triggered if the stream is not readable,
            // so that it can be handled at this point, rather than later
            // during bitmap painting
            this.bitmap!!.getWidth() // NOSONAR
        } catch (e: Exception) {
            // this is really stupid, the runtime system actually throws a SocketTimeoutException,
            // but we cannot catch it, because it is not declared, so we needed to catch the base
            // class exception
            LOGGER.info("TILEBITMAP ERROR $e")
            // need to null out to avoid recycling
            this.bitmap = null
            // seems to improve memory usage
            IOUtils.closeQuietly(inputStream)
            this.destroy()
            throw CorruptedInputStreamException("Corrupted bitmap input stream", e)
        }
    }

    internal constructor(tileSize: Int, isTransparent: Boolean) : super() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            this.bitmap = getTileBitmapFromReusableSet(tileSize, isTransparent)
        }
        if (this.bitmap == null) {
            val config = if (isTransparent) {
                AndroidGraphicFactory.TRANSPARENT_BITMAP
            } else {
                AndroidGraphicFactory.INSTANCE.nonTransparentBitmapConfig
            }
            this.bitmap = AndroidBitmap.createAndroidBitmap(tileSize, tileSize, config)
        }
        if (AndroidGraphicFactory.DEBUG_BITMAPS) {
            tileInstances!!.incrementAndGet()
        }
    }

    final override val isExpired: Boolean
        get() {
            if (expiration == 0L) {
                return false
            }
            return expiration <= System.currentTimeMillis()
        }

    final override fun setExpiration(expiration: Long) {
        this.expiration = expiration
    }

    override fun destroy() {
        super.destroy()
        if (AndroidGraphicFactory.DEBUG_BITMAPS) {
            val i = tileInstances!!.decrementAndGet()
            LOGGER.info("TILEBITMAP COUNT " + Integer.toString(i))
        }
    }

    override fun destroyBitmap() {
        if (this.bitmap != null) {
            // bitmap can be null if there is an error creating it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                val tileSize = this.height
                synchronized(reusableTileBitmaps) {
                    val hash = composeHash(tileSize, this.bitmap!!.hasAlpha())
                    if (!reusableTileBitmaps.containsKey(hash)) {
                        // if the set specific to the tile size does not exist, create it. It will
                        // never be destroyed, but the contained bitmaps will be recycled if memory
                        // gets tight.
                        reusableTileBitmaps[hash] = HashSet()
                    }
                    val sizeSpecificSet = reusableTileBitmaps[hash]
                    sizeSpecificSet!!.add(SoftReference<Bitmap>(this.bitmap))
                }
            } else {
                this.bitmap!!.recycle()
            }
            this.bitmap = null
        }
    }

    private fun createTileBitmapFactoryOptions(
        tileSize: Int,
        isTransparent: Boolean,
    ): BitmapFactory.Options {
        val bitmapFactoryOptions = BitmapFactory.Options()
        if (isTransparent) {
            bitmapFactoryOptions.inPreferredConfig = AndroidGraphicFactory.TRANSPARENT_BITMAP
        } else {
            bitmapFactoryOptions.inPreferredConfig =
                AndroidGraphicFactory.INSTANCE.nonTransparentBitmapConfig
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            val reusableBitmap = getTileBitmapFromReusableSet(tileSize, isTransparent)
            if (reusableBitmap != null) {
                bitmapFactoryOptions.inMutable = true
                // not really sure why this is required, but otherwise decoding fails
                bitmapFactoryOptions.inSampleSize = 1
                bitmapFactoryOptions.inBitmap =
                    getTileBitmapFromReusableSet(tileSize, isTransparent)
            }
        }
        return bitmapFactoryOptions
    }

    private companion object {

        private val LOGGER = Logger.getLogger(AndroidTileBitmap::class.java.name)

        // For modern Android versions, bitmap storage can be recycled. To support different tile
        // sizes we have a hashmap that contains the caches by tileSize/alpha
        private val reusableTileBitmaps: MutableMap<Int, MutableSet<SoftReference<Bitmap>>> =
            HashMap()

        private var tileInstances: AtomicInteger? = null

        init {
            if (AndroidGraphicFactory.DEBUG_BITMAPS) {
                tileInstances = AtomicInteger()
            }
        }

        private fun composeHash(tileSize: Int, isTransparent: Boolean): Int {
            if (isTransparent) {
                return tileSize + 0x10000000
            }
            return tileSize
        }

        private fun getTileBitmapFromReusableSet(tileSize: Int, isTransparent: Boolean): Bitmap? {
            val hash = composeHash(tileSize, isTransparent)

            return synchronized(reusableTileBitmaps) {
                val subSet = reusableTileBitmaps[hash]
                if (subSet == null) {
                    return null
                }
                var bitmap: Bitmap? = null
                val iterator = subSet.iterator()
                var candidate: Bitmap?
                while (iterator.hasNext()) {
                    candidate = iterator.next().get()
                    if (null != candidate
                        && candidate.isMutable
                    ) {
                        bitmap = candidate
                        if (isTransparent) {
                            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                        }
                        // Remove from reusable set so it can't be used again.
                        iterator.remove()
                        break
                    } else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove()
                    }
                }
                bitmap
            }
        }
    }
}
