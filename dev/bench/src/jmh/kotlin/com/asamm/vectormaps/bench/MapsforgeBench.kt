/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.LatLong
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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Baseline of the vendored Mapsforge renderer per stage, in Locus' primary label mode with 512 px
 * tiles (ROADMAP M2.7); M5 must not regress it and M7 improves on it.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class MapsforgeBench {

    /**
     * Tile zoom for the read and render benchmarks.
     */
    @Param("12", "15", "17")
    var zoom = 0

    private lateinit var scene: Scene
    private lateinit var tile: Tile

    @Setup(Level.Trial)
    fun setUp() {
        scene = Scene()
        tile = centerTile(store = scene.store, zoom = zoom.toByte())
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        scene.close()
    }

    @Benchmark
    fun readTile(blackhole: Blackhole) {
        blackhole.consume(scene.store.readMapData(tile))
    }

    /**
     * Read plus render of one tile through a long-lived renderer, as Locus renders.
     */
    @Benchmark
    fun renderTile(blackhole: Blackhole) {
        val bitmap = scene.render(tile)
        blackhole.consume(bitmap)
        bitmap.decrementRefCount()
    }

    /**
     * Parsing the LoMaps day theme; symbols are rasterised lazily on first render, not here.
     */
    @State(Scope.Benchmark)
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    open class ThemeParse {

        private val displayModel = newDisplayModel()

        @Benchmark
        fun themeParse(blackhole: Blackhole) {
            val future = parseTheme(displayModel = displayModel)
            blackhole.consume(future.get())
            future.decrementRefCount()
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
        private lateinit var block: List<Tile>

        @Setup(Level.Trial)
        fun setUp() {
            scene = Scene()
            executor = Executors.newFixedThreadPool(threads)
            val center = centerTile(store = scene.store, zoom = BLOCK_ZOOM)
            block = (-2..1).flatMap { dy ->
                (-2..1).map { dx ->
                    Tile(center.tileX + dx, center.tileY + dy, BLOCK_ZOOM, TILE_SIZE)
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
                val bitmap = future.get()
                blackhole.consume(bitmap)
                bitmap.decrementRefCount()
            }
        }
    }

    /**
     * Map, theme and one long-lived renderer in Locus' primary label mode.
     */
    private class Scene : AutoCloseable {

        val store = MapFile(File(requiredProperty("bench.mapFile")))
        private val displayModel = newDisplayModel()
        private val themeFuture = parseTheme(displayModel = displayModel)

        // no tile cache, a label store, renderLabels false, cacheLabels true, no hillshading
        private val renderer = DatabaseRenderer(
            store,
            AwtGraphicFactory.INSTANCE,
            null,
            TileBasedLabelStore(100),
            false,
            true,
            null,
        )

        /**
         * Renders one tile; Mapsforge only logs a failed render and returns null, which must not
         * be timed as a fast result.
         */
        fun render(tile: Tile): TileBitmap {
            // text scale 1, opaque, not labels only
            val job = RendererJob(tile, store, themeFuture, displayModel, 1.0f, false, false)
            return checkNotNull(renderer.executeJob(job)) {
                "render($tile), failed"
            }
        }

        override fun close() {
            themeFuture.decrementRefCount()
            store.close()
        }
    }

    private companion object {

        const val TILE_SIZE = 512
        const val BLOCK_ZOOM: Byte = 16

        fun newDisplayModel(): DisplayModel {
            return DisplayModel().apply {
                setFixedTileSize(TILE_SIZE)
            }
        }

        fun parseTheme(displayModel: DisplayModel): RenderThemeFuture {
            val themeFile = File(requiredProperty("bench.fixturesDir"), "themes/lomaps/theme.xml")
            val future = RenderThemeFuture(
                AwtGraphicFactory.INSTANCE,
                ExternalRenderTheme(themeFile),
                displayModel,
            )
            future.run()
            future.get()
            return future
        }

        /**
         * The tile at `bench.center` if set, else at the map's start position or bounding-box
         * centre; it must lie inside the map, or the render is just background.
         */
        fun centerTile(store: MapFile, zoom: Byte): Tile {
            val center = System.getProperty("bench.center").orEmpty()
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.let { LatLong(it[0].trim().toDouble(), it[1].trim().toDouble()) }
                ?: store.startPosition()
            check(store.boundingBox().contains(center)) {
                "centerTile($center), outside the map ${store.boundingBox()}"
            }
            return Tile(
                MercatorProjection.longitudeToTileX(center.longitude, zoom),
                MercatorProjection.latitudeToTileY(center.latitude, zoom),
                zoom,
                TILE_SIZE,
            )
        }

        fun requiredProperty(name: String): String {
            return requireNotNull(System.getProperty(name)) {
                "requiredProperty($name), not set; run through Gradle: ./gradlew :dev:bench:jmh"
            }
        }
    }
}
