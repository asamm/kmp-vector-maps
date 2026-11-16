/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import com.asamm.vectormaps.mvt.tile.Feature
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.Layer
import com.asamm.vectormaps.mvt.tile.VectorTile
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    @Test
    fun parsesHikeBike() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())

        assertEquals(455, style.layers.size)
        assertEquals(emptyList(), style.problems)
        // elevation layers are the host's (HILLSHADE anchor), their stops passed on
        val relief = style.layers.first { it.id == "bathymetry_colored" }.paint.getValue("color-relief-color")
        assertEquals(-11000.0 to Colors.parse("#0d3d5f"), (relief.eval(EvalContext()) as List<*>).first())
        assertEquals("terrarium", style.sources.getValue("bathymetry-rgb").encoding)
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
    fun keepsNoTileItEvaluated() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())
        val layers = evaluateFilters(style)

        repeat(10) {
            if (layers.any { it.get() != null }) {
                System.gc()
                Thread.sleep(20)
            }
        }
        assertEquals(emptyList(), layers.mapNotNull { it.get()?.name }, "layers the style keeps")
    }

    // the tile's layers, weakly, after every filter of `style` read them; the tile is local here,
    // so only the style can keep it
    private fun evaluateFilters(style: Style): List<WeakReference<Layer>> {
        val tile = VectorTile.decode(File(mvtDir, "tiles/planet/14/8529/5974.pbf").readBytes())
        val ctx = EvalContext(14.0)
        for (layer in style.layers) {
            for (feature in tile.layers[layer.sourceLayer]?.features.orEmpty()) {
                ctx.feature = feature
                layer.filter?.eval(ctx)
            }
        }
        return tile.layers.values.map { WeakReference(it) }
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
        assertEquals("bc", eval("""["slice", "abc", -2]"""))
        assertEquals("ab", eval("""["slice", "abc", 0, -1]"""))
        assertEquals(-2.0, eval("""["round", -1.5]"""))
        assertEquals(3.0, eval("""["round", 2.5]"""))
        assertEquals(true, eval("""["==", ["round", -0.3], 0]"""))
        assertEquals(0.0, eval("""["round", 0.49999999999999994]"""))
        assertEquals(4503599627370497.0, eval("""["round", 4503599627370497]"""))
        assertEquals(true, eval("""["==", ["ceil", -0.3], 0]"""))
        assertEquals(false, eval("""["<", ["ceil", -0.3], 0]"""))
        assertEquals(true, eval("""["in", ["ceil", -0.3], ["literal", [0, 1]]]"""))
        assertEquals(0.0, eval("""["to-number", null, "7"]"""))
        assertEquals(7.0, eval("""["to-number", "x", "7"]"""))
        assertEquals("b", eval("""["string", 1, "b"]"""))
        assertEquals(null, eval("""["number", "a"]"""))
        assertEquals("b", eval("""["at", 1, ["literal", ["a", "b"]]]"""))
        assertEquals(null, eval("""["at", 2, ["literal", ["a", "b"]]]"""))
        assertEquals(3.0, eval("""["sqrt", 9]"""))
        assertEquals(kotlin.math.PI, eval("""["pi"]"""))
        assertEquals(Color(0x80ff8000.toInt()), eval("""["rgba", 255, 128, 0, 0.5]"""))
        assertEquals(listOf(255.0, 0.0, 0.0, 1.0), eval("""["to-rgba", "#f00"]"""))
        assertEquals("number", eval("""["typeof", 1]"""))
        assertEquals("bike", eval("""["image", "bike"]"""))
        // legacy functions
        assertEquals(15.0, eval("""{"stops": [[10, 10], [12, 20]]}""", zoom = 11.0))
        assertEquals("b", eval("""{"stops": [[0, "a"], [10, "b"], [12, "c"]]}""", zoom = 11.0))
        assertEquals(Color(0xff808080.toInt()), eval("""{"stops": [[0, "#000"], [20, "#fff"]]}"""))
        assertEquals(5.0, eval("""{"property": "x", "type": "categorical", "stops": [["a", 1]], "default": 5}"""))
        // a feature without the property: the function's default, else none (the property's)
        assertEquals(7.0, eval("""{"property": "x", "stops": [[0, 1], [10, 2]], "default": 7}"""))
        assertEquals(null, eval("""{"property": "x", "stops": [[0, 1], [10, 2]]}"""))
        // number lists interpolate, as offsets do in MapLibre
        assertEquals(listOf(0.0, 1.5), eval("""{"stops": [[10, [0, 1]], [12, [0, 2]]]}""", zoom = 11.0))
        assertEquals(null, eval("""["rgb", 300, 0, 0]"""))
        // named colours interpolate; a dasharray steps, as the spec does not interpolate it
        assertEquals(Colors.interpolate(Color(0xff000000.toInt()), Color(0xffffffff.toInt()), 0.5), eval("""{"stops": [[0, "black"], [20, "white"]]}"""))
        val dashes = Expression.compile(Json.parseToJsonElement("""{"stops": [[10, [1, 1]], [12, [3, 3]]]}"""), "line-dasharray")
        assertEquals(listOf(1.0, 1.0), dashes.eval(EvalContext(11.0)))
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
    fun readsSpritePixelRatios() {
        val index = SpriteIndex.parse(
            """{"a": {"width": 2, "height": 2, "pixelRatio": 2},
               "b": {"width": 2, "height": 2, "pixelRatio": 1e400},
               "c": {"width": 2, "height": 2, "pixelRatio": 1e-50}}""",
        )

        assertEquals(2f, index.pixelRatio("a"))
        // a malformed ratio reads as 1, as a missing one
        assertEquals(1f, index.pixelRatio("b"))
        assertEquals(1f, index.pixelRatio("c"))
        assertEquals(1f, index.pixelRatio("none"))
    }

    @Test
    fun evaluatesLegacyFilters() {
        val json = Json.parseToJsonElement("""["all", ["==", "class", "x"], ["!in", "k", 1, 2]]""")
        val filter = Expression.compileFilter(json)
        assertEquals(false, filter.isFeatureConstant)
        assertEquals(false, filter.eval(EvalContext()))
    }

    @Test
    fun readsLegacyTokensAndMapLibreDefaults() {
        val style = Style.parse(
            """{"layers": [{"id": "a", "type": "symbol",
                "layout": {"text-field": "{name} ({ele} m)", "icon-image": "{class}_11"}}]}""",
        )
        val layer = style.layers.single()
        val ctx = EvalContext(10.0)
        val source = Layer(
            name = "l",
            extent = 4096,
            keys = arrayOf("name", "ele", "class"),
            values = arrayOf("Sněžka", 1603.0, "peak"),
        )
        ctx.feature = Feature(
            layer = source,
            id = null,
            tags = intArrayOf(0, 0, 1, 1, 2, 2),
            type = GeometryType.POINT,
            geometry = IntArray(0),
        )

        assertEquals("Sněžka (1603 m)", layer.layout.getValue("text-field").eval(ctx))
        assertEquals("peak_11", layer.layout.getValue("icon-image").eval(ctx))
        val zoomed = Expression.compile(Json.parseToJsonElement("""{"stops": [[0, "{name}"]]}"""), "text-field")
        assertEquals("Sněžka", zoomed.eval(ctx))
        // no maxzoom: active at any zoom, as MapLibre
        assertTrue(layer.isActive(26.0))
    }

    @Test
    fun listsMalformedCaseAndMatchAsProblems() {
        val style = Style.parse(
            """{"layers": [
                {"id": "a", "type": "line", "paint": {"line-width": ["case", false, 2]}},
                {"id": "b", "type": "line", "paint": {"line-width": ["match", ["get", "k"], "x", 2]}},
                {"id": "c", "type": "line", "paint": {"line-width": ["case", false, 2, 3]}}
            ]}""",
        )

        assertEquals(2, style.problems.size, "${style.problems}")
        assertEquals(false, Expression.compile(Json.parseToJsonElement("""["to-boolean", ["*", -1, 0]]""")).eval(EvalContext(0.0)))
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
