/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.MapsforgeRenderer
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import com.asamm.vectormaps.viewer.TileEngine
import com.asamm.vectormaps.viewer.TileImages
import com.asamm.vectormaps.viewer.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.mapelements.PointTextContainer
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.mapelements.WayTextContainer
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * The M5 gate (ROADMAP): a random walk of pans and zooms on a phone-sized screen, rendered through
 * the Mapsforge driver and the viewer's tile engine on [THREADS] threads for half of `soak.minutes`
 * per label mode. Every `SEPARATE` render is compared pixel by pixel with a render of the same tile,
 * one at a time, by a second renderer, and so is the tile's label store entry (kind, point-label
 * text, position, priority, clash box, standalone symbol size).
 * `IN_TILE` tiles depend on render order (M6.1), so they are only checked to render. Skipped
 * without the argument:
 * `./gradlew :dev:bench:android:connectedReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=com.asamm.vectormaps.bench.android.SoakTest
 * -Pandroid.testInstrumentationRunnerArguments.soak.minutes=15`.
 */
@RunWith(AndroidJUnit4::class)
class SoakTest {

    // the run plus a margin: a hung render fails the test instead of blocking the device for good
    @get:Rule
    val timeout: Timeout = Timeout.builder()
        .withTimeout((runCatching { runMs() }.getOrNull() ?: 0L) + TIMEOUT_MARGIN_MS, TimeUnit.MILLISECONDS)
        .withLookingForStuckThread(true)
        .build()

    @Test
    fun separateLabelsMatchSerialRenders() {
        soak(labelMode = LabelMode.SEPARATE, compare = true)
    }

    @Test
    fun inTileLabelsRender() {
        soak(labelMode = LabelMode.IN_TILE, compare = false)
    }

    private fun soak(labelMode: LabelMode, compare: Boolean) {
        val runMs = runMs()
        assumeTrue("soak.minutes not set", runMs != null)
        // the files first: preparing them creates the graphic factory the driver takes
        val mapFile = BenchScene.mapFile()
        val themeFile = BenchScene.themeFile()
        val driver = MapsforgeDriver(AndroidGraphicFactory.INSTANCE)
        val dataset = driver.open(listOf(mapFile))
        val style = driver.loadStyle(themeFile)
        val config = RenderConfig(tileSizePx = BenchScene.TILE_SIZE, labelMode = labelMode)
        val reference = dataset.createRenderer(style = style, selection = StyleSelection.DEFAULT, config = config)
        try {
            val recording = Recording(
                renderer = dataset.createRenderer(style = style, selection = StyleSelection.DEFAULT, config = config),
                hashing = compare,
            )
            val arrived = AtomicInteger()
            // owns the renderer from here
            val engine = TileEngine(
                renderer = recording,
                images = ReleasingImages,
                threads = THREADS,
                capacity = KEPT_TILES,
            ) {
                arrived.incrementAndGet()
            }
            try {
                walk(
                    labelMode = labelMode,
                    runMs = checkNotNull(runMs),
                    dataset = dataset,
                    engine = engine,
                    recording = recording,
                    reference = reference,
                    arrived = arrived,
                )
            } catch (e: Throwable) {
                // logged before the close below, which waits for a render that may never return
                Log.e(TAG, "soak($labelMode) failed", e)
                throw e
            } finally {
                engine.close()
            }
        } finally {
            reference.close()
            dataset.close()
        }
    }

