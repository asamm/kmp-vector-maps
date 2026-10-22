/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import com.asamm.mapsforge.viewer.common.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tile map view: pan by drag, zoom by pinch or double tap. Tiles render on a bounded pool sharing
 * one DatabaseRenderer; the overlay shows per-tile render timings.
 */
@SuppressLint("ViewConstructor")
class MapView(
    context: Context,
    mapFile: File,
    themeFile: File,
) : View(context) {

    private val graphicFactory = AndroidGraphicFactory.INSTANCE
    private val displayModel = DisplayModel()
    private val tileSize = displayModel.tileSize
    private val mapDataStore = MapFile(mapFile)
    private val themeFuture = RenderThemeFuture(graphicFactory, ExternalRenderTheme(themeFile), displayModel)
    private val renderer = DatabaseRenderer(
        mapDataStore,
        graphicFactory,
        InMemoryTileCache(64),
        null,
        true,
        true,
        null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(RENDER_THREADS))
    private val tiles = ConcurrentHashMap<Tile, TileBitmap>()
    private val inFlight = ConcurrentHashMap.newKeySet<Tile>()
    private val renderTimesMs = mutableListOf<Double>()
    private var themeParseMs = 0.0

    private var viewport = Viewport.of(
        center = mapDataStore.startPosition() ?: mapDataStore.boundingBox().centerPoint,
        zoom = mapDataStore.startZoomLevel()?.toInt() ?: 14,
        tileSize = tileSize,
    )

    private val placeholderPaint = Paint().apply {
        color = Color.LTGRAY
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 14f * resources.displayMetrics.density
    }
    private val textBackground = Paint().apply {
        color = 0xCCFFFFFF.toInt()
    }

    // the app draws edge-to-edge, keep the status text above the navigation bar
    private var bottomInset = 0

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            viewport = viewport.panBy(-distanceX.toDouble(), -distanceY.toDouble())
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAround(delta = 1, x = e.x, y = e.y)
            return true
        }
    })

    private var pinchScale = 1f
    private val scaleGestures = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinchScale = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pinchScale *= detector.scaleFactor
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (pinchScale > PINCH_ZOOM_IN) {
                zoomAround(delta = 1, x = detector.focusX, y = detector.focusY)
            } else if (pinchScale < PINCH_ZOOM_OUT) {
                zoomAround(delta = -1, x = detector.focusX, y = detector.focusY)
            }
        }
    })

    init {
        val start = System.nanoTime()
        themeFuture.run()
        themeParseMs = (System.nanoTime() - start) / 1_000_000.0
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.systemBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        return insets
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestures.onTouchEvent(event)
        if (!scaleGestures.isInProgress) {
            gestures.onTouchEvent(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(224, 224, 224))
        val vp = viewport
        val visible = vp.visibleTiles(width, height)
        request(visible)
        val originX = vp.centerX - width / 2.0
        val originY = vp.centerY - height / 2.0
        for (tile in visible) {
            val left = (tile.tileX.toLong() * tileSize - originX).toFloat()
            val top = (tile.tileY.toLong() * tileSize - originY).toFloat()
            val bitmap = tiles[tile]
            if (bitmap == null) {
                canvas.drawRect(left + 1, top + 1, left + tileSize - 1, top + tileSize - 1, placeholderPaint)
            } else {
                canvas.drawBitmap(AndroidGraphicFactory.getBitmap(bitmap), left, top, null)
            }
        }
        drawStatus(canvas, vp)
    }

    fun close() {
        scope.cancel()
        tiles.values.forEach { it.decrementRefCount() }
        tiles.clear()
        themeFuture.decrementRefCount()
        mapDataStore.close()
    }

    private fun zoomAround(delta: Int, x: Float, y: Float) {
        viewport = viewport.zoomBy(delta = delta, anchorX = x - width / 2.0, anchorY = y - height / 2.0)
        invalidate()
    }

    private fun request(visible: List<Tile>) {
        for (tile in visible) {
            if (tiles.containsKey(tile) || !inFlight.add(tile)) {
                continue
            }
            scope.launch {
                render(tile)
            }
        }
        if (tiles.size > MAX_CACHED_TILES) {
            val keep = visible.toSet()
            tiles.keys.filter { it !in keep }.forEach { tile ->
                tiles.remove(tile)?.decrementRefCount()
            }
        }
    }

    private fun render(tile: Tile) {
        try {
            val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, 1f, false, false)
            val start = System.nanoTime()
            val bitmap = renderer.executeJob(job) ?: return
            val ms = (System.nanoTime() - start) / 1_000_000.0
            synchronized(renderTimesMs) {
                renderTimesMs += ms
            }
            tiles[tile] = bitmap
            postInvalidate()
        } finally {
            inFlight.remove(tile)
        }
    }

    private fun drawStatus(canvas: Canvas, vp: Viewport) {
        val sorted = synchronized(renderTimesMs) {
            renderTimesMs.sorted()
        }
        val timing = if (sorted.isEmpty()) {
            "no tiles yet"
        } else {
            "tiles %d · avg %.1f · median %.1f · max %.1f ms".format(
                sorted.size,
                sorted.average(),
                sorted[sorted.size / 2],
                sorted.last(),
            )
        }
        val lines = listOf("z%d · tile %d px · theme parse %.0f ms".format(vp.zoom, tileSize, themeParseMs), timing)
        val lineHeight = textPaint.textSize * 1.3f
        val top = height - bottomInset - lineHeight * lines.size - lineHeight / 2
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), textBackground)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, textPaint.textSize / 2, top + lineHeight * (i + 1), textPaint)
        }
    }

    private companion object {
        const val RENDER_THREADS = 4
        const val MAX_CACHED_TILES = 200
        const val PINCH_ZOOM_IN = 1.4f
        const val PINCH_ZOOM_OUT = 0.7f
    }
}
