/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtRenderer
import com.asamm.vectormaps.mvt.driver.MvtStyle
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AwtFonts
import com.asamm.vectormaps.mvt.render.AwtMvtGraphics
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.source.PmTilesSource
import com.asamm.vectormaps.mvt.tile.VectorTile
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole
import java.awt.Font
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * The MVT driver on the `bench.mvt.mapFile` tiles, else the Asamm fixture tiles of Sněžka
 * (`dev/fixtures/mvt`, PMTiles), with the `hike_bike` style and 512 px tiles: decoding a data tile, rendering a tile at and above the data
 * zoom again (with its symbol layout), and a 4x4 block of z16 tiles cut from one z14 tile.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class MvtBench {

    /**
     * Tile zoom; 16 is cut from the z14 data tile.
     */
    @Param("10", "12", "14", "16")
    var zoom = 0

    private lateinit var scene: Scene
    private lateinit var tile: TileRequest

    @Setup(Level.Trial)
    fun setUp() {
        scene = Scene()
        tile = tileAt(zoom)
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        scene.close()
    }

    /**
     * Rendering one tile again and laying out its symbols through a long-lived renderer: above
     * the data zoom its data tile stays decoded after the first call, at or below it each call
     * reads and decodes it anew.
     */
    @Benchmark
    fun renderTile(blackhole: Blackhole) {
        blackhole.consume(scene.render(tile))
    }

    /**
     * Decoding the data tile at the centre, as the renderer does once per data tile.
     */
    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    open class Decode {

        @Param("10", "12", "14")
        var zoom = 0

        private lateinit var data: ByteArray

        @Setup(Level.Trial)
        fun setUp() {
            val tile = tileAt(zoom)
            data = PmTilesSource(mapFile()).use { source ->
                checkNotNull(source.tile(tile.zoom, tile.x, tile.y)) {
                    "setUp(), no data tile $tile"
                }
            }
        }

        @Benchmark
        fun decodeTile(blackhole: Blackhole) {
            blackhole.consume(VectorTile.decode(MvtTileSource.uncompressed(data)))
        }
    }

    /**
     * Rendering the 16 z16 tiles of one z14 data tile with a fixed thread count through one
     * shared renderer, a fresh one per block, so the data tile is read and decoded each time.
     */
    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    open class ParallelBlock {

        @Param("1", "4", "8")
        var threads = 0

        private lateinit var scene: Scene
        private lateinit var executor: ExecutorService
        private val block = (0..3).flatMap { dy ->
            (0..3).map { dx ->
                val data = tileAt(DATA_ZOOM)
                TileRequest(x = (data.x shl 2) + dx, y = (data.y shl 2) + dy, zoom = DATA_ZOOM + 2)
            }
        }

        @Setup(Level.Trial)
        fun setUp() {
            scene = Scene()
            executor = Executors.newFixedThreadPool(threads)
        }

        @TearDown(Level.Trial)
        fun tearDown() {
            executor.shutdownNow()
            scene.close()
        }

        @Benchmark
        fun renderBlock(blackhole: Blackhole) {
            scene.newRenderer().use { renderer ->
                val tasks = block.map { tile ->
                    Callable {
                        scene.render(tile = tile, renderer = renderer)
                    }
                }
                for (future in executor.invokeAll(tasks)) {
                    blackhole.consume(future.get())
                }
            }
        }
    }

    /**
     * The dataset, its style and one long-lived renderer.
     */
    private class Scene : AutoCloseable {

        // the golden's bundled font, so text measures the same on every system
        private val font = Font.createFont(
            Font.TRUETYPE_FONT,
            File(fixturesDir(), "fonts/dejavu/DejaVuSans.ttf"),
        )

        private val driver = MvtDriver(AwtMvtGraphics(AwtFonts(font)))

        private val dataset = driver.open(listOf(mapFile()))

        private val style: MvtStyle = driver.loadStyle(
            File(fixturesDir(), "mvt/styles/hike_bike/style.json"),
        )

        private val renderer = lazy { newRenderer() }

        fun newRenderer(): MvtRenderer {
            return dataset.createRenderer(
                style = style,
                selection = StyleSelection.DEFAULT,
                config = RenderConfig(tileSizePx = TILE_SIZE),
            )
        }

        /**
         * Renders one tile; a tile without data must not be timed as a fast result.
         */
        fun render(tile: TileRequest, renderer: MvtRenderer = this.renderer.value): Any {
            val target = MvtTileTarget(TILE_SIZE)
            val result = renderer.render(tile = tile, target = target) { false }
            check(result == RenderResult.DRAWN) {
                "render($tile), $result"
            }
            val canvas = target.canvas as AwtMvtRenderTarget
            canvas.dispose()
            return canvas.image
        }

        override fun close() {
            if (renderer.isInitialized()) {
                renderer.value.close()
            }
            dataset.close()
        }
    }

    private companion object {

        const val TILE_SIZE = 512

        // the fixture's z14 tile of Sněžka; its z10 and z12 ancestors are fixtures too
        const val DATA_ZOOM = 14
        const val DATA_X = 8908
        const val DATA_Y = 5504

        /**
         * The `bench.mvt.mapFile` property, else the fixture tiles.
         */
        fun mapFile(): File {
            return System.getProperty("bench.mvt.mapFile").orEmpty()
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: File(fixturesDir(), "mvt/tiles/fixtures.pmtiles")
        }

        /**
         * The tile at `zoom` holding the `bench.mvt.center` property (`<lat>,<lon>`), else the
         * fixture's tile of Sněžka, or its top-left child above the data zoom.
         */
        fun tileAt(zoom: Int): TileRequest {
            val center = System.getProperty("bench.mvt.center").orEmpty().takeIf { it.isNotBlank() }
                ?.split(Regex("[,:]"))
                ?.let { it[0].trim().toDouble() to it[1].trim().toDouble() }
            if (center != null) {
                val n = (1 shl zoom).toDouble()
                val lat = Math.toRadians(center.first)
                return TileRequest(
                    x = floor((center.second + 180.0) / 360.0 * n).toInt(),
                    y = floor((1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * n).toInt(),
                    zoom = zoom,
                )
            }
            val dz = zoom - DATA_ZOOM
            return if (dz > 0) {
                TileRequest(x = DATA_X shl dz, y = DATA_Y shl dz, zoom = zoom)
            } else {
                TileRequest(x = DATA_X shr -dz, y = DATA_Y shr -dz, zoom = zoom)
            }
        }

        fun fixturesDir(): File {
            return File(
                requireNotNull(System.getProperty("bench.fixturesDir")) {
                    "fixturesDir(), not set; run through Gradle: ./gradlew :dev:bench:jmh"
                },
            )
        }
    }
}
