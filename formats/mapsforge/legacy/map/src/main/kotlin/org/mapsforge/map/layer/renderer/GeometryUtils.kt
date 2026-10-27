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

import org.mapsforge.core.model.Point

internal object GeometryUtils {

    /**
     * The center of the minimum bounding rectangle of `coordinates`.
     */
    fun calculateCenterOfBoundingBox(coordinates: Array<Point>): Point {
        var pointXMin = coordinates[0].x
        var pointXMax = coordinates[0].x
        var pointYMin = coordinates[0].y
        var pointYMax = coordinates[0].y

        for (immutablePoint in coordinates) {
            if (immutablePoint.x < pointXMin) {
                pointXMin = immutablePoint.x
            } else if (immutablePoint.x > pointXMax) {
                pointXMax = immutablePoint.x
            }

            if (immutablePoint.y < pointYMin) {
                pointYMin = immutablePoint.y
            } else if (immutablePoint.y > pointYMax) {
                pointYMax = immutablePoint.y
            }
        }

        return Point((pointXMin + pointXMax) / 2, (pointYMax + pointYMin) / 2)
    }

    /**
     * The middle element of `coordinates`.
     */
    fun calculateCenterPoint(coordinates: Array<Point>): Point {
        return coordinates[coordinates.size / 2]
    }
}
