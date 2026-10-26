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
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import com.asamm.vectormaps.viewer.TileEngine
import com.asamm.vectormaps.viewer.TileImages
import com.asamm.vectormaps.viewer.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * The M5 gate (ROADMAP): a random walk of pans and zooms on a phone-sized screen, rendered through
 * the Mapsforge driver and the viewer's tile engine on [THREADS] threads for half of `soak.minutes`
 * per label mode. `SEPARATE` tiles are compared pixel by pixel with renders of the same tiles,
 * one at a time, by a second renderer; `IN_TILE` tiles depend on render order (M6.1), so they are
 * only checked to render. Skipped without the argument:
 * `./gradlew :dev:bench:android:connectedReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=com.asamm.vectormaps.bench.android.SoakTest
 * -Pandroid.testInstrumentationRunnerArguments.soak.minutes=15`.
 */
@RunWith(AndroidJUnit4::class)
class SoakTest {

    @Test
    fun separateLabelsMatchSerialRenders() {
        soak(labelMode = LabelMode.SEPARATE, compare = true)
    }

    @Test
    fun inTileLabelsRender() {
        soak(labelMode = LabelMode.IN_TILE, compare = false)
    }

    private fun soak(labelMode: LabelMode, compare: Boolean) {
        val raw = InstrumentationRegistry.getArguments().getString("soak.minutes")
        assumeTrue("soak.minutes not set", raw != null)
        val minutes = requireNotNull(raw?.toDoubleOrNull()) {
            "soak.minutes=$raw, not a number"
        }
        require(minutes > 0 && minutes.isFinite()) {
            "soak.minutes=$raw, not a positive number"
        }
        val runMs = (minutes * 60_000 / 2).toLong()
        // the files first: preparing them creates the graphic factory the driver takes
        val mapFile = BenchScene.mapFile()
        val themeFile = BenchScene.themeFile()
        val driver = MapsforgeDriver(AndroidGraphicFactory.INSTANCE)
        val dataset = driver.open(listOf(mapFile))
        val style = driver.loadStyle(themeFile)
        val config = RenderConfig(tileSizePx = BenchScene.TILE_SIZE, labelMode = labelMode)
        val reference = dataset.createRenderer(style = style, selection = StyleSelection.DEFAULT, config = config)
        val renderer = dataset.createRenderer(style = style, selection = StyleSelection.DEFAULT, config = config)
        val arrived = AtomicInteger()
        val engine = TileEngine(
            renderer = renderer,
            images = HashingImages,
            threads = THREADS,
            capacity = KEPT_TILES,
        ) {
            arrived.incrementAndGet()
        }
        val expected = HashMap<TileRequest, Long>()
        val checked = HashMap<TileRequest, Drawn>()
        val mismatches = LinkedHashSet<TileRequest>()
        val recent = ArrayDeque<List<TileRequest>>()
        var steps = 0
        var compared = 0
        // each render once, by identity, also those that arrive after their tile left the screen
        fun checkRecent() {
            for (tile in recent.flatten().toSet()) {
                val drawn = engine[tile] ?: continue
                if (checked.put(tile, drawn) === drawn) {
                    continue
                }
                compared++
                if (drawn.hash != expected.getOrPut(tile) { serialHash(reference, tile) }) {
                    mismatches += tile
                }
            }
        }
        try {
            val random = Random(SEED)
            val home = home(dataset)
            var viewport = home
            val deadline = System.currentTimeMillis() + runMs
            while (System.currentTimeMillis() < deadline) {
                viewport = step(viewport = viewport, home = home, bounds = dataset.bounds, random = random)
                val tiles = viewport.tiles(SCREEN_WIDTH, SCREEN_HEIGHT)
                if (compare) {
                    // before show(), which evicts the tiles that left the screen
                    checkRecent()
                    recent.addLast(tiles)
                    if (recent.size > RECENT_SCREENS) {
                        recent.removeFirst()
                    }
                }
                engine.show(tiles)
                steps++
                // most steps move on at once, as a fling does; some wait for the whole screen
                if (random.nextInt(4) == 0) {
                    waitFor(timeoutMs = 20_000) {
                        engine.error?.let { error(it) }
                        tiles.all { engine[it] != null }
                    }
                } else {
                    Thread.sleep(random.nextLong(5, 40))
                }
                engine.error?.let { error(it) }
            }
            if (compare) {
                checkRecent()
            }
            engine.error?.let { error(it) }
            val summary = "$labelMode: $steps steps, ${engine.stats.summary()}, $compared renders compared, " +
                "${mismatches.size} differed ${mismatches.take(10)}"
            Log.i(TAG, summary)
            report(name = "soak-$labelMode.txt", text = summary)
            assertEquals(summary, 0, mismatches.size)
            check(arrived.get() > 0) {
                "soak($labelMode), no tile arrived"
            }
        } finally {
            engine.close()
            reference.close()
            dataset.close()
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

    private fun serialHash(reference: MapRenderer, tile: TileRequest): Long {
        val target = TileBitmapTarget(BenchScene.TILE_SIZE)
        val result = reference.render(tile, target) { false }
        check(result == RenderResult.DRAWN) {
            "serialHash($tile), $result"
        }
        val bitmap = checkNotNull(target.bitmap)
        try {
            return pixelHash(bitmap)
        } finally {
            bitmap.decrementRefCount()
        }
    }

    private fun report(name: String, text: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(dir, name).writeText(text + "\n")
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "waitFor(), screen not drawn in $timeoutMs ms"
            }
            Thread.sleep(10)
        }
    }

    /**
     * One drawn tile: its pixel hash, and by identity the render it came from.
     */
    private class Drawn(val hash: Long)

    /**
     * Keeps a hash of each drawn tile instead of the tile, so the engine's cache costs no bitmaps.
     */
    private object HashingImages : TileImages<Drawn> {

        override fun newTarget(sizePx: Int): RenderTarget {
            return TileBitmapTarget(sizePx)
        }

        override fun take(target: RenderTarget): Drawn {
            val bitmap = checkNotNull((target as TileBitmapTarget).bitmap)
            try {
                return Drawn(pixelHash(bitmap))
            } finally {
                bitmap.decrementRefCount()
            }
        }

        override fun release(image: Drawn) {
            // nothing held
        }
    }

    private companion object {

        const val TAG = "SoakTest"

        const val THREADS = 4

        // a few screens, so a walk back renders tiles again
        const val KEPT_TILES = 60

        // screens whose tiles are still checked: a queued render can land several steps later
        const val RECENT_SCREENS = 10

        const val SCREEN_WIDTH = 1080

        const val SCREEN_HEIGHT = 2340

        const val MIN_ZOOM = 12

        const val MAX_ZOOM = 18

        const val SEED = 20261025

        fun pixelHash(bitmap: TileBitmap): Long {
            val android = AndroidGraphicFactory.getBitmap(bitmap)
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
