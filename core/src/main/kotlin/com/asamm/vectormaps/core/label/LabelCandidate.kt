/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

/**
 * A label a format offers for placement: a point text or symbol, a symbol along a line, or a text
 * along a path. Coordinates are world pixels at the zoom being labelled; collision geometry is
 * measured by the format, in screen pixels.
 *
 * @param id stable identity, equal for the same label stored from neighbouring tiles; breaks
 *  priority ties, so it must not come from an identity hash
 * @param x anchor in world pixels; a path text's anchor is its path's first point
 * @param boxes collision geometry relative to the anchor; empty never clashes
 * @param path [LabelKind.PATH_TEXT] only: the path as x, y pairs in world pixels
 */
class LabelCandidate(
    val id: Long,
    val kind: LabelKind,
    val x: Double,
    val y: Double,
    val priority: Int,
    val boxes: List<CollisionBox>,
    val display: LabelDisplay = LabelDisplay.IF_SPACE,
    val text: LabelText? = null,
    val path: DoubleArray? = null,
) {

    init {
        require(x.isFinite()
            && y.isFinite()
        ) {
            "LabelCandidate(), id $id, anchor $x, $y"
        }
        require((kind == LabelKind.PATH_TEXT) == (path != null)) {
            "LabelCandidate(), id $id, kind $kind, path ${path != null}"
        }
        require(path == null
            || (path.size >= 4 && path.size % 2 == 0)
        ) {
            "LabelCandidate(), id $id, path of ${path?.size} values"
        }
    }
}

enum class LabelKind {

    /**
     * A text or symbol at a point; stays upright.
     */
    POINT,

    /**
     * A symbol along a line, turning with the map.
     */
    LINE_SYMBOL,

    /**
     * A text along a path, flipped so it never reads upside down.
     */
    PATH_TEXT,
}

/**
 * How a candidate takes part in collisions (Mapsforge `Display`).
 */
enum class LabelDisplay {

    /**
     * Placed when it clashes with no placed [IF_SPACE] candidate.
     */
    IF_SPACE,

    /**
     * Clashes with other [ORDER] candidates only.
     */
    ORDER,

    /**
     * Always placed; never blocks another candidate.
     */
    ALWAYS,
}

/**
 * A label's text and its format-neutral style.
 *
 * @param fillArgb text colour as ARGB
 * @param haloArgb outline colour as ARGB, drawn only when [haloWidthPx] is above 0
 */
data class LabelText(
    val value: String,
    val sizePx: Float,
    val fillArgb: Int,
    val haloArgb: Int = 0,
    val haloWidthPx: Float = 0.0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
) {

    init {
        require(sizePx > 0.0f
            && sizePx.isFinite()
            && haloWidthPx >= 0.0f
            && haloWidthPx.isFinite()
        ) {
            "LabelText(), '$value', size $sizePx, halo $haloWidthPx"
        }
    }
}

/**
 * A rectangle a candidate occupies, in screen pixels, centered at an offset from its anchor and
 * turned by [angleDeg] clockwise. Edges that touch count as a clash.
 */
data class CollisionBox(
    val centerX: Double,
    val centerY: Double,
    val halfWidth: Double,
    val halfHeight: Double,
    val angleDeg: Double = 0.0,
    val alignment: BoxAlignment = BoxAlignment.VIEWPORT,
) {

    init {
        require(centerX.isFinite()
            && centerY.isFinite()
            && angleDeg.isFinite()
        ) {
            "CollisionBox(), center $centerX, $centerY, angle $angleDeg"
        }
        require(halfWidth >= 0.0
            && halfWidth.isFinite()
            && halfHeight >= 0.0
            && halfHeight.isFinite()
        ) {
            "CollisionBox(), half size $halfWidth x $halfHeight"
        }
    }
}

enum class BoxAlignment {

    /**
     * Offset and angle are in the screen frame: the box stays upright when the map turns.
     */
    VIEWPORT,

    /**
     * Offset and angle are in the map frame: the box turns with the map.
     */
    MAP,
}
