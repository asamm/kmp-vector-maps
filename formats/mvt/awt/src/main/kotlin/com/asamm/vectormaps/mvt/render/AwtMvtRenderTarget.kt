/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.TexturePaint
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * [MvtRenderTarget] over a `BufferedImage` through `Graphics2D`, antialiased; patterns come from
 * `sprite`, shared by the targets of one style.
 */
class AwtMvtRenderTarget(
    val image: BufferedImage,
    private val sprite: AwtSprite? = null,
) : MvtRenderTarget {

    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    override val widthPx: Int = image.width

    override val heightPx: Int = image.height

    override fun drawBackground(color: Color) {
        g.composite = AlphaComposite.SrcOver
        g.color = awt(color)
        g.fillRect(0, 0, widthPx, heightPx)
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
    ) {
        val tile = sprite?.image(pattern) ?: return
        val w = tile.width
        val h = tile.height
        val anchor = Rectangle2D.Float(
            -Math.floorMod(tileOriginX, w.toLong()).toFloat(),
            -Math.floorMod(tileOriginY, h.toLong()).toFloat(),
            w.toFloat(),
            h.toFloat(),
        )
        val saved = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity.coerceIn(0f, 1f))
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (antialias) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF,
        )
        g.paint = TexturePaint(tile, anchor)
        g.fill(toPath(path, Path2D.WIND_NON_ZERO))
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

    private fun awt(color: Color): java.awt.Color {
        return java.awt.Color(color.argb, true)
    }
}
