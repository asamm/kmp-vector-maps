/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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

import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.Rotation

interface Canvas : GraphicContext {

    val dimension: Dimension

    val height: Int

    val width: Int

    fun destroy()

    fun restore()

    fun rotate(degrees: Float, px: Float, py: Float)

    fun rotate(rotation: Rotation)

    fun save()

    /**
     * The bitmap to draw into; AWT accepts null to detach it, Android does not.
     */
    fun setBitmap(bitmap: Bitmap?)

    fun setBitmap(bitmap: Bitmap?, dx: Float, dy: Float, degrees: Float, px: Float, py: Float)

    fun translate(dx: Float, dy: Float)
}
