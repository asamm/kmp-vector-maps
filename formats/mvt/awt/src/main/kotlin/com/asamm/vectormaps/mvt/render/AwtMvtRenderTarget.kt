/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.symbol.MvtIcon
import com.asamm.vectormaps.mvt.symbol.MvtText
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.TexturePaint
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * [MvtRenderTarget] over a `BufferedImage` through `Graphics2D`, antialiased; patterns and icons
 * come from `sprite`, text from `fonts`, both shared by the targets of one style.
 */
class AwtMvtRenderTarget(
    val image: BufferedImage,
    private val sprite: AwtSprite? = null,
    private val fonts: AwtFonts? = null,
) : MvtRenderTarget {

    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    override val widthPx: Int = image.width

    override val heightPx: Int = image.height

    override fun clear() {
        g.composite = AlphaComposite.Clear
        g.fillRect(0, 0, widthPx, heightPx)
        g.composite = AlphaComposite.SrcOver
    }

    override fun drawBackground(color: Color) {
        g.composite = AlphaComposite.SrcOver
        g.color = awt(color)
        g.fillRect(0, 0, widthPx, heightPx)
    }

    override fun withPlatformCanvas(block: (Any) -> Unit) {
        val copy = g.create() as Graphics2D
        // not the last layer's pattern, dash or blending
        copy.paint = java.awt.Color.BLACK
        copy.stroke = BasicStroke()
        copy.composite = AlphaComposite.SrcOver
        try {
            block(copy)
        } finally {
            copy.dispose()
        }
    }

    override fun fillPath(path: MvtPath, color: Color, antialias: Boolean) {
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (antialias) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF,
        )
        g.color = awt(color)
        g.fill(toPath(path, Path2D.WIND_NON_ZERO))
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
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
        val tile = sprite?.image(pattern) ?: return
        val factor = scale / sprite.pixelRatio(pattern)
        val w = tile.width * factor
        val h = tile.height * factor
        val anchor = Rectangle2D.Float(-patternOffset(tileOriginX, w), -patternOffset(tileOriginY, h), w, h)
        val saved = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity.coerceIn(0f, 1f))
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (antialias) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF,
        )
        g.paint = TexturePaint(tile, anchor)
        // a scaled pattern samples between its pixels, as icons do
        // a fresh Graphics2D reports no hint; its default is nearest neighbour
        val interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
            ?: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.fill(toPath(path, Path2D.WIND_NON_ZERO))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.composite = saved
    }

    override fun strokePath(
        path: MvtPath,
        color: Color,
        width: Float,
        cap: LineCap,
        join: LineJoin,
        dash: FloatArray?,
    ) {
        g.color = awt(color)
        g.stroke = BasicStroke(
            width,
            when (cap) {
                LineCap.BUTT -> BasicStroke.CAP_BUTT
                LineCap.ROUND -> BasicStroke.CAP_ROUND
                LineCap.SQUARE -> BasicStroke.CAP_SQUARE
            },
            when (join) {
                LineJoin.MITER -> BasicStroke.JOIN_MITER
                LineJoin.ROUND -> BasicStroke.JOIN_ROUND
                LineJoin.BEVEL -> BasicStroke.JOIN_BEVEL
            },
            // MapLibre's default line-miter-limit
            2f,
            dash?.takeIf { d -> d.isNotEmpty() && d.any { it > 0f } },
            0f,
        )
        g.draw(toPath(path, Path2D.WIND_NON_ZERO))
    }

    override fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Color,
        strokeWidth: Float,
        strokeColor: Color,
    ) {
        g.color = awt(color)
        g.fill(Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2))
        if (strokeWidth > 0f) {
            // the stroke lies outside the radius, as in MapLibre
            val r = radius + strokeWidth / 2
            g.color = awt(strokeColor)
            g.stroke = BasicStroke(strokeWidth)
            g.draw(Ellipse2D.Float(x - r, y - r, r * 2, r * 2))
        }
    }

    override fun drawText(text: MvtText, x: Float, y: Float) {
        val fonts = fonts ?: return
        val ascent = fonts.font(text.font, text.sizePx).getLineMetrics("Hg", AwtFonts.CONTEXT).ascent
        val top = y - text.height / 2f
        // all lines in one outline, so no line's halo covers another's glyphs
        val outline = Path2D.Float()
        for ((i, line) in text.lines.withIndex()) {
            val glyphs = fonts.shape(line, text.sizePx, text.font, text.letterSpacingPx)
            val width = glyphs.logicalBounds.width.toFloat()
            // each line box is a line height tall, its baseline where MapLibre puts it
            val baseline = top + i * text.lineHeightPx + text.lineHeightPx / 2f + text.baselinePx(ascent)
            // centred by default; text-justify moves a shorter line to a side of the box
            val left = if (text.lines.size < 2) {
                x - width / 2f
            } else {
                x - text.width / 2f + (text.width - width) * text.justify
            }
            outline.append(glyphs.getOutline(left, baseline), false)
        }
        if (text.rotateDeg != 0f) {
            val turn = AffineTransform.getRotateInstance(
                Math.toRadians(text.rotateDeg.toDouble()),
                x.toDouble(),
                y.toDouble(),
            )
            paint(turn.createTransformedShape(outline), text)
            return
        }
        paint(outline, text)
    }

    override fun drawTextAlongPath(text: MvtText, path: FloatArray) {
        val fonts = fonts ?: return
        val line = text.lines.firstOrNull() ?: return
        val glyphs = fonts.shape(line, text.sizePx, text.font, text.letterSpacingPx)
        val total = glyphs.logicalBounds.width.toFloat()
        var length = 0f
        for (i in 2 until path.size step 2) {
            length += hypot(path[i] - path[i - 2], path[i + 1] - path[i - 1])
        }
        val start = (length - total) / 2f
        // the baseline where MapLibre puts it below the line
        val ascent = fonts.font(text.font, text.sizePx).getLineMetrics("Hg", AwtFonts.CONTEXT).ascent
        val baseline = text.baselinePx(ascent)
        val rotate = Math.toRadians(text.rotateDeg.toDouble())
        val outline = Path2D.Float()
        for (g in 0 until glyphs.numGlyphs) {
            val position = glyphs.getGlyphPosition(g)
            val advance = glyphs.getGlyphMetrics(g).advance
            val at = start + position.x.toFloat() + advance / 2f
            val (px, py, angle) = pointAt(path, at)
            val glyph = glyphs.getGlyphOutline(g, -(position.x.toFloat() + advance / 2f), baseline)
            // shifted across the line by text-offset, then turned by text-rotate about its centre
            val transform = AffineTransform.getTranslateInstance(px.toDouble(), py.toDouble())
            transform.rotate(angle)
            transform.translate(0.0, text.centerY.toDouble())
            transform.rotate(rotate)
            outline.append(transform.createTransformedShape(glyph), false)
        }
        paint(outline, text)
    }

    override fun drawIcon(icon: MvtIcon, x: Float, y: Float, rotateDeg: Float) {
        val tile = sprite?.image(icon.name) ?: return
        val w = icon.width * icon.scale
        val h = icon.height * icon.scale
        val transform = AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble())
        transform.rotate(Math.toRadians(rotateDeg.toDouble()))
        transform.translate(-w / 2.0, -h / 2.0)
        transform.scale(w / tile.width.toDouble(), h / tile.height.toDouble())
        // a fresh Graphics2D reports no hint; its default is nearest neighbour
        val saved = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
            ?: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val composite = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, icon.opacity)
        g.drawImage(tile, transform, null)
        g.composite = composite
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, saved)
    }

    fun dispose() {
        g.dispose()
    }

    private fun toPath(path: MvtPath, rule: Int): Path2D.Float {
        val p = Path2D.Float(rule, path.coords.size / 2 + path.partEnds.size)
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
                    p.closePath()
                }
            }
            start = end
        }
        return p
    }

    // the halo as round strokes around the glyphs, outermost first, then the glyphs over it
    private fun paint(outline: Shape, text: MvtText) {
        if (text.hasHalo) {
            for (ring in 0 until text.haloRings) {
                val radius = text.haloRingRadius(ring)
                // the inner rings lie within the glyphs
                if (radius <= 0f) {
                    break
                }
                g.color = awt(text.haloRingColor(ring))
                g.stroke = BasicStroke(radius * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(outline)
            }
        }
        g.color = awt(text.color)
        g.fill(outline)
    }

    // the point at `distance` along `path` and the angle of its segment; past either end it goes on
    // straight, for glyphs measured a little wider than the stretch
    private fun pointAt(path: FloatArray, distance: Float): Triple<Float, Float, Double> {
        var at = 0f
        var i = 2
        while (i < path.size) {
            val x0 = path[i - 2]
            val y0 = path[i - 1]
            val dx = path[i] - x0
            val dy = path[i + 1] - y0
            val segment = hypot(dx, dy)
            if (segment > 0f
                && (distance <= at + segment || i + 2 >= path.size)
            ) {
                val t = (distance - at) / segment
                return Triple(x0 + dx * t, y0 + dy * t, atan2(dy.toDouble(), dx.toDouble()))
            }
            at += segment
            i += 2
        }
        return Triple(path[0], path[1], 0.0)
    }

    private fun awt(color: Color): java.awt.Color {
        return java.awt.Color(color.argb, true)
    }
}
