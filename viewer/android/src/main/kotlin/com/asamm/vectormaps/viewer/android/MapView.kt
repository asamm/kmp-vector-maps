/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.OverScroller
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.style.Color as GarminColor
import com.asamm.vectormaps.garminimg.driver.GarminLabelPass
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.core.label.TiledLabelPass
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import com.asamm.vectormaps.garminimg.render.AndroidRenderTarget
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.MapsforgeLabelPass
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import com.asamm.vectormaps.mapsforge.map.android.graphics.AndroidGraphicFactory
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtLabelPass
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AndroidMvtRenderTarget
import com.asamm.vectormaps.viewer.TileEngine
import com.asamm.vectormaps.viewer.TileImages
import com.asamm.vectormaps.viewer.Viewport
import com.asamm.vectormaps.viewer.readsStyle
import java.io.File

/**
 * The map on a plain view: drag pans and glides on after release, pinch and double tap zoom by
 * whole levels, and a panel at the top shows the zoom and render timings.
 *
 * @param startView the view to show first; null fits the map's bounds
 */
@SuppressLint("ViewConstructor")
class MapView(
    context: Context,
    formats: MapFormats,
    mapFile: File,
    styleFile: File,
    config: RenderConfig,
    startView: Viewport?,
) : View(context) {

    private val dataset: MapDataset

    // a style read from a file, closed with the view; the map's own styles belong to the dataset
    private val loadedStyle: MapStyle?

    private val engine: TileEngine<TileImage>

    private val setupMs: Long

    /**
     * What the screen shows; null until the first draw fits the map.
     */
    var viewport: Viewport? = startView
        private set

    private val density = resources.displayMetrics.density

    // the view the label pass was last asked for; UI thread only
    private var preparedFor: LabelViewport? = null

    // the Garmin pass draws into this bitmap, reused while the view keeps its size; UI thread only
    private var garminTarget: AndroidRenderTarget? = null

    // the placement and view the Garmin bitmap shows; UI thread only
    private var garminDrawn: Pair<PreparedLabels, LabelViewport>? = null

    // the MVT pass draws into this bitmap, cleared and reused while the view keeps its size; UI
    // thread only
    private var mvtTarget: AndroidMvtRenderTarget? = null

    // the placement and view the MVT bitmap shows; UI thread only
    private var mvtDrawn: Pair<PreparedLabels, LabelViewport>? = null

    // the TYP and text scale of the Garmin tiles, for their labels
    private val renderScale = config.deviceScale

    @Volatile
    private var labelsStale = false

    private val missingPaint = Paint().apply {
        color = 0xFFC4C4C4.toInt()
    }

    private val panelPaint = Paint().apply {
        color = 0xD9FFFFFF.toInt()
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202020.toInt()
        textSize = 13f * density
    }

    // system bars and cutout the panel keeps clear of; the window is edge to edge from API 35
    private var insetLeft = 0

    private var insetTop = 0

    private var insetRight = 0

    private var closed = false

    // pinch scale gathered since the last whole zoom step
    private var pinch = 1f

    // a list's friction lets a map glide too far
    private val scroller = OverScroller(context).apply {
        setFriction(ViewConfiguration.getScrollFriction() * 4)
    }

    // the scroller's position already applied to the viewport
    private var flungX = 0

    private var flungY = 0

    private val pinchDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scroller.forceFinished(true)
            pinch = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pinch *= detector.scaleFactor
            // step as soon as the fingers have doubled or halved their distance
            if (pinch >= 2f) {
                pinch /= 2f
                zoomAt(steps = 1, x = detector.focusX, y = detector.focusY)
            } else if (pinch <= 0.5f) {
                pinch *= 2f
                zoomAt(steps = -1, x = detector.focusX, y = detector.focusY)
            }
            return true
        }
    })

    private val dragDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            viewport = viewport?.dragged(dx = -distanceX.toDouble(), dy = -distanceY.toDouble())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            flungX = 0
            flungY = 0
            scroller.fling(0, 0, velocityX.toInt(), velocityY.toInt(), Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
            postInvalidateOnAnimation()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAt(steps = 1, x = e.x, y = e.y)
            return true
        }
    })

    init {
        val started = System.currentTimeMillis()
        // the probe swallows its errors, which would hide a path the app may not read
        require(mapFile.canRead()) {
            "MapView(), cannot read $mapFile"
        }
        val probed = checkNotNull(formats.probe(mapFile)) {
            "MapView(), no driver reads $mapFile: ${formats.unreadableReason(mapFile)}"
        }
        val images = imagesFor(probed.driver)
        val ownStyleFile = styleFile.takeIf(probed.driver::readsStyle)
        if (ownStyleFile != null) {
            require(ownStyleFile.canRead()) {
                "MapView(), cannot read $ownStyleFile"
            }
        }
        val loaded = ownStyleFile?.let(probed.driver::loadStyle)
        loadedStyle = loaded
        dataset = try {
            probed.driver.open(listOf(mapFile))
        } catch (e: Exception) {
            loaded?.close()
            throw e
        }
        val renderer = try {
            val style: MapStyle = loaded ?: checkNotNull(dataset.styles.firstOrNull()) {
                "MapView(), $styleFile does not style $mapFile, which brings no style"
            }
            dataset.createRenderer(style = style, selection = StyleSelection.DEFAULT, config = config)
        } catch (e: Exception) {
            dataset.close()
            loaded?.close()
            throw e
        }
        setupMs = System.currentTimeMillis() - started
        engine = TileEngine(
            renderer = renderer,
            images = images,
            threads = RENDER_THREADS,
            capacity = keptTiles(
                renderer = renderer,
                argb = probed.driver is GarminDriver || probed.driver is MvtDriver,
                tileSize = config.tileSizePx,
            ),
        ) {
            // a new tile stored its labels
            labelsStale = true
            postInvalidate()
        }
        renderer.labelPass?.onPrepared = {
            postInvalidate()
        }
    }

    // a tile shows its labels while the label store keeps it, so no more tiles than that
    private fun keptTiles(renderer: MapRenderer, argb: Boolean, tileSize: Int): Int {
        // ARGB_8888 Garmin and MVT tiles take twice the RGB_565 Mapsforge ones
        val byMemory = (KEPT_BYTES / ((if (argb) 4L else 2L) * tileSize * tileSize)).toInt().coerceAtLeast(1)
        val labelTiles = (renderer.labelPass as? TiledLabelPass<*>)?.storeCapacity ?: return byMemory
        return minOf(byMemory, labelTiles)
    }

    /**
     * Stops drawing and closes the engine and dataset in the background, so leaving never waits
     * for a render in progress.
     */
    fun close() {
        closed = true
        // the last frame must not keep drawing tiles the engine hands back to Mapsforge's pool
        invalidate()
        engine.closeLater {
            dataset.close()
            loadedStyle?.close()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinchDetector.onTouchEvent(event)
        if (!pinchDetector.isInProgress) {
            dragDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFFDADADA.toInt())
        if (closed) {
            return
        }
        val vp = viewport
            ?: Viewport.fitting(bounds = dataset.bounds, width = width, height = height, tileSize = engine.tileSizePx)
                .also { viewport = it }
        val tiles = vp.tiles(width, height)
        engine.show(tiles)
        val size = vp.tileSize.toFloat()
        for (tile in tiles) {
            val left = vp.screenX(tile.x, width)
            val top = vp.screenY(tile.y, height)
            val image = engine[tile]
            if (image != null) {
                canvas.drawBitmap(image.bitmap, left, top, null)
            } else {
                canvas.drawRect(left + 1f, top + 1f, left + size - 1f, top + size - 1f, missingPaint)
            }
        }
        drawLabels(canvas, vp)
        drawPanel(canvas, vp)
    }

    // the label pass over the tiles, upright, as the viewer never turns the map
    private fun drawLabels(canvas: Canvas, vp: Viewport) {
        val pass = engine.labelPass ?: return
        if (width <= 0
            || height <= 0
        ) {
            return
        }
        val view = vp.labelViewport(width, height, rotationDeg = 0.0)
        // one placement at a time, so a gesture's frames don't cancel each other and labels keep
        // landing; a placement wakes the next frame, which asks for the latest view
        val inFlight = preparedFor != null
            && pass.prepared?.view != preparedFor
        if (!inFlight
            && (view != preparedFor || labelsStale)
        ) {
            preparedFor = view
            labelsStale = false
            pass.prepare(view)
        }
        when (pass) {
            is MapsforgeLabelPass -> {
                pass.draw(canvas = AndroidGraphicFactory.createGraphicContext(canvas), view = view)
            }
            is GarminLabelPass -> {
                canvas.drawBitmap(garminLabels(pass = pass, view = view) ?: return, 0f, 0f, null)
            }
            is MvtLabelPass -> {
                canvas.drawBitmap(mvtLabels(pass = pass, view = view) ?: return, 0f, 0f, null)
            }
        }
    }

    // the MVT pass draws into its own bitmap, redrawn when the placement or view changes
    private fun mvtLabels(pass: MvtLabelPass, view: LabelViewport): Bitmap? {
        val prepared = pass.prepared ?: return null
        val reused = mvtTarget
        val target = if (reused != null
            && reused.widthPx == view.widthPx
            && reused.heightPx == view.heightPx
        ) {
            reused
        } else {
            (pass.newTarget(widthPx = view.widthPx, heightPx = view.heightPx) as AndroidMvtRenderTarget)
                .also { mvtTarget = it }
        }
        val drawn = mvtDrawn
        if (target !== reused
            || drawn == null
            || drawn.first !== prepared
            || drawn.second != view
        ) {
            target.clear()
            pass.draw(target = target, view = view, prepared = prepared)
            mvtDrawn = Pair(prepared, view)
        }
        return target.bitmap
    }

    // the Garmin pass draws into its own bitmap, redrawn when the placement or view changes
    private fun garminLabels(pass: GarminLabelPass, view: LabelViewport): Bitmap? {
        val prepared = pass.prepared ?: return null
        val reused = garminTarget
        val target = if (reused != null
            && reused.bitmap.width == view.widthPx
            && reused.bitmap.height == view.heightPx
        ) {
            reused
        } else {
            AndroidRenderTarget(widthPx = view.widthPx, heightPx = view.heightPx, renderScale = renderScale)
                .also { garminTarget = it }
        }
        val drawn = garminDrawn
        if (target !== reused
            || drawn == null
            || drawn.first !== prepared
            || drawn.second != view
        ) {
            target.beginFrame(GarminColor.Transparent)
            pass.draw(target = target, view = view, prepared = prepared)
            target.endFrame()
            garminDrawn = Pair(prepared, view)
        }
        return target.bitmap
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) {
            return
        }
        val dx = scroller.currX - flungX
        val dy = scroller.currY - flungY
        flungX = scroller.currX
        flungY = scroller.currY
        viewport = viewport?.dragged(dx = dx.toDouble(), dy = dy.toDouble())
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        invalidate()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            val bars = insets.getInsets(types)
            insetLeft = bars.left
            insetTop = bars.top
            insetRight = bars.right
        } else {
            @Suppress("DEPRECATION")
            insetLeft = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            insetTop = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            insetRight = insets.systemWindowInsetRight
        }
        invalidate()
        return insets
    }

    private fun zoomAt(steps: Int, x: Float, y: Float) {
        scroller.forceFinished(true)
        viewport = viewport?.zoomed(steps = steps, focusX = x - width / 2.0, focusY = y - height / 2.0)
        invalidate()
    }

    private fun drawPanel(canvas: Canvas, vp: Viewport) {
        val lines = listOf(
            "z${vp.zoom}, ${vp.tileSize} px tiles, setup $setupMs ms",
            engine.stats.summary(),
        )
        val lineHeight = textPaint.textSize * 1.4f
        val left = insetLeft + 12f * density
        val available = width - insetRight - 12f * density - left
        // an error gets a few lines, so its cause and the pointer to the log stay readable
        val error = engine.error?.let { text ->
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, available.toInt().coerceAtLeast(1))
                .setMaxLines(ERROR_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }
        val errorTop = insetTop + lineHeight * (lines.size + 0.3f)
        val bottom = errorTop + (error?.height ?: 0) + lineHeight * 0.2f
        canvas.drawRect(0f, 0f, width.toFloat(), bottom, panelPaint)
        var baseline = insetTop + lineHeight
        for (line in lines) {
            val fitted = TextUtils.ellipsize(line, textPaint, available, TextUtils.TruncateAt.END)
            canvas.drawText(fitted, 0, fitted.length, left, baseline, textPaint)
            baseline += lineHeight
        }
        if (error != null) {
            canvas.save()
            canvas.translate(left, errorTop)
            error.draw(canvas)
            canvas.restore()
        }
    }

    private fun imagesFor(driver: MapFormatDriver): TileImages<TileImage> {
        return when (driver) {
            is MapsforgeDriver -> MapsforgeImages
            is GarminDriver -> GarminImages
            is MvtDriver -> MvtImages
            else -> throw IllegalArgumentException("imagesFor(), no images for driver ${driver.id}")
        }
    }

    private companion object {

        // the A54 stops scaling above its 4 big cores (docs/performance.md M2.7b)
        const val RENDER_THREADS = 4

        // 100 Mapsforge or 50 Garmin or MVT tiles at 512 px
        const val KEPT_BYTES = 50L shl 20

        const val ERROR_LINES = 3
    }

    /**
     * A drawn tile and how to give its bitmap back.
     */
    private class TileImage(
        val bitmap: Bitmap,
        val release: () -> Unit,
    )

    /**
     * Draws the Mapsforge bitmap directly; the engine releases it when it drops the tile.
     */
    private object MapsforgeImages : TileImages<TileImage> {

        override fun newTarget(sizePx: Int): RenderTarget {
            return TileBitmapTarget(sizePx)
        }

        override fun take(target: RenderTarget): TileImage {
            val tile = checkNotNull((target as TileBitmapTarget).bitmap)
            return TileImage(bitmap = checkNotNull(AndroidGraphicFactory.getBitmap(tile))) {
                tile.decrementRefCount()
            }
        }

        override fun release(image: TileImage) {
            image.release()
        }
    }

    /**
     * Draws the MVT canvas's bitmap; dropping it frees the pixels, as a frame may still draw it.
     */
    private object MvtImages : TileImages<TileImage> {

        override fun newTarget(sizePx: Int): RenderTarget {
            return MvtTileTarget(sizePx)
        }

        override fun take(target: RenderTarget): TileImage {
            val canvas = checkNotNull((target as MvtTileTarget).canvas) as AndroidMvtRenderTarget
            return TileImage(bitmap = canvas.bitmap) {}
        }

        override fun release(image: TileImage) {
            image.release()
        }
    }

    /**
     * Draws the Garmin canvas's bitmap; dropping it frees the pixels, as a frame may still draw it.
     */
    private object GarminImages : TileImages<TileImage> {

        override fun newTarget(sizePx: Int): RenderTarget {
            return GarminTileTarget(sizePx)
        }

        override fun take(target: RenderTarget): TileImage {
            val canvas = checkNotNull((target as GarminTileTarget).canvas) as AndroidRenderTarget
            return TileImage(bitmap = canvas.bitmap) {}
        }

        override fun release(image: TileImage) {
            image.release()
        }
    }
}
