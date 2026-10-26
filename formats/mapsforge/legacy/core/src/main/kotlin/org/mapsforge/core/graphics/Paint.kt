/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016-2017 devemux86
 * Copyright 2024-2025 Sublimis
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
import org.mapsforge.core.model.Rectangle

interface Paint {

    /**
     * The color, [Color.BLACK] by default.
     */
    var color: Int

    val isComplexStyle: Boolean

    val isTransparent: Boolean

    var strokeWidth: Float

    fun getTextBounds(text: String): Rectangle

    fun getTextHeight(text: String): Int

    fun getTextWidth(text: String): Int

    fun setBitmapShader(bitmap: Bitmap?)

    fun setBitmapShaderShift(origin: Point)

    fun setColor(color: Color)

    fun setDashPathEffect(strokeDasharray: FloatArray?)

    /**
     * [Cap.ROUND] by default.
     */
    fun setStrokeCap(cap: Cap)

    fun setStrokeJoin(join: Join)

    /**
     * [Style.FILL] by default.
     */
    fun setStyle(style: Style)

    fun setTextAlign(align: Align)

    fun setTextSize(textSize: Float)

    fun setTypeface(fontFamily: FontFamily, fontStyle: FontStyle)

    companion object {

        /**
         * Times the font height gives the font padding.
         */
        const val FONT_PADDING_FACTOR = 0.2
    }
}
