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
import java.awt.Font
import kotlin.test.Test
import kotlin.test.fail

/**
 * Monaco tiles through the Garmin driver against committed PNGs. Labels use the bundled DejaVu
 * Sans and anti-aliasing is off, so the renders match on every system.
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

    /**
     * Renders `tile` and returns why it does not match its golden, if it does not.
     */
    private fun compare(renderer: GarminRenderer, tile: TileRequest, scale: Float): List<String> {
        val suffix = if (scale == 1f) "" else "-x${scale.toInt()}"
        val name = "otm-monaco-${tile.zoom}-${tile.x}-${tile.y}$suffix"
        val target = GarminTileTarget(TILE_SIZE)
        val result = renderer.render(tile, target) { false }
        if (result != RenderResult.DRAWN) {
            return listOf("$name: $result")
        }
        val image = (target.canvas as AwtRenderTarget).image
        val golden = GoldenPngDiff.loadFromClasspath("golden/$name.png")
            ?: return listOf("$name: no golden, candidate ${GoldenPngDiff.saveCandidate(image, name)}")
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
    }
}