    private fun walk(
        labelMode: LabelMode,
        runMs: Long,
        dataset: MapDataset,
        engine: TileEngine<Unit>,
        recording: Recording,
        reference: MapRenderer,
        arrived: AtomicInteger,
    ) {
        val expected = HashMap<TileRequest, Drawn>()
        val mismatches = LinkedHashSet<TileRequest>()
        var steps = 0
        var compared = 0
        var differed = 0
        var labelsDiffered = 0
        var withLabels = 0
        fun drain() {
            while (true) {
                val drawn = recording.drawn.poll() ?: return
                compared++
                if (drawn.labelCount > 0) {
                    withLabels++
                }
                val serial = expected.getOrPut(drawn.tile) { serialRender(reference, drawn.tile) }
                if (drawn.pixels != serial.pixels) {
                    differed++
                    mismatches += drawn.tile
                }
                if (drawn.labels != serial.labels) {
                    labelsDiffered++
                    mismatches += drawn.tile
                }
            }
        }
        val random = Random(SEED)
        val home = home(dataset)
        var viewport = home
        val deadline = System.currentTimeMillis() + runMs
        while (System.currentTimeMillis() < deadline) {
            viewport = step(viewport = viewport, home = home, bounds = dataset.bounds, random = random)
            val tiles = viewport.tiles(SCREEN_WIDTH, SCREEN_HEIGHT)
            engine.show(tiles)
            steps++
            // most steps move on at once, as a fling does; some wait for the whole screen
            if (random.nextInt(4) == 0) {
                waitFor(timeoutMs = 20_000, what = "screen not drawn") {
                    engine.error?.let { error(it) }
                    tiles.all { engine[it] != null }
                }
            } else {
                Thread.sleep(random.nextLong(5, 40))
            }
            engine.error?.let { error(it) }
            drain()
        }
        val stats = engine.stats.summary()
        // the last renders too: close waits for those running, then nothing starts
        engine.close()
        waitFor(timeoutMs = 20_000, what = "renders still running after close") {
            recording.running.get() == 0
        }
        drain()
        val summary = "$labelMode: $steps steps, $stats, ${recording.drawnCount.get()} drawn, " +
            "$compared compared, $differed differed, labels of $labelsDiffered differed " +
            "($withLabels with labels), " +
            "in ${mismatches.size} tiles ${mismatches.take(10)}"
        Log.i(TAG, summary)
        writeReport(name = "soak-$labelMode.txt", text = summary)
        assertEquals(summary, 0, differed)
        assertEquals(summary, 0, labelsDiffered)
        if (recording.hashing) {
            // an always-empty entry would make the label check pass without comparing anything
            check(withLabels > 0) {
                "soak($labelMode), no render had labels: $summary"
            }
        }
        check(arrived.get() > 0) {
            "soak($labelMode), no tile arrived"
        }
        if (recording.hashing) {
            check(compared == recording.drawnCount.get()) {
                "soak($labelMode), $compared of ${recording.drawnCount.get()} renders compared"
            }
        }
    }

    // the map's bounds at a zoom that shows streets
    private fun home(dataset: MapDataset): Viewport {
        val bounds = dataset.bounds
        return Viewport.at(
            lat = (bounds.minLat + bounds.maxLat) / 2,
            lon = (bounds.minLon + bounds.maxLon) / 2,
            zoom = 14,
            tileSize = BenchScene.TILE_SIZE,
        )
    }

    // a zoom by one level, else a pan of up to a screen; back home once the centre leaves the data
    private fun step(viewport: Viewport, home: Viewport, bounds: GeoBounds, random: Random): Viewport {
        val next = if (random.nextInt(4) == 0) {
            val steps = if (random.nextBoolean()) 1 else -1
            viewport.zoomed(
                steps = steps,
                focusX = random.nextDouble(-SCREEN_WIDTH / 2.0, SCREEN_WIDTH / 2.0),
                focusY = random.nextDouble(-SCREEN_HEIGHT / 2.0, SCREEN_HEIGHT / 2.0),
            )
        } else {
            viewport.dragged(
                dx = random.nextDouble(-SCREEN_WIDTH.toDouble(), SCREEN_WIDTH.toDouble()),
                dy = random.nextDouble(-SCREEN_HEIGHT.toDouble(), SCREEN_HEIGHT.toDouble()),
            )
        }
        if (next.zoom !in MIN_ZOOM..MAX_ZOOM
            || next.lat !in bounds.minLat..bounds.maxLat
            || next.lon !in bounds.minLon..bounds.maxLon
        ) {
            return home
        }
        return next
    }

    private fun serialRender(reference: MapRenderer, tile: TileRequest): Drawn {
        val target = TileBitmapTarget(BenchScene.TILE_SIZE)
        val result = reference.render(tile, target) { false }
        check(result == RenderResult.DRAWN) {
            "serialRender($tile), $result"
        }
        val bitmap = checkNotNull(target.bitmap)
        try {
            val labels = labelEntry(reference, tile)
            return Drawn(
                tile = tile,
                pixels = pixelHash(bitmap),
                labels = labels.first,
                labelCount = labels.second,
            )
        } finally {
            bitmap.decrementRefCount()
        }
    }

