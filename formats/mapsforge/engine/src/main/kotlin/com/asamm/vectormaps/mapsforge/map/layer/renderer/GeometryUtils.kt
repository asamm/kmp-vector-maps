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
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.model.Point

internal object GeometryUtils {

    /**
     * The center of the minimum bounding rectangle of `coordinates`, x, y pairs.
     */
    fun calculateCenterOfBoundingBox(coordinates: DoubleArray): Point {
        var pointXMin = coordinates[0]
        var pointXMax = coordinates[0]
        var pointYMin = coordinates[1]
        var pointYMax = coordinates[1]

        for (i in 0 until coordinates.size step 2) {
            val x = coordinates[i]
            val y = coordinates[i + 1]
            if (x < pointXMin) {
                pointXMin = x
            } else if (x > pointXMax) {
                pointXMax = x
            }

            if (y < pointYMin) {
                pointYMin = y
            } else if (y > pointYMax) {
                pointYMax = y
            }
        }

        return Point((pointXMin + pointXMax) / 2, (pointYMax + pointYMin) / 2)
    }

    /**
     * The middle point of `coordinates`, x, y pairs.
     */
    fun calculateCenterPoint(coordinates: DoubleArray): Point {
        val middle = 2 * (coordinates.size / 4)
        return Point(coordinates[middle], coordinates[middle + 1])
    }
}
