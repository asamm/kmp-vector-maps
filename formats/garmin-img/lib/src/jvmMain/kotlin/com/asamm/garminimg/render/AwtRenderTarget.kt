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
 *
 * @param antiAliased if true, enables anti-aliasing for shapes and text. Default true for humans;
 *   tests that need pixel-stable output can pass false.
 * @param renderScale multiplier applied to TYP-driven pixel sizes (line/polygon pattern tile
 *   dimensions, point-icon dimensions, font sizes, stroke widths). TYP bitmaps were authored
 *   against handheld-DPI screens (~3× density); rendering them 1:1 on a desktop monitor (~1×
 *   density) is too thin for crossties and POI icons to read. Defaults to 1.5× — comparable to
 *   mid-density Android — and is configurable per-target so tests can stay byte-stable.
 */
class AwtRenderTarget(
    override val widthPx: Int,
    override val heightPx: Int,
    private val antiAliased: Boolean = true,
    override val renderScale: Float = 1.5f,
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
        // Always lay down a solid fill first. TYP polygons frequently
        // pair a colour with a sparse pattern (parks: light-green ground
        // + green-dot stipple, marsh: blue ground + dark stipple). The
        // TYP author's intent is "ground colour visible where the pattern
        // is transparent" — drawing only the pattern would let the page
        // background bleed through, which is exactly what made parks
        // disappear in earlier renders.
        if (style.fillColor.alpha != 0) {
            g.color = style.fillColor.toAwt()
            g.fill(path)
        }
        val pattern = style.pattern
        if (pattern != null) {
            // Tile the TYP 32×32 pattern across the polygon, scaled by
            // [renderScale] so handheld-DPI bitmaps don't read at ant-size
            // on desktop. Anchor the tile at (0,0) in the target so
            // adjacent polygons of the same type share a tile grid.
            val tile = patternCache.getOrPut(pattern) { pattern.toBufferedImage() }
            val tileW = tile.width * renderScale
            val tileH = tile.height * renderScale
            val savedPaint = g.paint
            g.paint = TexturePaint(tile, Rectangle2D.Float(0f, 0f, tileW, tileH))
            g.fill(path)
            g.paint = savedPaint
        }
        // Suppress the polygon outline when a TYP pattern is active — the
        // pattern itself separates adjacent areas; an extra stroke just
        // reads as a grey rectangle around forest patches.
        val strokeColor = style.strokeColor
        if (strokeColor != null && pattern == null) {
            g.color = strokeColor.toAwt()
            // Full constructor so polygon outlines use the same miter-
            // limit + butt-cap as polyline strokes. Single-arg
            // `BasicStroke(width)` defaults to miterLimit=10 / CAP_SQUARE,
            // which spikes at sharp corners while the matched Android
            // backend bevels at miterLimit=4 / CAP_BUTT.
            g.stroke = BasicStroke(
                style.strokeWidth * renderScale,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                STROKE_MITER_LIMIT,
            )
            g.draw(path)
        }
    }

    /**
     * Pattern → BufferedImage cache. TYP patterns are interned by parser
     * (one [Bitmap] per `(type, subType)`), so identity-keying drops
     * the per-polygon lookup cost from O(rgba.size) — `Bitmap`'s
     * content-based equals/hashCode walks the byte array — to O(1).
     */
    private val patternCache: MutableMap<Bitmap, BufferedImage> = java.util.IdentityHashMap()

    /** Counters for quick diagnostic of label placement effectiveness. */
    var curvedPlacements: Int = 0
        private set
    var shortPathFallbacks: Int = 0
        private set

    private companion object {
        /**
         * Miter limit for stroked polylines. Java's default of 10 is too
         * generous for crisp road outlines — at sharp angles miter spikes
         * shoot out past the road. 4 trims those spikes back to bevelled
         * joints while keeping moderate-angle joints sharp.
         */
        private const val STROKE_MITER_LIMIT: Float = 4f

    }

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
            style.borderWidth * renderScale,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_MITER,
            STROKE_MITER_LIMIT,
        )
        g.draw(path)
    }

    override fun drawPolylineStroke(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val pattern = style.pattern
        if (pattern != null) {
            if (pattern.height == 1) {
                // Single-row TYP patterns are dash patterns by design:
                // 32×1 alternating "set / clear" pixels → a dashed line.
                // Rendering via TexturePaint per-segment gives mushy
                // anti-aliased blurs and corner artefacts; converting to
                // a BasicStroke with the equivalent dash array uses
                // AWT's path stroker and matches the look reference
                // renderers (mkgmap, GPSMapEdit, old Locus) produce.
                drawDashedFromPattern(xs, ys, pattern, style)
                return
            }
            // Hairline multi-row patterned lines should stay hairline.
            // TYP authors choose 1 px deliberately; renderScale would
            // otherwise fatten siding rails to fight the main network.
            val scale = if (style.strokeWidth <= 1f) 1f else renderScale
            drawPatternedPolyline(xs, ys, pattern, scale)
            return
        }
        val path = buildPath(xs, ys, closed = false)
        g.color = style.strokeColor.toAwt()
        val dash = style.dashPattern
        val w = style.strokeWidth * renderScale
        g.stroke = if (dash != null && dash.isNotEmpty()) {
            // Dash lengths are in stroke-width units; scale them too so
            // dashes stay proportional to the now-thicker line.
            val scaledDash = FloatArray(dash.size) { dash[it] * renderScale }
            BasicStroke(
                w,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                STROKE_MITER_LIMIT,
                scaledDash,
                0f,
            )
        } else {
            BasicStroke(
                w,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_MITER,
                STROKE_MITER_LIMIT,
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
    /**
     * Renders a polyline whose TYP "pattern" is a single-row dash bitmap.
     * Walks the bitmap once to extract run lengths of opaque vs
     * transparent pixels, then strokes the polyline with a [BasicStroke]
     * dash array — fixed line width, sharp corners, anti-aliased only at
     * the line edge (not along its body). Dominant colour comes from the
     * first opaque pixel; if the bitmap is all-transparent the call is a
     * no-op.
     */
    private fun drawDashedFromPattern(
        xs: FloatArray,
        ys: FloatArray,
        pattern: Bitmap,
        style: Style.LineStyle,
    ) {
        val w = pattern.width
        val rgba = pattern.rgba
        // Extract dash runs (alternating "on" / "off" lengths). AWT
        // BasicStroke expects an even-length array; if the row starts
        // transparent we shift so it begins with an "on" run.
        val runs = ArrayList<Int>(8)
        var dashColor = -1
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
            if (opaque && dashColor == -1) {
                // First opaque pixel — capture its colour as the line colour.
                val r = rgba[i * 4].toInt() and 0xFF
                val g = rgba[i * 4 + 1].toInt() and 0xFF
                val b = rgba[i * 4 + 2].toInt() and 0xFF
                dashColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                firstOnIndex = runs.size
            }
            runs.add(runLen)
            i = j
        }
        if (dashColor == -1 || runs.isEmpty()) {
            // Fully transparent pattern — nothing to draw. This is a
            // valid TYP edge case (an "invisible" line type).
            return
        }
        // Re-order so the array begins with an "on" run.
        if (firstOnIndex > 0) {
            val rotated = ArrayList<Int>(runs.size)
            rotated.addAll(runs.subList(firstOnIndex, runs.size))
            rotated.addAll(runs.subList(0, firstOnIndex))
            runs.clear()
            runs.addAll(rotated)
        }
        val path = buildPath(xs, ys, closed = false)
        g.color = AwtColor(dashColor, true)
        if (runs.size == 1) {
            // Fully opaque row — a malformed / hand-crafted TYP can ship
            // a "dash" pattern with no gap pixels. BasicStroke needs an
            // even-length dash; mutating a 1-element list (as we do
            // below for odd sizes ≥ 3) would IOOB. Stroke solid and
            // return.
            g.stroke = BasicStroke(
                (style.strokeWidth * renderScale).coerceAtLeast(1f),
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                STROKE_MITER_LIMIT,
            )
            g.draw(path)
            return
        }
        // BasicStroke needs an even-length dash; fold a trailing "on"
        // run into the leading one so the alternation stays consistent.
        if (runs.size % 2 == 1) {
            runs[0] = runs[0] + runs.removeAt(runs.size - 1)
        }
        val dashArray = FloatArray(runs.size) { runs[it] * renderScale }
        g.stroke = BasicStroke(
            (style.strokeWidth * renderScale).coerceAtLeast(1f),
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            STROKE_MITER_LIMIT,
            dashArray,
            0f,
        )
        g.draw(path)
    }

    private fun drawPatternedPolyline(
        xs: FloatArray,
        ys: FloatArray,
        pattern: Bitmap,
        scale: Float = renderScale,
    ) {
        val tile = patternCache.getOrPut(pattern) { pattern.toBufferedImage() }
        // TYP line bitmaps were authored at handheld DPI (~3×). At 1:1
        // they read as ant-thin lines on a desktop display. Scaling the
        // *paint rectangle* up makes the TexturePaint stretch the tile
        // proportionally — railway crossties become readable, road dashes
        // stay in proportion with the rest of the styling. Caller can
        // override the scale (see [drawPolylineStroke]'s hairline guard)
        // when fattening a particular line type would harm the rendering.
        val tileW = tile.width * scale
        val tileH = tile.height * scale
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

            val savedTransform = g.transform
            val savedPaint = g.paint
            g.transform = AffineTransform(savedTransform).also { it.concatenate(transform) }
            // TexturePaint anchored at x = -carriedOffset shifts the
            // tile's phase so the pattern continues seamlessly from the
            // previous segment without us having to extend the fill
            // rectangle backwards into the previous segment's space.
            //
            // Earlier the rectangle started at x = -carriedOffset and
            // spanned (len + carriedOffset). At polyline corners the
            // back-extension overlapped the previous segment's
            // rectangle, and each rectangle painted ticks in its own
            // segment-aligned orientation — so corners sprouted ticks
            // pointing past where the polyline actually ends. Strict
            // [0, len] fill clips ticks to inside the segment.
            //
            // y = -tileH/2 origin keeps the asymmetric-tile fix (rails
            // / fences alignment) intact.
            g.paint = TexturePaint(
                tile,
                Rectangle2D.Float(-carriedOffset, -tileH / 2f, tileW, tileH),
            )
            g.fill(Rectangle2D.Float(0f, -tileH / 2f, len, tileH))
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
        g.font = fontFor(fontSize)
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
        g.font = fontFor(fontSize)
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
                val drawX = (-charW / 2f)
                // Lift the text above the line by half the line's
                // typical stroke (renderScale-scaled) plus a small
                // breathing gap. Centred-on-line placement (drawY=0)
                // bisects the road and reads worse than QMS / mkgmap
                // output where the label always sits on one side.
                val lineLift = (renderScale * 2f) + 1f
                val drawY = (-lineLift)
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
        val w = (bitmap.width * renderScale).toInt().coerceAtLeast(bitmap.width)
        val h = (bitmap.height * renderScale).toInt().coerceAtLeast(bitmap.height)
        g.drawImage(
            img,
            (x - w / 2f).toInt(),
            (y - h / 2f).toInt(),
            w,
            h,
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

    /**
     * Sans-serif font at the requested logical size scaled by
     * [renderScale]. Uses `deriveFont(Float)` so fractional sizes are
     * preserved — `Font(name, style, Int)` truncates the size, which
     * was the source of cross-backend label drift vs Android's
     * `Paint.setTextSize(Float)`.
     */
    private fun fontFor(fontSize: Float): Font {
        val pxSize = (fontSize * renderScale).coerceAtLeast(8f)
        return Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(pxSize)
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
