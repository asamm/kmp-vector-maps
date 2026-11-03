/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.driver.GarminDataset
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.driver.GarminRenderer
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import com.asamm.vectormaps.garminimg.render.AwtBitmapCache
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget
import com.asamm.vectormaps.garminimg.render.ImgRenderer
import com.asamm.vectormaps.garminimg.render.Viewport
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
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
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Baseline of the Garmin driver per stage with the map's own TYP and 512 px tiles (ROADMAP
 * M4.3c), set up like [MapsforgeBench] so the two compare.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class GarminBench {

    /**
     * Tile zoom for the read and render benchmarks.
     */
    @Param("12", "15", "17")
    var zoom = 0

    private lateinit var scene: Scene
    private lateinit var tile: TileRequest
    private lateinit var maps: List<ImgFile>

    @Setup(Level.Trial)
    fun setUp() {
        scene = Scene()
        tile = centerTile(dataset = scene.dataset, zoom = zoom)
        maps = ImgFile.openAll { FileImgRandomAccessReader(mapFile()) }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        maps.first().close()
        scene.close()
    }

    /**
     * Every object the render reads, each map at the level it picks; unlike the render, it also
     * picks the level of maps outside the tile.
     */
    @Benchmark
    fun readTile(blackhole: Blackhole) {
        val viewport = viewportOf(tile)
        for (map in maps) {
            val level = ImgRenderer.pickRenderLevelWithCoverage(viewport, map) ?: continue
            for (mapObject in map.readMapInBounds(
                minLong = viewport.minLon,
                maxLong = viewport.maxLon,
                minLat = viewport.minLat,
                maxLat = viewport.maxLat,
                levelFilter = level,
                poiDetails = false,
            )) {
                blackhole.consume(mapObject)
            }
        }
    }

    /**
     * Read plus render of one tile through a long-lived renderer, as Locus renders.
     */
    @Benchmark
    fun renderTile(blackhole: Blackhole) {
        blackhole.consume(scene.render(tile))
    }

    /**
     * Opening the map: the dataset reads and parses every map of the file. Prints the heap the
     * dataset keeps.
     */
    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    open class Open {

        @Setup(Level.Trial)
        fun printRetainedHeap() {
            val before = usedHeap()
            val dataset = newDriver().open(listOf(mapFile()))
            val after = usedHeap()
            println("[bench] retained by the dataset: %.1f MB".format((after - before) / 1e6))
            dataset.close()
        }

        @Benchmark
        fun openDataset(blackhole: Blackhole) {
            newDriver().open(listOf(mapFile())).use { dataset ->
                blackhole.consume(dataset)
            }
        }

        private fun usedHeap(): Long {
            val memory = ManagementFactory.getMemoryMXBean()
            repeat(3) {
                System.gc()
            }
            return memory.heapMemoryUsage.used
        }
    }

    /**
     * Rendering a 4x4 block with a fixed thread count through one shared renderer, as Locus' tile
     * workers do; time per block, so throughput is 16 tiles divided by it.
     */
    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    open class ParallelBlock {

        @Param("1", "2", "4", "8")
        var threads = 0

        private lateinit var scene: Scene
        private lateinit var executor: ExecutorService
        private lateinit var block: List<TileRequest>

        @Setup(Level.Trial)
        fun setUp() {
            scene = Scene()
            executor = Executors.newFixedThreadPool(threads)
            val center = centerTile(dataset = scene.dataset, zoom = BLOCK_ZOOM)
            block = (-2..1).flatMap { dy ->
                (-2..1).map { dx ->
                    TileRequest(x = center.x + dx, y = center.y + dy, zoom = BLOCK_ZOOM)
                }
            }
        }

        @TearDown(Level.Trial)
        fun tearDown() {
            executor.shutdownNow()
            scene.close()
        }

        @Benchmark
        fun renderBlock(blackhole: Blackhole) {
            val tasks = block.map { tile ->
                Callable {
                    scene.render(tile)
                }
            }
            for (future in executor.invokeAll(tasks)) {
                blackhole.consume(future.get())
            }
        }
    }

    /**
     * The map and one long-lived renderer with the map's first style, its TYP when it has one.
     */
    private class Scene : AutoCloseable {

        val dataset: GarminDataset = newDriver().open(listOf(mapFile()))

        private val renderer: GarminRenderer = dataset.createRenderer(
            style = dataset.styles.first(),
            selection = StyleSelection.DEFAULT,
            config = RenderConfig(tileSizePx = TILE_SIZE),
        )

        /**
         * Renders one tile; a tile outside the map must not be timed as a fast result.
         */
        fun render(tile: TileRequest): Any {
            val target = GarminTileTarget(TILE_SIZE)
            val result = renderer.render(tile = tile, target = target) { false }
            check(result == RenderResult.DRAWN) {
                "render($tile), $result"
            }
            return checkNotNull(target.canvas)
        }

        override fun close() {
            renderer.close()
            dataset.close()
        }
    }

    private companion object {

        const val TILE_SIZE = 512
        const val BLOCK_ZOOM = 16

        fun newDriver(): GarminDriver {
            // as the viewer: one cache of converted TYP bitmaps for all canvases
            val bitmaps = AwtBitmapCache()
            return GarminDriver { size, antiAliased, scale ->
                AwtRenderTarget(
                    widthPx = size,
                    heightPx = size,
                    antiAliased = antiAliased,
                    renderScale = scale,
                    bitmapCache = bitmaps,
                )
            }
        }

        fun mapFile(): File {
            return File(requiredProperty("bench.garmin.mapFile"))
        }

        /**
         * The tile at `bench.garmin.center` if set, else at the centre of the map's bounds.
         */
        fun centerTile(dataset: GarminDataset, zoom: Int): TileRequest {
            val bounds = dataset.bounds
            val (lat, lon) = System.getProperty("bench.garmin.center").orEmpty()
                .takeIf { it.isNotBlank() }
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
                widthPx = TILE_SIZE,
                heightPx = TILE_SIZE,
                minLonDeg = tile.x / n * 360.0 - 180.0,
                maxLonDeg = (tile.x + 1) / n * 360.0 - 180.0,
                minLatDeg = latitudeOf(tile.y + 1, n),
                maxLatDeg = latitudeOf(tile.y, n),
            )
        }

        fun latitudeOf(y: Int, n: Double): Double {
            return atan(sinh(PI * (1.0 - 2.0 * y / n))) * 180.0 / PI
        }

        fun requiredProperty(name: String): String {
            return requireNotNull(System.getProperty(name)) {
                "requiredProperty($name), not set; run through Gradle: ./gradlew :dev:bench:jmh"
            }
        }
    }
}
