/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.symbol.MvtIcon
import com.asamm.vectormaps.mvt.symbol.MvtText

/**
 * Platform drawing surface of the MVT renderer, in screen pixels with a top-left origin. The AWT
 * target is in `formats/mvt/awt`; Android follows.
 */
interface MvtRenderTarget {

    val widthPx: Int

    val heightPx: Int

    fun drawBackground(color: Color)

    /**
     * Runs `block` on the platform canvas drawn on, for host drawing such as hillshading: a
     * `java.awt.Graphics2D` copy, or the `android.graphics.Canvas` with its state restored after.
     */
    fun withPlatformCanvas(block: (Any) -> Unit)

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
     * Draws the lines of `text` centred on (`x`, `y`), each line centred, halo below the fill.
     */
    fun drawText(text: MvtText, x: Float, y: Float)

    /**
     * Draws `text` along `path` (x, y pairs, from its first point), centred on its length and on
     * the line, each glyph turned with the path.
     */
    fun drawTextAlongPath(text: MvtText, path: FloatArray)

    /**
     * Draws `icon` centred on (`x`, `y`), `icon.scale` times its sprite size, turned clockwise by
     * `rotateDeg`; nothing for an unknown image.
     */
    fun drawIcon(icon: MvtIcon, x: Float, y: Float, rotateDeg: Float)

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
