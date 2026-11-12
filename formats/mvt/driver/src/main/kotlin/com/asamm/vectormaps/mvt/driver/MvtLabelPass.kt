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
import com.asamm.vectormaps.mvt.render.MvtGraphics
import com.asamm.vectormaps.mvt.render.MvtRenderTarget
import com.asamm.vectormaps.mvt.symbol.MvtIcon
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import com.asamm.vectormaps.mvt.symbol.MvtText
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
class MvtLabelPass internal constructor(
    source: TileLabelSource<MvtLabel>,
    private val tileSize: Int,
    private val graphics: MvtGraphics,
    private val sprite: Any?,
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
        val value = text?.lines?.joinToString("\n")
        val labelText = text?.let {
            LabelText(
                value = value!!,
                sizePx = it.sizePx,
                fillArgb = it.color.argb,
                haloArgb = it.haloColor.argb,
                haloWidthPx = it.haloWidthPx,
                bold = it.font.bold,
                italic = it.font.italic,
            )
        }
        val id = idOf(label, x, y, value)
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
            // icon-offset turns with icon-rotate, and with its line; icon-translate never turns
            val turn = (angle ?: 0f) + icon.rotateDeg.toDouble()
            val (ox, oy) = turned(icon.centerX, icon.centerY, turn)
            boxes += CollisionBox(
                centerX = ox + icon.translateX,
                centerY = oy + icon.translateY,
                halfWidth = icon.width * icon.scale / 2.0 + icon.paddingPx,
                halfHeight = icon.height * icon.scale / 2.0 + icon.paddingPx,
                angleDeg = if (angle != null) turn else 0.0,
                alignment = if (angle != null) BoxAlignment.MAP else BoxAlignment.VIEWPORT,
            )
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
     * A transparent target of the screen's size for [draw], with the fonts and sprite the labels
     * were measured and drawn with.
     */
    fun newTarget(widthPx: Int, heightPx: Int): MvtRenderTarget {
        return graphics.createTarget(widthPx, heightPx, sprite)
    }

    /**
     * Draws [prepared], by default the latest placement, on [target] the size of [view], made by
     * [newTarget]; nothing while it is for another zoom.
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
                // an icon along its line turns with the line and the map; icon-translate with the map
                val turn = (label.lineAngleDeg?.let { a -> a + view.rotationDeg } ?: 0.0) + it.rotateDeg
                val (ox, oy) = turned(it.centerX, it.centerY, turn)
                val (tx, ty) = turned(it.translateX, it.translateY, view.rotationDeg)
                target.drawIcon(it, sx + (ox + tx).toFloat(), sy + (oy + ty).toFloat(), turn.toFloat())
            }
            if (text != null) {
                target.drawText(text, sx + text.centerX, sy + text.centerY)
            }
        }
    }

    private fun isFinite(label: MvtLabel): Boolean {
        val text = label.text
        val icon = label.icon
        return label.x.isFinite()
            && label.y.isFinite()
            && label.lineAngleDeg?.isFinite() != false
            && label.path?.all { it.isFinite() } != false
            && (text == null || isFinite(text))
            && (icon == null || isFinite(icon))
    }

    private fun isFinite(text: MvtText): Boolean {
        return text.sizePx > 0f
            && text.sizePx.isFinite()
            && nonNegative(text.width / 2f + text.paddingPx)
            && nonNegative(text.height / 2f + text.paddingPx)
            && nonNegative(text.sizePx / 2f + abs(text.centerY) + text.paddingPx)
            && nonNegative(text.haloWidthPx)
            && text.centerX.isFinite()
            && text.centerY.isFinite()
    }

    private fun isFinite(icon: MvtIcon): Boolean {
        return icon.width > 0f
            && icon.height > 0f
            && nonNegative(icon.width * icon.scale / 2f + icon.paddingPx)
            && nonNegative(icon.height * icon.scale / 2f + icon.paddingPx)
            && icon.centerX.isFinite()
            && icon.centerY.isFinite()
            && icon.translateX.isFinite()
            && icon.translateY.isFinite()
            && icon.rotateDeg.isFinite()
    }

    private fun nonNegative(value: Float): Boolean {
        return value >= 0f
            && value.isFinite()
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
    private fun idOf(label: MvtLabel, x: Double, y: Double, text: String?): Long {
        var hash = LabelId.mix(hash = LabelId.START, text = label.layerId)
        hash = LabelId.mix(hash = hash, value = (x * POSITION_STEPS).roundToLong())
        hash = LabelId.mix(hash = hash, value = (y * POSITION_STEPS).roundToLong())
        if (text != null) {
            hash = LabelId.mix(hash = hash, text = text)
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
