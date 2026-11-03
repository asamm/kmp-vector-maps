/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.driver.GarminDataset
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.driver.GarminRenderer
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import com.asamm.vectormaps.garminimg.render.AndroidBitmapCache
import com.asamm.vectormaps.garminimg.render.AndroidRenderTarget
import com.asamm.vectormaps.garminimg.render.ImgRenderer
import com.asamm.vectormaps.garminimg.render.Viewport
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
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
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Opening the Garmin map: the dataset reads and parses every map of the file.
 */
@RunWith(AndroidJUnit4::class)
class GarminOpenBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun openDataset() {
        val file = GarminScene.mapFile()
        val driver = GarminScene.newDriver()
        benchmarkRule.measureRepeated {
            val dataset = driver.open(listOf(file))
            runWithMeasurementDisabled {
                dataset.close()
            }
        }
    }
}

/**
 * Reading and rendering one Garmin tile per zoom through a long-lived renderer, as
 * `TileBenchmark` does for Mapsforge.
 */
@RunWith(Parameterized::class)
class GarminTileBenchmark(private val zoom: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: GarminScene
    private lateinit var tile: TileRequest

    @Before
    fun setUp() {
        scene = GarminScene()
        tile = GarminScene.centerTile(dataset = scene.dataset, zoom = zoom)
    }

    @After
    fun tearDown() {
        scene.close()
    }

    /**
     * Every object the render reads, each map at the level it picks; unlike the render, it also
     * picks the level of maps outside the tile.
     */
    @Test
    fun readTile() {
        val maps = ImgFile.openAll { FileImgRandomAccessReader(GarminScene.mapFile()) }
        try {
            val viewport = GarminScene.viewportOf(tile)
            benchmarkRule.measureRepeated {
                for (map in maps) {
                    val level = ImgRenderer.pickRenderLevelWithCoverage(viewport, map) ?: continue
                    map.readMapInBounds(
                        minLong = viewport.minLon,
                        maxLong = viewport.maxLon,
                        minLat = viewport.minLat,
                        maxLat = viewport.maxLat,
                        levelFilter = level,
                        poiDetails = false,
                    ).count()
                }
            }
        } finally {
            maps.first().close()
        }
    }

    @Test
    fun renderTile() {
        benchmarkRule.measureRepeated {
            val canvas = scene.render(tile)
            runWithMeasurementDisabled {
                canvas.bitmap.recycle()
            }
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "z{0}")
        fun zooms(): List<Int> {
            return listOf(12, 15, 17)
        }
    }
}

/**
 * Rendering a 4x4 Garmin block at z16 with a fixed thread count through one shared renderer, as
 * `ParallelBlockBenchmark` does for Mapsforge; time per block.
 */
@RunWith(Parameterized::class)
class GarminParallelBlockBenchmark(private val threads: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: GarminScene
    private lateinit var executor: ExecutorService
    private lateinit var block: List<TileRequest>

    @Before
    fun setUp() {
        scene = GarminScene()
        executor = Executors.newFixedThreadPool(threads)
        val center = GarminScene.centerTile(dataset = scene.dataset, zoom = BLOCK_ZOOM)
        block = (-2..1).flatMap { dy ->
            (-2..1).map { dx ->
                TileRequest(x = center.x + dx, y = center.y + dy, zoom = BLOCK_ZOOM)
            }
        }
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
        scene.close()
    }

    @Test
    fun renderBlock() {
        val tasks = block.map { tile ->
            Callable {
                scene.render(tile)
            }
        }
        benchmarkRule.measureRepeated {
            val canvases = executor.invokeAll(tasks).map { it.get() }
            runWithMeasurementDisabled {
                canvases.forEach { it.bitmap.recycle() }
            }
        }
    }

    companion object {

        private const val BLOCK_ZOOM = 16

        @JvmStatic
        @Parameterized.Parameters(name = "threads{0}")
        fun threadCounts(): List<Int> {
            return listOf(1, 2, 4, 8)
        }
    }
}

