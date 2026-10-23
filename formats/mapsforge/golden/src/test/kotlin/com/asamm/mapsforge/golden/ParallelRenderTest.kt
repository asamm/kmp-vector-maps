/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Renders a 3x3 block in Locus' primary label mode on several threads sharing one long-lived
 * renderer, as Locus does, and requires the same pixels and stored labels as a serial render.
 *
 * In that mode a tile's labels depend only on the tile itself, so any difference comes from render
 * order or from shared state that parallel renders corrupt.
 */
class ParallelRenderTest {

    @Test
    fun parallelRenderMatchesSerial() {
        val reference = BlockRenderer().use { it.render(threads = 1) }
        val mismatches = mutableListOf<String>()
        // one renderer for all rounds: cold caches in the first round, warm ones after
        BlockRenderer().use { renderer ->
            for (round in 1..ROUNDS) {
                val parallel = renderer.render(threads = THREADS)
                for (tile in tiles) {
                    mismatches += compare(
                        round = round,
                        tile = tile,
                        expected = reference.getValue(tile),
                        actual = parallel.getValue(tile),
                    )
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            fail(
                "${mismatches.size} differences from the serial render, diffs in $diffDir\n" +
                    mismatches.joinToString("\n"),
            )
        }
        // guard against a block without labels passing trivially
        assertTrue(reference.values.sumOf { it.labels.size } > 0, "the block holds no labels")
    }

    private fun compare(round: Int, tile: Tile, expected: TileResult, actual: TileResult): List<String> {
        val result = mutableListOf<String>()
        val diffPixels = expected.pixels.indices.count { expected.pixels[it] != actual.pixels[it] }
        if (diffPixels > 0) {
            result += "round $round, $tile: $diffPixels px"
            writeDiff(name = "r${round}_z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}", expected = expected, actual = actual)
        }
        if (expected.labels != actual.labels) {
            result += "round $round, $tile: labels differ"
        }
        return result
    }

    /**
     * Writes the serial and the parallel render of one tile, plus a diff with differing pixels red.
     */
    private fun writeDiff(name: String, expected: TileResult, actual: TileResult) {
        diffDir.mkdirs()
        val diff = IntArray(expected.pixels.size) { i ->
            if (expected.pixels[i] == actual.pixels[i]) {
                (expected.pixels[i] and 0x00FFFFFF) or 0x40000000
            } else {
                0xFFFF0000.toInt()
            }
        }
        for ((suffix, pixels) in listOf("serial" to expected.pixels, "parallel" to actual.pixels, "diff" to diff)) {
            val image = BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, TILE_SIZE, TILE_SIZE, pixels, 0, TILE_SIZE)
            ImageIO.write(image, "png", File(diffDir, "${name}_$suffix.png"))
        }
    }

    /**
     * One renderer in Locus' primary mode over the fixture map and theme; `render` may run many
     * times on it.
     */
    private class BlockRenderer : AutoCloseable {

        private val graphicFactory = AwtGraphicFactory.INSTANCE
        private val displayModel = DisplayModel().apply {
            setFixedTileSize(TILE_SIZE)
        }
        private val mapDataStore = MapFile(File(TestDirs.fixtures, "maps/monaco.osm.map"))
        private val themeFuture = RenderThemeFuture(
            graphicFactory,
            ExternalRenderTheme(File(TestDirs.fixtures, "themes/lomaps/theme.xml")),
            displayModel,
        )
        private val labelStore = TileBasedLabelStore(100)
        private val renderer: DatabaseRenderer

        init {
            themeFuture.run()
            themeFuture.get()
            renderer = DatabaseRenderer(mapDataStore, graphicFactory, null, labelStore, false, true, null)
        }

        /**
         * Renders every tile once; with more than one thread, all jobs start together to maximise
         * overlap.
         */
        fun render(threads: Int): Map<Tile, TileResult> {
            val executor = Executors.newFixedThreadPool(threads)
            try {
                val start = CountDownLatch(1)
                val futures = tiles.associateWith { tile ->
                    executor.submit<IntArray> {
                        start.await()
                        val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, 1.0f, false, false)
                        val bitmap = requireNotNull(renderer.executeJob(job)) {
                            "render($threads), no bitmap for $tile"
                        }
                        val image = AwtGraphicFactory.getBitmap(bitmap)
                        image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                    }
                }
                start.countDown()
                return futures.mapValues { (tile, future) ->
                    TileResult(
                        pixels = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        labels = LabelDump.lines(store = labelStore, tile = tile),
                    )
                }
            } finally {
                executor.shutdownNow()
            }
        }

        override fun close() {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }

    /**
     * What one tile render produced: its pixels and the labels stored for it.
     */
    private class TileResult(val pixels: IntArray, val labels: List<String>)

    private companion object {

        const val TILE_SIZE = 512
        const val THREADS = 4
        const val ROUNDS = 5
        const val TIMEOUT_SECONDS = 60L

        // Monaco harbour at z16, dense in labels
        const val CENTER_LAT = 43.7350
        const val CENTER_LON = 7.4230
        const val ZOOM: Byte = 16

        val diffDir: File by lazy {
            File(TestDirs.diff, "parallel")
        }

        val tiles: List<Tile> by lazy {
            val cx = MercatorProjection.longitudeToTileX(CENTER_LON, ZOOM)
            val cy = MercatorProjection.latitudeToTileY(CENTER_LAT, ZOOM)
            (-1..1).flatMap { dy ->
                (-1..1).map { dx ->
                    Tile(cx + dx, cy + dy, ZOOM, TILE_SIZE)
                }
            }
        }
    }
}
