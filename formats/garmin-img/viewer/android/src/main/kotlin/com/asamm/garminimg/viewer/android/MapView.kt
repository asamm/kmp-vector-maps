/*
 * kmp-garmin-img — Android viewer custom View.
 */
package com.asamm.garminimg.viewer.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.render.AndroidCanvasRenderTarget
import com.asamm.garminimg.render.ImgRenderer
import com.asamm.garminimg.render.StyleResolver
import com.asamm.garminimg.render.Viewport
import com.asamm.garminimg.style.Color
import com.asamm.garminimg.utils.coord.CoordUtils
import com.asamm.garminimg.utils.io.ByteArrayImgRandomAccessReader
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * Minimal map view: parses the given IMG once, then re-renders into a
 * platform [android.graphics.Bitmap] via [AndroidCanvasRenderTarget] on
 * every pan / pinch-zoom gesture. The rendered bitmap is blitted onto
 * the view's canvas in [onDraw].
 *
 * Mirrors the desktop viewer's interaction model — drag to pan, pinch
 * to zoom — but skips the toolbar (no layer toggles, no level dropdown,
 * no "Save view"). The desktop viewer remains the primary diagnostic
 * tool; this is here to validate that [AndroidCanvasRenderTarget]
 * renders the same output as the AWT target on real device hardware.
 */
class MapView(context: Context, imgFile: File) : View(context) {

    private val img: ImgFile
    private val resolver: StyleResolver
    private val statusPaint: Paint = Paint().apply {
        color = 0xFF333333.toInt()
        // Roughly 14sp at the device's density — readable without
        // dominating the map at any DPI.
        textSize = 14f * resources.displayMetrics.density
        isAntiAlias = true
    }

    /**
     * Viewport bbox in Garmin fixed-point coords. Kept as `Double` so
     * the integer rounding only happens when we hand the Viewport to
     * the renderer — see the same fix in the desktop viewer.
     */
    private var minLon: Double = 0.0
    private var maxLon: Double = 0.0
    private var minLat: Double = 0.0
    private var maxLat: Double = 0.0

