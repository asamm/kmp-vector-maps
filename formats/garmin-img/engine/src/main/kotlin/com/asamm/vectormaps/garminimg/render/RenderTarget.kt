/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.style.Bitmap
import com.asamm.vectormaps.garminimg.style.Color
import com.asamm.vectormaps.garminimg.style.Style

/**
 * Platform-neutral rendering surface. The renderer dispatches one method per
 * [com.asamm.vectormaps.garminimg.model.MapObject] it draws. Concrete impls translate
 * these calls into platform-specific draw operations:
 *
 * - **AwtRenderTarget** (`formats/garmin-img/awt`) — backs onto `java.awt.Graphics2D`.
 * - **AndroidRenderTarget** (`formats/garmin-img/android`) — backs onto
 *   `android.graphics.Canvas`.
 *
 * # Coordinate system
 *
 * All coordinates the renderer hands to a [RenderTarget] are in **screen
 * pixels** — the renderer owns the projection from Garmin lat/lon to screen
 * XY. Top-left origin. X increases right, Y increases down.
 *
 * # Colour and stroke conventions
 *
 * Colours are 32-bit ARGB ([Color]). Stroke widths are in logical pixels at
 * device-pixel-ratio 1.0; the platform impl is responsible for scaling them
 * by the device's actual DPI before drawing.
 *
 * # Lifecycle
 *
 * The renderer:
 * 1. Calls [beginFrame] once at the start.
 * 2. Calls [drawPolygon] / [drawPolyline] / [drawPoint] / [drawText] in
 *    draw order (polygons first, then polylines, then points + labels on top).
 * 3. Calls [endFrame] once at the end. The platform impl flushes / commits.
 *
 * Targets must accept zero-length point arrays without crashing — the parser
 * occasionally yields degenerate records for malformed IMGs.
 */
interface RenderTarget {

    /**
     * Surface dimensions in screen pixels. Used by the renderer's coordinate
     * transform.
     */
    val widthPx: Int
    val heightPx: Int

    /**
     * Multiplier the target applies to TYP-driven pixel sizes — POI icons,
     * line/polygon pattern tiles, font sizes, stroke widths. The renderer
     * uses this to compute label collision bounds in the *rendered* size
     * (not the logical TYP size), so collision matches what is drawn.
     *
     * Defaults to 1.0 — implementations that compensate for high-DPI
     * displays override.
     */
    val renderScale: Float
        get() = 1.0f

    /**
     * Called once at the start of a render pass. Implementations clear /
     * fill the canvas with [backgroundColor] (transparent by default).
     */
    fun beginFrame(backgroundColor: Color = Color.Transparent)

    /**
     * Draws a closed polygon described by [Style.PolygonStyle].
     *
     * @param xs x-coordinates in screen pixels, parallel to [ys].
     * @param ys y-coordinates in screen pixels.
     */
    fun drawPolygon(xs: FloatArray, ys: FloatArray, style: Style.PolygonStyle)

    /**
     * Draws an open polyline. Borders are drawn beneath the foreground stroke
     * if [Style.LineStyle.borderColor] is non-null.
     *
     * Two-pass callers (the renderer at junction-clean mode) instead use
     * [drawPolylineBorder] + [drawPolylineStroke] separately so every
     * border lands before any centre stroke is laid down — that's what
     * keeps roads continuous across intersections instead of leaving
     * dark blobs at every junction.
     */
    fun drawPolyline(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        drawPolylineBorder(xs, ys, style)
        drawPolylineStroke(xs, ys, style)
    }

    /**
     * Draws just the polyline's border casing (no centre stroke). No-op when
     * the style has no border colour or `borderWidth <= strokeWidth`.
     */
    fun drawPolylineBorder(xs: FloatArray, ys: FloatArray, style: Style.LineStyle)

    /**
     * Draws just the polyline's foreground centre stroke (no border).
     */
    fun drawPolylineStroke(xs: FloatArray, ys: FloatArray, style: Style.LineStyle)

    /**
     * Draws a point feature. If `style.icon` is non-null, the bitmap is
     * blitted centred on `(x, y)`; if null, a small default marker glyph is
     * drawn (target's choice — typically a coloured dot).
     */
    fun drawPoint(x: Float, y: Float, style: Style.PointStyle)

    /**
     * Draws a text label centred on `(x, y)` (or anchored per
     * [TextAnchor] if specified).
     */
    fun drawText(
        x: Float,
        y: Float,
        text: String,
        color: Color,
        outlineColor: Color? = null,
        fontSize: Float = 12f,
        anchor: TextAnchor = TextAnchor.CENTER,
    )

    /**
     * Width in pixels of `text` as [drawText] draws it at `fontSize`; the default estimates it
     * from the character count, 0.55 × the drawn size each, at least 8 px as the targets draw.
     */
    fun measureText(text: String, fontSize: Float): Float {
        return text.length * (fontSize * renderScale).coerceAtLeast(MIN_TEXT_PX) * 0.55f
    }

    /**
     * Draws a bitmap centred on `(x, y)`.
     */
    fun drawBitmap(x: Float, y: Float, bitmap: Bitmap)

    /**
     * Draws `text` curving along the polyline given by `(xs, ys)`. Each
     * character is positioned and rotated to follow the path tangent at
     * its centre, so road names bend with the road. Implementations
     * should fall back to a straight horizontal placement at the path
     * midpoint when the path is too short to fit the rendered text.
     *
     * The default draws the text straight and centred at the middle vertex;
     * override for curve-following text.
     */
    fun drawTextOnPath(
        xs: FloatArray,
        ys: FloatArray,
        text: String,
        color: Color,
        outlineColor: Color? = null,
        fontSize: Float = 12f,
    ) {
        if (xs.isNotEmpty() && ys.isNotEmpty() && text.isNotEmpty()) {
            val mid = xs.size / 2
            drawText(xs[mid], ys[mid], text, color, outlineColor, fontSize, TextAnchor.CENTER)
        }
    }

    /**
     * Called once at the end of a render pass. Implementations flush, commit,
     * or otherwise finalise the underlying surface.
     */
    fun endFrame()

    companion object {

        /**
         * The smallest text size in pixels the targets draw and measure.
         */
        const val MIN_TEXT_PX: Float = 8f
    }
}

/**
 * Where the `(x, y)` anchor sits relative to the text bounds.
 */
enum class TextAnchor {
    /** `(x, y)` is the centre of the text bounds. */
    CENTER,

    /** `(x, y)` is the start of the baseline (left for LTR scripts). */
    BASELINE_START,
}
