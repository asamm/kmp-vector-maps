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
 * - **AndroidCanvasRenderTarget** (androidMain, Phase C.7) — backs onto
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
     */
    fun drawPolyline(xs: FloatArray, ys: FloatArray, style: Style.LineStyle)

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
