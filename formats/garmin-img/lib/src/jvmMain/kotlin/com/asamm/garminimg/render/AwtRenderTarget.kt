/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import com.asamm.garminimg.style.Bitmap
import com.asamm.garminimg.style.Color as GarminColor
import com.asamm.garminimg.style.Style
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.TexturePaint
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.atan2
import kotlin.math.hypot
import javax.imageio.ImageIO
import java.io.File
import java.io.OutputStream

/**
 * AWT [Graphics2D]-backed [RenderTarget] used by:
 *
 * - the desktop test harness (jvmTest) — renders an IMG to a [BufferedImage]
 *   and writes a PNG.
 * - the future `cli/` desktop CLI (`RenderImgToPng`).
 *
 * The target owns its [BufferedImage]. Callers can extract it via [image]
 * after [endFrame] and either save it ([writePng]) or hand it on to a
 * higher-level pipeline.
 */
class AwtRenderTarget(
    override val widthPx: Int,
    override val heightPx: Int,
    /**
     * If true, enables anti-aliasing for shapes and text. Default true for
     * humans; tests that need pixel-stable output can pass false.
     */
    private val antiAliased: Boolean = true,
) : RenderTarget {

    /**
     * Backing image. Populated incrementally during a render pass.
     */
    val image: BufferedImage = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)

    private val g: Graphics2D = image.createGraphics().apply {
        if (antiAliased) {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        }
    }

    override fun beginFrame(backgroundColor: GarminColor) {
        if (backgroundColor.alpha == 0) {
            // Transparent fill: SrcOver compositing with alpha=0 is a no-op,
            // so previous frame content would persist on a re-rendered target.
            // Switch to Src compositing to actually replace pixels with the
            // transparent value, then restore.
            val savedComposite = g.composite
            g.composite = AlphaComposite.Src
            g.color = AwtColor(0, 0, 0, 0)
            g.fillRect(0, 0, widthPx, heightPx)
            g.composite = savedComposite
        } else {
            g.color = backgroundColor.toAwt()
            g.fillRect(0, 0, widthPx, heightPx)
        }
    }

    override fun drawPolygon(xs: FloatArray, ys: FloatArray, style: Style.PolygonStyle) {
        if (xs.size < 3) {
            return
        }
        val path = buildPath(xs, ys, closed = true)
        val pattern = style.pattern
        if (pattern != null) {
            // Tile the TYP 32×32 pattern across the polygon. We anchor the
            // tile at (0,0) in the target — adjacent polygons of the same
            // type then line up on a shared tile grid (matching mkgmap /
            // QMapShack behaviour, where the pattern is screen-aligned).
            val tile = patternCache.getOrPut(pattern) { pattern.toBufferedImage() }
            val savedPaint = g.paint
            g.paint = TexturePaint(
                tile,
                Rectangle2D.Float(0f, 0f, tile.width.toFloat(), tile.height.toFloat()),
            )
            g.fill(path)
            g.paint = savedPaint
        } else {
            g.color = style.fillColor.toAwt()
            g.fill(path)
        }
        // Suppress the polygon outline when a TYP pattern is active — the
        // pattern itself separates adjacent areas; an extra stroke just
        // reads as a grey rectangle around forest patches.
        val strokeColor = style.strokeColor
        if (strokeColor != null && pattern == null) {
            g.color = strokeColor.toAwt()
            g.stroke = BasicStroke(style.strokeWidth)
            g.draw(path)
        }
    }

    /**
     * Pattern → BufferedImage cache. TYP patterns are interned by parser
     * (one [Bitmap] per `(type, subType)`), so a small identity-keyed map
     * keeps the per-polygon cost down to a single hash lookup.
     */
    private val patternCache: MutableMap<Bitmap, BufferedImage> = HashMap()

    /** Counters for quick diagnostic of label placement effectiveness. */
    var curvedPlacements: Int = 0
        private set
    var shortPathFallbacks: Int = 0
        private set

    override fun drawPolylineBorder(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val borderColor = style.borderColor ?: return
        if (style.borderWidth <= style.strokeWidth) {
            return
        }
        val path = buildPath(xs, ys, closed = false)
        g.color = borderColor.toAwt()
        g.stroke = BasicStroke(
            style.borderWidth,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
        )
        g.draw(path)
    }

    override fun drawPolylineStroke(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val pattern = style.pattern
        if (pattern != null) {
            drawPatternedPolyline(xs, ys, pattern)
            return
        }
        val path = buildPath(xs, ys, closed = false)
        g.color = style.strokeColor.toAwt()
        val dash = style.dashPattern
        g.stroke = if (dash != null && dash.isNotEmpty()) {
            BasicStroke(
                style.strokeWidth,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                10f,
                dash,
                0f,
            )
        } else {
            BasicStroke(
                style.strokeWidth,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
            )
        }
        g.draw(path)
    }

    /**
     * Renders a polyline as a textured stroke: each segment is filled
     * with the [pattern] tile rotated to follow the segment angle. The
     * tile is anchored at the segment's start so the texture progresses
     * continuously along the line; height equals the pattern's height
     * (Garmin's natural pixel-stroke width for that line type).
     *
     * AWT's [TexturePaint] is screen-aligned, not stroke-aligned, so we
     * rotate the world via an [AffineTransform] for each segment and
     * fill an axis-aligned rectangle in that rotated space — which
     * lands as the line-aligned rectangle in screen space.
     *
     * Sharp corners produce small visual gaps because adjacent segments
     * don't share a join geometry. For typical Garmin lines (gently
     * curved roads, contours, railways) this is invisible at render
     * resolution.
     */
    private fun drawPatternedPolyline(
        xs: FloatArray,
        ys: FloatArray,
        pattern: Bitmap,
    ) {
        val tile = patternCache.getOrPut(pattern) { pattern.toBufferedImage() }
        val tileW = tile.width.toFloat()
        val tileH = tile.height.toFloat()
        var carriedOffset = 0f
        for (i in 0 until xs.size - 1) {
            val x0 = xs[i].toDouble()
            val y0 = ys[i].toDouble()
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < 0.5f) {
                continue
            }
            val angle = atan2(dy.toDouble(), dx.toDouble())
            val transform = AffineTransform()
            transform.translate(x0, y0)
            transform.rotate(angle)
            // Carry the tile offset across segments so the texture
            // progression doesn't reset at every joint — long dashed
            // patterns stay in phase along the polyline.
            transform.translate(-carriedOffset.toDouble(), 0.0)

            val savedTransform = g.transform
            val savedPaint = g.paint
            g.transform = AffineTransform(savedTransform).also { it.concatenate(transform) }
            g.paint = TexturePaint(tile, Rectangle2D.Float(0f, 0f, tileW, tileH))
            g.fill(Rectangle2D.Float(0f, -tileH / 2f, len + carriedOffset, tileH))
            g.transform = savedTransform
            g.paint = savedPaint

            carriedOffset = ((carriedOffset + len) % tileW)
        }
    }

    override fun drawPoint(x: Float, y: Float, style: Style.PointStyle) {
        val icon = style.icon
        if (icon != null) {
            drawBitmap(x, y, icon)
        } else {
            // Fallback: 4-pixel dot in the label colour.
            g.color = style.labelColor.toAwt()
            g.fillOval((x - 2).toInt(), (y - 2).toInt(), 4, 4)
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
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize.toInt().coerceAtLeast(8))
        val fm = g.fontMetrics
        val w = fm.stringWidth(text)
        val ascent = fm.ascent
        val (drawX, drawY) = when (anchor) {
            TextAnchor.CENTER -> Pair(x - w / 2f, y + ascent / 2f - fm.descent / 2f)
            TextAnchor.BASELINE_START -> Pair(x, y)
        }
        if (outlineColor != null) {
            g.color = outlineColor.toAwt()
            // 8-direction outline pass for cheap text halo.
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) {
                        continue
                    }
                    g.drawString(text, drawX + dx, drawY + dy)
                }
            }
        }
        g.color = color.toAwt()
        g.drawString(text, drawX, drawY)
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
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize.toInt().coerceAtLeast(8))
        val fm = g.fontMetrics
        val ascent = fm.ascent
        val descent = fm.descent
        // Cumulative segment lengths so we can locate any d-along-path
        // in O(log n) (or O(n) with linear scan, fine for typical road
        // polylines of <50 vertices).
        val cum = DoubleArray(xs.size)
        for (i in 1 until xs.size) {
            val dx = (xs[i] - xs[i - 1]).toDouble()
            val dy = (ys[i] - ys[i - 1]).toDouble()
            cum[i] = cum[i - 1] + kotlin.math.hypot(dx, dy)
        }
        val totalLen = cum[xs.size - 1]
        val widths = IntArray(text.length) { fm.charWidth(text[it]) }
        val textWidth = widths.sum()
        if (textWidth <= 0) {
            return
        }
        if (textWidth > totalLen - 4.0) {
            // Path too short — fall back to a centred horizontal
            // placement at the path midpoint so the label still appears.
            val midIdx = xs.size / 2
            drawText(xs[midIdx], ys[midIdx], text, color, outlineColor, fontSize, TextAnchor.CENTER)
            shortPathFallbacks++
            return
        }
        curvedPlacements++
        // Pick the path direction that keeps text readable left-to-right.
        // If the polyline runs net-westwards, reverse the char order
        // (and walk from path-end) so labels never read upside-down.
        val netDx = xs.last() - xs.first()
        val reversed = netDx < 0f
        val startD = (totalLen - textWidth) / 2.0
        var d = startD
        for (i in text.indices) {
            val charW = widths[i]
            val centreD = if (reversed) totalLen - (d + charW / 2.0) else d + charW / 2.0
            val placement = pointAndAngleAt(xs, ys, cum, centreD)
            val transform = AffineTransform()
            transform.translate(placement.x, placement.y)
            transform.rotate(if (reversed) placement.angle + Math.PI else placement.angle)
            val savedTransform = g.transform
            try {
                g.transform = AffineTransform(savedTransform).also { it.concatenate(transform) }
                val drawX = (-charW / 2f).toFloat()
                val drawY = ((ascent - descent) / 2f).toFloat()
                val s = text[i].toString()
                if (outlineColor != null) {
                    g.color = outlineColor.toAwt()
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            if (dx == 0 && dy == 0) {
                                continue
                            }
                            g.drawString(s, drawX + dx, drawY + dy)
                        }
                    }
                }
                g.color = color.toAwt()
                g.drawString(s, drawX, drawY)
            } finally {
                g.transform = savedTransform
            }
            d += charW
        }
    }

    /**
     * Returns the (x, y, tangent-angle-in-radians) at distance `d` along
     * the polyline `(xs, ys)` whose cumulative arc-lengths are in `cum`.
     */
    private fun pointAndAngleAt(
        xs: FloatArray,
        ys: FloatArray,
        cum: DoubleArray,
        d: Double,
    ): PathPoint {
        val total = cum[cum.size - 1]
        val target = d.coerceIn(0.0, total)
        // Linear scan is fine — n is small.
        var seg = 1
        while (seg < cum.size && cum[seg] < target) {
            seg++
        }
        if (seg >= cum.size) {
            seg = cum.size - 1
        }
        val segLen = cum[seg] - cum[seg - 1]
        val t = if (segLen > 0.0) (target - cum[seg - 1]) / segLen else 0.0
        val x = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * t
        val y = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * t
        val angle = atan2((ys[seg] - ys[seg - 1]).toDouble(), (xs[seg] - xs[seg - 1]).toDouble())
        return PathPoint(x, y, angle)
    }

    private data class PathPoint(val x: Double, val y: Double, val angle: Double)

    override fun drawBitmap(x: Float, y: Float, bitmap: Bitmap) {
        val img = bitmap.toBufferedImage()
        g.drawImage(
            img,
            (x - bitmap.width / 2f).toInt(),
            (y - bitmap.height / 2f).toInt(),
            null,
        )
    }

    override fun endFrame() {
        g.dispose()
    }

    /**
     * Saves the rendered image to `file` as PNG.
     */
    fun writePng(file: File) {
        ImageIO.write(image, "PNG", file)
    }

    /**
     * Saves the rendered image to `out` as PNG.
     */
    fun writePng(out: OutputStream) {
        ImageIO.write(image, "PNG", out)
    }

    private fun buildPath(xs: FloatArray, ys: FloatArray, closed: Boolean): Path2D {
        val path = Path2D.Float()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) {
            path.lineTo(xs[i], ys[i])
        }
        if (closed) {
            path.closePath()
        }
        return path
    }

    private fun GarminColor.toAwt(): AwtColor {
        return AwtColor(red, green, blue, alpha)
    }

    private fun Bitmap.toBufferedImage(): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val a = rgba[i + 3].toInt() and 0xFF
                img.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                i += 4
            }
        }
        return img
    }
}
