/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.BoxAlignment
import com.asamm.vectormaps.core.label.CollisionBox
import com.asamm.vectormaps.core.label.LabelCandidate
import com.asamm.vectormaps.core.label.LabelDisplay
import com.asamm.vectormaps.core.label.LabelId
import com.asamm.vectormaps.core.label.LabelKind
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.core.label.ScreenTransform
import com.asamm.vectormaps.core.label.TileLabelSource
import com.asamm.vectormaps.core.label.TiledLabelPass
import com.asamm.vectormaps.mvt.render.MvtRenderTarget
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The label pass of the MVT renderer (D39): places the symbols its tiles laid out and draws them
 * on an [MvtRenderTarget] the size of the screen. Point symbols stay upright; path texts follow
 * their line and read upright.
 */
class MvtLabelPass(
    source: TileLabelSource<MvtLabel>,
    private val tileSize: Int,
) : TiledLabelPass<MvtLabel>(source = source, tileSize = tileSize) {

    override fun candidateOf(label: MvtLabel, tile: TileRequest): LabelCandidate? {
        // a malformed style's values, which the core rejects
        if (!isFinite(label)) {
            return null
        }
        val originX = tile.x.toDouble() * tileSize
        val originY = tile.y.toDouble() * tileSize
        val x = originX + label.x
        val y = originY + label.y
        val text = label.text
        val display = if (label.allowOverlap) LabelDisplay.ALWAYS else LabelDisplay.IF_SPACE
        val labelText = text?.let {
            LabelText(
                value = it.lines.joinToString("\n"),
                sizePx = it.sizePx,
                fillArgb = it.color.argb,
                haloArgb = it.haloColor.argb,
                haloWidthPx = it.haloWidthPx,
                bold = it.font.bold,
                italic = it.font.italic,
            )
        }
        val id = idOf(label, x, y)
        val path = label.path
        if (path != null
            && text != null
        ) {
            val world = DoubleArray(path.size) { i ->
                if (i % 2 == 0) originX + path[i] else originY + path[i]
            }
            return LabelCandidate(
                id = id,
                kind = LabelKind.PATH_TEXT,
                x = x,
                y = y,
                priority = label.priority,
                // the text sits off the line by its offset, on a side only placement decides
                boxes = pathBoxes(world, x, y, text.sizePx / 2.0 + abs(text.centerY) + text.paddingPx),
                display = display,
                text = labelText,
                path = world,
                source = label,
            )
        }
        val boxes = ArrayList<CollisionBox>(2)
        if (text != null) {
            boxes += CollisionBox(
                centerX = text.centerX.toDouble(),
                centerY = text.centerY.toDouble(),
                halfWidth = text.width / 2.0 + text.paddingPx,
                halfHeight = text.height / 2.0 + text.paddingPx,
            )
        }
        val icon = label.icon
        if (icon != null) {
            val angle = label.lineAngleDeg
            if (angle == null) {
                boxes += CollisionBox(
                    centerX = icon.centerX.toDouble(),
                    centerY = icon.centerY.toDouble(),
                    halfWidth = icon.width * icon.scale / 2.0 + icon.paddingPx,
                    halfHeight = icon.height * icon.scale / 2.0 + icon.paddingPx,
                )
            } else {
                // an icon along its line turns with it, offset included
                val (cx, cy) = turned(icon.centerX, icon.centerY, angle.toDouble())
                boxes += CollisionBox(
                    centerX = cx,
                    centerY = cy,
                    halfWidth = icon.width * icon.scale / 2.0 + icon.paddingPx,
                    halfHeight = icon.height * icon.scale / 2.0 + icon.paddingPx,
                    angleDeg = angle + icon.rotateDeg.toDouble(),
                    alignment = BoxAlignment.MAP,
                )
            }
        }
        return LabelCandidate(
            id = id,
            kind = LabelKind.POINT,
            x = x,
            y = y,
            priority = label.priority,
            boxes = boxes,
            display = display,
            text = labelText,
            source = label,
        )
    }

    /**
     * Draws [prepared], by default the latest placement, on [target] the size of [view]; nothing
     * while it is for another zoom. [target] needs the fonts that measured the labels.
     */
    fun draw(target: MvtRenderTarget, view: LabelViewport, prepared: PreparedLabels? = this.prepared) {
        if (prepared == null
            || prepared.view.zoom != view.zoom
        ) {
            return
        }
        val screen = ScreenTransform(view)
        for (placed in prepared.placed) {
            val candidate = placed.candidate
            val label = candidate.source as MvtLabel
            val path = candidate.path
            val text = label.text
            if (path != null
                && text != null
            ) {
                val points = path.size / 2
                val drawn = FloatArray(path.size)
                for (p in 0 until points) {
                    val from = if (placed.isReversed) points - 1 - p else p
                    drawn[p * 2] = screen.x(path[from * 2], path[from * 2 + 1]).toFloat()
                    drawn[p * 2 + 1] = screen.y(path[from * 2], path[from * 2 + 1]).toFloat()
                }
                target.drawTextAlongPath(text, drawn)
                continue
            }
            val sx = screen.x(candidate.x, candidate.y).toFloat()
            val sy = screen.y(candidate.x, candidate.y).toFloat()
            label.icon?.let {
                val angle = label.lineAngleDeg
                if (angle == null) {
                    target.drawIcon(it, sx + it.centerX, sy + it.centerY, it.rotateDeg)
                } else {
                    val screenAngle = angle + view.rotationDeg
                    val (ox, oy) = turned(it.centerX, it.centerY, screenAngle)
                    target.drawIcon(it, sx + ox.toFloat(), sy + oy.toFloat(), (screenAngle + it.rotateDeg).toFloat())
                }
            }
            if (text != null) {
                target.drawText(text, sx + text.centerX, sy + text.centerY)
            }
        }
    }

    private fun isFinite(label: MvtLabel): Boolean {
        val text = label.text
        val icon = label.icon
        val textFinite = text == null
            || (text.width.isFinite() && text.height.isFinite() && text.centerX.isFinite() &&
                text.centerY.isFinite() && text.sizePx > 0f && text.sizePx.isFinite() &&
                text.haloWidthPx >= 0f && text.paddingPx >= 0f)
        val iconFinite = icon == null
            || (icon.centerX.isFinite() && icon.centerY.isFinite() && icon.scale > 0f &&
                icon.scale.isFinite() && icon.paddingPx >= 0f && icon.rotateDeg.isFinite())
        return label.x.isFinite()
            && label.y.isFinite()
            && label.lineAngleDeg?.isFinite() != false
            && label.path?.all { it.isFinite() } != false
            && textFinite
            && iconFinite
    }

    // (x, y) turned clockwise by `angleDeg`
    private fun turned(x: Float, y: Float, angleDeg: Double): Pair<Double, Double> {
        val radians = Math.toRadians(angleDeg)
        val cos = cos(radians)
        val sin = sin(radians)
        return Pair(x * cos - y * sin, x * sin + y * cos)
    }

    // boxes along the path turning with the map, relative to the anchor
    private fun pathBoxes(path: DoubleArray, x: Double, y: Double, halfHeight: Double): List<CollisionBox> {
        val boxes = ArrayList<CollisionBox>()
        for (i in 2 until path.size step 2) {
            val x0 = path[i - 2]
            val y0 = path[i - 1]
            val dx = path[i] - x0
            val dy = path[i + 1] - y0
            val segment = hypot(dx, dy)
            if (segment <= 0.0) {
                continue
            }
            boxes += CollisionBox(
                centerX = x0 + dx / 2.0 - x,
                centerY = y0 + dy / 2.0 - y,
                halfWidth = segment / 2.0,
                halfHeight = halfHeight,
                angleDeg = Math.toDegrees(atan2(dy, dx)),
                alignment = BoxAlignment.MAP,
            )
        }
        return boxes
    }

    /**
     * The same symbol gets the same id in every run (A7): its layer, text and anchor rounded to
     * 1/16 px.
     */
    private fun idOf(label: MvtLabel, x: Double, y: Double): Long {
        var hash = LabelId.mix(hash = LabelId.START, text = label.layerId)
        hash = LabelId.mix(hash = hash, value = (x * POSITION_STEPS).roundToLong())
        hash = LabelId.mix(hash = hash, value = (y * POSITION_STEPS).roundToLong())
        label.text?.let {
            hash = LabelId.mix(hash = hash, text = it.lines.joinToString("\n"))
        }
        label.icon?.let {
            hash = LabelId.mix(hash = hash, text = it.name)
        }
        return hash
    }

    private companion object {

        const val POSITION_STEPS = 16.0
    }
}
