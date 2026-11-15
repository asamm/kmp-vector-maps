/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden

import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.mvt.golden.MvtFixtures.SIZE
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
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
        MvtFixtures.renderer().use { renderer ->
            val pass = renderer.labelPass
            val failures = ArrayList<String>()
            val references = MvtFixtures.references()
            assertEquals(MvtFixtures.TILES, references.size, "reference tiles in ${MvtFixtures.mvtDir}")
            for (ref in references) {
                // the render also stores the tile's labels for the pass
                val tile = MvtFixtures.render(renderer, ref.z, ref.x, ref.y)
                if (tile == null) {
                    failures.add("${ref.name}: no tile data")
                    continue
                }
                val start = System.nanoTime()
                for (rotationDeg in listOf(0.0, TURNED_DEG)) {
                    val view = LabelViewport(
                        zoom = ref.z,
                        centerX = (ref.x + 0.5) * SIZE,
                        centerY = (ref.y + 0.5) * SIZE,
                        widthPx = SIZE,
                        heightPx = SIZE,
                        rotationDeg = rotationDeg,
                    )
                    val labels = pass.newTarget(SIZE, SIZE) as AwtMvtRenderTarget
                    val prepared = pass.prepareNow(view)
                    pass.draw(labels, view, prepared)
                    labels.dispose()
                    val image = MvtFixtures.screen(map = tile, mapX = 0, mapY = 0, rotationDeg = rotationDeg, labels = labels.image)
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
                    MvtFixtures.checkLabelGolden(name = name, image = image, failures = failures)
                }
            }
            assertEquals(emptySet(), renderer.failedLayers, "layers that threw")
            assertEquals(0, failures.size, "failed: $failures")
        }
    }

    private companion object {

        const val TURNED_DEG = 30.0
    }
}