    private fun waitFor(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "waitFor(), $what in $timeoutMs ms"
            }
            Thread.sleep(10)
        }
    }

    /**
     * Hashes each drawn tile on its render thread, before the engine sees it, so the test thread
     * compares every render exactly once.
     *
     * @param hashing false skips the hash, for modes that are only checked to render
     */
    private class Recording(
        private val renderer: MapRenderer,
        val hashing: Boolean,
    ) : MapRenderer by renderer {

        val drawn = ConcurrentLinkedQueue<Drawn>()

        val drawnCount = AtomicInteger()

        val running = AtomicInteger()

        override fun render(tile: TileRequest, target: RenderTarget, isCancelled: () -> Boolean): RenderResult {
            running.incrementAndGet()
            try {
                val result = renderer.render(tile, target, isCancelled)
                if (result == RenderResult.DRAWN) {
                    drawnCount.incrementAndGet()
                    if (hashing) {
                        // the label entry right away, before another render can evict it
                        val labels = labelEntry(renderer, tile)
                        drawn += Drawn(
                            tile = tile,
                            pixels = pixelHash(checkNotNull((target as TileBitmapTarget).bitmap)),
                            labels = labels.first,
                            labelCount = labels.second,
                        )
                    }
                }
                return result
            } finally {
                running.decrementAndGet()
            }
        }
    }

    /**
     * A drawn tile's pixel hash, label store hash and label count; no label store gives 0 and 0.
     */
    private class Drawn(val tile: TileRequest, val pixels: Long, val labels: Long, val labelCount: Int)

    /**
     * Keeps nothing of a drawn tile, so the engine's cache costs no bitmaps; [Recording] has hashed
     * it already.
     */
    private object ReleasingImages : TileImages<Unit> {

        override fun newTarget(sizePx: Int): RenderTarget {
            return TileBitmapTarget(sizePx)
        }

        override fun take(target: RenderTarget) {
            checkNotNull((target as TileBitmapTarget).bitmap).decrementRefCount()
        }

        override fun release(image: Unit) {
            // nothing held
        }
    }

    private companion object {

        const val TAG = "SoakTest"

        const val THREADS = 4

        // a few screens, so a walk back renders tiles again
        const val KEPT_TILES = 60

        const val SCREEN_WIDTH = 1080

        const val SCREEN_HEIGHT = 2340

        const val MIN_ZOOM = 12

        const val MAX_ZOOM = 18

        const val SEED = 20261025

        const val MAX_RUN_MS = 24 * 60 * 60_000L

        const val TIMEOUT_MARGIN_MS = 5 * 60_000L

        /**
         * Half of `soak.minutes` in ms, for one label mode; null without the argument.
         */
        fun runMs(): Long? {
            val raw = InstrumentationRegistry.getArguments().getString("soak.minutes") ?: return null
            val minutes = requireNotNull(raw.toDoubleOrNull()) {
                "runMs(), soak.minutes=$raw, not a number"
            }
            val runMs = (minutes * 60_000 / 2).toLong()
            require(minutes.isFinite()
                && runMs in 1..MAX_RUN_MS
            ) {
                "runMs(), soak.minutes=$raw, out of range"
            }
            return runMs
        }

        /**
         * A hash of `tile`'s label store entry in store order, and its label count; 0 and 0
         * without a label store. Kind, point-label text, priority, position and clash box of each
         * label, standalone symbols by size.
         */
        fun labelEntry(renderer: MapRenderer, tile: TileRequest): Pair<Long, Int> {
            val store = (renderer as MapsforgeRenderer).labelStore ?: return Pair(0L, 0)
            // keyed as the renderer keys its jobs
            val mapsforgeTile = Tile(tile.x, tile.y, tile.zoom.toByte(), renderer.key.config.tileSizePx)
            val lines = store.getVisibleItems(mapsforgeTile, mapsforgeTile).map { describe(it) }
            var hash = 1L
            for (line in lines) {
                hash = 31 * hash + line.hashCode()
            }
            return Pair(31 * hash + lines.size, lines.size)
        }

        // mirrors the golden harness's LabelDump.describe, with raw doubles and symbols by size
        private fun describe(item: MapElementContainer): String {
            val clash = item.getClashRect(Rotation.NULL_ROTATION)
            val geometry = "p=${item.priority} xy=${item.point.x},${item.point.y} " +
                "clash=${clash?.let { "${it.left},${it.top},${it.right},${it.bottom}" }}"
            return when (item) {
                is PointTextContainer -> "text $geometry \"${item.text}\" withSymbol=${item.symbolContainer != null}"
                is SymbolContainer -> "symbol $geometry ${item.symbol.width}x${item.symbol.height}"
                // the way text itself is private; priority and clash box identify it
                is WayTextContainer -> "waytext $geometry"
                else -> "other ${item.javaClass.simpleName} $geometry"
            }
        }

        fun pixelHash(bitmap: TileBitmap): Long {
            val android = AndroidGraphicFactory.getBitmap(bitmap)!!
            val pixels = IntArray(android.width * android.height)
            android.getPixels(pixels, 0, android.width, 0, 0, android.width, android.height)
            // FNV-1a over the ARGB words
            var hash = -0x340d631b7bdddcdbL
            for (pixel in pixels) {
                hash = (hash xor pixel.toLong()) * 0x100000001b3L
            }
            return hash
        }
    }
}
