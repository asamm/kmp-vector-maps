/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mapsforge.core.model.Tile
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Parsing the LoMaps day theme on the device; symbols are rasterised lazily on first render.
 */
@RunWith(AndroidJUnit4::class)
class ThemeParseBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun themeParse() {
        val displayModel = BenchScene.newDisplayModel()
        benchmarkRule.measureRepeated {
            val future = BenchScene.parseTheme(displayModel = displayModel)
            runWithMeasurementDisabled {
                future.decrementRefCount()
            }
        }
    }
}

/**
 * Reading and rendering one tile per zoom through a long-lived renderer, as Locus renders.
 */
@RunWith(Parameterized::class)
class TileBenchmark(private val zoom: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: BenchScene
    private lateinit var tile: Tile

    @Before
    fun setUp() {
        scene = BenchScene()
        tile = BenchScene.centerTile(store = scene.store, zoom = zoom.toByte())
    }

    @After
    fun tearDown() {
        scene.close()
    }

    @Test
    fun readTile() {
        benchmarkRule.measureRepeated {
            scene.store.readMapData(tile)
        }
    }

    @Test
    fun renderTile() {
        benchmarkRule.measureRepeated {
            val bitmap = scene.render(tile)
            runWithMeasurementDisabled {
                bitmap.decrementRefCount()
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
 * Rendering a 4x4 block at z16 with a fixed thread count through one shared renderer, as Locus'
 * tile workers do; time per block, so throughput is 16 tiles divided by it.
 */
@RunWith(Parameterized::class)
class ParallelBlockBenchmark(private val threads: Int) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scene: BenchScene
    private lateinit var executor: ExecutorService
    private lateinit var block: List<Tile>

    @Before
    fun setUp() {
        scene = BenchScene()
        executor = Executors.newFixedThreadPool(threads)
        val center = BenchScene.centerTile(store = scene.store, zoom = BLOCK_ZOOM)
        block = (-2..1).flatMap { dy ->
            (-2..1).map { dx ->
                Tile(center.tileX + dx, center.tileY + dy, BLOCK_ZOOM, BenchScene.TILE_SIZE)
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
            val bitmaps = executor.invokeAll(tasks).map { it.get() }
            runWithMeasurementDisabled {
                bitmaps.forEach { it.decrementRefCount() }
            }
        }
    }

    companion object {

        private const val BLOCK_ZOOM: Byte = 16

        @JvmStatic
        @Parameterized.Parameters(name = "threads{0}")
        fun threadCounts(): List<Int> {
            return listOf(1, 2, 4, 8)
        }
    }
}
