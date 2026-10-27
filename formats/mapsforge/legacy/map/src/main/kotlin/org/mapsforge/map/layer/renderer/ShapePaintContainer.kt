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
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.Paint

/**
 * A shape with the paint to draw it, offset by `dy` and drawn in `curveStyle`.
 */
open class ShapePaintContainer(
    internal val shapeContainer: ShapeContainer,
    internal val paint: Paint,
    internal val dy: Float,
    internal val curveStyle: Curve,
) {

    constructor(shapeContainer: ShapeContainer, paint: Paint) : this(shapeContainer, paint, 0f, Curve.NO)
}
