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
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
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
        g.color = backgroundColor.toAwt()
        g.fillRect(0, 0, widthPx, heightPx)
    }

    override fun drawPolygon(xs: FloatArray, ys: FloatArray, style: Style.PolygonStyle) {
        if (xs.size < 3) {
            return
        }
        val path = buildPath(xs, ys, closed = true)
        g.color = style.fillColor.toAwt()
        g.fill(path)
        val strokeColor = style.strokeColor
        if (strokeColor != null) {
            g.color = strokeColor.toAwt()
            g.stroke = BasicStroke(style.strokeWidth)
            g.draw(path)
        }
    }

    override fun drawPolyline(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        if (xs.size < 2) {
            return
        }
        val path = buildPath(xs, ys, closed = false)

        val borderColor = style.borderColor
        if (borderColor != null && style.borderWidth > style.strokeWidth) {
            g.color = borderColor.toAwt()
            g.stroke = BasicStroke(
                style.borderWidth,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
            )
            g.draw(path)
        }
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
