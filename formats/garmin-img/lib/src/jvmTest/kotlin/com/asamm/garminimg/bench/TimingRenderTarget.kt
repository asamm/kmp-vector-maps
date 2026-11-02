/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.bench

import com.asamm.garminimg.render.RenderTarget
import com.asamm.garminimg.render.TextAnchor
import com.asamm.garminimg.style.Bitmap
import com.asamm.garminimg.style.Color
import com.asamm.garminimg.style.Style

/**
 * [RenderTarget] wrapper that counts calls + sums wall-clock time per
 * method. Used by [RenderBenchmarkTest] to break down where a single
 * [com.asamm.garminimg.render.ImgRenderer.render] call spends its
 * milliseconds. The wrapper itself adds two `System.nanoTime()` calls
 * per delegated method; that's negligible vs. the AWT draw cost we're
 * trying to measure.
 */
class TimingRenderTarget(private val delegate: RenderTarget) : RenderTarget {

    override val widthPx: Int get() = delegate.widthPx
    override val heightPx: Int get() = delegate.heightPx
    override val renderScale: Float get() = delegate.renderScale

    var beginEndNanos: Long = 0L; private set
    var polygonCount: Int = 0; private set
    var polygonNanos: Long = 0L; private set
    var polylineBorderCount: Int = 0; private set
    var polylineBorderNanos: Long = 0L; private set
    var polylineStrokeCount: Int = 0; private set
    var polylineStrokeNanos: Long = 0L; private set
    var pointCount: Int = 0; private set
    var pointNanos: Long = 0L; private set
    var bitmapCount: Int = 0; private set
    var bitmapNanos: Long = 0L; private set
    var textCount: Int = 0; private set
    var textNanos: Long = 0L; private set
    var textOnPathCount: Int = 0; private set
    var textOnPathNanos: Long = 0L; private set

    fun reset() {
        beginEndNanos = 0L
        polygonCount = 0; polygonNanos = 0L
        polylineBorderCount = 0; polylineBorderNanos = 0L
        polylineStrokeCount = 0; polylineStrokeNanos = 0L
        pointCount = 0; pointNanos = 0L
        bitmapCount = 0; bitmapNanos = 0L
        textCount = 0; textNanos = 0L
        textOnPathCount = 0; textOnPathNanos = 0L
    }

    /** Sum of all `drawX` time slots — excludes begin/end frame. */
    val drawNanos: Long
        get() = polygonNanos + polylineBorderNanos + polylineStrokeNanos +
            pointNanos + bitmapNanos + textNanos + textOnPathNanos

    override fun beginFrame(backgroundColor: Color) {
        val t = System.nanoTime()
        delegate.beginFrame(backgroundColor)
        beginEndNanos += System.nanoTime() - t
    }

    override fun endFrame() {
        val t = System.nanoTime()
        delegate.endFrame()
        beginEndNanos += System.nanoTime() - t
    }

    override fun drawPolygon(xs: FloatArray, ys: FloatArray, style: Style.PolygonStyle) {
        val t = System.nanoTime()
        delegate.drawPolygon(xs, ys, style)
        polygonNanos += System.nanoTime() - t
        polygonCount++
    }

    override fun drawPolylineBorder(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        val t = System.nanoTime()
        delegate.drawPolylineBorder(xs, ys, style)
        polylineBorderNanos += System.nanoTime() - t
        polylineBorderCount++
    }

    override fun drawPolylineStroke(xs: FloatArray, ys: FloatArray, style: Style.LineStyle) {
        val t = System.nanoTime()
        delegate.drawPolylineStroke(xs, ys, style)
        polylineStrokeNanos += System.nanoTime() - t
        polylineStrokeCount++
    }

    override fun drawPoint(x: Float, y: Float, style: Style.PointStyle) {
        val t = System.nanoTime()
        delegate.drawPoint(x, y, style)
        pointNanos += System.nanoTime() - t
        pointCount++
    }

    override fun drawBitmap(x: Float, y: Float, bitmap: Bitmap) {
        val t = System.nanoTime()
        delegate.drawBitmap(x, y, bitmap)
        bitmapNanos += System.nanoTime() - t
        bitmapCount++
    }

    override fun drawText(
        x: Float, y: Float, text: String,
        color: Color, outlineColor: Color?, fontSize: Float,
        anchor: TextAnchor,
    ) {
        val t = System.nanoTime()
        delegate.drawText(x, y, text, color, outlineColor, fontSize, anchor)
        textNanos += System.nanoTime() - t
        textCount++
    }

    override fun drawTextOnPath(
        xs: FloatArray, ys: FloatArray, text: String,
        color: Color, outlineColor: Color?, fontSize: Float,
    ) {
        val t = System.nanoTime()
        delegate.drawTextOnPath(xs, ys, text, color, outlineColor, fontSize)
        textOnPathNanos += System.nanoTime() - t
        textOnPathCount++
    }
}
