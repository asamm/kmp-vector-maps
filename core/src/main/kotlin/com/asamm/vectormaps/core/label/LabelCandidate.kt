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
 * @param id stable identity, equal for the same label stored from neighbouring tiles, whose copies
 *  must then carry identical geometry; breaks priority ties, so it must not come from an identity
 *  hash
 * @param x anchor in world pixels, which [boxes] and same-text distances count from; for a path
 *  text the format picks a point, such as its path's first point or the middle of its text
 * @param boxes collision geometry relative to the anchor; empty never clashes
 * @param path [LabelKind.PATH_TEXT] only: the path as x, y pairs in world pixels, copied
 * @param direction [LabelKind.PATH_TEXT] only: which way the text runs along its path
 * @param sameTextRadiusPx a kept candidate with equal text closer than this, in world pixels, blocks
 *  this one (the larger of the two radii counts); 0 neither blocks nor is blocked
 * @param source the format's own object behind the candidate, for drawing it; placement ignores it
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
    path: DoubleArray? = null,
    val direction: PathTextDirection = PathTextDirection.UPRIGHT,
    val sameTextRadiusPx: Double = 0.0,
    val source: Any? = null,
) {

    val path: DoubleArray? = path?.copyOf()

    init {
        require(x.isFinite()
            && y.isFinite()
        ) {
            "LabelCandidate(), id $id, anchor $x, $y"
        }
        require(sameTextRadiusPx >= 0.0
            && sameTextRadiusPx.isFinite()
        ) {
            "LabelCandidate(), id $id, same-text radius $sameTextRadiusPx"
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
     * A text along a path, running as its [LabelCandidate.direction] says.
     */
    PATH_TEXT,
}

/**
 * How a candidate takes part in collisions (Mapsforge `Display`); at equal priority, later entries
 * draw above earlier ones, as in Mapsforge.
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
 * Which way a path text runs (Mapsforge `text-orientation`).
 */
enum class PathTextDirection {

    /**
     * Flipped so it never reads upside down (Mapsforge `auto`).
     */
    UPRIGHT,

    /**
     * Flipped so it always reads upside down (Mapsforge `auto_down`).
     */
    UPSIDE_DOWN,

    /**
     * From the path's first point to its last (Mapsforge `right`).
     */
    FORWARD,

    /**
     * From the path's last point to its first (Mapsforge `left`).
     */
    REVERSE,
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
