/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015 devemux86
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.mapsforge.core.util.IOUtils
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.logging.Logger

/**
 * Stores rendered SVG images as PNG bitmaps in the SVG cache dir (`setSvgCacheDir`, else
 * `cacheDir/mapsforge`), named by their hash, which also encodes the size, so they are not
 * rendered again; storage is asynchronous.
 *
 * A bitmap might be read while it is still being stored; not yet seen in testing. Storing under a
 * temporary name and renaming it would avoid that.
 *
 * Nothing invalidates an entry: a new icon with the same name and size (= hash) returns the old,
 * cached one until the cache is cleared.
 */
object AndroidSvgBitmapStore {

    private val LOGGER = Logger.getLogger(AndroidSvgBitmapStore::class.java.name)

    private const val SVG_PREFIX = "svg-"

    private const val SVG_SUFFIX = ".png"

    @JvmStatic
    fun clear() {
        val files = AndroidGraphicFactory.INSTANCE.fileList()
        if (files == null) {
            return
        }
        for (file in files) {
            if (file.startsWith(SVG_PREFIX)
                && file.endsWith(SVG_SUFFIX)
            ) {
                AndroidGraphicFactory.INSTANCE.deleteFile(file)
            }
        }
    }

    private fun createFileName(hash: Int): String {
        val sb = StringBuilder().append(SVG_PREFIX).append(hash).append(SVG_SUFFIX)
        return sb.toString()
    }

    @JvmStatic
    fun get(hash: Int): Bitmap? {
        val fileName = createFileName(hash)
        var inputStream: FileInputStream? = null
        try {
            inputStream = AndroidGraphicFactory.INSTANCE.openFileInput(fileName)
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: FileNotFoundException) {
            // ignore: file is not yet in cache
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
        return null
    }

    @JvmStatic
    fun put(hash: Int, bitmap: Bitmap) {
        // perform in background
        Thread(SvgStorer(hash, bitmap)).start()
    }

    private class SvgStorer(
        private val hash: Int,
        private val bitmap: Bitmap,
    ) : Runnable {

        override fun run() {
            val fileName = createFileName(hash)
            var outputStream: FileOutputStream? = null
            try {
                outputStream = AndroidGraphicFactory.INSTANCE.openFileOutput(
                    fileName,
                    Context.MODE_PRIVATE,
                )
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream)) {
                    LOGGER.warning("SVG Failed to write svg bitmap $fileName")
                }
            } catch (e: IllegalStateException) {
                LOGGER.warning("SVG Failed to stream bitmap to file $fileName")
            } catch (e: FileNotFoundException) {
                LOGGER.warning("SVG Failed to create file for svg bitmap $fileName")
            } finally {
                IOUtils.closeQuietly(outputStream)
            }
        }
    }
}
