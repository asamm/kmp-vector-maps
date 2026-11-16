/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.tile.Feature
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureIndexTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    @Test
    fun leavesEveryFeatureAFilterPassesInDrawOrder() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())
        val tiles = File(mvtDir, "tiles/planet").walk().filter { it.extension == "pbf" }.toList()
        var narrowed = 0
        for (file in tiles) {
            val tile = VectorTile.decode(file.readBytes())
            val index = FeatureIndex()
            for (layer in style.layers) {
                val source = tile.layers[layer.sourceLayer] ?: continue
                val ctx = EvalContext(14.0)
                val passes = { features: List<Feature> ->
                    features.filter {
                        ctx.feature = it
                        layer.filter == null || layer.filter.eval(ctx) == true
                    }
                }
                val candidates = index.candidates(layer = source, filter = layer.filter)
                assertEquals(passes(source.features), passes(candidates), "${file.name} ${layer.id}")
                if (candidates.size < source.features.size) {
                    narrowed++
                }
            }
        }
        assertTrue(narrowed > 100, "layers narrowed: $narrowed")
    }

    @Test
    fun leavesWhatLegacyFiltersPass() {
        val filters = listOf(
            """["==", "class", "primary"]""",
            """["in", "class", "primary", "secondary", "minor"]""",
            """["all", ["==", "${'$'}type", "LineString"], ["in", "class", "path", "track"]]""",
            """["all", ["!in", "class", "motorway"], ["==", "brunnel", "bridge"]]""",
            """["==", "${'$'}type", "Polygon"]""",
            """["in", "class"]""",
        )
        val layers = filters.withIndex().joinToString(",") { (i, filter) ->
            """{"id": "l$i", "type": "line", "source": "s", "source-layer": "transportation", "filter": $filter}"""
        }
        val style = Style.parse("""{"version": 8, "sources": {}, "layers": [$layers]}""")
        assertEquals(emptyList(), style.problems)
        val tile = VectorTile.decode(File(mvtDir, "tiles/planet/14/8529/5974.pbf").readBytes())
        val source = tile.layers.getValue("transportation")
        val ctx = EvalContext(14.0)
        val narrowed = style.layers.map { layer ->
            val passes = { features: List<Feature> ->
                features.filter {
                    ctx.feature = it
                    layer.filter?.eval(ctx) == true
                }
            }
            val candidates = FeatureIndex().candidates(layer = source, filter = layer.filter)
            assertEquals(passes(source.features), passes(candidates), layer.id)
            candidates.size < source.features.size
        }
        // `==` and `in` on a property narrow, the geometry type does not
        assertEquals(listOf(true, true, true, true, false, true), narrowed)
    }
}
