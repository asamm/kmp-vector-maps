/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every reference tile of the fixtures and checks it against MapLibre's render without
 * symbols (`dev/mvt/reference`); writes ours, MapLibre's and the server's side by side into
 * `build/renders`.
 */
class AwtRenderTest {

    private val mvtDir = File(System.getProperty("fixturesDir"), "mvt")
    private val outDir = File(System.getProperty("outDir"))

    @Test
    fun rendersReferenceTiles() {
        val style = Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())
        val renderer = TileRenderer(style, maxDataZoom = 14) { z, x, y ->
            File(mvtDir, "tiles/planet/$z/$x/$y.pbf")
                .takeIf { it.isFile }
                ?.let { VectorTile.decode(it.readBytes()) }
        }
        val sprite = AwtSprite(
            SpriteIndex.parse(File(mvtDir, "styles/hike_bike/sprite.json").readText()),
            ImageIO.read(File(mvtDir, "styles/hike_bike/sprite.png")),
        )
        val references = File(mvtDir, "reference/server/hike_bike").walk()
            .filter { it.extension == "png" }
            .toList()
        assertTrue(references.isNotEmpty())
        outDir.mkdirs()
        // every tile is rendered and written before the check, so a failure shows them all
        val failures = ArrayList<String>()
        for (ref in references) {
            val y = ref.nameWithoutExtension.toInt()
            val x = ref.parentFile.name.toInt()
            val z = ref.parentFile.parentFile.name.toInt()
            val image = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
            val target = AwtMvtRenderTarget(image, sprite)
            val start = System.nanoTime()
            assertTrue(renderer.render(z, x, y, target) { false }, "data for $z/$x/$y")
            val ms = (System.nanoTime() - start) / 1e6
            target.dispose()
            val server = ImageIO.read(ref)
            // MapLibre without symbols and elevation, what this renderer draws so far
            val base = ImageIO.read(File(mvtDir, "reference/maplibre/base/$z/$x/$y.png"))
            val strip = BufferedImage(512 * 3, 512, BufferedImage.TYPE_INT_ARGB)
            strip.createGraphics().apply {
                drawImage(image, 0, 0, null)
                drawImage(base, 512, 0, null)
                drawImage(server, 1024, 0, null)
                dispose()
            }
            ImageIO.write(strip, "png", File(outDir, "${z}_${x}_$y.png"))
            val (mean, offShare) = difference(image, base)
            val result = "%s in %.1f ms, vs MapLibre mean %.1f, %.1f %% off".format("$z/$x/$y", ms, mean, offShare)
            println("rendered $result")
            if (mean > MAX_MEAN
                || offShare > MAX_OFF_PERCENT
            ) {
                failures.add(result)
            }
        }
        assertTrue(failures.isEmpty(), "over the limits: $failures")
    }

    // mean absolute RGB difference (0-255) and the percentage of pixels off by more than 32
    private fun difference(a: BufferedImage, b: BufferedImage): Pair<Double, Double> {
        var sum = 0L
        var off = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                var worst = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = kotlin.math.abs((p ushr shift and 0xff) - (q ushr shift and 0xff))
                    sum += d
                    worst = maxOf(worst, d)
                }
                if (worst > 32) {
                    off++
                }
            }
        }
        val n = a.width * a.height
        return sum / (n * 3.0) to off * 100.0 / n
    }

    private companion object {

        // the spike's worst tile is at 2.6 and 4.1 % (docs/mvt.md)
        const val MAX_MEAN = 4.0
        const val MAX_OFF_PERCENT = 6.0
    }
}
