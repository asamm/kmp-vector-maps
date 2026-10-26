/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package org.mapsforge.map.reader

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexCacheTest {

    private val fixtures = File(checkNotNull(System.getProperty("fixturesDir")) {
        "IndexCacheTest, fixturesDir not set"
    })

    @Test
    fun concurrentLookupsWithEvictionsMatchSerialLookups() {
        val file = File(fixtures, "maps/monaco.osm.map")
        val mapFile = MapFile(file)
        val subFiles = try {
            (0..mapFile.mapFileInfo.zoomLevelMax).mapNotNull { mapFile.mapFileHeader.getSubFileParameter(it) }
                .distinct()
        } finally {
            mapFile.close()
        }
        val entries = subFiles.flatMap { subFile ->
            (0 until subFile.numberOfBlocks).map { block -> subFile to block }
        }
        RandomAccessFile(file, "r").use { raf ->
            val expected = IndexCache(raf.channel, 64).let { serial ->
                entries.associateWith { (subFile, block) -> serial.getIndexEntry(subFile, block) }
            }
            // one index block per sub-file, so a cache of one evicts on nearly every lookup
            assertTrue(subFiles.size > 1, "sub-files: ${subFiles.size}")
            val cache = IndexCache(raf.channel, 1)
            val differed = runConcurrently { thread ->
                entries.shuffled(Random(thread)).repeatedly(ROUNDS).count { (subFile, block) ->
                    cache.getIndexEntry(subFile, block) != expected.getValue(subFile to block)
                }
            }
            assertEquals(0, differed, "lookups that differ from the serial lookup")
            // a racing access-ordered map loses or keeps links rather than returning wrong blocks
            val map = cachedMap(cache)
            val linked = synchronized(map) { map.entries.count() }
            assertEquals(map.size, linked, "entries linked vs size")
            assertTrue(map.size <= 1, "size ${map.size} over the capacity")
        }
    }

    private fun cachedMap(cache: IndexCache): Map<*, *> {
        val field = IndexCache::class.java.getDeclaredField("map")
        field.isAccessible = true
        return field.get(cache) as Map<*, *>
    }

    // THREADS jobs released together; the sum of their results
    private fun runConcurrently(job: (thread: Int) -> Int): Int {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val futures = (0 until THREADS).map { thread ->
                pool.submit(Callable {
                    start.await()
                    job(thread)
                })
            }
            start.countDown()
            return futures.sumOf { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private fun <T> List<T>.repeatedly(times: Int): List<T> {
        return List(times) { this }.flatten()
    }

    private companion object {

        const val THREADS = 8

        const val ROUNDS = 2000

        const val TIMEOUT_SECONDS = 60L
    }
}
