/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden

import com.asamm.vectormaps.mvt.golden.MvtFixtures.SIZE
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders every reference tile of the fixtures and checks it against MapLibre's render without
 * symbols (`dev/mvt/reference`); writes ours, MapLibre's and the server's side by side into
 * `build/renders`.
 */
class GoldenRenderTest {

    @Test
    fun rendersReferenceTiles() {
        val renderer = MvtFixtures.renderer()
        val references = MvtFixtures.references()
        assertEquals(MvtFixtures.TILES, references.size, "reference tiles in ${MvtFixtures.mvtDir}")
        // every tile is rendered and written before the check, so a failure shows them all
        val failures = ArrayList<String>()
        for (ref in references) {
            // MapLibre without symbols and elevation, what this renderer draws
            val baseFile = ref.maplibre("base")
            if (!baseFile.isFile) {
                failures.add("${ref.name}: no MapLibre reference $baseFile")
                continue
            }
            val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
            val target = AwtMvtRenderTarget(image, MvtFixtures.sprite)
            val start = System.nanoTime()
            val drawn = renderer.render(ref.z, ref.x, ref.y, target) { false }
            val ms = (System.nanoTime() - start) / 1e6
            target.dispose()
            if (!drawn) {
                failures.add("${ref.name}: no tile data")
                continue
            }
            val base = ImageIO.read(baseFile)
            MvtFixtures.writeStrip(ref.name, image, base, ImageIO.read(ref.server))
            if (base.width != SIZE
                || base.height != SIZE
            ) {
                failures.add("${ref.name}: MapLibre reference ${base.width}x${base.height}")
                continue
            }
            val (mean, offShare) = MvtFixtures.difference(image, base)
            val result = "%s in %.1f ms, vs MapLibre mean %.1f, %.1f %% off".format(ref.name, ms, mean, offShare)
            println("rendered $result")
            if (mean > MAX_MEAN
                || offShare > MAX_OFF_PERCENT
            ) {
                failures.add(result)
            }
        }
        assertEquals(emptySet(), renderer.failedLayers, "layers that threw")
        assertTrue(failures.isEmpty(), "failed: $failures")
    }

    private companion object {

        // the spike's worst tile is at 2.6 and 4.1 % (docs/mvt.md)
        const val MAX_MEAN = 4.0
        const val MAX_OFF_PERCENT = 6.0
    }
}
