/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2021 devemux86
 * Copyright 2018 Adrian Batzill
 * Copyright 2021 eddiemuc
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
package com.asamm.vectormaps.mapsforge.map.rendertheme

import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.renderinstruction.RenderInstruction
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.logging.Logger

object XmlUtils {

    private val LOGGER: Logger = Logger.getLogger(XmlUtils::class.java.name)

    private const val PREFIX_ASSETS = "assets:"
    const val PREFIX_FILE = "file:"
    private const val PREFIX_JAR = "jar:"

    private const val UNSUPPORTED_COLOR_FORMAT = "unsupported color format: "

    @Throws(XmlPullParserException::class)
    fun checkMandatoryAttribute(elementName: String, attributeName: String, attributeValue: Any?) {
        if (attributeValue == null) {
            throw XmlPullParserException("missing attribute '$attributeName' for element: $elementName")
        }
    }

    /**
     * Reads the bitmap [src] (SVG or raster) through [resourceProvider], else from a file, the
     * platform assets or the classpath; null when [src] is empty.
     */
    @Throws(IOException::class)
    fun createBitmap(
        graphicFactory: GraphicFactory,
        displayModel: DisplayModel,
        relativePathPrefix: String?,
        src: String?,
        resourceProvider: XmlThemeResourceProvider?,
        width: Int,
        height: Int,
        percent: Int,
    ): Bitmap? {
        if (src.isNullOrEmpty()) {
            // no image source defined
            return null
        }

        createInputStream(graphicFactory, relativePathPrefix, src, resourceProvider).use { inputStream ->
            val absoluteName = getAbsoluteName(relativePathPrefix, src)
            // we need to hash with the width/height included as the same symbol could be required
            // in a different size and must be cached with a size-specific hash
            // we also need to include the resourceProvider as different providers may give different input streams for same source
            val sb = StringBuilder().append(absoluteName).append(width).append(height).append(percent)
            if (resourceProvider != null) {
                sb.append(resourceProvider.javaClass.name)
            }
            val hash = sb.toString().hashCode()
            if (src.lowercase(Locale.ENGLISH).endsWith(".svg")) {
                try {
                    val bitmap = graphicFactory.renderSvg(
                        inputStream,
                        displayModel.scaleFactor,
                        width,
                        height,
                        percent,
                        hash,
                    )
                    return applyThemeCallback(displayModel, bitmap)
                } catch (e: IOException) {
                    throw IOException("SVG render failed $src", e)
                }
            }
            try {
                val bitmap = graphicFactory.createResourceBitmap(
                    inputStream,
                    displayModel.scaleFactor,
                    width,
                    height,
                    percent,
                    hash,
                )
                return applyThemeCallback(displayModel, bitmap)
            } catch (e: IOException) {
                throw IOException("Reading bitmap file failed $src", e)
            }
        }
    }

    fun logUnknownAttribute(element: String, name: String, value: String, attributeIndex: Int) {
        LOGGER.warning("unknown attribute in element $element $attributeIndex : $name = $value")
    }

    /**
     * Supported formats are `#RRGGBB` and `#AARRGGBB`.
     */
    fun getColor(graphicFactory: GraphicFactory, colorString: String): Int {
        return getColor(graphicFactory, colorString, null, null)
    }

    /**
     * Supported formats are `#RRGGBB` and `#AARRGGBB`; [themeCallback] may then replace the color.
     */
    fun getColor(
        graphicFactory: GraphicFactory,
        colorString: String,
        themeCallback: ThemeCallback?,
        origin: RenderInstruction?,
    ): Int {
        if (colorString.isEmpty()
            || colorString[0] != '#'
        ) {
            throw IllegalArgumentException(UNSUPPORTED_COLOR_FORMAT + colorString)
        } else if (colorString.length == 7) {
            return getColor(graphicFactory, colorString, 255, 1, themeCallback, origin)
        } else if (colorString.length == 9) {
            return getColor(
                graphicFactory,
                colorString,
                colorString.substring(1, 3).toInt(16),
                3,
                themeCallback,
                origin,
            )
        } else {
            throw IllegalArgumentException(UNSUPPORTED_COLOR_FORMAT + colorString)
        }
    }

    @Throws(XmlPullParserException::class)
    fun parseNonNegativeByte(name: String, value: String): Byte {
        val parsedByte = value.toByte()
        checkForNegativeValue(name, parsedByte.toFloat())
        return parsedByte
    }

    @Throws(XmlPullParserException::class)
    fun parseNonNegativeFloat(name: String, value: String): Float {
        val parsedFloat = value.toFloat()
        checkForNegativeValue(name, parsedFloat)
        return parsedFloat
    }

    @Throws(XmlPullParserException::class)
    fun parseNonNegativeInteger(name: String, value: String): Int {
        val parsedInt = value.toInt()
        checkForNegativeValue(name, parsedInt.toFloat())
        return parsedInt
    }

