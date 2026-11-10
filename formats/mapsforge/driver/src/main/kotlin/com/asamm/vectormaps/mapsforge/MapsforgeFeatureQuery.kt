/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.CaptionPosition
import com.asamm.vectormaps.core.FeatureCaption
import com.asamm.vectormaps.core.FeatureQuery
import com.asamm.vectormaps.core.FeatureStyle
import com.asamm.vectormaps.core.FeatureSymbol
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelDisplay
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.Curve
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.core.graphics.Position
import com.asamm.vectormaps.mapsforge.core.graphics.SymbolOrientation
import com.asamm.vectormaps.mapsforge.core.graphics.TextOrientation
import com.asamm.vectormaps.mapsforge.core.model.LatLong
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.core.model.Tag
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.datastore.MapDataStore
import com.asamm.vectormaps.mapsforge.map.datastore.PointOfInterest
import com.asamm.vectormaps.mapsforge.map.layer.renderer.PolylineContainer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderCallback
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The [FeatureQuery] of a [MapsforgeRenderer]: matches the tags against its compiled theme, as a
 * render matches a map's POIs, and records what the theme would draw. Shares the renderer's
 * permits, so its close waits for running queries.
 */
class MapsforgeFeatureQuery internal constructor(
    private val graphicFactory: GraphicFactory,
    private val store: MapDataStore,
    private val themeFuture: RenderThemeFuture,
    private val displayModel: DisplayModel,
    private val textScale: Float,
    private val tileSize: Int,
    private val symbols: SymbolPngs,
    private val permits: Semaphore,
    private val closed: AtomicBoolean,
) : FeatureQuery {

    override fun point(tags: Map<String, String>, zoom: Int): FeatureStyle {
        require(zoom in 0..TileRequest.MAX_ZOOM) {
            "point(), zoom $zoom"
        }
        permits.acquireUninterruptibly()
        try {
            check(!closed.get()) {
                "point(), renderer closed"
            }
            return resolve(tags = tags, zoom = zoom)
        } finally {
            permits.release()
        }
    }

    private fun resolve(tags: Map<String, String>, zoom: Int): FeatureStyle {
        val job = RendererJob(
            Tile(0, 0, zoom.toByte(), tileSize),
            store,
            themeFuture,
            displayModel,
            textScale,
            isTransparent = false,
            labelsOnly = false,
        )
        val theme = themeFuture.get()
        // keeps the zoom's scaled Paints while matching, as a render does (D29)
        theme.acquireZoom(job.tile.zoomLevel)
        try {
            val context = RenderContext(job, graphicFactory)
            try {
                val poi = PointOfInterest(0, tags.map { (key, value) -> Tag(key, value) }, LatLong(0.0, 0.0))
                return match(context = context, poi = poi)
            } finally {
                context.destroy()
            }
        } finally {
            theme.releaseZoom(job.tile.zoomLevel)
        }
    }

    /**
     * What the theme draws for [poi] in [context], whose job holds the theme's zoom.
     */
    internal fun match(context: RenderContext, poi: PointOfInterest): FeatureStyle {
        val recorder = Recorder()
        themeFuture.get().matchNode(recorder, context, poi)
        if (recorder.symbols.isEmpty()
            && recorder.captions.isEmpty()
        ) {
            return FeatureStyle.EMPTY
        }
        return FeatureStyle(symbols = recorder.symbols, captions = recorder.captions)
    }

    // what a node's instructions draw: symbols and captions; the rest is for ways
    private inner class Recorder : RenderCallback {

        val symbols = ArrayList<FeatureSymbol>()

        val captions = ArrayList<FeatureCaption>()

        override fun renderPointOfInterestSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            boundary: Rectangle?,
            symbol: Bitmap,
            poi: PointOfInterest,
        ) {
            val png = this@MapsforgeFeatureQuery.symbols.of(symbol)
            symbols += FeatureSymbol(
                id = png.id,
                png = png.png,
                widthPx = symbol.width,
                heightPx = symbol.height,
                // centred on the point without a boundary, as the map draws it
                offsetXPx = boundary?.left?.toFloat() ?: (-symbol.width / 2f),
                offsetYPx = boundary?.top?.toFloat() ?: (-symbol.height / 2f),
                priority = priority,
                display = displayOf(display),
            )
        }

        override fun renderPointOfInterestCaption(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            caption: String,
            horizontalOffset: Float,
            verticalOffset: Float,
            fill: Paint,
            stroke: Paint,
            position: Position,
            maxTextWidth: Int,
            poi: PointOfInterest,
        ) {
            // a blank caption draws nothing, as the label pass skips it
            if (fill.getTextHeight(caption) <= 0) {
                return
            }
            captions += FeatureCaption(
                text = LabelText(
                    value = caption,
                    sizePx = fill.textSize,
                    fillArgb = fill.color,
                    haloArgb = stroke.color,
                    haloWidthPx = stroke.strokeWidth,
                ),
                position = positionOf(position),
                offsetXPx = horizontalOffset,
                offsetYPx = verticalOffset,
                maxWidthPx = maxTextWidth,
                priority = priority,
                display = displayOf(display),
            )
        }

        override fun renderPointOfInterestCircle(
            renderContext: RenderContext,
            radius: Float,
            fill: Paint,
            stroke: Paint,
            level: Int,
            poi: PointOfInterest,
        ) {
            // circles are drawn into tiles, not by a host's points
        }

        override fun renderArea(
            renderContext: RenderContext,
            fill: Paint,
            stroke: Paint,
            level: Int,
            way: PolylineContainer,
        ) {
            // ways only
        }

        override fun renderAreaCaption(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            caption: String,
            horizontalOffset: Float,
            verticalOffset: Float,
            fill: Paint,
            stroke: Paint,
            position: Position,
            maxTextWidth: Int,
            way: PolylineContainer,
        ) {
            // ways only
        }

        override fun renderAreaSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            symbol: Bitmap,
            way: PolylineContainer,
        ) {
            // ways only
        }

        override fun renderWay(
            renderContext: RenderContext,
            stroke: Paint,
            dy: Float,
            curveStyle: Curve,
            level: Int,
            way: PolylineContainer,
        ) {
            // ways only
        }

        override fun renderWaySymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            symbol: Bitmap,
            dy: Float,
            boundary: Rectangle?,
            repeat: Boolean,
            repeatGap: Float,
            repeatStart: Float,
            symbolOrientation: SymbolOrientation,
            way: PolylineContainer,
        ) {
            // ways only
        }

        override fun renderWayText(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            text: String,
            dy: Float,
            fill: Paint,
            stroke: Paint,
            repeat: Boolean,
            repeatGap: Float,
            repeatStart: Float,
            textOrientation: TextOrientation,
            way: PolylineContainer,
        ) {
            // ways only
        }
    }

    private fun displayOf(display: Display): LabelDisplay {
        return when (display) {
            Display.ALWAYS -> LabelDisplay.ALWAYS
            Display.ORDER -> LabelDisplay.ORDER
            else -> LabelDisplay.IF_SPACE
        }
    }

    private fun positionOf(position: Position): CaptionPosition {
        return when (position) {
            // Mapsforge places an `auto` caption as a centred one
            Position.AUTO, Position.CENTER -> CaptionPosition.CENTER
            Position.BELOW -> CaptionPosition.BELOW
            Position.BELOW_LEFT -> CaptionPosition.BELOW_LEFT
            Position.BELOW_RIGHT -> CaptionPosition.BELOW_RIGHT
            Position.ABOVE -> CaptionPosition.ABOVE
            Position.ABOVE_LEFT -> CaptionPosition.ABOVE_LEFT
            Position.ABOVE_RIGHT -> CaptionPosition.ABOVE_RIGHT
            Position.LEFT -> CaptionPosition.LEFT
            Position.RIGHT -> CaptionPosition.RIGHT
        }
    }
}
