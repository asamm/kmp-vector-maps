/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import com.asamm.garminimg.style.Bitmap
import com.asamm.garminimg.style.Style
import kotlin.math.atan2
import kotlin.math.hypot
import android.graphics.Bitmap as AndroidBitmap
import com.asamm.garminimg.style.Color as GarminColor

/**
 * Android [Canvas]-backed [RenderTarget].
 *
 * Mirrors the JVM-side [AwtRenderTarget] (in `jvmMain`) one-for-one — same
 * fill / pattern / dash / curve-text behaviour — but using Android's
 * native graphics primitives:
 *
 * - [BitmapShader] in place of AWT's `TexturePaint` for tiled patterns,
 *   with a [Matrix] local-matrix for phase shifts and high-DPI scale.
 * - [DashPathEffect] in place of AWT's `BasicStroke` dash array.
 * - [Canvas.drawTextOnPath] for curve-following labels.
 *
 * The target owns a [AndroidBitmap] and a [Canvas] wrapping it. Callers
 * pull the bitmap after [endFrame] for compositing into a Locus tile,
 * caching layer, or whatever the host needs.
 *
 * Render-scale defaults to **1.0** here (versus 1.5 on the desktop AWT
 * target). TYP icon and pattern bitmaps are authored at handheld DPI to
 * begin with, so on a phone they already read at the intended size;
 * tablets/foldables with unusually-high DPI can pass a larger scale
 * explicitly.
 *
 * @param antiAliased if true, enables antialiasing for shapes and text.
 * @param renderScale multiplier applied to TYP-driven pixel sizes (pattern tile dimensions,
 *   point-icon dimensions, font sizes, stroke widths). See [RenderTarget.renderScale]. Defaults
 *   to 1.0 — the host (Locus) is expected to handle device-density scaling externally by sizing
 *   the output bitmap appropriately.
 */
