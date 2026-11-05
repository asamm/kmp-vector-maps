/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A candidate kept by [LabelPlacer].
 *
 * @param isReversed a path text to draw from its path's last point to its first, so it reads
 *  upright
 */
data class PlacedLabel(
    val candidate: LabelCandidate,
    val isReversed: Boolean,
)

/**
 * Places labels on a screen: highest priority first, ties by lower id, each candidate kept only
 * when it clashes with no candidate kept before it. A grid of screen cells limits the checks to
 * kept candidates nearby.
 */
object LabelPlacer {

    private val PLACEMENT_ORDER = compareByDescending<LabelCandidate> { it.priority }
        .thenBy { it.id }

    /**
     * Returns the kept candidates in draw order, lowest priority first, for a map turned clockwise
     * by [rotationDeg]. Candidates sharing an id are one label, placed once.
     */
    fun place(candidates: List<LabelCandidate>, rotationDeg: Double): List<PlacedLabel> {
        val order = candidates.sortedWith(PLACEMENT_ORDER).distinctBy { it.id }
        val radians = Math.toRadians(rotationDeg)
        val cos = cos(radians)
        val sin = sin(radians)
        val shapes = Array(order.size) { i ->
            ScreenShape.of(candidate = order[i], cos = cos, sin = sin)
        }
        val grid = Grid(shapes)
        val kept = ArrayList<PlacedLabel>(order.size)
        for (i in order.indices) {
            val candidate = order[i]
            if (candidate.display != LabelDisplay.ALWAYS) {
                if (grid.clashes(index = i, order = order)) {
                    continue
                }
                grid.add(i)
            }
            kept.add(PlacedLabel(
                candidate = candidate,
                isReversed = isReversed(candidate = candidate, cos = cos, sin = sin),
            ))
        }
        kept.reverse()
        return kept
    }

    private fun isReversed(candidate: LabelCandidate, cos: Double, sin: Double): Boolean {
        val path = candidate.path ?: return false
        val startX = path[0] * cos - path[1] * sin
        val endX = path[path.size - 2] * cos - path[path.size - 1] * sin
        return endX < startX
    }
}

/**
 * A rectangle in the screen frame: center, half sizes and the unit vector along its width.
 */
internal class ScreenBox(
    val cx: Double,
    val cy: Double,
    val hw: Double,
    val hh: Double,
    val ux: Double,
    val uy: Double,
) {

    val extentX: Double = abs(ux) * hw + abs(uy) * hh

    val extentY: Double = abs(uy) * hw + abs(ux) * hh

    /**
     * Separating-axis test on the four box axes; touching edges overlap.
     */
    fun overlaps(other: ScreenBox): Boolean {
        val dx = other.cx - cx
        val dy = other.cy - cy
        if (abs(dx) > extentX + other.extentX
            || abs(dy) > extentY + other.extentY
        ) {
            return false
        }
        return !separatesOn(ax = ux, ay = uy, other = other, dx = dx, dy = dy)
            && !separatesOn(ax = -uy, ay = ux, other = other, dx = dx, dy = dy)
            && !separatesOn(ax = other.ux, ay = other.uy, other = other, dx = dx, dy = dy)
            && !separatesOn(ax = -other.uy, ay = other.ux, other = other, dx = dx, dy = dy)
    }

    private fun separatesOn(
        ax: Double,
        ay: Double,
        other: ScreenBox,
        dx: Double,
        dy: Double,
    ): Boolean {
        val distance = abs(dx * ax + dy * ay)
        return distance > radiusOn(ax = ax, ay = ay) + other.radiusOn(ax = ax, ay = ay)
    }

    private fun radiusOn(ax: Double, ay: Double): Double {
        return hw * abs(ux * ax + uy * ay) + hh * abs(-uy * ax + ux * ay)
    }
}

/**
 * A candidate's boxes in the screen frame, with their axis-aligned bounds.
 */
internal class ScreenShape(val boxes: Array<ScreenBox>) {

    val minX: Double = boxes.minOf { it.cx - it.extentX }

    val minY: Double = boxes.minOf { it.cy - it.extentY }

    val maxX: Double = boxes.maxOf { it.cx + it.extentX }

    val maxY: Double = boxes.maxOf { it.cy + it.extentY }

    fun overlaps(other: ScreenShape): Boolean {
        if (minX > other.maxX
            || other.minX > maxX
            || minY > other.maxY
            || other.minY > maxY
        ) {
            return false
        }
        return boxes.any { box ->
            other.boxes.any { box.overlaps(it) }
        }
    }

