/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.source

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmTilesSourceTest {

    private val tilesDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt/tiles")

    @Test
    fun countsTileIdsAsTheSpec() {
        // the spec's test vectors
        val ids = listOf(Triple(0, 0, 0), Triple(1, 0, 0), Triple(1, 0, 1), Triple(1, 1, 1), Triple(1, 1, 0), Triple(2, 0, 0))
            .map { (z, x, y) -> PmTilesSource.tileId(z, x, y) }

        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L), ids)
    }

    @Test
    fun readsTheFixtureTiles() {
        // written by dev/mvt/pmtiles.py from the fixture tiles, gzip tiles and directory
        PmTilesSource(File(tilesDir, "fixtures.pmtiles")).use { source ->
            assertEquals(10, source.minZoom)
            assertEquals(14, source.maxZoom)
            val files = File(tilesDir, "planet").walk().filter { it.extension == "pbf" }.toList()
            assertEquals(6, files.size)
            for (file in files) {
                val z = file.parentFile.parentFile.name.toInt()
                val x = file.parentFile.name.toInt()
                val y = file.nameWithoutExtension.toInt()
                assertContentEquals(file.readBytes(), source.tile(z, x, y), "$z/$x/$y")
            }
            assertNull(source.tile(14, 8529, 5975))
            assertNull(source.tile(16, 0, 0))
            // outside the zoom's grid, not another tile by wrapped bits
            assertNull(source.tile(14, 8529 + (1 shl 14), 5974))
            assertNull(source.tile(14, -1, 5974))
            val (west, south, east, north) = source.bounds.toList()
            assertTrue(west < 7.42 && east > 7.42 && south < 43.73 && north > 50.73, "bounds ${source.bounds.toList()}")
        }
    }

    @Test
    fun findsTileRunsAndLeaves() {
        val directory = PmTilesSource.Directory(
            tileIds = longArrayOf(5, 10, 20),
            runLengths = longArrayOf(1, 3, 0),
            lengths = longArrayOf(1, 1, 1),
            offsets = longArrayOf(0, 1, 2),
        )

        assertEquals(-1, directory.find(4))
        assertEquals(0, directory.find(5))
        assertEquals(-1, directory.find(6))
        // a run of 3 from 10
        assertEquals(1, directory.find(12))
        assertEquals(-1, directory.find(13))
        // a leaf holds every id from its first on
        assertEquals(2, directory.find(1000))
    }

    @Test
    fun rejectsADirectoryWithMoreEntriesThanBytes() {
        // a count of 2^20 in three bytes
        assertFailsWith<IllegalArgumentException> {
            PmTilesSource.parseDirectory(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x40))
        }
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<IllegalArgumentException> {
            PmTilesSource(File(tilesDir, "planet.json"))
        }
    }
}
