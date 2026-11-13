/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpTileSourceTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    @Test
    fun opensTheServersTileJsonAndNotAStyle() {
        assertTrue(HttpTileSource.isTileJson(File(mvtDir, "tiles/planet.json")))
        assertFalse(HttpTileSource.isTileJson(File(mvtDir, "styles/hike_bike/style.json")))
        assertFalse(HttpTileSource.isTileJson(File(mvtDir, "tiles/fixtures.pmtiles")))
    }

    @Test
    fun readsCachedTilesWithoutTheNetwork(@TempDir cache: File) {
        val source = HttpTileSource.open(File(mvtDir, "tiles/planet.json"), cacheRoot = cache)
        assertEquals(0, source.minZoom)
        assertEquals(14, source.maxZoom)
        // the server's host and tileset path name the folder
        val folder = File(cache, "tiles.locusmap.app/data_planet")
        val tile = File(mvtDir, "tiles/planet/14/8529/5974.pbf")
        tile.copyTo(File(folder, "14/8529/5974.pbf"))
        File(folder, "14/0/0.pbf").apply { parentFile.mkdirs() }.writeBytes(ByteArray(0))

        assertContentEquals(tile.readBytes(), source.tile(14, 8529, 5974))
        // a tile the server lacks
        assertNull(source.tile(14, 0, 0))
    }

    @Test
    fun keepsACraftedTemplateInTheCache(@TempDir dir: File) {
        val tileJson = File(dir, "evil.json")
        tileJson.writeText("""{"tiles": ["https://host/..%5C..%5Cout/{z}/{x}/{y}.pbf"]}""")
        val cache = File(dir, "cache")
        val folder = File(cache, "host/.._5C.._5Cout")
        File(folder, "1/0/0.pbf").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1))

        // served from the folder within the cache, so no request is made
        assertContentEquals(byteArrayOf(1), HttpTileSource.open(tileJson, cacheRoot = cache).tile(1, 0, 0))
    }
}
