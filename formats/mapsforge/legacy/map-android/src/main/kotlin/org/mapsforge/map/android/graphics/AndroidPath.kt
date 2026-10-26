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
package org.mapsforge.map.android.graphics

import android.graphics.Path.FillType
import org.mapsforge.core.graphics.FillRule
import org.mapsforge.core.graphics.Path

internal open class AndroidPath : Path {

    @JvmField
    internal val path = android.graphics.Path()

    final override val isEmpty: Boolean
        get() {
            return path.isEmpty
        }

    final override fun clear() {
        path.rewind()
    }

    final override fun close() {
        path.close()
    }

    final override fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    final override fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y)
    }

    final override fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        path.quadTo(x1, y1, x2, y2)
    }

    final override fun setFillRule(fillRule: FillRule) {
        path.fillType = getWindingRule(fillRule)
    }

    private companion object {

        private fun getWindingRule(fillRule: FillRule): FillType {
            return when (fillRule) {
                FillRule.EVEN_ODD -> FillType.EVEN_ODD
                FillRule.NON_ZERO -> FillType.WINDING
            }
        }
    }
}
