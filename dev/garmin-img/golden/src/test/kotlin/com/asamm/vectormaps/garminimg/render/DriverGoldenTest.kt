/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.driver.GarminOptions
import com.asamm.vectormaps.garminimg.driver.GarminRenderer
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import com.asamm.vectormaps.garminimg.fixtureFile
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.garminimg.style.Color
import java.awt.Font
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.fail

/**
 * Monaco tiles through the Garmin driver against committed PNGs, without labels (D39), and the
 * label pass over a Monaco screen, straight and turned, against a committed PNG and placement dump.
 * Labels use the bundled DejaVu Sans and anti-aliasing is off, so the renders match on every system.
 *
 * A missing golden is written as a candidate under `build/test-output/render-candidates/`; copy it to
 * `src/test/resources/golden/` once it looks right.
 */
class DriverGoldenTest {

    private val font = fixtureFile("fonts/dejavu/DejaVuSans.ttf").inputStream().use {
        Font.createFont(Font.TRUETYPE_FONT, it)
    }

    private val driver = GarminDriver { size, antiAliased, scale ->
        AwtRenderTarget(widthPx = size, heightPx = size, antiAliased = antiAliased, renderScale = scale, font = font)
    }

    @Test
    fun harbourTilesMatchGoldens() {
        val failures = ArrayList<String>()
        driver.open(listOf(fixtureFile("maps/otm-monaco/otm-monaco.img"))).use { dataset ->
            for (scale in TILES.keys) {
                val config = RenderConfig(tileSizePx = TILE_SIZE, deviceScale = scale)
                val options = GarminOptions(antiAliasing = false)
                dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config, options).use { renderer ->
                    for (tile in TILES.getValue(scale)) {
                        failures += compare(renderer, tile, scale)
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    @Test
    fun labelPassMatchesGoldens() {
        val failures = ArrayList<String>()
        driver.open(listOf(fixtureFile("maps/otm-monaco/otm-monaco.img"))).use { dataset ->
            val config = RenderConfig(tileSizePx = TILE_SIZE)
            val options = GarminOptions(antiAliasing = false)
            dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config, options).use { renderer ->
                val views = PASS_ROTATIONS.map { rotation ->
                    LabelViewport(
                        zoom = PASS_CORNER.zoom,
                        centerX = PASS_CORNER.x.toDouble() * TILE_SIZE,
                        centerY = PASS_CORNER.y.toDouble() * TILE_SIZE,
                        widthPx = TILE_SIZE,
                        heightPx = TILE_SIZE,
                        rotationDeg = rotation,
                    )
                }
                for (tile in tilesUnder(views)) {
                    renderer.render(tile, GarminTileTarget(TILE_SIZE)) { false }
                }
                for (view in views) {
                    failures += comparePass(renderer = renderer, view = view)
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    // the one-shot path a preview takes: the screen's tiles rendered, then the labels over them
    private fun comparePass(renderer: GarminRenderer, view: LabelViewport): List<String> {
        val name = "otm-monaco-pass-${view.zoom}-r${view.rotationDeg.toInt()}"
        val prepared = renderer.labelPass.prepareNow(view)
        val target = AwtRenderTarget(widthPx = view.widthPx, heightPx = view.heightPx, antiAliased = false, renderScale = 1f, font = font)
        target.beginFrame(Color.rgb(0xFF, 0xFF, 0xFF))
        renderer.labelPass.draw(target = target, view = view, prepared = prepared)
        target.endFrame()
        val failures = ArrayList<String>()
        val dump = prepared.placed.joinToString(separator = "\n", postfix = "\n") { placed ->
            val candidate = placed.candidate
            String.format(
                Locale.ROOT,
                "%s p=%d xy=%.2f,%.2f boxes=%d \"%s\"",
                candidate.kind,
                candidate.priority,
                candidate.x,
                candidate.y,
                candidate.boxes.size,
                candidate.text?.value ?: "",
            )
        }
        val goldenDump = javaClass.classLoader.getResource("golden/$name.txt")?.readText()
        if (goldenDump == null) {
            failures += "$name.txt: no golden, candidate ${saveDumpCandidate(dump, name)}"
        } else if (goldenDump != dump) {
            failures += "$name.txt: differs, candidate ${saveDumpCandidate(dump, name)}"
        }
        val golden = GoldenPngDiff.loadFromClasspath("golden/$name.png")
        if (golden == null) {
            failures += "$name: no golden, candidate ${GoldenPngDiff.saveCandidate(target.image, name)}"
        } else {
            val diff = GoldenPngDiff.compare(target.image, golden, perChannelTolerance = 8)
            if (diff.differingPixelFraction > MAX_DIFFERING) {
                failures += "$name: $diff, candidate ${GoldenPngDiff.saveCandidate(target.image, name)}"
            }
        }
        return failures
    }

    private fun saveDumpCandidate(dump: String, name: String): File {
        val file = File("build/test-output/render-candidates/$name.txt")
        file.parentFile.mkdirs()
        file.writeText(dump)
        return file
    }

    // every tile under the views at any of their rotations
    private fun tilesUnder(views: List<LabelViewport>): List<TileRequest> {
        val tiles = LinkedHashSet<TileRequest>()
        for (view in views) {
            val bounds = view.worldBounds()
            for (y in floor(bounds.minY / TILE_SIZE).toInt()..floor(bounds.maxY / TILE_SIZE).toInt()) {
                for (x in floor(bounds.minX / TILE_SIZE).toInt()..floor(bounds.maxX / TILE_SIZE).toInt()) {
                    tiles += TileRequest(x = x, y = y, zoom = view.zoom)
                }
            }
        }
        return tiles.toList()
    }

    /**
     * Renders `tile` and returns why it does not match its golden, if it does not.
     */
    private fun compare(renderer: GarminRenderer, tile: TileRequest, scale: Float): List<String> {
        val suffix = if (scale == 1f) "" else "-x${scale.toInt()}"
        val name = "otm-monaco-${tile.zoom}-${tile.x}-${tile.y}$suffix"
        val target = GarminTileTarget(TILE_SIZE)
        // a tile that throws is reported with the others, which still render
        val result = try {
            renderer.render(tile, target) { false }
        } catch (e: Exception) {
            return listOf("$name: $e")
        }
        if (result != RenderResult.DRAWN) {
            return listOf("$name: $result")
        }
        val image = (target.canvas as AwtRenderTarget).image
        val golden = GoldenPngDiff.loadFromClasspath("golden/$name.png")
            ?: return listOf("$name: no golden, candidate ${GoldenPngDiff.saveCandidate(image, name)}")
        if (image.width != golden.width
            || image.height != golden.height
        ) {
            val candidate = GoldenPngDiff.saveCandidate(image, name)
            return listOf("$name: ${image.width}x${image.height}, golden ${golden.width}x${golden.height}, candidate $candidate")
        }
        val diff = GoldenPngDiff.compare(image, golden, perChannelTolerance = 8)
        if (diff.differingPixelFraction > MAX_DIFFERING) {
            return listOf("$name: $diff, candidate ${GoldenPngDiff.saveCandidate(image, name)}")
        }
        return emptyList()
    }

    private companion object {

        const val TILE_SIZE = 512

        // per device scale: Monaco harbour at an overview, a town and a street zoom; Monte Carlo at
        // the scale of a phone, where TYP sizes and labels are scaled
        val TILES = mapOf(
            1f to listOf(
                TileRequest(x = 4264, y = 2987, zoom = 13),
                TileRequest(x = 17059, y = 11948, zoom = 15),
                TileRequest(x = 68238, y = 47794, zoom = 17),
            ),
            2f to listOf(
                TileRequest(x = 34120, y = 23894, zoom = 16),
            ),
        )

        // rasterizers differ by a few edge pixels between systems; a regression moves far more
        const val MAX_DIFFERING = 0.002

        // the label pass screen: centred on the corner of four harbour tiles at z15
        val PASS_CORNER = TileRequest(x = 17060, y = 11949, zoom = 15)

        val PASS_ROTATIONS = listOf(0.0, 30.0)
    }
}