/**
 * The Garmin map and one long-lived renderer with the map's first style, set up like the JVM
 * `GarminBench` (512 px tiles, device scale 1) so the two baselines compare.
 */
class GarminScene : AutoCloseable {

    val dataset: GarminDataset = newDriver().open(listOf(mapFile()))

    private val renderer: GarminRenderer = dataset.createRenderer(
        style = dataset.styles.first(),
        selection = StyleSelection.DEFAULT,
        config = RenderConfig(tileSizePx = BenchScene.TILE_SIZE),
    )

    /**
     * Renders one tile; a tile outside the map must not be timed as a fast result.
     */
    fun render(tile: TileRequest): AndroidRenderTarget {
        val target = GarminTileTarget(BenchScene.TILE_SIZE)
        val result = renderer.render(tile = tile, target = target) { false }
        check(result == RenderResult.DRAWN) {
            "render($tile), $result"
        }
        return target.canvas as AndroidRenderTarget
    }

    override fun close() {
        renderer.close()
        dataset.close()
    }

    companion object {

        fun newDriver(): GarminDriver {
            // as the viewer: one cache of converted TYP bitmaps for all canvases
            val bitmaps = AndroidBitmapCache()
            return GarminDriver { size, antiAliased, scale ->
                AndroidRenderTarget(
                    widthPx = size,
                    heightPx = size,
                    antiAliased = antiAliased,
                    renderScale = scale,
                    bitmapCache = bitmaps,
                )
            }
        }

        /**
         * The `bench.garmin.mapFile` argument, else the bundled OpenTopoMap Monaco map.
         */
        fun mapFile(): File {
            val file = BenchScene.argument("bench.garmin.mapFile")?.let(::File)
                ?: File(BenchScene.fixturesDir(), "maps/otm-monaco/otm-monaco.img")
            require(file.canRead()) {
                "mapFile(), cannot read $file; shared storage needs `adb shell appops set " +
                    "com.asamm.vectormaps.bench.android.test MANAGE_EXTERNAL_STORAGE allow`"
            }
            return file
        }

        /**
         * The tile at the `bench.garmin.center` argument if given, else at the centre of the map's
         * bounds.
         */
        fun centerTile(dataset: GarminDataset, zoom: Int): TileRequest {
            val bounds = dataset.bounds
            val (lat, lon) = BenchScene.argument("bench.garmin.center")
                ?.split(",")
                ?.let { it[0].trim().toDouble() to it[1].trim().toDouble() }
                ?: ((bounds.minLat + bounds.maxLat) / 2 to (bounds.minLon + bounds.maxLon) / 2)
            check(lat in bounds.minLat..bounds.maxLat
                && lon in bounds.minLon..bounds.maxLon
            ) {
                "centerTile($lat, $lon), outside the map $bounds"
            }
            val n = (1 shl zoom).toDouble()
            val latRad = lat * PI / 180.0
            return TileRequest(
                x = floor((lon + 180.0) / 360.0 * n).toInt(),
                y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt(),
                zoom = zoom,
            )
        }

        // as `GarminRenderer` projects a tile
        fun viewportOf(tile: TileRequest): Viewport {
            val n = (1 shl tile.zoom).toDouble()
            return Viewport.ofDegrees(
                widthPx = BenchScene.TILE_SIZE,
                heightPx = BenchScene.TILE_SIZE,
                minLonDeg = tile.x / n * 360.0 - 180.0,
                maxLonDeg = (tile.x + 1) / n * 360.0 - 180.0,
                minLatDeg = latitudeOf(tile.y + 1, n),
                maxLatDeg = latitudeOf(tile.y, n),
            )
        }

        private fun latitudeOf(y: Int, n: Double): Double {
            return atan(sinh(PI * (1.0 - 2.0 * y / n))) * 180.0 / PI
        }
    }
}
