/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.render.LineCap
import com.asamm.vectormaps.mvt.render.LineJoin
import com.asamm.vectormaps.mvt.render.MvtPath
import com.asamm.vectormaps.mvt.render.MvtRenderTarget
import com.asamm.vectormaps.mvt.render.TileRenderer
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Style properties beyond the base set (mvt.md Spec coverage), each on the fixture tile of Monaco
 * at z14 through a style of one layer: what the renderer hands its target and what the layout
 * hands the label pass.
 */
class StylePropertiesTest {

    private val mvtDir = File(requireNotNull(System.getProperty("fixturesDir")), "mvt")

    private val tile = VectorTile.decode(File(mvtDir, "tiles/planet/14/8529/5974.pbf").readBytes())

    private val sprite = SpriteIndex.parse(File(mvtDir, "styles/hike_bike/sprite.json").readText())

    private fun style(layer: String): Style {
        return Style.parse("""{"version": 8, "sources": {}, "layers": [$layer]}""").also {
            assertEquals(emptyList(), it.problems)
        }
    }

    private fun labels(layer: String): List<MvtLabel> {
        val layout = SymbolLayout(
            style = style(layer),
            sprite = sprite,
            metrics = { text, size, _, _ -> text.length * size * 0.6f },
            maxDataZoom = 14,
        ) { _, _, _ -> tile }
        return layout.labels(zoom = 14, x = 8529, y = 5974, tileSizePx = 512)
    }

    private fun draws(layer: String): Recorder {
        val target = Recorder()
        TileRenderer(style = style(layer), maxDataZoom = 14) { _, _, _ -> tile }
            .render(zoom = 14, x = 8529, y = 5974, target = target, isCancelled = { false })
        return target
    }

    @Test
    fun outlinesFillsOnlyWithAnOutlineColour() {
        val layer = """{"id": "f", "type": "fill", "source": "s", "source-layer": "building", "paint": {"fill-color": "#00f""""
        val plain = draws("$layer}}")
        val outlined = draws("""$layer, "fill-outline-color": "#f00", "fill-opacity": 0.5}}""")
        val unAntialiased = draws("""$layer, "fill-outline-color": "#f00", "fill-antialias": false}}""")

        assertTrue(plain.fills > 0)
        assertEquals(0, plain.strokes.size)
        assertEquals(outlined.fills, outlined.strokes.size)
        assertTrue(outlined.strokes.all { it == Color(0x80ff0000.toInt()) })
        assertEquals(0, unAntialiased.strokes.size)
    }

    @Test
    fun fadesCircles() {
        val drawn = draws(
            """{"id": "c", "type": "circle", "source": "s", "source-layer": "poi",
               "paint": {"circle-color": "#000", "circle-opacity": 0.5, "circle-stroke-color": "#fff",
                         "circle-stroke-opacity": 0, "circle-stroke-width": 1}}""",
        )

        assertTrue(drawn.circles.isNotEmpty())
        assertTrue(drawn.circles.all { (fill, stroke) -> fill.alpha == 128 && stroke.alpha == 0 })
    }

    @Test
    fun fadesAndJustifiesText() {
        val labels = labels(
            """{"id": "t", "type": "symbol", "source": "s", "source-layer": "poi",
               "layout": {"text-field": "first line words", "text-max-width": 3, "text-justify": "left"},
               "paint": {"text-color": "#000", "text-halo-color": "#fff", "text-opacity": 0.5}}""",
        )
        val text = labels.first().text!!

        assertTrue(text.lines.size > 1, "${text.lines}")
        assertEquals(0f, text.justify)
        assertEquals(128, text.color.alpha)
        assertEquals(128, text.haloColor.alpha)
    }

    @Test
    fun justifiesAutoToTheAnchorSide() {
        fun justify(anchor: String): Float {
            return labels(
                """{"id": "t", "type": "symbol", "source": "s", "source-layer": "poi",
                   "layout": {"text-field": "x", "text-justify": "auto", "text-anchor": "$anchor"}}""",
            ).first().text!!.justify
        }

        assertEquals(0f, justify("left"))
        assertEquals(1f, justify("top-right"))
        assertEquals(0.5f, justify("top"))
    }

    @Test
    fun anchorsAndFadesIcons() {
        fun icon(extra: String): MvtIcon {
            return labels(
                """{"id": "i", "type": "symbol", "source": "s", "source-layer": "poi",
                   "layout": {"icon-image": "icons\\bike"$extra}, "paint": {"icon-opacity": 0.25}}""",
            ).first().icon!!
        }
        val centred = icon("")
        val bottom = icon(""", "icon-anchor": "bottom-left"""")

        assertEquals(0.25f, centred.opacity)
        assertEquals(0f, centred.centerX)
        // the icon sits right of and above its point
        assertEquals(centred.width * centred.scale / 2f, bottom.centerX)
        assertEquals(-centred.height * centred.scale / 2f, bottom.centerY)
    }

    @Test
    fun keepsPathTextUprightUnlessTold() {
        fun keepUpright(value: String): List<Boolean> {
            return labels(
                """{"id": "p", "type": "symbol", "source": "s", "source-layer": "transportation_name",
                   "layout": {"text-field": ["get", "name"], "symbol-placement": "line"$value}}""",
            ).filter { it.path != null }.map { it.keepUpright }
        }

        assertTrue(keepUpright("").let { it.isNotEmpty() && it.all { k -> k } })
        assertTrue(keepUpright(""", "text-keep-upright": false""").let { it.isNotEmpty() && it.none { k -> k } })
    }

    // what a render hands its target
    private class Recorder : MvtRenderTarget {

        var fills = 0

        val strokes = ArrayList<Color>()

        val circles = ArrayList<Pair<Color, Color>>()

        override val widthPx = 512

        override val heightPx = 512

        override fun clear() = Unit

        override fun drawBackground(color: Color) = Unit

        override fun withPlatformCanvas(block: (Any) -> Unit) = Unit

        override fun fillPath(path: MvtPath, color: Color, antialias: Boolean) {
            fills++
        }

        override fun fillPathPattern(
            path: MvtPath,
            pattern: String,
            opacity: Float,
            antialias: Boolean,
            tileOriginX: Long,
            tileOriginY: Long,
            scale: Float,
        ) = Unit

        override fun strokePath(path: MvtPath, color: Color, width: Float, cap: LineCap, join: LineJoin, dash: FloatArray?) {
            strokes += color
        }

        override fun drawText(text: MvtText, x: Float, y: Float) = Unit

        override fun drawTextAlongPath(text: MvtText, path: FloatArray) = Unit

        override fun drawIcon(icon: MvtIcon, x: Float, y: Float, rotateDeg: Float) = Unit

        override fun drawCircle(x: Float, y: Float, radius: Float, color: Color, strokeWidth: Float, strokeColor: Color) {
            circles += color to strokeColor
        }
    }
}
