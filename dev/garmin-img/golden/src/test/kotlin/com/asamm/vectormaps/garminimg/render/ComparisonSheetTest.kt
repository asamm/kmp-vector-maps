/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
/*
 * Generates side-by-side comparison renders. Drop your
 * QMS / GPSMapEdit / old-Locus screenshots into the same output folder
 * (`dev/garmin-img/golden/build/test-output/comparison/`) for visual auditing.
 */
package com.asamm.vectormaps.garminimg.render

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.utils.coord.CoordUtils
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not really a test — a render harness. Run with:
 *
 *     ./gradlew :dev:garmin-img:golden:test --tests "com.asamm.vectormaps.garminimg.render.ComparisonSheetTest" -Pbench.garmin=true
 *
 * Output PNGs land in `dev/garmin-img/golden/build/test-output/comparison/`. The harness
 * renders the same Nice train-station area at:
 *
 * - several pixel widths (= effective zoom scales),
 * - all three layer-toggle modes (areas-only, lines-only, points-only),
 *
 * each annotated with the picked level and viewport bbox so they can be
 * lined up against external references.
 */
// a sheet to look at, no assertion: runs with the benchmark, `-Pbench.garmin=true`
@EnabledIfSystemProperty(named = "bench.garmin", matches = "true")
class ComparisonSheetTest {

    private val outDir = File("build/test-output/comparison").apply { mkdirs() }

    @Test
    fun generateComparisonSheet() {
        val img = openMonaco()

        // Same area as the user's QMS / GPSMapEdit references — Nice
        // train station rail yard plus surrounding streets.
        val centreLon = 7.2680
        val centreLat = 43.7065

        // Five zoom scales. lonHalfDeg is half the longitude span; height
        // is auto-derived to match Mercator aspect.
        val zooms = listOf(
            "wide" to 0.030, // city overview
            "city" to 0.012, // city blocks
            "block" to 0.006, // street level
            "deep" to 0.003, // single block
            "very-deep" to 0.0015, // individual rails
        )

        val widthPx = 1200
        val rows = mutableListOf<List<File>>()
        for ((zoomName, lonHalf) in zooms) {
            val rowFiles = mutableListOf<File>()
            for (mode in listOf(LayerMode.AREAS_LINES_POINTS, LayerMode.AREAS_ONLY, LayerMode.LINES_ONLY, LayerMode.POINTS_ONLY)) {
                val file = File(outDir, "${zoomName}-${mode.tag}.png")
                renderOne(
                    img = img,
                    file = file,
                    widthPx = widthPx,
                    centreLon = centreLon,
                    centreLat = centreLat,
                    lonHalfDeg = lonHalf,
                    mode = mode,
                    zoomName = zoomName,
                )
                rowFiles.add(file)
            }
            rows.add(rowFiles)
        }

        // Stitch all rows into one contact-sheet PNG.
        val rowImgs = rows.map { row -> row.map { ImageIO.read(it) } }
        val cellH = rowImgs.firstOrNull()?.firstOrNull()?.height ?: return
        val sheetH = rowImgs.sumOf { it.maxOf { im -> im.height } } + 24 * rows.size
        val sheetW = widthPx * 4 + 24 * 5
        val sheet = BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, sheetW, sheetH)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        var y = 12
        for (row in rowImgs) {
            var x = 24
            var rowH = 0
            for (im in row) {
                g.drawImage(im, x, y, null)
                rowH = maxOf(rowH, im.height)
                x += im.width + 24
            }
            y += rowH + 24
        }
        g.dispose()
        ImageIO.write(sheet, "PNG", File(outDir, "comparison-sheet.png"))
        println("[comparison] wrote ${File(outDir, "comparison-sheet.png").absolutePath}")
    }

    private fun renderOne(
        img: ImgFile,
        file: File,
        widthPx: Int,
        centreLon: Double,
        centreLat: Double,
        lonHalfDeg: Double,
        mode: LayerMode,
        zoomName: String,
    ) {
        val cosLat = kotlin.math.cos(centreLat * kotlin.math.PI / 180.0)
        // Latitude span derived from longitude span × cos(lat) × aspect; we
        // pick a 4:3-ish aspect.
        val latHalfDeg = lonHalfDeg * cosLat * 0.6
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = (widthPx * latHalfDeg / lonHalfDeg).toInt().coerceAtLeast(200),
            minLon = CoordUtils.fromWGS84(centreLon - lonHalfDeg),
            maxLon = CoordUtils.fromWGS84(centreLon + lonHalfDeg),
            minLat = CoordUtils.fromWGS84(centreLat - latHalfDeg),
            maxLat = CoordUtils.fromWGS84(centreLat + latHalfDeg),
        )
        val target = AwtRenderTarget(viewport.widthPx, viewport.heightPx)
        val resolver = img.typStyles
            ?.let { StyleResolver.typAware(it) }
            ?: StyleResolver.internalDefaults()
        val pickedLevel = ImgRenderer.pickRenderLevelWithCoverage(viewport, img)
        ImgRenderer(img, resolver).render(
            viewport = viewport,
            target = target,
            objectKindFilter = mode.kindFilter,
        )

        // Annotate top-left with metadata.
        val image = target.image
        val gg = image.createGraphics()
        gg.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f)
        gg.color = Color.WHITE
        gg.fillRect(0, 0, 320, 38)
        gg.composite = AlphaComposite.SrcOver
        gg.color = Color.BLACK
        gg.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        gg.drawString(
            "zoom=$zoomName  mode=${mode.tag}  level=${pickedLevel ?: "?"}",
            6, 16,
        )
        gg.drawString(
            "lon=${"%.4f".format(centreLon - lonHalfDeg)}..${"%.4f".format(centreLon + lonHalfDeg)}",
            6, 32,
        )
        gg.dispose()
        ImageIO.write(image, "PNG", file)
    }

    private fun openMonaco(): ImgFile {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
        return ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
    }

    private enum class LayerMode(val tag: String, val kindFilter: Int) {
        AREAS_LINES_POINTS(
            "all",
            com.asamm.vectormaps.garminimg.model.ObjectKind.ALL,
        ),
        AREAS_ONLY(
            "areas",
            com.asamm.vectormaps.garminimg.model.ObjectKind.POLYGON or
                com.asamm.vectormaps.garminimg.model.ObjectKind.EXTENDED_POLYGON,
        ),
        LINES_ONLY(
            "lines",
            com.asamm.vectormaps.garminimg.model.ObjectKind.POLYLINE or
                com.asamm.vectormaps.garminimg.model.ObjectKind.EXTENDED_POLYLINE,
        ),
        POINTS_ONLY(
            "points",
            com.asamm.vectormaps.garminimg.model.ObjectKind.POINT or
                com.asamm.vectormaps.garminimg.model.ObjectKind.INDEXED_POINT or
                com.asamm.vectormaps.garminimg.model.ObjectKind.EXTENDED_POINT,
        ),
    }
}