class AndroidRenderTarget(
    override val widthPx: Int,
    override val heightPx: Int,
    private val antiAliased: Boolean = true,
    override val renderScale: Float = 1.0f,
) : RenderTarget {

    /**
     * Backing bitmap. Populated incrementally during a render pass.
     */
    val bitmap: AndroidBitmap = AndroidBitmap.createBitmap(
        widthPx,
        heightPx,
        AndroidBitmap.Config.ARGB_8888,
    )

    private val canvas: Canvas = Canvas(bitmap)

    /**
     * Reusable paint — its state (color, stroke, shader, antialias) is
     * mutated per draw call. Reusing one Paint across the whole frame
     * avoids per-call allocation hot-spots that show up in profiles
     * on large IMGs.
     */
    private val paint: Paint = Paint().apply {
        isAntiAlias = antiAliased
    }

    /**
     * Reusable path — cleared and re-used per polygon / polyline.
     */
    private val path: Path = Path()

    override fun beginFrame(backgroundColor: GarminColor) {
        if (backgroundColor.alpha == 0) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(backgroundColor.argb)
        }
    }

    override fun drawPolygon(xs: FloatArray, ys: FloatArray, style: Style.PolygonStyle) {
        if (xs.size < 3) {
            return
        }
        buildPath(xs, ys, closed = true)
        // Mirror AWT: lay down solid fill first (so polygons with sparse
        // patterns — parks, marshes — show their ground colour through
        // the pattern's transparent gaps), then overlay the pattern.
        if (style.fillColor.alpha != 0) {
            resetPaintForFill()
            paint.color = style.fillColor.argb
            canvas.drawPath(path, paint)
        }
        val pattern = style.pattern
        if (pattern != null) {
            val tile = patternCache.getOrPut(pattern) { pattern.toAndroidBitmap() }
            resetPaintForFill()
            val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val matrix = Matrix()
            matrix.setScale(renderScale, renderScale)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawPath(path, paint)
            paint.shader = null
        }
        // Suppress the polygon outline when a TYP pattern is active —
        // same rationale as AwtRenderTarget.
        val strokeColor = style.strokeColor
        if (strokeColor != null && pattern == null) {
            resetPaintForStroke(style.strokeWidth * renderScale)
            paint.color = strokeColor.argb
            // Match the AWT polygon outline: BUTT cap + MITER join +
            // STROKE_MITER_LIMIT so sharp corners bevel the same way
            // on both backends.
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeJoin = Paint.Join.MITER
            paint.strokeMiter = STROKE_MITER_LIMIT
            canvas.drawPath(path, paint)
        }
    }

    /**
     * Pattern → [AndroidBitmap] cache. TYP patterns are interned by
     * the parser, so a small identity-keyed map keeps per-polygon
     * cost down to a single hash lookup.
     */
    private val patternCache: MutableMap<Bitmap, AndroidBitmap> = HashMap()

    override fun drawPolylineBorder(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val borderColor = style.borderColor ?: return
        if (style.borderWidth <= style.strokeWidth) {
            return
        }
        buildPath(xs, ys, closed = false)
        resetPaintForStroke(style.borderWidth * renderScale)
        paint.color = borderColor.argb
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeMiter = STROKE_MITER_LIMIT
        canvas.drawPath(path, paint)
    }

    override fun drawPolylineStroke(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val pattern = style.pattern
        if (pattern != null) {
            if (pattern.height == 1) {
                drawDashedFromPattern(xs, ys, pattern, style)
                return
            }
            // Hairline guard — same logic as AWT.
            val scale = if (style.strokeWidth <= 1f) 1f else renderScale
            drawPatternedPolyline(xs, ys, pattern, scale)
            return
        }
        buildPath(xs, ys, closed = false)
        val w = style.strokeWidth * renderScale
        resetPaintForStroke(w)
        paint.color = style.strokeColor.argb
        val dash = style.dashPattern
        if (dash != null && dash.isNotEmpty()) {
            // Dash lengths are in stroke-width units; scale them too so
            // dashes stay proportional to the now-thicker line.
            val scaledDash = FloatArray(dash.size) { dash[it] * renderScale }
            paint.pathEffect = DashPathEffect(scaledDash, 0f)
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeJoin = Paint.Join.MITER
            paint.strokeMiter = STROKE_MITER_LIMIT
            canvas.drawPath(path, paint)
            paint.pathEffect = null
        } else {
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.MITER
            paint.strokeMiter = STROKE_MITER_LIMIT
            canvas.drawPath(path, paint)
        }
    }

    /**
     * Renders a polyline whose TYP "pattern" is a single-row dash bitmap.
     * Mirrors [AwtRenderTarget.drawDashedFromPattern] — extracts dash
     * run-lengths from the bitmap row, derives the dominant colour from
     * the first opaque pixel, then strokes the polyline with a
     * [DashPathEffect] using the equivalent on/off array.
     */
    private fun drawDashedFromPattern(
        xs: FloatArray,
        ys: FloatArray,
        pattern: Bitmap,
        style: Style.LineStyle,
    ) {
        val w = pattern.width
        val rgba = pattern.rgba
        val runs = ArrayList<Int>(8)
        var dashColor = 0
        var hasColor = false
        var i = 0
        var firstOnIndex = -1
        while (i < w) {
            val opaque = (rgba[i * 4 + 3].toInt() and 0xFF) != 0
            var j = i
            while (j < w) {
                val o = (rgba[j * 4 + 3].toInt() and 0xFF) != 0
                if (o != opaque) break
                j++
            }
            val runLen = j - i
            if (opaque && !hasColor) {
                val r = rgba[i * 4].toInt() and 0xFF
                val g = rgba[i * 4 + 1].toInt() and 0xFF
                val b = rgba[i * 4 + 2].toInt() and 0xFF
                dashColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                hasColor = true
                firstOnIndex = runs.size
            }
            runs.add(runLen)
            i = j
        }
        if (!hasColor || runs.isEmpty()) {
            return
        }
        if (firstOnIndex > 0) {
            val rotated = ArrayList<Int>(runs.size)
            rotated.addAll(runs.subList(firstOnIndex, runs.size))
            rotated.addAll(runs.subList(0, firstOnIndex))
            runs.clear()
            runs.addAll(rotated)
        }
        buildPath(xs, ys, closed = false)
        resetPaintForStroke((style.strokeWidth * renderScale).coerceAtLeast(1f))
        paint.color = dashColor
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeMiter = STROKE_MITER_LIMIT
        if (runs.size == 1) {
            // Fully opaque row — a malformed / hand-crafted TYP can ship
            // a "dash" pattern with no gap pixels. Mutating a 1-element
            // list (as we do below for odd sizes ≥ 3) would IOOB. Stroke
            // solid and return.
            canvas.drawPath(path, paint)
            return
        }
        if (runs.size % 2 == 1) {
            runs[0] = runs[0] + runs.removeAt(runs.size - 1)
        }
        val dashArray = FloatArray(runs.size) { runs[it] * renderScale }
        paint.pathEffect = DashPathEffect(dashArray, 0f)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    /**
     * Renders a polyline as a textured stroke: each segment is filled
     * with the [pattern] tile rotated to follow the segment angle.
     *
     * Direct port of [AwtRenderTarget]'s `drawPatternedPolyline`:
     *
     * - Tile y-origin at `-tileH/2` keeps asymmetric tiles (rail
     *   gap-tie patterns, fence rails+ticks) aligned with the fill
     *   rectangle.
     * - Texture phase shift via the shader's local matrix at
     *   `-carriedOffset` instead of extending the fill rectangle
     *   backwards — clips perpendicular ticks to the segment's
     *   strict `[0, len]` span so polyline corners don't sprout ticks
     *   past the geometry.
     */
    private fun drawPatternedPolyline(
        xs: FloatArray,
        ys: FloatArray,
        pattern: Bitmap,
        scale: Float = renderScale,
    ) {
        val tile = patternCache.getOrPut(pattern) { pattern.toAndroidBitmap() }
        val tileH = tile.height * scale
        var carriedOffset = 0f
        val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val matrix = Matrix()
        resetPaintForFill()
        paint.shader = shader
        for (i in 0 until xs.size - 1) {
            val x0 = xs[i]
            val y0 = ys[i]
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < 0.5f) {
                continue
            }
            val angleDeg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / Math.PI).toFloat()
            val saveCount = canvas.save()
            canvas.translate(x0, y0)
            canvas.rotate(angleDeg)
            // Local matrix: tile-space → canvas-local (segment-aligned)
            // space. Scale up to renderScale, then translate so the
            // tile's (0,0) corner lands at (-carriedOffset, -tileH/2)
            // in the rotated frame. This keeps long patterns
            // (dashes, crossties) in phase across segment joins
            // without overlapping the previous segment's rectangle.
            matrix.setScale(scale, scale)
            matrix.postTranslate(-carriedOffset, -tileH / 2f)
            shader.setLocalMatrix(matrix)
            canvas.drawRect(0f, -tileH / 2f, len, tileH / 2f, paint)
            canvas.restoreToCount(saveCount)

            val tileW = tile.width * scale
            carriedOffset = ((carriedOffset + len) % tileW)
        }
        paint.shader = null
    }

    override fun drawPoint(x: Float, y: Float, style: Style.PointStyle) {
        val icon = style.icon
        if (icon != null) {
            drawBitmap(x, y, icon)
        } else {
            resetPaintForFill()
            paint.color = style.labelColor.argb
            canvas.drawCircle(x, y, 2f, paint)
        }
    }

    override fun drawText(
        x: Float,
        y: Float,
        text: String,
        color: GarminColor,
        outlineColor: GarminColor?,
        fontSize: Float,
        anchor: TextAnchor,
    ) {
        if (text.isEmpty()) {
            return
        }
        resetPaintForFill()
        paint.textSize = (fontSize * renderScale).coerceAtLeast(8f)
        val w = paint.measureText(text)
        val fm = paint.fontMetrics
        val ascent = -fm.ascent
        val descent = fm.descent
        val (drawX, drawY) = when (anchor) {
            TextAnchor.CENTER -> Pair(x - w / 2f, y + ascent / 2f - descent / 2f)
            TextAnchor.BASELINE_START -> Pair(x, y)
        }
        if (outlineColor != null) {
            paint.color = outlineColor.argb
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) {
                        continue
                    }
                    canvas.drawText(text, drawX + dx, drawY + dy, paint)
                }
            }
        }
        paint.color = color.argb
        canvas.drawText(text, drawX, drawY, paint)
    }

    override fun drawTextOnPath(
        xs: FloatArray,
        ys: FloatArray,
        text: String,
        color: GarminColor,
        outlineColor: GarminColor?,
        fontSize: Float,
    ) {
        if (text.isEmpty() || xs.size < 2) {
            return
        }
        resetPaintForFill()
        paint.textSize = (fontSize * renderScale).coerceAtLeast(8f)

        // Total path length and net direction — same shortest-path
        // fallback as AWT, and the same left-to-right readability
        // flip when the polyline runs net-westwards.
        var totalLen = 0.0
        for (i in 1 until xs.size) {
            val dx = (xs[i] - xs[i - 1]).toDouble()
            val dy = (ys[i] - ys[i - 1]).toDouble()
            totalLen += hypot(dx, dy)
        }
        val textWidth = paint.measureText(text)
        if (textWidth <= 0f) {
            return
        }
        if (textWidth > totalLen - 4.0) {
            val midIdx = xs.size / 2
            drawText(xs[midIdx], ys[midIdx], text, color, outlineColor, fontSize, TextAnchor.CENTER)
            return
        }
        val reversed = (xs.last() - xs.first()) < 0f
        // Lift text above the line by half a stroke + breathing gap,
        // matching the AWT renderer's perpendicular offset.
        val lineLift = (renderScale * 2f) + 1f
        val pathLocal = Path()
        if (reversed) {
            pathLocal.moveTo(xs.last(), ys.last())
            for (i in xs.size - 2 downTo 0) {
                pathLocal.lineTo(xs[i], ys[i])
            }
        } else {
            pathLocal.moveTo(xs[0], ys[0])
            for (i in 1 until xs.size) {
                pathLocal.lineTo(xs[i], ys[i])
            }
        }
        val hOffset = ((totalLen - textWidth) / 2.0).toFloat()
        if (outlineColor != null) {
            paint.color = outlineColor.argb
            // Halo: 8-direction offset draws. drawTextOnPath doesn't
            // support per-glyph styling, so we draw the whole string
            // shifted along the path's normal and tangent. dx is
            // along-path; dy is perpendicular (Canvas convention).
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) {
                        continue
                    }
                    canvas.drawTextOnPath(
                        text,
                        pathLocal,
                        hOffset + dx,
                        -lineLift + dy,
                        paint,
                    )
                }
            }
        }
        paint.color = color.argb
        canvas.drawTextOnPath(text, pathLocal, hOffset, -lineLift, paint)
    }

    override fun drawBitmap(x: Float, y: Float, bitmap: Bitmap) {
        val img = patternCache.getOrPut(bitmap) { bitmap.toAndroidBitmap() }
        val w = (bitmap.width * renderScale).toInt().coerceAtLeast(bitmap.width)
        val h = (bitmap.height * renderScale).toInt().coerceAtLeast(bitmap.height)
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        val left = (x - w / 2f).toInt()
        val top = (y - h / 2f).toInt()
        val dst = Rect(left, top, left + w, top + h)
        resetPaintForFill()
        canvas.drawBitmap(img, src, dst, paint)
    }

    override fun endFrame() {
        // Canvas+Bitmap don't need an explicit flush — pixels are
        // committed as draw calls happen. Method kept for interface
        // contract.
    }

    private fun buildPath(xs: FloatArray, ys: FloatArray, closed: Boolean) {
        path.rewind()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) {
            path.lineTo(xs[i], ys[i])
        }
        if (closed) {
            path.close()
        }
    }

    private fun resetPaintForFill() {
        paint.reset()
        paint.isAntiAlias = antiAliased
        paint.style = Paint.Style.FILL
    }

    private fun resetPaintForStroke(width: Float) {
        paint.reset()
        paint.isAntiAlias = antiAliased
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
    }

    private fun Bitmap.toAndroidBitmap(): AndroidBitmap {
        val bmp = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        // Android's ARGB_8888 expects an Int[] in `setPixels`; convert
        // from our row-major RGBA byte buffer once at first encounter.
        val pixels = IntArray(width * height)
        var i = 0
        for (idx in 0 until width * height) {
            val r = rgba[i].toInt() and 0xFF
            val g = rgba[i + 1].toInt() and 0xFF
            val b = rgba[i + 2].toInt() and 0xFF
            val a = rgba[i + 3].toInt() and 0xFF
            pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private companion object {

        /**
         * Miter limit for stroked polylines. Java's default of 10 is too
         * generous for crisp road outlines — at sharp angles miter spikes
         * shoot out past the road. 4 trims those spikes back to bevelled
         * joints while keeping moderate-angle joints sharp. Matches the
         * AWT renderer.
         */
        private const val STROKE_MITER_LIMIT: Float = 4f
    }
}
