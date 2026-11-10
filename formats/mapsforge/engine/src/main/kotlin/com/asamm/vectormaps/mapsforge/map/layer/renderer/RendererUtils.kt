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
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.model.Point

object RendererUtils {

    private const val ANGLE_LIMIT = 170.0

    private val ANGLE_LIMIT_COS = Math.cos(Math.toRadians(ANGLE_LIMIT))

    /**
     * A polyline at distance `dy` parallel to `p`, x, y pairs; `p` itself if a corner turns sharper
     * than the angle limit. See
     * http://objectmix.com/graphics/132987-draw-parallel-polyline-algorithm-needed.html
     */
    fun parallelPath(p: DoubleArray, dy: Double): DoubleArray {
        val n = p.size / 2 - 1
        val ux = DoubleArray(n)
        val uy = DoubleArray(n)
        val h = DoubleArray(p.size)

        // Generate an array u[] of unity vectors of each direction
        for (k in 0 until n) {
            val c = p[2 * k + 2] - p[2 * k]
            val s = p[2 * k + 3] - p[2 * k + 1]
            val l = Math.sqrt(c * c + s * s)
            if (l == 0.0) {
                ux[k] = 0.0
                uy[k] = 0.0
            } else {
                ux[k] = c / l
                uy[k] = s / l
            }

            // Detect angles above the allowed limit - return original path in this case
            if (k == 0) {
                continue
            }
            if (ux[k] * ux[k - 1] + uy[k] * uy[k - 1] < ANGLE_LIMIT_COS) {
                return p
            }
        }

        // For the start point calculate the normal
        h[0] = p[0] - dy * uy[0]
        h[1] = p[1] + dy * ux[0]

        // For 1 to N-1 calculate the intersection of the offset lines
        for (k in 1 until n) {
            val l = dy / (1 + ux[k] * ux[k - 1] + uy[k] * uy[k - 1])
            h[2 * k] = p[2 * k] - l * (uy[k] + uy[k - 1])
            h[2 * k + 1] = p[2 * k + 1] + l * (ux[k] + ux[k - 1])
        }

        // For the end point use the normal
        h[2 * n] = p[2 * n] - dy * uy[n - 1]
        h[2 * n + 1] = p[2 * n + 1] + dy * ux[n - 1]

        return h
    }

    /**
     * The x, y pairs of `ring` relative to `origin`, the tile's, as a new array.
     */
    fun relativeTo(ring: DoubleArray, origin: Point): DoubleArray {
        val relative = DoubleArray(ring.size)
        for (i in 0 until ring.size step 2) {
            relative[i] = ring[i] - origin.x
            relative[i + 1] = ring[i + 1] - origin.y
        }
        return relative
    }
}
