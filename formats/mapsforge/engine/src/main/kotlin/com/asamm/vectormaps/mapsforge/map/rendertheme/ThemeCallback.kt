/*
 * Copyright 2016-2019 devemux86
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
import com.asamm.vectormaps.mapsforge.map.datastore.PointOfInterest
import com.asamm.vectormaps.mapsforge.map.layer.renderer.PolylineContainer
import com.asamm.vectormaps.mapsforge.map.rendertheme.renderinstruction.RenderInstruction

/**
 * Callback methods for render theme.
 */
interface ThemeCallback {

    /**
     * The resource bitmap to use in place of [bitmap].
     */
    fun getBitmap(bitmap: Bitmap): Bitmap?

    /**
     * The color-int to use for a theme color; [origin] is null for the theme's own backgrounds.
     */
    fun getColor(origin: RenderInstruction?, color: Int): Int

    /**
     * The color-int to use when drawing [way].
     */
    fun getColor(way: PolylineContainer, color: Int): Int

    /**
     * The caption text to draw for [poi].
     */
    fun getText(poi: PointOfInterest, text: String): String

    /**
     * The caption or path text to draw for [way].
     */
    fun getText(way: PolylineContainer, text: String): String
}
