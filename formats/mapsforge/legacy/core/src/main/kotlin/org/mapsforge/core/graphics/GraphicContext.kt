/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016-2019 devemux86
 * Copyright 2017 usrusr
 * Copyright 2019 cpt1gl0
 * Copyright 2019 Adrian Batzill
 * Copyright 2019 mg4gh
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
package org.mapsforge.core.graphics

import org.mapsforge.core.model.Point

interface GraphicContext {

    var isAntiAlias: Boolean

    var isFilterBitmap: Boolean

    fun drawBitmap(bitmap: Bitmap, left: Int, top: Int)

    fun drawBitmap(bitmap: Bitmap, left: Int, top: Int, alpha: Float)

    fun drawBitmap(bitmap: Bitmap, matrix: Matrix)

    fun drawBitmap(bitmap: Bitmap, matrix: Matrix, alpha: Float)

    fun drawBitmap(
        bitmap: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Int,
        dstTop: Int,
        dstRight: Int,
        dstBottom: Int,
    )

    fun drawBitmap(
        bitmap: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Int,
        dstTop: Int,
        dstRight: Int,
        dstBottom: Int,
        alpha: Float,
    )

    fun drawCircle(x: Int, y: Int, radius: Int, paint: Paint)

    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, paint: Paint)

    fun drawLines(coordinates: Array<Array<Point>>, dy: Float, paint: Paint)

    fun drawPath(path: Path, paint: Paint)

    fun drawPathText(text: String, path: Path, paint: Paint)

    fun drawText(text: String, x: Int, y: Int, paint: Paint)

    fun drawTextRotated(text: String, x1: Int, y1: Int, x2: Int, y2: Int, paint: Paint)

    fun fillColor(color: Color)

    fun fillColor(color: Int)

    fun resetClip()

    fun setClip(left: Int, top: Int, width: Int, height: Int)

    fun setClip(left: Int, top: Int, width: Int, height: Int, intersect: Boolean)

    fun setClipDifference(left: Float, top: Float, width: Float, height: Float)
}