    private fun applyThemeCallback(displayModel: DisplayModel, bitmap: Bitmap): Bitmap? {
        val themeCallback = displayModel.themeCallback ?: return bitmap
        return themeCallback.getBitmap(bitmap)
    }

    @Throws(XmlPullParserException::class)
    private fun checkForNegativeValue(name: String, value: Float) {
        if (value < 0) {
            throw XmlPullParserException("Attribute '$name' must not be negative: $value")
        }
    }

    /**
     * Create InputStream from assets, file or jar resource.
     *
     * If the resource has not a location prefix, then the search order is (file, assets, jar).
     */
    @Throws(IOException::class)
    private fun createInputStream(
        graphicFactory: GraphicFactory,
        relativePathPrefix: String?,
        src: String,
        resourceProvider: XmlThemeResourceProvider?,
    ): InputStream {
        if (resourceProvider != null) {
            try {
                val inputStream = resourceProvider.createInputStream(relativePathPrefix, src)
                if (inputStream != null) {
                    return inputStream
                }
            } catch (ioe: IOException) {
                LOGGER.fine("Exception trying to access resource: $src using custom provider: $ioe")
                // Ignore and try to resolve input stream using the standard process
            }
        }

        var source = src
        var inputStream: InputStream?
        if (source.startsWith(PREFIX_ASSETS)) {
            source = source.substring(PREFIX_ASSETS.length)
            inputStream = inputStreamFromAssets(graphicFactory, relativePathPrefix, source)
        } else if (source.startsWith(PREFIX_FILE)) {
            source = source.substring(PREFIX_FILE.length)
            inputStream = inputStreamFromFile(relativePathPrefix, source)
        } else if (source.startsWith(PREFIX_JAR)) {
            source = source.substring(PREFIX_JAR.length)
            inputStream = inputStreamFromJar(relativePathPrefix, source)
        } else {
            inputStream = inputStreamFromFile(relativePathPrefix, source)

            if (inputStream == null) {
                inputStream = inputStreamFromAssets(graphicFactory, relativePathPrefix, source)
            }

            if (inputStream == null) {
                inputStream = inputStreamFromJar(relativePathPrefix, source)
            }
        }

        // Fallback to internal resources
        if (inputStream == null) {
            inputStream = inputStreamFromJar("/assets/", source)
        }

        if (inputStream != null) {
            return inputStream
        }

        LOGGER.severe("invalid resource: $source")
        throw FileNotFoundException("invalid resource: $source")
    }

    /**
     * Create InputStream from (platform specific) assets resource.
     */
    private fun inputStreamFromAssets(
        graphicFactory: GraphicFactory,
        relativePathPrefix: String?,
        src: String,
    ): InputStream? {
        return try {
            graphicFactory.platformSpecificSources(relativePathPrefix, src)
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Create InputStream from file resource.
     */
    @Throws(IOException::class)
    private fun inputStreamFromFile(relativePathPrefix: String?, src: String): InputStream? {
        var file = getFile(relativePathPrefix, src)
        if (!file.exists()) {
            if (src.isNotEmpty()
                && src[0] == File.separatorChar
            ) {
                file = getFile(relativePathPrefix, src.substring(1))
            }
            if (!file.exists()) {
                return null
            }
        } else if (!file.isFile
            || !file.canRead()
        ) {
            return null
        }
        return FileInputStream(file)
    }

    /**
     * Create InputStream from jar resource.
     */
    private fun inputStreamFromJar(relativePathPrefix: String?, src: String): InputStream? {
        val absoluteName = getAbsoluteName(relativePathPrefix, src)
        return XmlUtils::class.java.getResourceAsStream(absoluteName)
    }

    private fun getAbsoluteName(relativePathPrefix: String?, name: String): String {
        if (name[0] == File.separatorChar) {
            return name
        }
        return relativePathPrefix + name
    }

    private fun getColor(
        graphicFactory: GraphicFactory,
        colorString: String,
        alpha: Int,
        rgbStartIndex: Int,
        themeCallback: ThemeCallback?,
        origin: RenderInstruction?,
    ): Int {
        val red = colorString.substring(rgbStartIndex, rgbStartIndex + 2).toInt(16)
        val green = colorString.substring(rgbStartIndex + 2, rgbStartIndex + 4).toInt(16)
        val blue = colorString.substring(rgbStartIndex + 4, rgbStartIndex + 6).toInt(16)

        var color = graphicFactory.createColor(alpha, red, green, blue)
        if (themeCallback != null) {
            color = themeCallback.getColor(origin, color)
        }
        return color
    }

    private fun getFile(parentPath: String?, pathName: String): File {
        if (pathName[0] == File.separatorChar) {
            return File(pathName)
        }
        return File(parentPath, pathName)
    }
}
