/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The pole of inaccessibility of a polygon, the inner point farthest from its edges, where
 * MapLibre puts a polygon's point label: the cell search of Mapbox's polylabel algorithm.
 */
internal object Polylabel {

    /**
     * The rings of `coords` grouped into polygons, an outer ring and the holes after it, as ranges
     * of coordinate indices; the first ring's winding marks the outer rings, as vector-tile-js.
     */
    fun polygons(coords: FloatArray, partEnds: IntArray): List<List<IntRange>> {
        val out = ArrayList<MutableList<IntRange>>()
        var outerNegative: Boolean? = null
        var start = 0
        for (end in partEnds) {
            val ring = start until end
            val area = area(coords, ring)
            if (area != 0f) {
                if (outerNegative == null) {
                    outerNegative = area < 0f
                }
                if ((area < 0f) == outerNegative) {
                    out += mutableListOf(ring)
                } else {
                    out.lastOrNull()?.add(ring)
                }
            }
            start = end
        }
        return out
    }

    /**
     * The pole of `polygon` (its outer ring first), found to within `precision`.
     */
    fun find(coords: FloatArray, polygon: List<IntRange>, precision: Float): Pair<Float, Float> {
        val outer = polygon.first()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in outer step 2) {
            minX = min(minX, coords[i])
            minY = min(minY, coords[i + 1])
            maxX = maxOf(maxX, coords[i])
            maxY = maxOf(maxY, coords[i + 1])
        }
        val width = maxX - minX
        val height = maxY - minY
        if (!(min(width, height) > 0f)) {
            return Pair(minX, minY)
        }
        // square cells of the shorter side, grown so a sliver seeds at most MAX_SEEDS of them
        val cellSize = maxOf(min(width, height), sqrt(width * height / MAX_SEEDS))
        val columns = ceil(width / cellSize).toInt()
        val rows = ceil(height / cellSize).toInt()
        val queue = PriorityQueue<Cell>(compareByDescending { it.max })
        var h = cellSize / 2f
        for (c in 0 until columns) {
            for (r in 0 until rows) {
                queue += Cell(minX + c * cellSize + h, minY + r * cellSize + h, h, coords, polygon)
            }
        }
        var best = centroidCell(coords, polygon)
        val boxCell = Cell(minX + (maxX - minX) / 2f, minY + (maxY - minY) / 2f, 0f, coords, polygon)
        if (boxCell.distance > best.distance) {
            best = boxCell
        }
        var probes = 0
        while (queue.isNotEmpty()
            && probes < MAX_PROBES
        ) {
            val cell = queue.poll()
            probes++
            if (cell.distance > best.distance) {
                best = cell
            }
            if (cell.max - best.distance <= precision) {
                continue
            }
            h = cell.h / 2f
            queue += Cell(cell.x - h, cell.y - h, h, coords, polygon)
            queue += Cell(cell.x + h, cell.y - h, h, coords, polygon)
            queue += Cell(cell.x - h, cell.y + h, h, coords, polygon)
            queue += Cell(cell.x + h, cell.y + h, h, coords, polygon)
        }
        return Pair(best.x, best.y)
    }

    private const val MAX_PROBES = 2000

    private const val MAX_SEEDS = 1024f

    private class Cell(
        val x: Float,
        val y: Float,
        val h: Float,
        coords: FloatArray,
        polygon: List<IntRange>,
    ) {
        val distance = signedDistance(x, y, coords, polygon)
        val max = distance + h * SQRT2
    }

    private val SQRT2 = sqrt(2f)

    private fun centroidCell(coords: FloatArray, polygon: List<IntRange>): Cell {
        val ring = polygon.first()
        var area = 0f
        var cx = 0f
        var cy = 0f
        var j = ring.last - 1
        for (i in ring step 2) {
            val f = coords[i] * coords[j + 1] - coords[j] * coords[i + 1]
            cx += (coords[i] + coords[j]) * f
            cy += (coords[i + 1] + coords[j + 1]) * f
            area += f * 3f
            j = i
        }
        if (area == 0f) {
            return Cell(coords[ring.first], coords[ring.first + 1], 0f, coords, polygon)
        }
        return Cell(cx / area, cy / area, 0f, coords, polygon)
    }

    // distance to the nearest edge, negative outside
    private fun signedDistance(x: Float, y: Float, coords: FloatArray, polygon: List<IntRange>): Float {
        var inside = false
        var minDistSq = Float.MAX_VALUE
        for (ring in polygon) {
            var j = ring.last - 1
            for (i in ring step 2) {
                val ax = coords[i]
                val ay = coords[i + 1]
                val bx = coords[j]
                val by = coords[j + 1]
                if ((ay > y) != (by > y)
                    && x < (bx - ax) * (y - ay) / (by - ay) + ax
                ) {
                    inside = !inside
                }
                minDistSq = min(minDistSq, segmentDistSq(x, y, ax, ay, bx, by))
                j = i
            }
        }
        val d = sqrt(minDistSq)
        return if (inside) d else -d
    }

    private fun segmentDistSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        var x = ax
        var y = ay
        val dx = bx - ax
        val dy = by - ay
        if (dx != 0f
            || dy != 0f
        ) {
            val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
            if (t > 1f) {
                x = bx
                y = by
            } else if (t > 0f) {
                x += dx * t
                y += dy * t
            }
        }
        val d = hypot(px - x, py - y)
        return d * d
    }

    private fun area(coords: FloatArray, ring: IntRange): Float {
        var sum = 0f
        var j = ring.last - 1
        for (i in ring step 2) {
            sum += (coords[j] - coords[i]) * (coords[i + 1] + coords[j + 1])
            j = i
        }
        return sum / 2f
    }
}
