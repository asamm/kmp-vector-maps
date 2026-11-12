/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color

/**
 * Platform drawing surface of the MVT renderer, in screen pixels with a top-left origin. The AWT
 * target is in `formats/mvt/awt`; Android follows.
 */
interface MvtRenderTarget {

    val widthPx: Int

    val heightPx: Int

    fun drawBackground(color: Color)

    /**
     * Fills the rings of `path` with the non-zero rule, so MVT holes (opposite winding) stay open.
     */
    fun fillPath(path: MvtPath, color: Color, antialias: Boolean)

    /**
     * Fills the rings of `path` with the sprite image `pattern`, repeated from the world pixel
     * origin, so neighbouring tiles join; `tileOriginX`, `tileOriginY` are this tile's top-left
     * world pixel. Does nothing for an unknown pattern.
     */
    fun fillPathPattern(
        path: MvtPath,
        pattern: String,
        opacity: Float,
        antialias: Boolean,
        tileOriginX: Long,
        tileOriginY: Long,
    )

    /**
     * Strokes every part of `path`; `dash` holds on and off lengths in pixels, or is null.
     */
    fun strokePath(
        path: MvtPath,
        color: Color,
        width: Float,
        cap: LineCap,
        join: LineJoin,
        dash: FloatArray?,
    )

    /**
     * Fills a circle of `radius` and strokes `strokeWidth` outside it.
     */
    fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Color,
        strokeWidth: Float,
        strokeColor: Color,
    )
}

/**
 * Screen-space geometry: x, y pairs in [coords]; part `i` ends before `partEnds[i]`.
 */
class MvtPath(
    val coords: FloatArray,
    val partEnds: IntArray,
    val closed: Boolean,
)

enum class LineCap {
    BUTT,
    ROUND,
    SQUARE,
}

enum class LineJoin {
    MITER,
    ROUND,
    BEVEL,
}