    companion object {

        /**
         * The candidate's shape on a map turned by the angle whose cosine and sine are given; null
         * without boxes.
         */
        fun of(candidate: LabelCandidate, cos: Double, sin: Double): ScreenShape? {
            if (candidate.boxes.isEmpty()) {
                return null
            }
            val anchorX = candidate.x * cos - candidate.y * sin
            val anchorY = candidate.x * sin + candidate.y * cos
            val boxes = Array(candidate.boxes.size) { i ->
                val box = candidate.boxes[i]
                val boxRadians = Math.toRadians(box.angleDeg)
                var ux = cos(boxRadians)
                var uy = sin(boxRadians)
                var offsetX = box.centerX
                var offsetY = box.centerY
                if (box.alignment == BoxAlignment.MAP) {
                    offsetX = box.centerX * cos - box.centerY * sin
                    offsetY = box.centerX * sin + box.centerY * cos
                    val turnedX = ux * cos - uy * sin
                    uy = ux * sin + uy * cos
                    ux = turnedX
                }
                ScreenBox(
                    cx = anchorX + offsetX,
                    cy = anchorY + offsetY,
                    hw = box.halfWidth,
                    hh = box.halfHeight,
                    ux = ux,
                    uy = uy,
                )
            }
            return ScreenShape(boxes)
        }
    }
}

/**
 * Kept candidates bucketed by the screen cells their bounds cover. Cells past [MAX_CELLS] per
 * axis merge into the edge cells, which costs speed, not correctness.
 */
private class Grid(private val shapes: Array<ScreenShape?>) {

    private val minCol: Int

    private val minRow: Int

    private val cols: Int

    private val rows: Int

    private val cells: Array<IntList?>

    // the candidate last checked against each kept one, so a kept one in several cells is
    // checked once
    private val lastChecked = IntArray(shapes.size) { -1 }

    init {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (shape in shapes) {
            if (shape == null) {
                continue
            }
            minX = min(minX, shape.minX)
            minY = min(minY, shape.minY)
            maxX = max(maxX, shape.maxX)
            maxY = max(maxY, shape.maxY)
        }
        if (minX > maxX) {
            minCol = 0
            minRow = 0
            cols = 1
            rows = 1
        } else {
            minCol = cellOf(minX)
            minRow = cellOf(minY)
            cols = (cellOf(maxX) - minCol + 1).coerceAtMost(MAX_CELLS)
            rows = (cellOf(maxY) - minRow + 1).coerceAtMost(MAX_CELLS)
        }
        cells = arrayOfNulls(cols * rows)
    }

    /**
     * True when the candidate at [index] clashes with a kept one of the same collision group.
     */
    fun clashes(index: Int, order: List<LabelCandidate>): Boolean {
        val shape = shapes[index] ?: return false
        val display = order[index].display
        for (row in rowOf(shape.minY)..rowOf(shape.maxY)) {
            for (col in colOf(shape.minX)..colOf(shape.maxX)) {
                val cell = cells[row * cols + col] ?: continue
                for (k in 0 until cell.size) {
                    val kept = cell[k]
                    if (lastChecked[kept] == index) {
                        continue
                    }
                    lastChecked[kept] = index
                    // ORDER clashes with ORDER only, IF_SPACE with IF_SPACE only
                    if (order[kept].display == display
                        && shape.overlaps(shapes[kept]!!)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun add(index: Int) {
        val shape = shapes[index] ?: return
        for (row in rowOf(shape.minY)..rowOf(shape.maxY)) {
            for (col in colOf(shape.minX)..colOf(shape.maxX)) {
                val cellIndex = row * cols + col
                val cell = cells[cellIndex] ?: IntList().also { cells[cellIndex] = it }
                cell.add(index)
            }
        }
    }

    private fun colOf(x: Double): Int {
        return (cellOf(x) - minCol).coerceIn(0, cols - 1)
    }

    private fun rowOf(y: Double): Int {
        return (cellOf(y) - minRow).coerceIn(0, rows - 1)
    }

    private fun cellOf(value: Double): Int {
        return floor(value / CELL_SIZE).toInt()
    }

    private companion object {

        const val CELL_SIZE = 256.0

        const val MAX_CELLS = 512
    }
}

private class IntList {

    private var values = IntArray(4)

    var size = 0
        private set

    operator fun get(index: Int): Int {
        return values[index]
    }

    fun add(value: Int) {
        if (size == values.size) {
            values = values.copyOf(size * 2)
        }
        values[size++] = value
    }
}
