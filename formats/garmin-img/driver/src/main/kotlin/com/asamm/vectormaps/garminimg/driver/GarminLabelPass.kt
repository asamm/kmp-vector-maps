/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.BackgroundLabelPass
import com.asamm.vectormaps.core.label.BoxAlignment
import com.asamm.vectormaps.core.label.CollisionBox
import com.asamm.vectormaps.core.label.LabelCandidate
import com.asamm.vectormaps.core.label.LabelKind
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.garminimg.render.MapLabel
import com.asamm.vectormaps.garminimg.render.RenderTarget
import com.asamm.vectormaps.garminimg.render.TextAnchor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The labels of a [GarminRenderer]'s rendered tiles, least recently used dropped beyond
 * [capacity] tiles.
 */
internal class GarminLabelStore(val capacity: Int) {

    private val tiles = object : LinkedHashMap<TileRequest, List<MapLabel>>(capacity, 0.75f, true) {

        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TileRequest, List<MapLabel>>,
        ): Boolean {
            return size > capacity
        }
    }

    @Synchronized
    fun store(tile: TileRequest, labels: List<MapLabel>) {
        tiles[tile] = labels
    }

    @Synchronized
    fun labelsOf(tile: TileRequest): List<MapLabel>? {
        return tiles[tile]
    }
}

/**
 * The label pass of a [GarminRenderer] (D39): places the labels its tiles stored and draws them
 * through an engine [RenderTarget] the size of the screen. A tile's labels show while the store
 * keeps it, so the store holds at least the tiles one screen covers and a host keeps no more
 * rendered tiles than it holds.
 */
