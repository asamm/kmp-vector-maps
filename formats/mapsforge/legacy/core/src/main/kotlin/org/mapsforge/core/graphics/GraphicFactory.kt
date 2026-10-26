/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2017 usrusr
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
package org.mapsforge.core.graphics

import org.mapsforge.core.mapelements.PointTextContainer
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import java.io.IOException
import java.io.InputStream

interface GraphicFactory {

    fun createBitmap(width: Int, height: Int): Bitmap

    fun createBitmap(width: Int, height: Int, isTransparent: Boolean): Bitmap

    fun createCanvas(): Canvas

    fun createColor(color: Color): Int

    fun createColor(alpha: Int, red: Int, green: Int, blue: Int): Int

    fun createMatrix(): Matrix

    /**
     * A single-channel bitmap for hillshading, optionally over `buffer`.
     */
    fun createMonoBitmap(
        width: Int,
        height: Int,
        buffer: ByteArray?,
        padding: Int,
        area: BoundingBox?,
        color: Int,
    ): HillshadingBitmap

    fun createPaint(): Paint

    fun createPaint(paint: Paint): Paint

    fun createPath(): Path

    fun createPointTextContainer(
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
    ): PointTextContainer

    @Throws(IOException::class)
    fun createResourceBitmap(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap

    @Throws(IOException::class)
    fun createTileBitmap(
        inputStream: InputStream,
        tileSize: Int,
        isTransparent: Boolean,
    ): TileBitmap

    fun createTileBitmap(tileSize: Int, isTransparent: Boolean): TileBitmap

    /**
     * The stream of a platform resource such as an Android asset; null or an [IOException] when
     * `src` is not one.
     */
    @Throws(IOException::class)
    fun platformSpecificSources(relativePathPrefix: String?, src: String): InputStream?

    @Throws(IOException::class)
    fun renderSvg(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
        hash: Int,
    ): ResourceBitmap
}
