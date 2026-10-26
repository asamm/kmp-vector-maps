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
package org.mapsforge.map.awt.graphics

import org.mapsforge.core.graphics.FillRule
import org.mapsforge.core.graphics.Path
import java.awt.geom.Path2D

internal open class AwtPath : Path {

    // declared as Path2D, as upstream: calls go to the double overloads
    val path2D: Path2D = Path2D.Float()

    final override fun clear() {
        this.path2D.reset()
    }

    final override fun close() {
        this.path2D.closePath()
    }

    final override val isEmpty: Boolean
        get() {
            return this.path2D.currentPoint == null
        }

    final override fun lineTo(x: Float, y: Float) {
        this.path2D.lineTo(x.toDouble(), y.toDouble())
    }

    final override fun moveTo(x: Float, y: Float) {
        this.path2D.moveTo(x.toDouble(), y.toDouble())
    }

    final override fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        this.path2D.quadTo(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
    }

    final override fun setFillRule(fillRule: FillRule) {
        this.path2D.windingRule = getWindingRule(fillRule)
    }

    companion object {

        private fun getWindingRule(fillRule: FillRule): Int {
            return when (fillRule) {
                FillRule.EVEN_ODD -> Path2D.WIND_EVEN_ODD
                FillRule.NON_ZERO -> Path2D.WIND_NON_ZERO
            }
        }
    }
}