class GarminLabelPass internal constructor(
    private val store: GarminLabelStore,
    private val tileSize: Int,
) : BackgroundLabelPass() {

    /**
     * Tiles the label store keeps.
     */
    val storeCapacity: Int
        get() {
            return store.capacity
        }

    override fun collect(view: LabelViewport, isCancelled: () -> Boolean): List<LabelCandidate> {
        val bounds = view.worldBounds()
        val maxTile = (1 shl view.zoom) - 1
        val candidates = ArrayList<LabelCandidate>()
        for (y in tileOf(bounds.minY, maxTile)..tileOf(bounds.maxY, maxTile)) {
            for (x in tileOf(bounds.minX, maxTile)..tileOf(bounds.maxX, maxTile)) {
                if (isCancelled()) {
                    return emptyList()
                }
                val labels = store.labelsOf(TileRequest(x = x, y = y, zoom = view.zoom)) ?: continue
                for (label in labels) {
                    // a corrupt map's degenerate geometry, which the core rejects
                    if (!isFinite(label)) {
                        continue
                    }
                    candidates += candidateOf(
                        label = label,
                        originX = x.toDouble() * tileSize,
                        originY = y.toDouble() * tileSize,
                    )
                }
            }
        }
        return candidates
    }

    /**
     * Draws [prepared], by default the latest placement, on [target] the size of [view]; nothing
     * while it is for another zoom. [target] needs the tiles' render scale (the config's device
     * scale) and font, which measured the labels. Text stays upright; road names follow their lines.
     */
    fun draw(target: RenderTarget, view: LabelViewport, prepared: PreparedLabels? = this.prepared) {
        if (prepared == null
            || prepared.view.zoom != view.zoom
        ) {
            return
        }
        val radians = Math.toRadians(view.rotationDeg)
        val screen = ScreenTransform(view = view, cos = cos(radians), sin = sin(radians))
        for (placed in prepared.placed) {
            val candidate = placed.candidate
            val label = candidate.source as MapLabel
            val path = candidate.path
            if (path == null) {
                target.drawText(
                    x = screen.x(candidate.x, candidate.y),
                    y = screen.y(candidate.x, candidate.y) + label.offsetYPx,
                    text = label.text,
                    color = label.color,
                    outlineColor = label.outlineColor,
                    fontSize = label.fontSize,
                    anchor = TextAnchor.CENTER,
                )
                continue
            }
            val points = path.size / 2
            val xs = FloatArray(points) { screen.x(path[it * 2], path[it * 2 + 1]) }
            val ys = FloatArray(points) { screen.y(path[it * 2], path[it * 2 + 1]) }
            // the target centres the text along the line and turns it to read upright
            target.drawTextOnPath(
                xs = xs,
                ys = ys,
                text = label.text,
                color = label.color,
                outlineColor = label.outlineColor,
                fontSize = label.fontSize,
            )
        }
    }

    private fun isFinite(label: MapLabel): Boolean {
        return label.x.isFinite()
            && label.y.isFinite()
            && label.offsetYPx.isFinite()
            && label.widthPx.isFinite()
            && label.heightPx.isFinite()
            && label.fontSize.isFinite()
            && label.pathXs?.all { it.isFinite() } != false
            && label.pathYs?.all { it.isFinite() } != false
    }

    private fun tileOf(value: Double, maxTile: Int): Int {
        return floor(value / tileSize).toInt().coerceIn(0, maxTile)
    }

    private fun candidateOf(label: MapLabel, originX: Double, originY: Double): LabelCandidate {
        val text = LabelText(
            value = label.text,
            sizePx = label.fontSize.coerceAtLeast(MIN_TEXT_SIZE),
            fillArgb = label.color.argb,
            haloArgb = label.outlineColor?.argb ?: 0,
        )
        val pathXs = label.pathXs
        val pathYs = label.pathYs
        val path = if (pathXs != null
            && pathYs != null
            && pathXs.size >= 2
        ) {
            DoubleArray(pathXs.size * 2) { i ->
                if (i % 2 == 0) originX + pathXs[i / 2] else originY + pathYs[i / 2]
            }
        } else {
            null
        }
        val stretch = path?.let {
            textStretch(path = it, label = label)
        }
        if (path == null
            || stretch == null
        ) {
            // points, areas, and lines too short for their text, which the targets draw straight
            val x = originX + label.x
            val y = originY + label.y
            return LabelCandidate(
                id = idOf(label = label, x = x, y = y, path = null),
                kind = LabelKind.POINT,
                x = x,
                y = y,
                priority = label.priority,
                boxes = listOf(
                    CollisionBox(
                        centerX = 0.0,
                        centerY = label.offsetYPx.toDouble(),
                        halfWidth = label.widthPx / 2.0 + LABEL_PADDING_PX,
                        halfHeight = label.heightPx / 2.0 + LABEL_PADDING_PX,
                    ),
                ),
                text = text,
                sameTextRadiusPx = SAME_TEXT_RADIUS_PX,
                source = label,
            )
        }
        return LabelCandidate(
            id = idOf(label = label, x = path[0], y = path[1], path = path),
            kind = LabelKind.PATH_TEXT,
            x = stretch.x,
            y = stretch.y,
            priority = label.priority,
            boxes = stretch.boxes,
            text = text,
            path = path,
            sameTextRadiusPx = SAME_TEXT_RADIUS_PX,
            source = label,
        )
    }

    /**
     * Boxes turning with the map over the stretch of [path] the text covers, centred along it as
     * the targets draw it, relative to the stretch's middle; null when the text does not fit.
     */
    private fun textStretch(path: DoubleArray, label: MapLabel): TextStretch? {
        val points = path.size / 2
        var length = 0.0
        for (i in 1 until points) {
            length += hypot(path[i * 2] - path[i * 2 - 2], path[i * 2 + 1] - path[i * 2 - 1])
        }
        val textWidth = label.widthPx.toDouble()
        // the targets' own fallback to straight text
        if (textWidth > length - SHORT_PATH_PX) {
            return null
        }
        val start = (length - textWidth) / 2.0 - LABEL_PADDING_PX
        val end = start + textWidth + 2.0 * LABEL_PADDING_PX
        val (centerX, centerY) = pointAt(path = path, distance = length / 2.0)
        // the targets lift the text off the line on the side that reads upright, which turns
        val halfHeight = label.heightPx + LABEL_PADDING_PX
        val boxes = ArrayList<CollisionBox>()
        var at = 0.0
        for (i in 1 until points) {
            val x0 = path[i * 2 - 2]
            val y0 = path[i * 2 - 1]
            val dx = path[i * 2] - x0
            val dy = path[i * 2 + 1] - y0
            val segment = hypot(dx, dy)
            val from = maxOf(start, at)
            val to = minOf(end, at + segment)
            if (to > from
                && segment > 0.0
            ) {
                val t = ((from + to) / 2.0 - at) / segment
                boxes += CollisionBox(
                    centerX = x0 + dx * t - centerX,
                    centerY = y0 + dy * t - centerY,
                    halfWidth = (to - from) / 2.0,
                    halfHeight = halfHeight,
                    angleDeg = Math.toDegrees(atan2(dy, dx)),
                    alignment = BoxAlignment.MAP,
                )
            }
            at += segment
        }
        return TextStretch(x = centerX, y = centerY, boxes = boxes)
    }

    private fun pointAt(path: DoubleArray, distance: Double): Pair<Double, Double> {
        var at = 0.0
        for (i in 1 until path.size / 2) {
            val x0 = path[i * 2 - 2]
            val y0 = path[i * 2 - 1]
            val dx = path[i * 2] - x0
            val dy = path[i * 2 + 1] - y0
            val segment = hypot(dx, dy)
            if (segment > 0.0
                && distance <= at + segment
            ) {
                val t = (distance - at) / segment
                return Pair(x0 + dx * t, y0 + dy * t)
            }
            at += segment
        }
        return Pair(path[path.size - 2], path[path.size - 1])
    }

    /**
     * The same label from neighbouring tiles gets the same id, in every run (A7): positions are
     * rounded to 1/16 px, as each tile projects them in its own floats.
     */
    private fun idOf(label: MapLabel, x: Double, y: Double, path: DoubleArray?): Long {
        var hash = FNV_OFFSET
        hash = mix(hash = hash, value = label.kind.ordinal.toLong())
        hash = mix(hash = hash, value = label.priority.toLong())
        hash = mix(hash = hash, value = (x * POSITION_STEPS).roundToLong())
        hash = mix(hash = hash, value = (y * POSITION_STEPS).roundToLong())
        if (path != null) {
            hash = mix(hash = hash, value = path.size.toLong())
            hash = mix(hash = hash, value = (path[path.size - 2] * POSITION_STEPS).roundToLong())
            hash = mix(hash = hash, value = (path[path.size - 1] * POSITION_STEPS).roundToLong())
        }
        label.text.forEach { char ->
            hash = mix(hash = hash, value = char.code.toLong())
        }
        return hash
    }

    private fun mix(hash: Long, value: Long): Long {
        var result = hash
        for (shift in 0 until 64 step 8) {
            result = (result xor ((value ushr shift) and 0xFF)) * FNV_PRIME
        }
        return result
    }

    // a road name's anchor, the middle of its line, which same-text distances count from
    private class TextStretch(
        val x: Double,
        val y: Double,
        val boxes: List<CollisionBox>,
    )

    // world pixels to the turned screen, the label placer's convention
    private class ScreenTransform(
        private val view: LabelViewport,
        private val cos: Double,
        private val sin: Double,
    ) {

        fun x(worldX: Double, worldY: Double): Float {
            val dx = worldX - view.centerX
            val dy = worldY - view.centerY
            return (dx * cos - dy * sin + view.widthPx / 2.0).toFloat()
        }

        fun y(worldX: Double, worldY: Double): Float {
            val dx = worldX - view.centerX
            val dy = worldY - view.centerY
            return (dx * sin + dy * cos + view.heightPx / 2.0).toFloat()
        }
    }

    private companion object {

        // room between labels, as the engine's former LabelLayout
        const val LABEL_PADDING_PX = 2.0

        // the engine's former LabelLayout radius, now measured exactly
        const val SAME_TEXT_RADIUS_PX = 200.0

        // the targets draw a line's text straight when it is longer than the line less this
        const val SHORT_PATH_PX = 4.0

        const val MIN_TEXT_SIZE = 1.0f

        const val POSITION_STEPS = 16.0

        const val FNV_OFFSET = -0x340d631b7bdddcdbL

        const val FNV_PRIME = 0x100000001b3L
    }
}
