/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.Curve
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.Paint
import com.asamm.vectormaps.mapsforge.core.graphics.Position
import com.asamm.vectormaps.mapsforge.core.graphics.SymbolOrientation
import com.asamm.vectormaps.mapsforge.core.graphics.TextOrientation
import com.asamm.vectormaps.mapsforge.core.model.LatLong
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.core.model.Tag
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.core.util.LRUCache
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mapsforge.map.datastore.MultiMapDataStore
import com.asamm.vectormaps.mapsforge.map.datastore.PointOfInterest
import com.asamm.vectormaps.mapsforge.map.layer.renderer.PolylineContainer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.ExternalRenderTheme
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderCallback
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The theme's matching caches compare tags in full and stay intact under concurrent misses (P7), and
 * its scaled Paints are kept for a window of zoom levels plus the ones a job holds (D29); a
 * `scale="all"` line without dashes scales (A9, C9).
 */
class ThemeCachesTest {

    private val displayModel = DisplayModel()
    private val themeFuture = parse(
        """
            <rule e="node" k="k1" v="a"><circle radius="5" fill="#ff0000"/></rule>
            <rule e="way" k="highway" v="*"><line stroke="#000000" stroke-width="1"/></rule>
            <rule e="way" k="railway" v="*"><line stroke="#000000" stroke-width="1" scale="all"/></rule>
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

    @Test
    fun scaleAllLineWithoutDashesScalesItsWidth() {
        // upstream throws while scaling the railway line (A9)
        val context = context(zoom = 14)

        assertEquals(strokeWidth(context), strokeWidth(context, key = "railway", value = "rail"))
        assertNotEquals(1f, strokeWidth(context, key = "railway", value = "rail"))
    }

    @Test
    fun matchingCacheSurvivesConcurrentMisses() {
        val context = context(zoom = 14)
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val start = CountDownLatch(1)
            val counts = (0 until THREADS).map { thread ->
                pool.submit<Int> {
                    val recorder = Recorder()
                    start.await()
                    for (i in 0 until WAYS_PER_THREAD) {
                        // a new key, evicting once all threads together pass the cache size
                        matchWay(context, recorder, listOf(Tag("highway", "track"), Tag("ref", "$thread-$i")))
                        // a key every thread reads
                        matchWay(context, recorder, listOf(Tag("highway", "track")))
                    }
                    recorder.ways
                }
            }
            start.countDown()
            for (count in counts) {
                assertEquals(2 * WAYS_PER_THREAD, count.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        assertIntact(cache = themeFuture.get().privateField("wayMatchingCache") as LRUCache<*, *>)
    }

    /**
     * A map written by two threads without its lock loses the link between its size, its entry
     * order and its hash table, which a lost update alone never shows.
     */
    private fun assertIntact(cache: LRUCache<*, *>) {
        var linked = 0
        val entries = cache.entries.iterator()
        while (entries.hasNext()
            && linked <= cache.size
        ) {
            val key = entries.next().key
            assertTrue(cache.containsKey(key), "matching cache entry $linked not found by its key")
            linked++
        }
        assertEquals(cache.size, linked, "matching cache size against its linked entries")
        assertTrue(cache.size <= cache.capacity, "matching cache over its capacity: ${cache.size}")
    }

    private fun Any.privateField(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
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

    private fun strokeWidth(context: RenderContext, key: String = "highway", value: String = "track"): Float {
        matchWay(context, recorder, listOf(Tag(key, value)))
        return recorder.lastStroke!!.strokeWidth
    }

    private fun matchWay(context: RenderContext, recorder: Recorder, tags: List<Tag>) {
        val tile = context.rendererJob.tile
        val way = PolylineContainer(arrayOf(Point(0.0, 0.0), Point(10.0, 10.0)), tile, tile, tags)
        themeFuture.get().matchLinearWay(recorder, context, way)
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
     * Counts circles and lines and keeps the last line and caption fill Paints; ignores everything
     * else.
     */
    private class Recorder : RenderCallback {

        var circles = 0
        var ways = 0
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
            ways++
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

    private companion object {

        const val THREADS = 8

        // 8 × 25,000 new keys pass the theme's 8,192-entry matching cache many times over
        const val WAYS_PER_THREAD = 25_000

        const val TIMEOUT_SECONDS = 60L
    }
}
