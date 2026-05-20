/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import com.asamm.garminimg.style.Bitmap
import com.asamm.garminimg.style.Color
import com.asamm.garminimg.style.Style

/**
 * Platform-neutral rendering surface. The renderer dispatches one method per
 * [com.asamm.garminimg.model.MapObject] it draws. Concrete impls translate
 * these calls into platform-specific draw operations:
 *
 * - **AwtRenderTarget** (jvmMain) — backs onto `java.awt.Graphics2D`.
 * - **AndroidRenderTarget** (androidMain) — backs onto
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
     * Default no-op so existing targets compile; override for proper
     * curve-following text.
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
