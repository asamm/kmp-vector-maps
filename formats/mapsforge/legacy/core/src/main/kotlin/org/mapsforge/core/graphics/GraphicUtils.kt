/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016 devemux86
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

object GraphicUtils {

    /**
     * The alpha of an AARRGGBB color.
     */
    @JvmStatic
    fun getAlpha(color: Int): Int {
        return (color shr 24) and 0xff
    }

    /**
     * The final image size, width then height, from the original size scaled by `scaleFactor`;
     * `width` or `height` other than 0 override it, keeping the aspect ratio when only one is set,
     * then `percent` scales the result.
     */
    @JvmStatic
    fun imageSize(
        picWidth: Float,
        picHeight: Float,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
    ): FloatArray {
        var bitmapWidth = picWidth * scaleFactor
        var bitmapHeight = picHeight * scaleFactor

        val aspectRatio = picWidth / picHeight

        if (width != 0
            && height != 0
        ) {
            // both width and height set, override any other setting
            bitmapWidth = width.toFloat()
            bitmapHeight = height.toFloat()
        } else if (width == 0
            && height != 0
        ) {
            // only height set, calculate from aspect ratio
            bitmapWidth = height * aspectRatio
            bitmapHeight = height.toFloat()
        } else if (width != 0
            && height == 0
        ) {
            // only width set, calculate from aspect ratio
            bitmapHeight = width / aspectRatio
            bitmapWidth = width.toFloat()
        }

        if (percent != 100) {
            bitmapWidth *= percent / 100f
            bitmapHeight *= percent / 100f
        }

        return floatArrayOf(bitmapWidth, bitmapHeight)
    }
}
