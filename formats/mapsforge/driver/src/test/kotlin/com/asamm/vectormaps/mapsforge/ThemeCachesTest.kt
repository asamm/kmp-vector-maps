/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.SymbolOrientation
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * The theme's matching caches compare tags in full, and its scaled Paints are kept for a window of
 * zoom levels plus the ones a job holds (D29).
 */
class ThemeCachesTest {

    private val displayModel = DisplayModel()
    private val themeFuture = parse(
        """
            <rule e="node" k="k1" v="a"><circle radius="5" fill="#ff0000"/></rule>
            <rule e="way" k="highway" v="*"><line stroke="#000000" stroke-width="1"/></rule>
            <rule e="node" k="place" v="*"><caption k="name" font-size="10" fill="#000000"/></rule>
        """,
    )
    private val recorder = Recorder()

    @AfterTest
    fun tearDown() {
        themeFuture.decrementRefCount()
    }

    @Test
    fun tagListsWithTheSameHashDoNotShareMatches() {
        // one tag hash 1 higher, the next 31 lower: the same 31-based list hash
        val matching = listOf(Tag("k1", "a"), Tag("k2", "x"))
        val other = listOf(Tag("k1", "b"), Tag("k2", "Y"))
        assertEquals(
            31 * matching[0].hashCode() + matching[1].hashCode(),
            31 * other[0].hashCode() + other[1].hashCode(),
        )
        val context = context(zoom = 14)

        matchNode(context, matching)
        matchNode(context, other)

        assertEquals(1, recorder.circles)
    }

    @Test
    fun zoomInTheWindowIsNotScaledAgain() {
        strokeWidth(context(zoom = 14))
        val stroke = recorder.lastStroke
        val caption = caption(context(zoom = 14))
        for (zoom in 15..17) {
            context(zoom = zoom)
        }

        strokeWidth(context(zoom = 14))
        assertSame(stroke, recorder.lastStroke)
        assertSame(caption, caption(context(zoom = 14)))
    }

    @Test
    fun heldZoomKeepsItsScaledPaints() {
        val theme = themeFuture.get()
        theme.acquireZoom(14)
        val held = context(zoom = 14)
        val scaled = strokeWidth(held)
        for (zoom in 15..19) {
            context(zoom = zoom)
        }

        assertEquals(scaled, strokeWidth(held))

        theme.releaseZoom(14)
        for (zoom in listOf(20, 8, 9, 10)) {
            context(zoom = zoom)
        }

        // evicted: the line falls back to its unscaled Paint
        assertNotEquals(scaled, strokeWidth(held))
    }

    private fun context(zoom: Int): RenderContext {
        val tile = Tile(0, 0, zoom.toByte(), 256)
        val store = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        val job = RendererJob(tile, store, themeFuture, displayModel, 1.0f, false, false)
        return RenderContext(job, AwtGraphicFactory.INSTANCE)
    }

    private fun matchNode(context: RenderContext, tags: List<Tag>) {
        val poi = PointOfInterest(0, tags, LatLong(0.0, 0.0))
        themeFuture.get().matchNode(recorder, context, poi)
    }

    private fun caption(context: RenderContext): Paint {
        matchNode(context, listOf(Tag("place", "town"), Tag("name", "Town")))
        return recorder.lastCaption!!
    }

    private fun strokeWidth(context: RenderContext): Float {
        val tile = context.rendererJob.tile
        val way = PolylineContainer(
            arrayOf(Point(0.0, 0.0), Point(10.0, 10.0)),
            tile,
            tile,
            listOf(Tag("highway", "track")),
        )
        themeFuture.get().matchLinearWay(recorder, context, way)
        return recorder.lastStroke!!.strokeWidth
    }

    private fun parse(rules: String): RenderThemeFuture {
        val xml = """
            <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5">
            $rules
            </rendertheme>
        """.trimIndent()
        val file: File = createTempFile(prefix = "theme", suffix = ".xml").toFile()
        try {
            file.writeText(xml)
            val future = RenderThemeFuture(AwtGraphicFactory.INSTANCE, ExternalRenderTheme(file), displayModel)
            future.run()
            future.get()
            return future
        } finally {
            file.delete()
        }
    }

    /**
     * Counts circles and keeps the last line and caption fill Paints; ignores everything else.
     */
    private class Recorder : RenderCallback {

        var circles = 0
        var lastCaption: Paint? = null
        var lastStroke: Paint? = null

        override fun renderArea(renderContext: RenderContext, fill: Paint, stroke: Paint, level: Int, way: PolylineContainer) {
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
        }

        override fun renderAreaSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            symbol: Bitmap,
            way: PolylineContainer,
        ) {
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
            lastCaption = fill
        }

        override fun renderPointOfInterestCircle(
            renderContext: RenderContext,
            radius: Float,
            fill: Paint,
            stroke: Paint,
            level: Int,
            poi: PointOfInterest,
        ) {
            circles++
        }

        override fun renderPointOfInterestSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            boundary: Rectangle?,
            symbol: Bitmap,
            poi: PointOfInterest,
        ) {
        }

        override fun renderWay(
            renderContext: RenderContext,
            stroke: Paint,
            dy: Float,
            curveStyle: Curve,
            level: Int,
            way: PolylineContainer,
        ) {
            lastStroke = stroke
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
        }
    }
}
