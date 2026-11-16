/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mvt.driver.MvtDataset
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtRenderer
import com.asamm.vectormaps.mvt.driver.MvtStyle
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AndroidMvtGraphics
import com.asamm.vectormaps.mvt.render.AndroidMvtRenderTarget
import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.source.PmTilesSource
import com.asamm.vectormaps.mvt.tile.VectorTile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Decoding the data tile at the centre, as the renderer does once per data tile; as the JVM
 * `MvtBench`.
 */
@RunWith(Parameterized::class)
class MvtDecodeBenchmark(private val zoom: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun decodeTile() {
        val tile = MvtScene.tileAt(zoom)
        val bytes = PmTilesSource(MvtScene.mapFile()).use { source ->
            checkNotNull(source.tile(tile.zoom, tile.x, tile.y)) {
                "decodeTile(), no data tile $tile"
            }
        }
        benchmarkRule.measureRepeated {
            VectorTile.decode(MvtTileSource.uncompressed(bytes))
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "z{0}")
        fun zooms(): List<Int> {
            return listOf(10, 12, 14)
        }
    }
}

/**
 * Rendering one MVT tile with its symbol layout at and above the data zoom: again through a
 * long-lived renderer (its data tile kept decoded only above the data zoom), and cold through a
 * fresh renderer per call, as a tile first seen; as the JVM `MvtBench`.
 */
@RunWith(Parameterized::class)
class MvtTileBenchmark(private val zoom: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: MvtScene
    private lateinit var tile: TileRequest

    @Before
    fun setUp() {
        scene = MvtScene()
        tile = MvtScene.tileAt(zoom)
    }

    @After
    fun tearDown() {
        scene.close()
    }

    @Test
    fun renderTileAgain() {
        benchmarkRule.measureRepeated {
            val canvas = scene.render(tile)
            runWithMeasurementDisabled {
                canvas.bitmap.recycle()
            }
        }
    }

    @Test
    fun renderTileCold() {
        benchmarkRule.measureRepeated {
            val renderer = runWithMeasurementDisabled {
                scene.newRenderer()
            }
            val canvas = scene.render(tile = tile, renderer = renderer)
            runWithMeasurementDisabled {
                canvas.bitmap.recycle()
                renderer.close()
            }
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "z{0}")
        fun zooms(): List<Int> {
            return listOf(10, 12, 14, 16)
        }
    }
}

/**
 * Rendering the 16 z16 tiles of one z14 data tile with a fixed thread count through one shared
 * renderer, a fresh one per block, so the data tile is read and decoded each time; time per
 * block, as the JVM `MvtBench.ParallelBlock`.
 */
@RunWith(Parameterized::class)
class MvtParallelBlockBenchmark(private val threads: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: MvtScene
    private lateinit var executor: ExecutorService
    private val block = (0..3).flatMap { dy ->
        (0..3).map { dx ->
            val data = MvtScene.tileAt(MvtScene.DATA_ZOOM)
            TileRequest(
                x = (data.x shl 2) + dx,
                y = (data.y shl 2) + dy,
                zoom = MvtScene.DATA_ZOOM + 2,
            )
        }
    }

    @Before
    fun setUp() {
        scene = MvtScene()
        executor = Executors.newFixedThreadPool(threads)
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
        scene.close()
    }

    @Test
    fun renderBlock() {
        benchmarkRule.measureRepeated {
            val renderer = runWithMeasurementDisabled {
                scene.newRenderer()
            }
            val tasks = block.map { tile ->
                Callable {
                    scene.render(tile = tile, renderer = renderer)
                }
            }
            val canvases = executor.invokeAll(tasks).map { it.get() }
            runWithMeasurementDisabled {
                canvases.forEach { it.bitmap.recycle() }
                renderer.close()
            }
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "threads{0}")
        fun threadCounts(): List<Int> {
            return listOf(1, 2, 4, 8)
        }
    }
}

/**
 * The `bench.mvt.mapFile` tiles, else the MVT fixture tiles (`dev/fixtures/mvt`, PMTiles), with
 * the `hike_bike` style and one
 * long-lived renderer, set up like the JVM `MvtBench` (512 px tiles, device scale 1) so the two
 * baselines compare; text in the system fonts, as the JVM bench draws the bundled DejaVu Sans.
 */
class MvtScene : AutoCloseable {

    private val driver = MvtDriver(AndroidMvtGraphics())

    private val dataset: MvtDataset = driver.open(listOf(mapFile()))

    private val style: MvtStyle = driver.loadStyle(
        File(BenchScene.fixturesDir(), "mvt/styles/hike_bike/style.json"),
    )

    private val renderer = lazy { newRenderer() }

    fun newRenderer(): MvtRenderer {
        return dataset.createRenderer(
            style = style,
            selection = StyleSelection.DEFAULT,
            config = RenderConfig(tileSizePx = BenchScene.TILE_SIZE),
        )
    }

    /**
     * Renders one tile; a tile without data must not be timed as a fast result.
     */
    fun render(
        tile: TileRequest,
        renderer: MvtRenderer = this.renderer.value,
    ): AndroidMvtRenderTarget {
        val target = MvtTileTarget(BenchScene.TILE_SIZE)
        val result = renderer.render(tile = tile, target = target) { false }
        check(result == RenderResult.DRAWN) {
            "render($tile), $result"
        }
        return target.canvas as AndroidMvtRenderTarget
    }

    override fun close() {
        if (renderer.isInitialized()) {
            renderer.value.close()
        }
        dataset.close()
    }

    companion object {

        // the fixture's z14 tile of Sněžka; its z10 and z12 ancestors are fixtures too
        const val DATA_ZOOM = 14
        const val DATA_X = 8908
        const val DATA_Y = 5504

        /**
         * The `bench.mvt.mapFile` argument, else the bundled fixture tiles.
         */
        fun mapFile(): File {
            val file = BenchScene.argument("bench.mvt.mapFile")?.let(::File)
                ?: File(BenchScene.fixturesDir(), "mvt/tiles/fixtures.pmtiles")
            require(file.canRead()) {
                "mapFile(), cannot read $file; shared storage needs `adb shell appops set " +
                    "com.asamm.vectormaps.bench.android.test MANAGE_EXTERNAL_STORAGE allow`"
            }
            return file
        }

        /**
         * The tile at `zoom` holding the `bench.mvt.center` argument (`<lat>:<lon>`), else the
         * fixture's tile of Sněžka, or its child at the top-left corner above the data zoom.
         */
        fun tileAt(zoom: Int): TileRequest {
            val center = BenchScene.argument("bench.mvt.center")
                ?.split(BenchScene.CENTER_SEPARATOR)
                ?.let { it[0].trim().toDouble() to it[1].trim().toDouble() }
            if (center != null) {
                val (west, south, east, north) = PmTilesSource(mapFile()).use { it.bounds }
                check(center.first in south..north
                    && center.second in west..east
                ) {
                    "tileAt($center), outside the map ${listOf(west, south, east, north)}"
                }
                return BenchScene.tileOf(lat = center.first, lon = center.second, zoom = zoom)
            }
            val dz = zoom - DATA_ZOOM
            return if (dz > 0) {
                TileRequest(x = DATA_X shl dz, y = DATA_Y shl dz, zoom = zoom)
            } else {
                TileRequest(x = DATA_X shr -dz, y = DATA_Y shr -dz, zoom = zoom)
            }
        }
    }
}
