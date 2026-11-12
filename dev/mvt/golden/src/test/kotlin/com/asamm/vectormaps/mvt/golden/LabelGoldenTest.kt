/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden

import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.TileLabelStore
import com.asamm.vectormaps.mvt.driver.MvtLabelPass
import com.asamm.vectormaps.mvt.golden.MvtFixtures.SIZE
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders every reference tile with the label pass over it, on a screen the size of the tile and
 * on one turned by [TURNED_DEG], and checks both against the committed `golden/labels` PNGs, drawn
 * with the bundled font; the difference to MapLibre's render with labels (`dev/mvt/reference`) is
 * reported, not checked, as MapLibre's own fonts differ. A missing or differing golden is written
 * as a candidate to `build/renders/candidates`; copy it over the golden once it looks right.
 */
class LabelGoldenTest {

    @Test
    fun drawsLabelsOverReferenceTiles() {
        val renderer = MvtFixtures.renderer()
        val layout = MvtFixtures.layout()
        val store = TileLabelStore<MvtLabel>(capacity = 16)
        val pass = MvtLabelPass(store, tileSize = SIZE)
        val failures = ArrayList<String>()
        val references = MvtFixtures.references()
        assertEquals(MvtFixtures.TILES, references.size, "reference tiles in ${MvtFixtures.mvtDir}")
        for (ref in references) {
            val tile = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
            val tileTarget = AwtMvtRenderTarget(tile, MvtFixtures.sprite)
            renderer.render(ref.z, ref.x, ref.y, tileTarget) { false }
            tileTarget.dispose()
            val start = System.nanoTime()
            store.store(TileRequest(x = ref.x, y = ref.y, zoom = ref.z), layout.labels(ref.z, ref.x, ref.y, SIZE))
            for (rotationDeg in listOf(0.0, TURNED_DEG)) {
                val view = LabelViewport(
                    zoom = ref.z,
                    centerX = (ref.x + 0.5) * SIZE,
                    centerY = (ref.y + 0.5) * SIZE,
                    widthPx = SIZE,
                    heightPx = SIZE,
                    rotationDeg = rotationDeg,
                )
                val image = screen(tile, rotationDeg)
                val target = AwtMvtRenderTarget(image, MvtFixtures.sprite, MvtFixtures.fonts)
                val prepared = pass.prepareNow(view)
                pass.draw(target, view, prepared)
                target.dispose()
                val name = if (rotationDeg == 0.0) ref.name else "${ref.name}_r${rotationDeg.toInt()}"
                if (rotationDeg == 0.0) {
                    val ms = (System.nanoTime() - start) / 1e6
                    val maplibre = ref.maplibre("labels").takeIf { it.isFile }?.let { ImageIO.read(it) }
                        ?.takeIf { it.width == SIZE && it.height == SIZE }
                    MvtFixtures.writeStrip("labels_$name", image, maplibre)
                    val vsMaplibre = maplibre?.let {
                        val (mean, off) = MvtFixtures.difference(image, it)
                        "vs MapLibre mean %.1f, %.1f %% off".format(mean, off)
                    }
                    println("labelled $name: ${prepared.placed.size} placed in ${"%.1f".format(ms)} ms, $vsMaplibre")
                } else {
                    MvtFixtures.writeStrip("labels_$name", image)
                }
                val expected = javaClass.classLoader.getResource("golden/labels/$name.png")
                    ?.let { ImageIO.read(it) }
                    ?.takeIf { it.width == SIZE && it.height == SIZE }
                val (_, off) = expected?.let { MvtFixtures.difference(image, it, tolerance = GOLDEN_TOLERANCE) }
                    ?: (0.0 to 100.0)
                if (off > MAX_GOLDEN_OFF_PERCENT) {
                    val candidate = File(MvtFixtures.outDir, "candidates/$name.png")
                    candidate.parentFile.mkdirs()
                    ImageIO.write(image, "png", candidate)
                    failures.add("$name: %.2f %% off golden, candidate $candidate".format(off))
                }
            }
        }
        pass.close()
        assertEquals(emptySet(), layout.failedLayers, "symbol layers that threw")
        assertEquals(0, failures.size, "failed: $failures")
    }

    // the tile on a white screen turned clockwise by `rotationDeg` about its centre, as the label
    // pass turns the map
    private fun screen(tile: BufferedImage, rotationDeg: Double): BufferedImage {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            color = java.awt.Color.WHITE
            fillRect(0, 0, SIZE, SIZE)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            rotate(Math.toRadians(rotationDeg), SIZE / 2.0, SIZE / 2.0)
            drawImage(tile, 0, 0, null)
            dispose()
        }
        return image
    }

    private companion object {

        const val TURNED_DEG = 30.0

        // antialiasing and font rasterising noise across systems
        const val GOLDEN_TOLERANCE = 8
        const val MAX_GOLDEN_OFF_PERCENT = 0.5
    }
}
