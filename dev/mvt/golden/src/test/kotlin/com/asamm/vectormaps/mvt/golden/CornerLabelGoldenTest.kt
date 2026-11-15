/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden

import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.mvt.golden.MvtFixtures.SIZE
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The label pass on a screen centred where four fixture tiles meet (`tiles/corner`, Pec pod
 * Sněžkou at z14), straight and turned by [TURNED_DEG]: the labels the four tiles store are placed
 * together, none twice, and checked as a dump of every placed label (text or icon, world position,
 * direction) against `golden/labels/corner*.txt` and as a picture against the PNGs beside it. A
 * missing or differing golden is written as a candidate to `build/renders/candidates`.
 */
class CornerLabelGoldenTest {

    @Test
    fun placesLabelsOfFourTilesTogether() {
        val failures = ArrayList<String>()
        MvtFixtures.renderer(tiles = "tiles/corner.pmtiles").use { renderer ->
            // the four tiles, rendered as the viewer does, store their labels for the pass
            val mosaic = BufferedImage(SIZE * 2, SIZE * 2, BufferedImage.TYPE_INT_ARGB)
            mosaic.createGraphics().apply {
                for (dx in 0..1) {
                    for (dy in 0..1) {
                        val tile = requireNotNull(MvtFixtures.render(renderer, ZOOM, CORNER_X - 1 + dx, CORNER_Y - 1 + dy)) {
                            "no data for tile ${CORNER_X - 1 + dx}, ${CORNER_Y - 1 + dy}"
                        }
                        drawImage(tile, SIZE * dx, SIZE * dy, null)
                    }
                }
                dispose()
            }
            for (rotationDeg in listOf(0.0, TURNED_DEG)) {
                val name = if (rotationDeg == 0.0) "corner" else "corner_r${rotationDeg.toInt()}"
                val view = LabelViewport(
                    zoom = ZOOM,
                    centerX = CORNER_X.toDouble() * SIZE,
                    centerY = CORNER_Y.toDouble() * SIZE,
                    widthPx = SIZE,
                    heightPx = SIZE,
                    rotationDeg = rotationDeg,
                )
                val pass = renderer.labelPass
                val prepared = pass.prepareNow(view)
                val labels = pass.newTarget(SIZE, SIZE) as AwtMvtRenderTarget
                pass.draw(labels, view, prepared)
                labels.dispose()
                val dump = dump(prepared)
                assertEquals(dump.size, dump.toSet().size, "$name: a label placed twice")
                check(name = name, dump = dump, failures = failures)
                // the mosaic's centre, the corner, at the screen's
                val image = MvtFixtures.screen(
                    map = mosaic,
                    mapX = -SIZE / 2,
                    mapY = -SIZE / 2,
                    rotationDeg = rotationDeg,
                    labels = labels.image,
                )
                MvtFixtures.writeStrip("labels_$name", image)
                MvtFixtures.checkLabelGolden(name = name, image = image, failures = failures)
                println("corner $name: ${dump.size} placed")
            }
            assertEquals(emptySet(), renderer.failedLayers, "layers that threw")
        }
        assertEquals(0, failures.size, "failed: $failures")
    }

    // a line per placed label: what it shows, where (world pixels from the corner), which way
    private fun dump(prepared: PreparedLabels): List<String> {
        return prepared.placed.map { placed ->
            val candidate = placed.candidate
            val label = candidate.source as MvtLabel
            val what = candidate.text?.value?.replace('\n', '|') ?: "icon ${label.icon?.name}"
            val x = (candidate.x - CORNER_X.toDouble() * SIZE).roundToLong()
            val y = (candidate.y - CORNER_Y.toDouble() * SIZE).roundToLong()
            "${label.layerId}\t$what\t$x\t$y${if (placed.isReversed) "\treversed" else ""}"
        }.sorted()
    }

    // the same labels at positions within MAX_SHIFT_PX, as font metrics differ a little across
    // systems: each expected line matched by an unused placed one, whatever their sort order
    private fun check(name: String, dump: List<String>, failures: MutableList<String>) {
        val expected = javaClass.classLoader.getResource("golden/labels/$name.txt")?.readText()?.lines()
            ?.filter { it.isNotBlank() }
        val unused = dump.toMutableList()
        val matches = expected != null
            && expected.size == dump.size
            && expected.all { e ->
                val match = unused.firstOrNull { sameLabel(e, it) }
                match != null && unused.remove(match)
            }
        if (!matches) {
            val candidate = File(MvtFixtures.outDir, "candidates/$name.txt")
            candidate.parentFile.mkdirs()
            candidate.writeText(dump.joinToString("\n", postfix = "\n"))
            failures.add("$name: placement differs from the golden dump, candidate $candidate")
        }
    }

    private fun sameLabel(expected: String, actual: String): Boolean {
        val e = expected.split('\t')
        val a = actual.split('\t')
        return e.size == a.size
            && e[0] == a[0]
            && e[1] == a[1]
            && abs(e[2].toLong() - a[2].toLong()) <= MAX_SHIFT_PX
            && abs(e[3].toLong() - a[3].toLong()) <= MAX_SHIFT_PX
            && e.drop(4) == a.drop(4)
    }

    private companion object {

        const val ZOOM = 14

        // the corner the four fixture tiles share, in the centre of Pec pod Sněžkou
        const val CORNER_X = 8908
        const val CORNER_Y = 5507

        const val TURNED_DEG = 30.0

        const val MAX_SHIFT_PX = 2
    }
}
