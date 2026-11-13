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
    fun wrapsAtSpaces() {
        val lines = SymbolLayout.wrapLine("Ravin de Sainte Dévote", maxWidth = 10f) { it.length.toFloat() }

        assertEquals(listOf("Ravin de", "Sainte", "Dévote"), lines)
        assertEquals(listOf("MONACO-", "VILLE"), SymbolLayout.wrapLine("MONACO-VILLE", 8f) { it.length.toFloat() })
    }
}
