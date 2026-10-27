/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015 devemux86
 * Copyright 2020 mg4gh
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

object RendererUtils {

    private const val ANGLE_LIMIT = 170.0

    private val ANGLE_LIMIT_COS = Math.cos(Math.toRadians(ANGLE_LIMIT))

    /**
     * A polyline at distance `dy` parallel to `p`; `p` itself if a corner turns sharper than the
     * angle limit. See
     * http://objectmix.com/graphics/132987-draw-parallel-polyline-algorithm-needed.html
     */
    @JvmStatic
    fun parallelPath(p: Array<Point>, dy: Double): Array<Point> {
        val n = p.size - 1
        val u = arrayOfNulls<Point>(n)
        val h = arrayOfNulls<Point>(p.size)

        // Generate an array u[] of unity vectors of each direction
        for (k in 0 until n) {
            val c = p[k + 1].x - p[k].x
            val s = p[k + 1].y - p[k].y
            val l = Math.sqrt(c * c + s * s)
            if (l == 0.0) {
                u[k] = Point(0.0, 0.0)
            } else {
                u[k] = Point(c / l, s / l)
            }

            // Detect angles above the allowed limit - return original path in this case
            if (k == 0) {
                continue
            }
            if (u[k]!!.x * u[k - 1]!!.x + u[k]!!.y * u[k - 1]!!.y < ANGLE_LIMIT_COS) {
                return p
            }
        }

        // For the start point calculate the normal
        h[0] = Point(p[0].x - dy * u[0]!!.y, p[0].y + dy * u[0]!!.x)

        // For 1 to N-1 calculate the intersection of the offset lines
        for (k in 1 until n) {
            val l = dy / (1 + u[k]!!.x * u[k - 1]!!.x + u[k]!!.y * u[k - 1]!!.y)
            h[k] = Point(p[k].x - l * (u[k]!!.y + u[k - 1]!!.y), p[k].y + l * (u[k]!!.x + u[k - 1]!!.x))
        }

        // For the end point use the normal
        h[n] = Point(p[n].x - dy * u[n - 1]!!.y, p[n].y + dy * u[n - 1]!!.x)

        // every element is set above
        @Suppress("UNCHECKED_CAST")
        return h as Array<Point>
    }
}
