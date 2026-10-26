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
package org.mapsforge.map.android.graphics

import org.mapsforge.core.graphics.Matrix

internal open class AndroidMatrix : Matrix {

    @JvmField
    internal val matrix = android.graphics.Matrix()

    final override fun reset() {
        matrix.reset()
    }

    final override fun rotate(theta: Float) {
        matrix.preRotate(Math.toDegrees(theta.toDouble()).toFloat())
    }

    final override fun rotate(theta: Float, pivotX: Float, pivotY: Float) {
        matrix.preRotate(Math.toDegrees(theta.toDouble()).toFloat(), pivotX, pivotY)
    }

    final override fun scale(scaleX: Float, scaleY: Float) {
        matrix.preScale(scaleX, scaleY)
    }

    final override fun scale(scaleX: Float, scaleY: Float, pivotX: Float, pivotY: Float) {
        matrix.preScale(scaleX, scaleY, pivotX, pivotY)
    }

    final override fun translate(translateX: Float, translateY: Float) {
        matrix.preTranslate(translateX, translateY)
    }
}
