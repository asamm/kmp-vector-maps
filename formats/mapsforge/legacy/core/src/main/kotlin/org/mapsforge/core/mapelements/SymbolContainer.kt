/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015-2016 devemux86
 * Copyright 2020 Adrian Batzill
 * Copyright 2024 Sublimis
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
package org.mapsforge.core.mapelements

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation

/**
 * A symbol bitmap placed on the map; without a `boundary` it is centered on its point.
 */
open class SymbolContainer(
    point: Point,
    display: Display,
    priority: Int,
    boundary: Rectangle?,
    symbol: Bitmap,
    theta: Float,
    alignCanvas: Boolean,
) : MapElementContainer(point, display, priority) {

    @JvmField
    protected val boundary: Rectangle

    // if it has a fixed angle to canvas.
    @JvmField
    val alignCanvas: Boolean

    @JvmField
    var symbol: Bitmap

    @JvmField
    val theta: Float

    constructor(
        point: Point,
        display: Display,
        priority: Int,
        boundary: Rectangle?,
        symbol: Bitmap,
        alignCanvas: Boolean,
    ) : this(point, display, priority, boundary, symbol, 0f, alignCanvas)

    init {
        this.symbol = symbol
        this.theta = theta
        this.alignCanvas = alignCanvas
        if (boundary != null) {
            this.boundary = boundary
        } else {
            val halfWidth = this.symbol.width / 2.0
            val halfHeight = this.symbol.height / 2.0
            this.boundary = Rectangle(-halfWidth, -halfHeight, halfWidth, halfHeight)
        }

        this.symbol.incrementRefCount()
    }

    override fun getBoundary(): Rectangle {
        return boundary
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) {
            return false
        }
        if (other !is SymbolContainer) {
            return false
        }
        if (this.symbol !== other.symbol) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + symbol.hashCode()
        return result
    }

    final override fun draw(canvas: Canvas?, origin: Point?, matrix: Matrix?, rotation: Rotation?) {
        matrix!!.reset()
        // We cast to int for pixel perfect positioning
        matrix.translate(
            (this.xy.x - origin!!.x + boundary.left).toInt().toFloat(),
            (this.xy.y - origin.y + boundary.top).toInt().toFloat(),
        )
        // this is the rotation angle combined from map rotation and symbol rotation
        var totalTheta = theta
        if (!Rotation.noRotation(rotation)
            && this.alignCanvas
        ) {
            totalTheta -= rotation!!.radians.toFloat()
        }
        if (totalTheta != 0f) {
            matrix.rotate(totalTheta, (-boundary.left).toFloat(), (-boundary.top).toFloat())
        }
        canvas!!.drawBitmap(this.symbol, matrix, 1f)
    }
}
