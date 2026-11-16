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
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.symbol.MvtIcon
import com.asamm.vectormaps.mvt.symbol.MvtText
import kotlin.math.atan2
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

    // the last dash pattern's effect, reused while a layer's lines share it (native memory)
    private var lastDash: FloatArray? = null

    private var lastDashEffect: DashPathEffect? = null

    private val measure = PathMeasure()

    private val position = FloatArray(2)

    private val tangent = FloatArray(2)

    // a turned path text's characters: their starts, and x, y, angle, advance of each
    private var charStarts = IntArray(CHARS)

    private var charPlaces = FloatArray(CHARS * 4)

    private val iconBox = RectF()

    // a tile's pattern fills share their pattern's shader
    private val shaders = HashMap<String, BitmapShader>()

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
        scale: Float,
    ) {
        val tile = sprite?.pattern(pattern) ?: return
        val shader = shaders.getOrPut(pattern) {
            BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        val factor = scale / sprite.pixelRatio(pattern)
        matrix.setScale(factor, factor)
        matrix.postTranslate(
            -patternOffset(tileOriginX, tile.width * factor),
            -patternOffset(tileOriginY, tile.height * factor),
        )
        shader.setLocalMatrix(matrix)
        fill.shader = shader
        fill.isAntiAlias = antialias
        // a scaled pattern samples between its pixels, as icons do
        fill.isFilterBitmap = true
        fill.color = android.graphics.Color.BLACK
        fill.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawPath(toPath(path), fill)
        fill.shader = null
        fill.isFilterBitmap = false
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
            dashEffect(it)
        }
        canvas.drawPath(toPath(path), stroke)
        stroke.pathEffect = null
    }

    private fun dashEffect(dash: FloatArray): DashPathEffect {
        lastDashEffect?.let {
            if (dash.contentEquals(lastDash)) {
                return it
            }
        }
        // Android takes pairs of on and off; an odd list repeats, as in AWT
        val effect = DashPathEffect(if (dash.size % 2 == 0) dash else dash + dash, 0f)
        lastDash = dash
        lastDashEffect = effect
        return effect
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
        val saved = canvas.save()
        canvas.rotate(text.rotateDeg, x, y)
        // all halos first, so no line's halo covers another's glyphs
        paint(text) {
            for ((i, line) in text.lines.withIndex()) {
                canvas.drawText(line, lineX(text, line, x), first + i * text.lineHeightPx, this.text)
            }
        }
        canvas.restoreToCount(saved)
    }

    // the centre of `line` in the box centred at `x`: text-justify moves a shorter line to a side
    private fun lineX(text: MvtText, line: String, x: Float): Float {
        if (text.justify == 0.5f
            || text.lines.size < 2
        ) {
            return x
        }
        // without the trailing letter spacing, as AndroidFonts measured text.width
        val width = this.text.measureText(line) - text.letterSpacingPx
        return x - text.width / 2f + (text.width - width) * text.justify + width / 2f
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
        // the glyphs centre on the line
        val baseline = -(metrics.ascent + metrics.descent) / 2f
        if (text.rotateDeg == 0f) {
            // shifted across the line by text-offset
            paint(text) {
                canvas.drawTextOnPath(line, along, start, baseline + text.centerY, this.text)
            }
            return
        }
        // drawTextOnPath cannot turn a glyph, so each character is placed on its own, unshaped
        measure.setPath(along, false)
        if (measure.length <= 0f) {
            return
        }
        // placed once, drawn for each halo ring and the fill
        val count = placeAlong(line = line, start = start)
        paint(text) {
            turnedAlong(text = text, line = line, count = count, baseline = baseline)
        }
    }

    // each character of `line` at its point on `along` into charStarts and charPlaces; the count
    private fun placeAlong(line: String, start: Float): Int {
        val count = line.codePointCount(0, line.length)
        if (charStarts.size <= count) {
            charStarts = IntArray(count + 1)
            charPlaces = FloatArray(count * 4)
        }
        var at = start
        var i = 0
        for (c in 0 until count) {
            val next = line.offsetByCodePoints(i, 1)
            val advance = this.text.measureText(line, i, next)
            val centre = at + advance / 2f
            // past either end the line goes on straight, as for glyphs measured a little wider
            val on = centre.coerceIn(0f, measure.length)
            measure.getPosTan(on, position, tangent)
            charStarts[c] = i
            charPlaces[c * 4] = position[0] + tangent[0] * (centre - on)
            charPlaces[c * 4 + 1] = position[1] + tangent[1] * (centre - on)
            charPlaces[c * 4 + 2] = Math.toDegrees(atan2(tangent[1], tangent[0]).toDouble()).toFloat()
            charPlaces[c * 4 + 3] = advance
            at += advance
            i = next
        }
        charStarts[count] = line.length
        return count
    }

    // the placed characters, shifted across the line by text-offset, then turned by text-rotate
    // about their centres
    private fun turnedAlong(text: MvtText, line: String, count: Int, baseline: Float) {
        for (c in 0 until count) {
            val saved = canvas.save()
            canvas.translate(charPlaces[c * 4], charPlaces[c * 4 + 1])
            canvas.rotate(charPlaces[c * 4 + 2])
            canvas.translate(0f, text.centerY)
            canvas.rotate(text.rotateDeg)
            canvas.drawText(line, charStarts[c], charStarts[c + 1], -charPlaces[c * 4 + 3] / 2f, baseline, this.text)
            canvas.restoreToCount(saved)
        }
    }

    override fun drawIcon(icon: MvtIcon, x: Float, y: Float, rotateDeg: Float) {
        val sprite = sprite ?: return
        val place = sprite.place(icon.name) ?: return
        val w = icon.width * icon.scale
        val h = icon.height * icon.scale
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotateDeg)
        iconBox.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        image.alpha = (icon.opacity * 255f).toInt()
        // straight from the sheet, so no icon is copied
        canvas.drawBitmap(sprite.sheet, place, iconBox, image)
        image.alpha = 255
        canvas.restoreToCount(saved)
    }

    // the halo as round strokes around the glyphs, outermost first, then the glyphs, each by `draw`
    private inline fun paint(text: MvtText, draw: () -> Unit) {
        if (text.hasHalo) {
            this.text.style = Paint.Style.STROKE
            this.text.strokeJoin = Paint.Join.ROUND
            this.text.strokeCap = Paint.Cap.ROUND
            for (ring in 0 until text.haloRings) {
                val radius = text.haloRingRadius(ring)
                // the inner rings lie within the glyphs
                if (radius <= 0f) {
                    break
                }
                this.text.strokeWidth = radius * 2f
                this.text.color = text.haloRingColor(ring).argb
                draw()
            }
        }
        this.text.style = Paint.Style.FILL
        this.text.color = text.color.argb
        draw()
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

    private companion object {

        // characters of a turned path text placed without growing the arrays
        const val CHARS = 32
    }
}
