/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2021 devemux86
 * Copyright 2017 usrusr
 * Copyright 2018 Adrian Batzill
 * Copyright 2025 Sublimis
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
package com.asamm.vectormaps.mapsforge.map.android.graphics

import android.content.Context
import android.graphics.Bitmap.Config
import android.text.TextUtils
import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.Canvas
import com.asamm.vectormaps.mapsforge.core.graphics.Color
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.graphics.Matrix
import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.core.graphics.Path
import com.asamm.vectormaps.mapsforge.core.graphics.Position
import com.asamm.vectormaps.mapsforge.core.graphics.ResourceBitmap
import com.asamm.vectormaps.mapsforge.core.graphics.TileBitmap
import com.asamm.vectormaps.mapsforge.core.mapelements.PointTextContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.SymbolContainer
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.util.Parameters
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class AndroidGraphicFactory private constructor(private val context: Context?) : GraphicFactory {

    private val cacheFolderSync = Any()

    @Volatile
    private var cacheFolder: File? = null

    init {
        if (context != null) {
            // the density is an approximate scale factor for the device
            DisplayModel.setDeviceScaleFactor(context.resources.displayMetrics.density)
        }
    }

    /**
     * The config of opaque bitmaps: RGB_565 takes 2 bytes per pixel, ARGB_8888 takes 4 and allows
     * transparency, so use it whenever any bitmap is transparent. ARGB_4444 is deprecated and,
     * despite its smaller size, much slower than ARGB_8888, as it takes an unoptimized path in
     * Skia.
     */
    val nonTransparentBitmapConfig: Config
        get() {
            return if (Parameters.ANDROID_32BIT_COLOR) Config.ARGB_8888 else Config.RGB_565
        }

    override fun createBitmap(width: Int, height: Int): Bitmap {
        return AndroidBitmap(width, height, TRANSPARENT_BITMAP)
    }

    override fun createBitmap(width: Int, height: Int, isTransparent: Boolean): Bitmap {
        if (isTransparent) {
            return AndroidBitmap(width, height, TRANSPARENT_BITMAP)
        }
        return AndroidBitmap(width, height, nonTransparentBitmapConfig)
    }

    override fun createCanvas(): Canvas {
        return AndroidCanvas()
    }

    override fun createColor(color: Color): Int {
        return getColor(color)
    }

    override fun createColor(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return android.graphics.Color.argb(alpha, red, green, blue)
    }

    override fun createMatrix(): Matrix {
        return AndroidMatrix()
    }

    override fun createPaint(): Paint {
        return AndroidPaint()
    }

    override fun createPaint(paint: Paint): Paint {
        return AndroidPaint(paint)
    }

    override fun createPath(): Path {
        return AndroidPath()
    }

    override fun createPointTextContainer(
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
        return AndroidPointTextContainer(
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
    override fun createResourceBitmap(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap {
        return AndroidResourceBitmap(inputStream, scaleFactor, width, height, percent, hash)
    }

    override fun createTileBitmap(
        inputStream: InputStream,
        tileSize: Int,
        isTransparent: Boolean,
    ): TileBitmap {
        return AndroidTileBitmap(inputStream, tileSize, isTransparent)
    }

    override fun createTileBitmap(tileSize: Int, isTransparent: Boolean): TileBitmap {
        return AndroidTileBitmap(tileSize, isTransparent)
    }

    /**
     * Android method accessible only via Context.
     */
    @Throws(FileNotFoundException::class)
    fun openFileInput(name: String): FileInputStream {
        return FileInputStream(getCacheFile(name))
    }

    /**
     * Android method accessible only via Context.
     */
    @Throws(FileNotFoundException::class)
    fun openFileOutput(name: String, mode: Int): FileOutputStream {
        return FileOutputStream(getCacheFile(name), mode == Context.MODE_APPEND)
    }

    @Throws(IOException::class)
    override fun platformSpecificSources(relativePathPrefix: String?, src: String): InputStream? {
        // this allows loading of resource bitmaps from the Android assets folder
        val pathName = (if (TextUtils.isEmpty(relativePathPrefix)) "" else relativePathPrefix) + src
        try {
            return context!!.assets.open(pathName)
        } catch (e: IOException) {
            throw FileNotFoundException("invalid resource: $pathName")
        }
    }

    @Throws(IOException::class)
    override fun renderSvg(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap {
        return AndroidSvgBitmap(inputStream, hash, scaleFactor, width, height, percent)
    }

    fun getCacheFile(filename: String): File {
        var ourCacheFolder = cacheFolder
        if (ourCacheFolder == null) {
            synchronized(cacheFolderSync) {
                ourCacheFolder = cacheFolder
                if (ourCacheFolder == null) {
                    val newCacheFolder = File(context!!.cacheDir, LIBRARY_FILE_NAME)
                    ourCacheFolder = newCacheFolder
                    newCacheFolder.mkdirs()
                    cacheFolder = newCacheFolder
                }
            }
        }

        return File(ourCacheFolder, filename)
    }

    companion object {

        // turn on for bitmap accounting
        const val DEBUG_BITMAPS = false

        /**
         * The factory in use, without a context until [createInstance], e.g. for IDE layout
         * designers.
         */
        var INSTANCE = AndroidGraphicFactory(null)

        // if true RESOURCE_BITMAPS will be kept in the cache to avoid
        // multiple loading
        const val KEEP_RESOURCE_BITMAPS = true

        // see nonTransparentBitmapConfig
        val TRANSPARENT_BITMAP: Config = Config.ARGB_8888

        val MONO_ALPHA_BITMAP: Config = Config.ALPHA_8

        const val LIBRARY_FILE_NAME = "mapsforge"

        fun createGraphicContext(canvas: android.graphics.Canvas): Canvas {
            return AndroidCanvas(canvas)
        }

        fun createInstance(context: Context?) {
            INSTANCE = AndroidGraphicFactory(context)
        }

        /**
         * The Android canvas, null once destroyed.
         */
        fun getCanvas(canvas: Canvas): android.graphics.Canvas? {
            return (canvas as AndroidCanvas).canvas
        }

        fun getPaint(paint: Paint): android.graphics.Paint {
            return (paint as AndroidPaint).paint
        }

        /**
         * The Android bitmap, null once destroyed.
         */
        fun getBitmap(bitmap: Bitmap): android.graphics.Bitmap? {
            return (bitmap as AndroidBitmap).bitmap
        }

        internal fun getColor(color: Color): Int {
            return when (color) {
                Color.BLACK -> android.graphics.Color.BLACK
                Color.BLUE -> android.graphics.Color.BLUE
                Color.GREEN -> android.graphics.Color.GREEN
                Color.RED -> android.graphics.Color.RED
                Color.TRANSPARENT -> android.graphics.Color.TRANSPARENT
                Color.WHITE -> android.graphics.Color.WHITE
            }
        }

        internal fun getMatrix(matrix: Matrix): android.graphics.Matrix {
            return (matrix as AndroidMatrix).matrix
        }

        internal fun getPath(path: Path): android.graphics.Path {
            return (path as AndroidPath).path
        }

    }
}
