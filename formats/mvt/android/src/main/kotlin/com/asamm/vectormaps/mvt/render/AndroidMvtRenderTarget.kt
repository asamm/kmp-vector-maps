/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.symbol.MvtIcon
import com.asamm.vectormaps.mvt.symbol.MvtText
import kotlin.math.hypot

/**
 * [MvtRenderTarget] over a `Bitmap` through `Canvas`, antialiased; patterns and icons come from
 * `sprite`, text from `fonts`, both shared by the targets of one style. One thread draws a target.
 */
class AndroidMvtRenderTarget(
    val bitmap: Bitmap,
    private val sprite: AndroidSprite? = null,
    private val fonts: AndroidFonts? = null,
) : MvtRenderTarget {

    private val canvas = Canvas(bitmap)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // MapLibre's default line-miter-limit
        strokeMiter = 2f
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val matrix = Matrix()

    // reused per draw: Path holds native memory, and one thread draws a target
    private val shape = Path()

    private val along = Path()

    private val metrics = Paint.FontMetrics()

    override val widthPx: Int = bitmap.width

    override val heightPx: Int = bitmap.height

    override fun clear() {
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
    }

    override fun drawBackground(color: Color) {
        canvas.drawColor(color.argb)
    }

    override fun withPlatformCanvas(block: (Any) -> Unit) {
        val saved = canvas.save()
        try {
            block(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    override fun fillPath(path: MvtPath, color: Color, antialias: Boolean) {
        fill.shader = null
        fill.isAntiAlias = antialias
        fill.color = color.argb
        canvas.drawPath(toPath(path), fill)
        fill.isAntiAlias = true
    }

    override fun fillPathPattern(
        path: MvtPath,
        pattern: String,
        opacity: Float,
        antialias: Boolean,
        tileOriginX: Long,
        tileOriginY: Long,
    ) {
        val tile = sprite?.image(pattern) ?: return
        val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        matrix.setTranslate(
            -Math.floorMod(tileOriginX, tile.width.toLong()).toFloat(),
            -Math.floorMod(tileOriginY, tile.height.toLong()).toFloat(),
        )
        shader.setLocalMatrix(matrix)
        fill.shader = shader
        fill.isAntiAlias = antialias
        fill.color = android.graphics.Color.BLACK
        fill.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawPath(toPath(path), fill)
        fill.shader = null
        fill.isAntiAlias = true
    }

    override fun strokePath(
        path: MvtPath,
        color: Color,
        width: Float,
        cap: LineCap,
        join: LineJoin,
        dash: FloatArray?,
    ) {
        stroke.color = color.argb
        stroke.strokeWidth = width
        stroke.strokeCap = when (cap) {
            LineCap.BUTT -> Paint.Cap.BUTT
            LineCap.ROUND -> Paint.Cap.ROUND
            LineCap.SQUARE -> Paint.Cap.SQUARE
        }
        stroke.strokeJoin = when (join) {
            LineJoin.MITER -> Paint.Join.MITER
            LineJoin.ROUND -> Paint.Join.ROUND
            LineJoin.BEVEL -> Paint.Join.BEVEL
        }
        stroke.pathEffect = dash?.takeIf { d -> d.isNotEmpty() && d.any { it > 0f } }?.let {
            // Android takes pairs of on and off; an odd list repeats, as in AWT
            DashPathEffect(if (it.size % 2 == 0) it else it + it, 0f)
        }
        canvas.drawPath(toPath(path), stroke)
        stroke.pathEffect = null
    }

    override fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Color,
        strokeWidth: Float,
        strokeColor: Color,
    ) {
        fill.color = color.argb
        canvas.drawCircle(x, y, radius, fill)
        if (strokeWidth > 0f) {
            // the stroke lies outside the radius, as in MapLibre
            stroke.color = strokeColor.argb
            stroke.strokeWidth = strokeWidth
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.strokeJoin = Paint.Join.MITER
            canvas.drawCircle(x, y, radius + strokeWidth / 2f, stroke)
        }
    }

    override fun drawText(text: MvtText, x: Float, y: Float) {
        val fonts = fonts ?: return
        fonts.apply(
            paint = this.text,
            font = text.font,
            sizePx = text.sizePx,
            letterSpacingPx = text.letterSpacingPx,
        )
        this.text.textAlign = Paint.Align.CENTER
        this.text.getFontMetrics(metrics)
        // each line box is a line height tall, its glyphs centred in it
        val first = y - text.height / 2f + text.lineHeightPx / 2f -
            (metrics.ascent + metrics.descent) / 2f
        // all halos first, so no line's halo covers another's glyphs
        if (hasHalo(text)) {
            halo(text)
            for ((i, line) in text.lines.withIndex()) {
                canvas.drawText(line, x, first + i * text.lineHeightPx, this.text)
            }
        }
        glyphs(text)
        for ((i, line) in text.lines.withIndex()) {
            canvas.drawText(line, x, first + i * text.lineHeightPx, this.text)
        }
    }

    override fun drawTextAlongPath(text: MvtText, path: FloatArray) {
        val fonts = fonts ?: return
        val line = text.lines.firstOrNull() ?: return
        if (path.size < 4) {
            return
        }
        fonts.apply(
            paint = this.text,
            font = text.font,
            sizePx = text.sizePx,
            letterSpacingPx = text.letterSpacingPx,
        )
        this.text.textAlign = Paint.Align.LEFT
        this.text.getFontMetrics(metrics)
        along.rewind()
        along.moveTo(path[0], path[1])
        var length = 0f
        for (i in 2 until path.size step 2) {
            along.lineTo(path[i], path[i + 1])
            length += hypot(path[i] - path[i - 2], path[i + 1] - path[i - 1])
        }
        val start = (length - this.text.measureText(line)) / 2f
        // the glyphs centre on the line, shifted across it by text-offset
        val across = -(metrics.ascent + metrics.descent) / 2f + text.centerY
        if (hasHalo(text)) {
            halo(text)
            canvas.drawTextOnPath(line, along, start, across, this.text)
        }
        glyphs(text)
        canvas.drawTextOnPath(line, along, start, across, this.text)
    }

    override fun drawIcon(icon: MvtIcon, x: Float, y: Float, rotateDeg: Float) {
        val tile = sprite?.image(icon.name) ?: return
        val w = icon.width * icon.scale
        val h = icon.height * icon.scale
        matrix.reset()
        matrix.postScale(w / tile.width, h / tile.height)
        matrix.postTranslate(-w / 2f, -h / 2f)
        matrix.postRotate(rotateDeg)
        matrix.postTranslate(x, y)
        canvas.drawBitmap(tile, matrix, image)
    }

    private fun hasHalo(text: MvtText): Boolean {
        return text.haloWidthPx > 0f
            && text.haloColor.alpha > 0
    }

    // the halo as a round stroke around the glyphs
    private fun halo(text: MvtText) {
        this.text.style = Paint.Style.STROKE
        this.text.strokeWidth = text.haloWidthPx * 2f
        this.text.strokeJoin = Paint.Join.ROUND
        this.text.strokeCap = Paint.Cap.ROUND
        this.text.color = text.haloColor.argb
    }

    private fun glyphs(text: MvtText) {
        this.text.style = Paint.Style.FILL
        this.text.color = text.color.argb
    }

    private fun toPath(path: MvtPath): Path {
        val p = shape
        p.rewind()
        p.fillType = Path.FillType.WINDING
        var start = 0
        for (end in path.partEnds) {
            if (end - start >= 2) {
                p.moveTo(path.coords[start], path.coords[start + 1])
                var i = start + 2
                while (i < end) {
                    p.lineTo(path.coords[i], path.coords[i + 1])
                    i += 2
                }
                if (path.closed) {
                    p.close()
                }
            }
            start = end
        }
        return p
    }
}
