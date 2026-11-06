/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.BoxAlignment
import com.asamm.vectormaps.core.label.CollisionBox
import com.asamm.vectormaps.core.label.LabelCandidate
import com.asamm.vectormaps.core.label.LabelId
import com.asamm.vectormaps.core.label.LabelKind
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.core.label.ScreenTransform
import com.asamm.vectormaps.core.label.TileLabelStore
import com.asamm.vectormaps.core.label.TiledLabelPass
import com.asamm.vectormaps.garminimg.render.MapLabel
import com.asamm.vectormaps.garminimg.render.RenderTarget
import com.asamm.vectormaps.garminimg.render.TextAnchor
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * The label pass of a [GarminRenderer] (D39): places the labels its tiles stored and draws them
 * through an engine [RenderTarget] the size of the screen.
 */
class GarminLabelPass internal constructor(
    store: TileLabelStore<MapLabel>,
    private val tileSize: Int,
) : TiledLabelPass<MapLabel>(source = store, tileSize = tileSize) {

    override fun candidateOf(label: MapLabel, tile: TileRequest): LabelCandidate? {
        // a corrupt map's degenerate geometry, which the core rejects
        if (!isFinite(label)) {
            return null
        }
        return candidateOf(
            label = label,
            originX = tile.x.toDouble() * tileSize,
            originY = tile.y.toDouble() * tileSize,
        )
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
        val screen = ScreenTransform(view)
        for (placed in prepared.placed) {
            val candidate = placed.candidate
            val label = candidate.source as MapLabel
            val path = candidate.path
            if (path == null) {
                target.drawText(
                    x = screen.x(candidate.x, candidate.y).toFloat(),
                    y = screen.y(candidate.x, candidate.y).toFloat() + label.offsetYPx,
                    text = label.text,
                    color = label.color,
                    outlineColor = label.outlineColor,
                    fontSize = label.fontSize,
                    anchor = TextAnchor.CENTER,
                )
                continue
            }
            val points = path.size / 2
            val xs = FloatArray(points) { screen.x(path[it * 2], path[it * 2 + 1]).toFloat() }
            val ys = FloatArray(points) { screen.y(path[it * 2], path[it * 2 + 1]).toFloat() }
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
        var hash = LabelId.START
        hash = LabelId.mix(hash = hash, value = label.kind.ordinal.toLong())
        hash = LabelId.mix(hash = hash, value = label.priority.toLong())
        hash = LabelId.mix(hash = hash, value = (x * POSITION_STEPS).roundToLong())
        hash = LabelId.mix(hash = hash, value = (y * POSITION_STEPS).roundToLong())
        if (path != null) {
            hash = LabelId.mix(hash = hash, value = path.size.toLong())
            hash = LabelId.mix(hash = hash, value = (path[path.size - 2] * POSITION_STEPS).roundToLong())
            hash = LabelId.mix(hash = hash, value = (path[path.size - 1] * POSITION_STEPS).roundToLong())
        }
        return LabelId.mix(hash = hash, text = label.text)
    }

    // a road name's anchor, the middle of its line, which same-text distances count from
    private class TextStretch(
        val x: Double,
        val y: Double,
        val boxes: List<CollisionBox>,
    )

    internal companion object {

        /**
         * [label] with a line's path cut to the stretch its text covers and a margin each side,
         * for the store; anything else, and a line too short for its text and margins, as it is.
         */
        fun trimmedToText(label: MapLabel): MapLabel {
            val xs = label.pathXs
            val ys = label.pathYs
            if (xs == null
                || ys == null
                || xs.size < 2
            ) {
                return label
            }
            val along = DoubleArray(xs.size)
            for (i in 1 until xs.size) {
                along[i] = along[i - 1] + hypot((xs[i] - xs[i - 1]).toDouble(), (ys[i] - ys[i - 1]).toDouble())
            }
            val length = along.last()
            val from = (length - label.widthPx) / 2.0 - TRIM_MARGIN_PX
            val to = length - from
            // a non-finite path stays whole, so the pass still skips it
            if (!length.isFinite()
                || label.widthPx > length - SHORT_PATH_PX
                || from <= 0.0
            ) {
                return label
            }
            val keptXs = ArrayList<Float>()
            val keptYs = ArrayList<Float>()
            for (i in 1 until xs.size) {
                val segment = along[i] - along[i - 1]
                if (segment <= 0.0) {
                    continue
                }
                if (from >= along[i - 1]
                    && from < along[i]
                ) {
                    val t = ((from - along[i - 1]) / segment).toFloat()
                    keptXs += xs[i - 1] + (xs[i] - xs[i - 1]) * t
                    keptYs += ys[i - 1] + (ys[i] - ys[i - 1]) * t
                }
                if (to <= along[i]) {
                    val t = ((to - along[i - 1]) / segment).toFloat()
                    keptXs += xs[i - 1] + (xs[i] - xs[i - 1]) * t
                    keptYs += ys[i - 1] + (ys[i] - ys[i - 1]) * t
                    break
                }
                if (keptXs.isNotEmpty()) {
                    keptXs += xs[i]
                    keptYs += ys[i]
                }
            }
            return MapLabel(
                kind = label.kind,
                x = label.x,
                y = label.y,
                offsetYPx = label.offsetYPx,
                widthPx = label.widthPx,
                heightPx = label.heightPx,
                priority = label.priority,
                text = label.text,
                color = label.color,
                outlineColor = label.outlineColor,
                fontSize = label.fontSize,
                pathXs = keptXs.toFloatArray(),
                pathYs = keptYs.toFloatArray(),
            )
        }

        // kept past each end of a stored road name; over half SHORT_PATH_PX, so the text still fits
        private const val TRIM_MARGIN_PX = 8.0

        // room between labels, as the engine's former LabelLayout
        private const val LABEL_PADDING_PX = 2.0

        // the engine's former LabelLayout radius, now measured exactly
        private const val SAME_TEXT_RADIUS_PX = 200.0

        // the targets draw a line's text straight when it is longer than the line less this
        private const val SHORT_PATH_PX = 4.0

        private const val MIN_TEXT_SIZE = 1.0f

        private const val POSITION_STEPS = 16.0
    }
}
