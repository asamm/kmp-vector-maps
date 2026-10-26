/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015 Andreas Schildbach
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

interface Path {

    val isEmpty: Boolean

    fun clear()

    fun close()

    fun lineTo(x: Float, y: Float)

    fun moveTo(x: Float, y: Float)

    /**
     * A quadratic bezier from the last point towards control point (x1, y1), ending at (x2, y2);
     * without a [moveTo] in this contour it starts at (0, 0).
     */
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float)

    fun setFillRule(fillRule: FillRule)
}
