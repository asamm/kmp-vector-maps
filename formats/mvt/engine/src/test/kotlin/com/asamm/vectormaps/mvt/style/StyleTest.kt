/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import com.asamm.vectormaps.mvt.tile.VectorTile
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    @Test
    fun parsesHikeBike() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())

        assertEquals(455, style.layers.size)
        // elevation layers are the host's (HILLSHADE anchor)
        assertEquals(listOf("bathymetry_colored color-relief-color: elevation"), style.problems)
    }

    @Test
    fun evaluatesEveryFilterAndPropertyOnTheFixtures() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())
        var matched = 0
        val files = File(mvtDir, "tiles/planet").walk().filter { it.extension == "pbf" }.toList()
        assertEquals(6, files.size, "fixture tiles")
        for (file in files) {
            val zoom = file.parentFile.parentFile.name.toDouble()
            val tile = VectorTile.decode(file.readBytes())
            val ctx = EvalContext(zoom)
            for (layer in style.layers) {
                val features = tile.layers[layer.sourceLayer]?.features ?: continue
                for (feature in features) {
                    ctx.feature = feature
                    if (layer.filter?.eval(ctx) == false) {
                        continue
                    }
                    matched++
                    for (p in layer.paint.values + layer.layout.values) {
                        p.eval(ctx)
                    }
                }
            }
        }
        assertTrue(matched > 1000, "matched $matched")
    }

    @Test
    fun evaluatesExpressions() {
        fun eval(json: String, zoom: Double = 10.0): Any? {
            return Expression.compile(Json.parseToJsonElement(json)).eval(EvalContext(zoom))
        }
        assertEquals(15.0, eval("""["interpolate", ["linear"], ["zoom"], 10, 10, 12, 20]""", zoom = 11.0))
        assertEquals("b", eval("""["step", ["zoom"], "a", 8, "b", 12, "c"]"""))
        assertEquals(true, eval("""["in", "x", ["literal", ["a", "x"]]]"""))
        assertEquals(1.0, eval("""["index-of", "x", ["literal", ["a", "x"]]]"""))
        assertEquals("AB", eval("""["upcase", ["concat", "a", "b"]]"""))
        assertEquals("bc", eval("""["slice", "abc", 1]"""))
        assertEquals(6.0, eval("""["let", "w", 3, ["*", ["var", "w"], 2]]"""))
        assertEquals("y", eval("""["case", false, "x", true, "y", "z"]"""))
        assertEquals(
            Color(0xff808080.toInt()),
            eval("""["interpolate", ["linear"], ["zoom"], 0, "#000", 20, "#fff"]"""),
        )
        // straight channels, as MapLibre
        assertEquals(Color(0x80ff0000.toInt()), Colors.interpolate(Color(0xffff0000.toInt()), Color(0x00ff0000), 0.5))
        assertEquals(Color(0x80ff0000.toInt()), Colors.parse("rgba(255, 0, 0, 0.5)"))
        assertEquals(Color(0xff00ff00.toInt()), Colors.parse("hsl(120, 100%, 50%)"))
    }

    @Test
    fun evaluatesLegacyFilters() {
        val json = Json.parseToJsonElement("""["all", ["==", "class", "x"], ["!in", "k", 1, 2]]""")
        val filter = Expression.compileFilter(json)
        assertEquals(false, filter.isFeatureConstant)
        assertEquals(false, filter.eval(EvalContext()))
    }

    @Test
    fun listsShortCallsAsProblems() {
        val style = Style.parse(
            """{"layers": [{"id": "a", "type": "line", "filter": ["==", ["get", "class"]]}]}""",
        )

        assertEquals(1, style.problems.size, "${style.problems}")
        assertEquals(null, style.layers.single().filter)
    }

    @Test
    fun listsMalformedShapesAsProblems() {
        val style = Style.parse(
            """{"layers": [
                1,
                {"id": "a", "type": "line", "paint": {"line-width": ["let", "x", ["zoom"]]}},
                {"id": "b", "type": "line", "filter": ["all", ["==", "class"], ["!in", "k"]]},
                {"id": "c", "type": "line", "paint": {"line-color": ["let"]}}
            ]}""",
        )

        assertEquals(3, style.layers.size)
        assertEquals(4, style.problems.size, "${style.problems}")
    }
}
