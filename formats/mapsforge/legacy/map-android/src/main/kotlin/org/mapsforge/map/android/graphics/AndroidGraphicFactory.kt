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
package org.mapsforge.map.android.graphics

import android.content.Context
import android.graphics.Bitmap.Config
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
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
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.model.DisplayModel
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

    private var svgCacheDir: File? = null

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
    fun deleteFile(name: String): Boolean {
        if (svgCacheDir != null) {
            return File(svgCacheDir, name).delete()
        }
        return getCacheFile(name).delete()
    }

    /**
     * Android method accessible only via Context.
     */
    fun fileList(): Array<String>? {
        if (svgCacheDir != null) {
            return svgCacheDir!!.list()
        }
        return getCacheFile("").list()
    }

    /**
     * Android method accessible only via Context.
     */
    @Throws(FileNotFoundException::class)
    fun openFileInput(name: String): FileInputStream {
        if (svgCacheDir != null) {
            return FileInputStream(File(svgCacheDir, name))
        }
        return FileInputStream(getCacheFile(name))
    }

    /**
     * Android method accessible only via Context.
     */
    @Throws(FileNotFoundException::class)
    fun openFileOutput(name: String, mode: Int): FileOutputStream {
        if (svgCacheDir != null) {
            return FileOutputStream(File(svgCacheDir, name), mode == Context.MODE_APPEND)
        }
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

    fun setSvgCacheDir(svgCacheDir: File?) {
        this.svgCacheDir = svgCacheDir
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
        @JvmField
        var INSTANCE = AndroidGraphicFactory(null)

        // if true RESOURCE_BITMAPS will be kept in the cache to avoid
        // multiple loading
        const val KEEP_RESOURCE_BITMAPS = true

        // see nonTransparentBitmapConfig
        @JvmField
        val TRANSPARENT_BITMAP: Config = Config.ARGB_8888

        @JvmField
        val MONO_ALPHA_BITMAP: Config = Config.ALPHA_8

        const val LIBRARY_FILE_NAME = "mapsforge"

        @JvmStatic
        fun convertToAndroidBitmap(drawable: Drawable): android.graphics.Bitmap {
            val bitmap: android.graphics.Bitmap
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                && drawable is BitmapDrawable
            ) {
                bitmap = drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                bitmap = android.graphics.Bitmap.createBitmap(width, height, TRANSPARENT_BITMAP)
                val canvas = android.graphics.Canvas(bitmap)

                val rect: Rect = drawable.bounds
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                drawable.bounds = rect
            }

            return bitmap
        }

        @JvmStatic
        fun convertToBitmap(drawable: Drawable): Bitmap {
            return AndroidBitmap(convertToAndroidBitmap(drawable))
        }

        @JvmStatic
        fun convertToBitmap(drawable: Drawable, paint: android.graphics.Paint?): Bitmap {
            val immutable = convertToAndroidBitmap(drawable)
            val mutable = immutable.copy(TRANSPARENT_BITMAP, true)
            val canvas = android.graphics.Canvas(mutable)

            canvas.drawBitmap(mutable, 0f, 0f, paint)

            return AndroidBitmap(mutable)
        }

        @JvmStatic
        fun createGraphicContext(canvas: android.graphics.Canvas): Canvas {
            return AndroidCanvas(canvas)
        }

        @JvmStatic
        fun createInstance(context: Context?) {
            INSTANCE = AndroidGraphicFactory(context)
        }

        /**
         * The bytes per pixel of a bitmap with `config`.
         */
        @Suppress("DEPRECATION")
        @JvmStatic
        fun getBytesPerPixel(config: Config): Int {
            if (config == Config.ARGB_8888) {
                return 4
            } else if (config == Config.RGB_565) {
                return 2
            } else if (config == Config.ARGB_4444) {
                return 2
            } else if (config == Config.ALPHA_8) {
                return 1
            }
            return 1
        }

        /**
         * The Android canvas, null once destroyed.
         */
        @JvmStatic
        fun getCanvas(canvas: Canvas): android.graphics.Canvas? {
            return (canvas as AndroidCanvas).canvas
        }

        @JvmStatic
        fun getPaint(paint: Paint): android.graphics.Paint {
            return (paint as AndroidPaint).paint
        }

        /**
         * The Android bitmap, null once destroyed.
         */
        @JvmStatic
        fun getBitmap(bitmap: Bitmap): android.graphics.Bitmap? {
            return (bitmap as AndroidBitmap).bitmap
        }

        @JvmStatic
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

        @JvmStatic
        internal fun getMatrix(matrix: Matrix): android.graphics.Matrix {
            return (matrix as AndroidMatrix).matrix
        }

        @JvmStatic
        internal fun getPath(path: Path): android.graphics.Path {
            return (path as AndroidPath).path
        }

        @JvmStatic
        fun clearResourceFileCache() {
            AndroidSvgBitmapStore.clear()
        }

        @JvmStatic
        fun clearResourceMemoryCache() {
            AndroidResourceBitmap.clearResourceBitmaps()
        }
    }
}
