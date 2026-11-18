/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.driver.GarminOptions
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * A robustness sweep of a large Garmin map through the driver, on request only:
 * `./gradlew :dev:garmin-img:golden:test --tests "*.GarminSweepTest" -Psweep.garmin.map=<img>
 * -Psweep.garmin.tiles=<tiles.txt> -Psweep.garmin.out=<dir>`. Renders every tile of the list
 * (lines `z/x/y`) and prepares its label view; writes `<out>/results.csv` (time, result, labels
 * placed, error) and each tile as `<out>/png/<z>_<x>_<y>.png`.
 */
@EnabledIfSystemProperty(named = "sweep.garmin.map", matches = ".+")
class GarminSweepTest {

    @Test
    fun sweep() {
        val map = File(System.getProperty("sweep.garmin.map"))
        val tilesPath = requireNotNull(System.getProperty("sweep.garmin.tiles")) {
            "-Psweep.garmin.tiles=<tiles.txt> missing"
        }
        val outPath = requireNotNull(System.getProperty("sweep.garmin.out")) {
            "-Psweep.garmin.out=<dir> missing"
        }
        // parsed up front: a bad line fails before the run, not halfway through it
        val tiles = File(tilesPath).readLines().filter { it.isNotBlank() }.map { line ->
            line.trim().split('/').map { it.toInt() }.also { parts ->
                require(parts.size == 3) {
                    "bad tile line '$line'"
                }
            }
        }
        val out = File(outPath).apply { mkdirs() }
        val png = File(out, "png").apply { mkdirs() }
        val driver = GarminDriver { size, antiAliased, scale ->
            AwtRenderTarget(widthPx = size, heightPx = size, antiAliased = antiAliased, renderScale = scale)
        }
        val csv = StringBuilder("z,x,y,ms,result,labels,error\n")
        val opened = System.nanoTime()
        driver.open(listOf(map)).use { dataset ->
            println("[sweep] opened in ${(System.nanoTime() - opened) / 1_000_000} ms, ${dataset.styles.size} styles")
            val config = RenderConfig(tileSizePx = TILE_SIZE)
            dataset.createRenderer(dataset.styles[0], StyleSelection.DEFAULT, config, GarminOptions()).use { renderer ->
                for ((i, tile) in tiles.withIndex()) {
                    val (z, x, y) = tile
                    var ms = 0.0
                    var result = ""
                    var labels = 0
                    var error = ""
                    try {
                        val target = GarminTileTarget(TILE_SIZE)
                        val started = System.nanoTime()
                        result = renderer.render(TileRequest(x = x, y = y, zoom = z), target) { false }.name
                        ms = (System.nanoTime() - started) / 1e6
                        val canvas = target.canvas as? AwtRenderTarget
                        if (canvas != null) {
                            ImageIO.write(canvas.image, "png", File(png, "${z}_${x}_$y.png"))
                        }
                        val view = LabelViewport(
                            zoom = z,
                            centerX = (x + 0.5) * TILE_SIZE,
                            centerY = (y + 0.5) * TILE_SIZE,
                            widthPx = TILE_SIZE,
                            heightPx = TILE_SIZE,
                            rotationDeg = 0.0,
                        )
                        labels = renderer.labelPass?.prepareNow(view)?.placed?.size ?: 0
                    } catch (e: Throwable) {
                        error = "${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}".replace(',', ';')
                    }
                    csv.append("$z,$x,$y,${"%.1f".format(ms)},$result,$labels,$error\n")
                    if ((i + 1) % 100 == 0) {
                        println("[sweep] ${i + 1} / ${tiles.size}")
                    }
                }
            }
        }
        File(out, "results.csv").writeText(csv.toString())
    }

    private companion object {

        const val TILE_SIZE = 512
    }
}