    /**
     * Last rendered bitmap. Reused as long as the viewport hasn't
     * changed (e.g. on resize the gesture detector hands the same view
     * geometry; we only re-render when the bbox actually moves).
     */
    private var rendered: android.graphics.Bitmap? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        val bytes = imgFile.readBytes()
        img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
        resolver = img.typStyles?.let { StyleResolver.typAware(it) }
            ?: StyleResolver.internalDefaults()
        // Initial viewport = whole TRE bbox. Aspect adjustment happens
        // implicitly on the first onSizeChanged because we don't know
        // the view's pixel size yet.
        minLon = img.tre.header.westBoundary.toDouble()
        maxLon = img.tre.header.eastBoundary.toDouble()
        minLat = img.tre.header.southBoundary.toDouble()
        maxLat = img.tre.header.northBoundary.toDouble()

        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, DragListener())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) {
            return
        }
        // Re-fit latitude to the current view aspect so the world
        // doesn't get horizontally squished on phones with non-square
        // map areas.
        val centreLat = (minLat + maxLat) / 2.0
        val lonHalf = (maxLon - minLon) / 2.0
        val latHalf = mercatorLatHalf(lonHalf, centreLat, w, h)
        minLat = centreLat - latHalf
        maxLat = centreLat + latHalf
        rendered = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = rendered ?: run {
            val fresh = renderFrame()
            rendered = fresh
            fresh
        }
        canvas.drawBitmap(bmp, 0f, 0f, null)
        // Tiny status line at the top — picked level + bbox in WGS84
        // degrees. Helpful when checking against the desktop captures.
        val statusText = buildString {
            append("L${ImgRenderer.pickRenderLevelWithCoverage(currentViewport(), img) ?: "?"} ")
            append("%.5f..%.5fN ".format(
                CoordUtils.toWGS84(minLat.toInt()),
                CoordUtils.toWGS84(maxLat.toInt()),
            ))
            append("%.5f..%.5fE".format(
                CoordUtils.toWGS84(minLon.toInt()),
                CoordUtils.toWGS84(maxLon.toInt()),
            ))
        }
        canvas.drawText(statusText, 16f, 40f, statusPaint)
    }

    private fun renderFrame(): android.graphics.Bitmap {
        // density = px-per-dp (1.0 mdpi → 3.0+ on modern phones). TYP
        // bitmaps and the lib's logical pixel sizes are dp-equivalent,
        // so multiplying by `density` lifts strokes / text / patterns
        // to a readable size on real device hardware. Real consumers
        // (Locus) manage density via their own bitmap allocation, so
        // the lib's renderScale defaults to 1.0; this viewer applies
        // density explicitly since nothing else scales the output.
        val target = AndroidCanvasRenderTarget(
            widthPx = width,
            heightPx = height,
            antiAliased = true,
            renderScale = resources.displayMetrics.density,
        )
        ImgRenderer(img, resolver).render(
            viewport = currentViewport(),
            target = target,
            backgroundColor = Color.rgb(0xF8, 0xF6, 0xF0),
        )
        return target.bitmap
    }

    private fun currentViewport(): Viewport {
        return Viewport(
            widthPx = width,
            heightPx = height,
            minLon = minLon.toInt(),
            maxLon = maxLon.toInt().coerceAtLeast(minLon.toInt() + 1),
            minLat = minLat.toInt(),
            maxLat = maxLat.toInt().coerceAtLeast(minLat.toInt() + 1),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Always feed both detectors. Scale wins precedence — if the
        // user is pinching, drag is suppressed by the detector itself
        // until the pinch ends.
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun panByPx(dxPx: Float, dyPx: Float) {
        if (width <= 0 || height <= 0) {
            return
        }
        val lonRange = maxLon - minLon
        val latRange = maxLat - minLat
        val dLon = -(dxPx.toDouble() / width) * lonRange
        val dLat = (dyPx.toDouble() / height) * latRange
        minLon += dLon
        maxLon += dLon
        minLat += dLat
        maxLat += dLat
        rendered = null
        invalidate()
    }

    private fun zoomAt(factor: Double, anchorXPx: Float, anchorYPx: Float) {
        if (width <= 0 || height <= 0 || factor <= 0.0) {
            return
        }
        val lonRange = maxLon - minLon
        val latRange = maxLat - minLat
        val anchorFracX = anchorXPx / width
        val anchorFracY = anchorYPx / height
        val anchorLon = minLon + anchorFracX * lonRange
        val anchorLat = maxLat - anchorFracY * latRange
        val newLonRange = (lonRange / factor).coerceAtLeast(2.0)
        val newLonHalf = newLonRange / 2.0
        val newLatHalf = mercatorLatHalf(newLonHalf, anchorLat, width, height)
        val newLatRange = (newLatHalf * 2.0).coerceAtLeast(2.0)
        val newMinLon = anchorLon - newLonRange * anchorFracX
        val newMaxLat = anchorLat + newLatRange * anchorFracY
        minLon = newMinLon
        maxLon = newMinLon + newLonRange
        minLat = newMaxLat - newLatRange
        maxLat = newMaxLat
        rendered = null
        invalidate()
    }

    private fun mercatorLatHalf(
        lonHalf: Double,
        centreLatGarmin: Double,
        widthPx: Int,
        heightPx: Int,
    ): Double {
        if (widthPx <= 0) {
            return lonHalf
        }
        val centreDeg = CoordUtils.toWGS84(centreLatGarmin.toInt())
        val cosLat = max(0.01, cos(centreDeg * PI / 180.0))
        return lonHalf * cosLat * heightPx / widthPx
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAt(detector.scaleFactor.toDouble(), detector.focusX, detector.focusY)
            return true
        }
    }

    private inner class DragListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            // Android's onScroll passes distance = old - new (the
            // opposite of "drag delta"). Negate so dragging right pans
            // the viewport left in world coords.
            panByPx(-distanceX, -distanceY)
            return true
        }
    }

}
