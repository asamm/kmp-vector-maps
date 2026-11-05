/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge.map.layer.labels

import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.TextOrientation
import com.asamm.vectormaps.mapsforge.core.mapelements.MapElementContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.PointTextContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.SymbolContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.WayTextContainer
import com.asamm.vectormaps.mapsforge.core.model.Rotation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A stored label described for a placer outside the engine: what a label model needs to place it,
 * beside the [container] that draws it. Coordinates are absolute map pixels at the label's zoom.
 *
 * @param boxes collision geometry relative to the anchor; empty never clashes
 * @param path [LabelShapeKind.PATH_TEXT] only: the text's line as x, y pairs
 */
class LabelShape internal constructor(
    val container: MapElementContainer,
    val kind: LabelShapeKind,
    val x: Double,
    val y: Double,
    val priority: Int,
    val display: Display,
    val boxes: List<LabelShapeBox>,
    val text: LabelShapeText?,
    val path: DoubleArray?,
    val orientation: TextOrientation?,
)

enum class LabelShapeKind {

    /**
     * A caption or a symbol drawn upright.
     */
    POINT,

    /**
     * A symbol turned along its way.
     */
    LINE_SYMBOL,

    /**
     * A text along its way.
     */
    PATH_TEXT,
}

/**
 * A rectangle centered at an offset from the anchor, turned clockwise by [angleDeg]; with
 * [turnsWithMap] the offset and angle are in the map's frame, else in the screen's.
 */
class LabelShapeBox(
    val centerX: Double,
    val centerY: Double,
    val halfWidth: Double,
    val halfHeight: Double,
    val angleDeg: Double,
    val turnsWithMap: Boolean,
)

/**
 * A label's text and the colours of its paints, as ARGB.
 */
class LabelShapeText(
    val value: String,
    val heightPx: Double,
    val fillArgb: Int,
    val haloArgb: Int,
    val haloWidthPx: Float,
)

object LabelShapes {

    /**
     * Describes `container`; null for one never displayed.
     */
    fun of(container: MapElementContainer): LabelShape? {
        val display = container.displayInternal
        if (display == Display.NEVER) {
            return null
        }
        return when (container) {
            is WayTextContainer -> {
                ofWayText(container = container, display = display)
            }
            is SymbolContainer -> {
                ofSymbol(container = container, display = display)
            }
            else -> {
                ofPoint(container = container, display = display)
            }
        }
    }

    private fun ofPoint(container: MapElementContainer, display: Display): LabelShape {
        val anchor = container.xyInternal
        // the clash rectangle at no rotation already holds the caption's screen offsets
        val rect = container.getClashRect(Rotation.NULL_ROTATION)
        val boxes = if (rect == null) {
            emptyList()
        } else {
            listOf(
                LabelShapeBox(
                    centerX = rect.centerX - anchor.x,
                    centerY = rect.centerY - anchor.y,
                    halfWidth = rect.width / 2.0,
                    halfHeight = rect.height / 2.0,
                    angleDeg = 0.0,
                    turnsWithMap = false,
                ),
            )
        }
        val text = (container as? PointTextContainer)?.let {
            LabelShapeText(
                value = it.text,
                // the clash box is the measured text; measuring again races the render threads
                heightPx = rect?.height ?: 0.0,
                fillArgb = it.paintFront.color,
                haloArgb = it.paintBack?.color ?: 0,
                haloWidthPx = it.paintBack?.strokeWidth ?: 0.0f,
            )
        }
        return LabelShape(
            container = container,
            kind = LabelShapeKind.POINT,
            x = anchor.x,
            y = anchor.y,
            priority = container.priority,
            display = display,
            boxes = boxes,
            text = text,
            path = null,
            orientation = null,
        )
    }

    private fun ofSymbol(container: SymbolContainer, display: Display): LabelShape {
        val anchor = container.xyInternal
        val rect = container.getClashRect(Rotation.NULL_ROTATION)!!
        // the symbol turns by theta around its anchor (SymbolContainer.draw)
        val theta = container.theta.toDouble()
        val offsetX = rect.centerX - anchor.x
        val offsetY = rect.centerY - anchor.y
        val box = LabelShapeBox(
            centerX = offsetX * cos(theta) - offsetY * sin(theta),
            centerY = offsetX * sin(theta) + offsetY * cos(theta),
            halfWidth = rect.width / 2.0,
            halfHeight = rect.height / 2.0,
            angleDeg = Math.toDegrees(theta),
            turnsWithMap = !container.alignCanvas,
        )
        return LabelShape(
            container = container,
            kind = if (container.alignCanvas) LabelShapeKind.POINT else LabelShapeKind.LINE_SYMBOL,
            x = anchor.x,
            y = anchor.y,
            priority = container.priority,
            display = display,
            boxes = listOf(box),
            text = null,
            path = null,
            orientation = null,
        )
    }

    private fun ofWayText(container: WayTextContainer, display: Display): LabelShape {
        val anchor = container.xyInternal
        val segments = container.lineString.segments
        val halfHeight = container.textHeight / 2.0
        // one box per segment, text height tall, turning with the map
        val boxes = segments.map { segment ->
            val dx = segment.end.x - segment.start.x
            val dy = segment.end.y - segment.start.y
            LabelShapeBox(
                centerX = (segment.start.x + segment.end.x) / 2.0 - anchor.x,
                centerY = (segment.start.y + segment.end.y) / 2.0 - anchor.y,
                halfWidth = hypot(dx, dy) / 2.0,
                halfHeight = halfHeight,
                angleDeg = Math.toDegrees(atan2(dy, dx)),
                turnsWithMap = true,
            )
        }
        val path = DoubleArray((segments.size + 1) * 2)
        path[0] = segments[0].start.x
        path[1] = segments[0].start.y
        for (i in segments.indices) {
            path[i * 2 + 2] = segments[i].end.x
            path[i * 2 + 3] = segments[i].end.y
        }
        return LabelShape(
            container = container,
            kind = LabelShapeKind.PATH_TEXT,
            x = anchor.x,
            y = anchor.y,
            priority = container.priority,
            display = display,
            boxes = boxes,
            text = LabelShapeText(
                value = container.text,
                heightPx = container.textHeight,
                fillArgb = container.paintFront.color,
                haloArgb = container.paintBack?.color ?: 0,
                haloWidthPx = container.paintBack?.strokeWidth ?: 0.0f,
            ),
            path = path,
            orientation = container.textOrientation,
        )
    }
}
