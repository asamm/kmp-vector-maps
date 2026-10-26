/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package org.mapsforge.map.reader

import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.datastore.MapReadResult
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapFileTest {

    private val fixtures = File(checkNotNull(System.getProperty("fixturesDir")) {
        "MapFileTest, fixturesDir not set"
    })

    @Test
    fun concurrentReadsMatchSerialReads() {
        val file = File(fixtures, "maps/monaco.osm.map")
        // the reference reads on its own instance, so the shared one starts with a cold cache
        val reference = MapFile(file)
        val tiles = tiles(reference, zooms = 0..16)
        val serial = try {
            tiles.associateWith { digest(reference.readMapData(it)) }
        } finally {
            reference.close()
        }
        assertTrue(serial.values.all { it != null }, "a serial read failed")
        assertTrue(serial.values.sumOf { it!!.second.size } > 0, "no ways read")
        val mapFile = MapFile(file)
        try {
            val pool = Executors.newFixedThreadPool(THREADS)
            try {
                val jobs = (0 until THREADS).map { thread ->
                    Callable {
                        tiles.shuffled(Random(thread)).repeatedly(ROUNDS).count { tile ->
                            digest(mapFile.readMapData(tile)) != serial.getValue(tile)
                        }
                    }
                }
                val futures = pool.invokeAll(jobs, TIMEOUT_SECONDS, TimeUnit.SECONDS)
                check(futures.none { it.isCancelled }) {
                    "concurrentReadsMatchSerialReads(), reads not done in $TIMEOUT_SECONDS s"
                }
                val differed = futures.sumOf { it.get() }
                assertEquals(0, differed, "reads that differ from the serial read")
            } finally {
                pool.shutdown()
                pool.awaitTermination(10, TimeUnit.SECONDS)
            }
        } finally {
            mapFile.close()
        }
    }

    // every tile of the map's bounds at each zoom
    private fun tiles(mapFile: MapFile, zooms: IntRange): List<Tile> {
        val box = mapFile.boundingBox()
        return zooms.flatMap { zoom ->
            val z = zoom.toByte()
            val left = MercatorProjection.longitudeToTileX(box.minLongitude, z)
            val right = MercatorProjection.longitudeToTileX(box.maxLongitude, z)
            val top = MercatorProjection.latitudeToTileY(box.maxLatitude, z)
            val bottom = MercatorProjection.latitudeToTileY(box.minLatitude, z)
            (left..right).flatMap { x ->
                (top..bottom).map { y ->
                    Tile(x, y, z, TILE_SIZE)
                }
            }
        }
    }

    private fun digest(result: MapReadResult?): Pair<List<*>, List<*>>? {
        if (result == null) {
            return null
        }
        return result.pois to result.ways
    }

    private fun <T> List<T>.repeatedly(times: Int): List<T> {
        return List(times) { this }.flatten()
    }

    private companion object {

        const val THREADS = 8

        const val ROUNDS = 3

        const val TIMEOUT_SECONDS = 60L

        const val TILE_SIZE = 256
    }
}
