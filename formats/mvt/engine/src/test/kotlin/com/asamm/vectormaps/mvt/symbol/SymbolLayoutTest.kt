/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.tile.VectorTile
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolLayoutTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    private val layout = SymbolLayout(
        style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText()),
        sprite = SpriteIndex.parse(File(mvtDir, "styles/hike_bike/sprite.json").readText()),
        // a fixed advance keeps the test independent of fonts
        metrics = { text, size, _, _ -> text.length * size * 0.6f },
        maxDataZoom = 14,
    ) { z, x, y ->
        File(mvtDir, "tiles/planet/$z/$x/$y.pbf").takeIf { it.isFile }?.let { VectorTile.decode(it.readBytes()) }
    }

    @Test
    fun stopsWhenCancelled() {
        val all = layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)
        var checks = 0
        val part = layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512) { ++checks > 300 }

        assertTrue(layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512) { true }.isEmpty())
        assertTrue(part.size < all.size, "${part.size} of ${all.size}")
    }

    @Test
    fun laysOutMonacoLabels() {
        val labels = layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)

        val texts = labels.mapNotNull { it.text?.lines?.joinToString(" ") }
        assertTrue(texts.any { "CONDAMINE" in it }, "place names: ${texts.take(40)}")
        assertTrue(labels.any { it.path != null }, "road names along their lines")
        assertTrue(labels.any { it.icon != null }, "icons")
        for (label in labels) {
            assertTrue(label.x in 0f..512f && label.y in 0f..512f, "${label.layerId} at ${label.x}, ${label.y}")
        }
    }

    @Test
    fun readsNamesInTheHostLanguageAndScalesText() {
        val style = Style.parse(
            """{"layers": [{"id": "places", "type": "symbol", "source-layer": "place",
                "layout": {"text-field": ["get", "name"], "text-size": 10}}]}""",
        )
        val tile = File(mvtDir, "tiles/planet/10/556/344.pbf")
        fun layoutOf(language: String?, scale: Float, textScale: Float): List<MvtText> {
            val layout = SymbolLayout(
                style = style,
                sprite = null,
                metrics = { text, size, _, _ -> text.length * size * 0.6f },
                maxDataZoom = 14,
                scale = scale,
                textScale = textScale,
                language = language,
            ) { _, _, _ -> VectorTile.decode(tile.readBytes()) }
            return layout.labels(zoom = 10, x = 556, y = 344, tileSizePx = 512).mapNotNull { it.text }
        }
        // German names of Czech and Polish places, where the tile has them
        val german = VectorTile.decode(tile.readBytes()).layers.getValue("place").features
            .filter { it.property("name:de") != null && it.property("name:de") != it.property("name") }
            .map { it.property("name:de") as String }
        assertTrue(german.isNotEmpty(), "places with a German name")

        val local = layoutOf(language = null, scale = 1f, textScale = 1f)
        val inGerman = layoutOf(language = "de-AT", scale = 1f, textScale = 1f)
        assertTrue(local.none { it.lines.joinToString(" ") in german })
        assertTrue(inGerman.any { it.lines.joinToString(" ") in german })
        // 10 px at a device scale of 2 and text scale of 1.5
        assertEquals(30f, layoutOf(language = null, scale = 2f, textScale = 1.5f).first().sizePx)
    }

    @Test
    fun readsLanguageTagsAsOpenMapTilesNamesThem() {
        assertEquals(listOf("name:de"), SymbolLayout.localNamesOf("de_AT"))
        assertEquals(listOf("name:zh-Hant", "name:zh"), SymbolLayout.localNamesOf("zh-hant-TW"))
        assertEquals(listOf("name:he"), SymbolLayout.localNamesOf("iw"))
        assertEquals(listOf("name:id"), SymbolLayout.localNamesOf(" in "))
        assertEquals(emptyList(), SymbolLayout.localNamesOf(""))
        assertEquals(emptyList(), SymbolLayout.localNamesOf(null))
    }

    @Test
    fun turnsLineIconsWithTheirLine() {
        val labels = layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)

        val arrows = labels.filter { it.layerId == "label_road_oneway__1" }
        assertTrue(arrows.isNotEmpty(), "one-way arrows")
        assertTrue(arrows.all { it.lineAngleDeg != null && it.path == null })
        assertTrue(arrows.map { it.lineAngleDeg }.distinct().size > 1, "arrows follow their roads")
    }

    @Test
    fun placesNoLineSymbolsAtPoints() {
        fun labelsOf(placement: String): List<MvtLabel> {
            val style = Style.parse(
                """{"layers": [{"id": "a", "type": "symbol", "source-layer": "poi",
                    "layout": {"symbol-placement": "$placement", "text-field": "x"}}]}""",
            )
            return SymbolLayout(style, null, { text, size, _, _ -> text.length * size * 0.6f }, 14) { z, x, y ->
                File(mvtDir, "tiles/planet/$z/$x/$y.pbf").takeIf { it.isFile }?.let { VectorTile.decode(it.readBytes()) }
            }.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)
        }

        // the poi layer holds points: placed with point placement, never with line placement, as MapLibre
        assertTrue(labelsOf("point").isNotEmpty())
        assertEquals(emptyList(), labelsOf("line"))
    }

    @Test
    fun clampsSymbolSpacingToItsMinimum() {
        // the Asamm style interpolates via ferrata icons to -3 at z14, which must not loop forever
        val style = Style.parse(
            """{"layers": [{"id": "a", "type": "symbol", "source-layer": "transportation",
                "layout": {"symbol-placement": "line", "symbol-spacing": -3, "text-field": "x"}}]}""",
        )
        val negative = SymbolLayout(style, null, { text, size, _, _ -> text.length * size * 0.6f }, 14) { z, x, y ->
            File(mvtDir, "tiles/planet/$z/$x/$y.pbf").takeIf { it.isFile }?.let { VectorTile.decode(it.readBytes()) }
        }

        val labels = assertTimeoutPreemptively(Duration.ofSeconds(10), ThrowingSupplier {
            negative.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)
        })

        assertTrue(labels.isNotEmpty())
    }

    @Test
    fun joinsLinePiecesBeforeAnchoring() {
        val merged = LineMerge.merge(
            listOf(floatArrayOf(10f, 0f, 20f, 0f), floatArrayOf(0f, 0f, 10f, 0f), floatArrayOf(50f, 50f, 60f, 50f)),
        )

        assertEquals(2, merged.size)
        assertEquals(listOf(0f, 0f, 10f, 0f, 20f, 0f), merged.first { it.coords.size == 6 }.coords.toList())
    }

    @Test
    fun breaksLinesAsMapLibre() {
        fun lines(text: String, maxWidth: Float): List<String> {
            return LineBreaks.breakLines(text, maxWidth, spacing = 0f) { it.length.toFloat() }
        }

        assertEquals(listOf("Ravin de", "Sainte", "Dévote"), lines("Ravin de Sainte Dévote", 10f))
        assertEquals(listOf("MONACO-", "VILLE"), lines("MONACO-VILLE", 8f))
        // balanced, not greedy: the later lines no longer than the first
        assertEquals(listOf("Freiberg", "am Neckar"), lines("Freiberg am Neckar", 10f))
        assertEquals(listOf("Steinheim", "an der Murr"), lines("Steinheim an der Murr", 10f))
        assertEquals(listOf("Praha", "- Brno"), lines("Praha - Brno", 7f))
        assertEquals(listOf("Short"), lines("Short", 10f))
        // an empty line keeps its place; a break before an open parenthesis
        assertEquals(listOf("a", "", "b"), lines("a\n\nb", 10f))
        assertEquals(listOf("Name", "(x)"), lines("Name(x)", 4f))
    }

    @Test
    fun anchorsLinesAsMapLibre() {
        // a 1000 px line, a 50 px label, anchors every 250 px
        val line = floatArrayOf(0f, 0f, 1000f, 0f)
        fun anchors(
            region: FloatArray,
            bounds: FloatArray,
            spacing: Float = 250f,
            glyphSize: Float = 10f,
            overscale: Int = 1,
        ): List<Float> {
            return LineAnchors.find(
                coords = line,
                start = 0,
                end = line.size,
                spacing = spacing,
                labelLength = 50f,
                glyphSize = glyphSize,
                overscale = overscale,
                center = false,
                maxAngleDeg = 45.0,
                angleWindow = 0f,
                region = region,
                bounds = bounds,
            ).map { it.x }
        }
        val all = floatArrayOf(-10f, -10f, 2000f, 10f)
        val fromEdge = floatArrayOf(0f, -10f, 2000f, 10f)

        // coming in over the tile's edge: from half a spacing in; a region between two anchors
        // shows none, as they are its neighbours'
        assertEquals(listOf(125f, 375f, 625f, 875f), anchors(all, fromEdge))
        assertEquals(emptyList(), anchors(floatArrayOf(400f, -10f, 600f, 10f), fromEdge))
        // starting in the tile: from half the label and two text sizes in
        assertEquals(listOf(45f, 295f, 545f, 795f), anchors(all, all))
        // a tile cut twice from its data tile: both offsets doubled, modulo the spacing
        assertEquals(listOf(250f, 500f, 750f), anchors(all, fromEdge, overscale = 2))
        assertEquals(listOf(90f, 340f, 590f, 840f), anchors(all, all, overscale = 2))
        // no spaced anchor fits: the middle for a line starting in the tile, none for one coming in
        assertEquals(listOf(500f), anchors(all, all, spacing = 2000f, glyphSize = 600f))
        assertEquals(emptyList(), anchors(all, fromEdge, spacing = 2000f))
        // the edge cuts a line into parts, each anchored on its own
        assertEquals(2, LineAnchors.clip(floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f), floatArrayOf(-1f, -1f, 50f, 101f)).size)
    }
}
