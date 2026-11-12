/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.tile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorTileTest {

    private val tilesDir = File(System.getProperty("fixturesDir"), "mvt/tiles/planet")

    @Test
    fun decodesMonacoZ14() {
        val tile = VectorTile.decode(File(tilesDir, "14/8529/5974.pbf").readBytes())

        for (name in listOf("transportation", "building", "water", "place", "poi")) {
            assertTrue(name in tile.layers, "layer $name in ${tile.layers.keys}")
        }
        val transportation = tile.layers.getValue("transportation")
        assertEquals(4096, transportation.extent)
        assertTrue(transportation.features.isNotEmpty())
        assertTrue(transportation.features.any { it.property("class") == "primary" })
    }

    @Test
    fun decodesEveryGeometry() {
        for (file in tilesDir.walk().filter { it.extension == "pbf" }) {
            val tile = VectorTile.decode(file.readBytes())
            for (layer in tile.layers.values) {
                for (feature in layer.features) {
                    val geometry = feature.decodeGeometry()
                    assertEquals(0, geometry.coords.size % 2)
                    assertTrue(geometry.partEnds.isNotEmpty(), "${file.path} ${layer.name} ${feature.id}")
                    assertEquals(geometry.coords.size, geometry.partEnds.last())
                    if (feature.type == GeometryType.POLYGON) {
                        // a ring has at least 3 vertices
                        var start = 0
                        for (end in geometry.partEnds) {
                            assertTrue(end - start >= 6, "${layer.name} ring of ${(end - start) / 2}")
                            start = end
                        }
                    }
                }
            }
        }
    }

    @Test
    fun readsAsammLayers() {
        val tile = VectorTile.decode(File(tilesDir, "14/8908/5504.pbf").readBytes())

        assertTrue("contour" in tile.layers, "${tile.layers.keys}")
        val contour = tile.layers.getValue("contour")
        assertTrue(contour.features.all { it.property("height") is Double })
    }
}
