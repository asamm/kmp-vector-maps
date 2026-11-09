/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
import com.asamm.vectormaps.mapsforge.map.layer.renderer.DatabaseRenderer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.reader.MapFile
import com.asamm.vectormaps.mapsforge.map.rendertheme.ExternalRenderTheme
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture
import org.junit.jupiter.api.io.TempDir
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
 * Pattern-fills every closed way and renders a block on many threads through one renderer: each
 * tile shifts the shared fill Paint's shader to its own origin, so a draw that runs without the
 * Paint lock takes another tile's shift (P7). Requires the same pixels as a serial render.
 */
class SharedPaintRenderTest {

    @TempDir
    lateinit var themeDir: File

    @Test
    fun parallelPatternFillsMatchSerial() {
        val theme = writeTheme()
        val reference = render(theme = theme, threads = 1)
        assertTrue(reference.values.any { pixels -> pixels.any { it != pixels[0] } }, "the block is blank")

        val mismatches = mutableListOf<String>()
        for (round in 1..ROUNDS) {
            val parallel = render(theme = theme, threads = THREADS)
            for ((tile, pixels) in parallel) {
                val expected = reference.getValue(tile)
                val diffPixels = pixels.indices.count { pixels[it] != expected[it] }
                if (diffPixels > 0) {
                    mismatches += "round $round, $tile: $diffPixels px"
                    writeDiff(name = "r${round}_z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}", expected = expected, actual = pixels)
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} tiles differ from the serial render, diffs in $diffDir\n" + mismatches.joinToString("\n"))
        }
    }

    /**
     * Writes the serial and the parallel render of one tile, plus a diff with differing pixels red.
     */
    private fun writeDiff(name: String, expected: IntArray, actual: IntArray) {
        diffDir.mkdirs()
        val diff = IntArray(expected.size) { i ->
            if (expected[i] == actual[i]) {
                (expected[i] and 0x00FFFFFF) or 0x40000000
            } else {
                0xFFFF0000.toInt()
            }
        }
        for ((suffix, pixels) in listOf("serial" to expected, "parallel" to actual, "diff" to diff)) {
            val image = BufferedImage(Corpus.TILE_SIZE, Corpus.TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, Corpus.TILE_SIZE, Corpus.TILE_SIZE, pixels, 0, Corpus.TILE_SIZE)
            ImageIO.write(image, "png", File(diffDir, "${name}_$suffix.png"))
        }
    }

    /**
     * A theme filling every closed way with the LoMaps beach pattern only, so a pattern that fails to
     * load leaves the block blank.
     */
    private fun writeTheme(): File {
        File(TestDirs.fixtures, "themes/lomaps/patterns/beach.png").copyTo(File(themeDir, "beach.png"))
        val theme = File(themeDir, "patterns.xml")
        theme.writeText(
            """
                <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5" map-background="#ffffff">
                  <rule e="way" k="*" v="*" closed="yes">
                    <area src="file:beach.png"/>
                  </rule>
                </rendertheme>
            """.trimIndent(),
        )
        return theme
    }

    /**
     * Renders every tile of the block once through one renderer; with more than one thread, all
     * jobs start together to maximise overlap.
     */
    private fun render(theme: File, threads: Int): Map<Tile, IntArray> {
        val graphicFactory = AwtGraphicFactory.INSTANCE
        val displayModel = DisplayModel().apply {
            setFixedTileSize(Corpus.TILE_SIZE)
            numberOfThreads = THREADS
        }
        val mapDataStore = MapFile(Corpus.mapFile(TestDirs.fixtures))
        val themeFuture = RenderThemeFuture(graphicFactory, ExternalRenderTheme(theme), displayModel)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            themeFuture.run()
            themeFuture.get()
            val renderer = DatabaseRenderer(mapDataStore, graphicFactory, null, TileBasedLabelStore(100), false, true, null)
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
            return futures.mapValues { (_, future) -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }

    private companion object {

        const val THREADS = 8
        const val ROUNDS = 5
        const val TIMEOUT_SECONDS = 60L

        // Monaco harbour at z16, dense in buildings
        const val ZOOM: Byte = 16

        val diffDir: File by lazy {
            File(TestDirs.diff, "shared-paint")
        }

        val tiles: List<Tile> by lazy {
            val cx = MercatorProjection.longitudeToTileX(Corpus.HARBOUR_LON, ZOOM)
            val cy = MercatorProjection.latitudeToTileY(Corpus.HARBOUR_LAT, ZOOM)
            (-2..1).flatMap { dy ->
                (-2..1).map { dx ->
                    Tile(cx + dx, cy + dy, ZOOM, Corpus.TILE_SIZE)
                }
            }
        }
    }
}
