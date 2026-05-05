package com.asamm.garminimg.render

import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end render test: open Monaco, render to PNG via [AwtRenderTarget],
 * write to disk, sanity-check the output.
 *
 * The first run produces an artifact under `lib/build/test-output/render/`
 * for human review. Once a render is approved, copy it into
 * `lib/src/jvmTest/resources/fixtures/golden/` and the
 * `goldenDiffMatchesMonaco` test (next sub-phase) compares against it
 * pixel-by-pixel.
 */
class MonacoRenderTest {

    @Test
    fun rendersOtmMonacoToPng() {
        val img = openMonaco()

        val widthPx = 1024
        val heightPx = 1024
        // Render the full TRE bbox (which extends past Monaco into the French
        // Riviera, per the otm-monaco fixture).
        val viewport = Viewport.ofTreBounds(
            widthPx = widthPx,
            heightPx = heightPx,
            northBoundary = img.tre.header.northBoundary,
            eastBoundary = img.tre.header.eastBoundary,
            southBoundary = img.tre.header.southBoundary,
            westBoundary = img.tre.header.westBoundary,
        )

        val target = AwtRenderTarget(widthPx, heightPx)
        ImgRenderer(img, resolverFor(img)).render(viewport, target)

        // Save the artifact for human review. The build dir is in .gitignore
        // so this never lands in the repo.
        val outDir = File("build/test-output/render").apply { mkdirs() }
        val outFile = File(outDir, "otm-monaco-treboundsfull.png")
        target.writePng(outFile)

        // Also render a tight viewport covering Monaco proper only.
        val tightViewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.40),
            maxLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.46),
            minLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.72),
            maxLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.79),
        )
        val tightTarget = AwtRenderTarget(widthPx, heightPx)
        ImgRenderer(img, resolverFor(img)).render(tightViewport, tightTarget)
        tightTarget.writePng(File(outDir, "otm-monaco-tight.png"))
        println(
            "[render] line-label placement: curved=${tightTarget.curvedPlacements} " +
                "shortPathFallback=${tightTarget.shortPathFallbacks}",
        )

        // Diagnostic: render the user-supplied Nice CP viewport so we can
        // visually compare against their QMapShack screenshot. Same scale
        // as the user (~6 km wide), 2x resolution so labels are legible.
        val niceVp = Viewport(
            widthPx = 2000,
            heightPx = 600,
            minLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.2423),
            maxLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.2819),
            minLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.6993),
            maxLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.7111),
        )
        val niceTarget = AwtRenderTarget(niceVp.widthPx, niceVp.heightPx)
        ImgRenderer(img, resolverFor(img)).render(niceVp, niceTarget)
        niceTarget.writePng(File(outDir, "otm-monaco-nice-cp.png"))

        // Also dump every (kept vs dropped) label string in this view so
        // we can visually audit the filter without zooming into a PNG.
        val labelLevel = ImgRenderer.pickRenderLevelWithCoverage(niceVp, img)
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        for (obj in img.readMapInBounds(
            minLong = niceVp.minLon,
            maxLong = niceVp.maxLon,
            minLat = niceVp.minLat,
            maxLat = niceVp.maxLat,
            levelFilter = labelLevel,
        )) {
            val label = when (obj) {
                is com.asamm.garminimg.model.MapObject.Point -> obj.label
                is com.asamm.garminimg.model.MapObject.PolyShape -> obj.label
            }
            if (label.isNullOrEmpty()) {
                continue
            }
            if (ImgRenderer.isDisplayableLabel(label)) {
                kept.add(label)
            } else {
                dropped.add(label)
            }
        }
        val auditFile = File(outDir, "nice-cp-labels.txt")
        auditFile.bufferedWriter().use { w ->
            w.write("=== KEPT (${kept.size}) ===\n")
            kept.sorted().forEach { w.write("+ $it\n") }
            w.write("=== DROPPED (${dropped.size}) ===\n")
            dropped.sorted().forEach { w.write("- $it\n") }
        }
        println("[label audit] wrote ${auditFile.absolutePath}")

        assertTrue(outFile.exists(), "expected PNG was not written")
        assertTrue(outFile.length() > 0, "PNG is empty")
        assertEquals(widthPx, target.image.width)
        assertEquals(heightPx, target.image.height)

        // Sanity: the rendered image isn't entirely the background colour. If
        // it were, no objects got drawn (parser misalignment, broken
        // projection, etc.) — fail loudly.
        val sampledPixels = sampleNonBackgroundPixels(target.image)
        assertTrue(
            sampledPixels >= 64,
            "rendered image looks empty (only $sampledPixels non-background pixels in 1024-pixel sample)",
        )

        println("[render] Wrote ${outFile.absolutePath} (${Files.size(outFile.toPath())} bytes)")
    }

    /**
     * Compares a freshly-rendered tight Monaco view against the committed
     * golden PNG under `lib/src/jvmTest/resources/fixtures/golden/`.
     *
     * Tolerance: anti-aliasing on AWT can drift a few percent of pixels by
     * 1-2 channel values across JDK versions / OSes. We allow up to 1% of
     * pixels to differ by more than 8 channel values; anything past that
     * is a real regression.
     *
     * If you intentionally changed the renderer/styles and need to update
     * the golden, copy the fresh `build/test-output/render/otm-monaco-tight.png`
     * over `lib/src/jvmTest/resources/fixtures/golden/otm-monaco-tight.png`
     * and inspect the diff visually before committing.
     */
    @Test
    fun tightMonacoMatchesGoldenPng() {
        val img = openMonaco()
        val widthPx = 1024
        val heightPx = 1024
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.40),
            maxLon = com.asamm.garminimg.coord.CoordUtils.fromWGS84(7.46),
            minLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.72),
            maxLat = com.asamm.garminimg.coord.CoordUtils.fromWGS84(43.79),
        )
        // Anti-aliasing off so the render is byte-deterministic across JDKs.
        val target = AwtRenderTarget(widthPx, heightPx, antiAliased = false)
        ImgRenderer(img, resolverFor(img)).render(viewport, target)

        val golden = GoldenPngDiff.loadFromClasspath("fixtures/golden/otm-monaco-tight.png")
        if (golden == null) {
            // First run — no golden committed yet. Save the candidate and
            // print instructions instead of failing.
            val candidate = GoldenPngDiff.saveCandidate(target.image, "otm-monaco-tight")
            error(
                "No golden image committed yet. Inspect $candidate, copy to " +
                    "lib/src/jvmTest/resources/fixtures/golden/otm-monaco-tight.png " +
                    "if it looks right.",
            )
        }
        val diff = GoldenPngDiff.compare(target.image, golden, perChannelTolerance = 8)
        if (diff.differingPixelFraction > 0.01) {
            // Save the candidate so a human can compare side-by-side.
            val candidate = GoldenPngDiff.saveCandidate(target.image, "otm-monaco-tight")
            error("Render diverged from golden: $diff. Candidate written to $candidate.")
        }
        println("[render] golden diff: $diff")
    }

    private fun openMonaco(): ImgFile {
        val bytes = MonacoRenderTest::class.java.classLoader
            ?.getResourceAsStream("fixtures/otm-monaco/otm-monaco.img")
            ?.use { it.readBytes() } ?: error("Monaco fixture missing")
        return ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
    }

    /**
     * Returns the right [StyleResolver] for the given IMG: TYP-aware when
     * the IMG has TYP styles, internal defaults otherwise.
     */
    private fun resolverFor(img: ImgFile): StyleResolver {
        return img.typStyles?.let { StyleResolver.typAware(it) }
            ?: StyleResolver.internalDefaults()
    }

    /**
     * Counts how many of a 32x32 evenly-spaced grid of pixels in the rendered
     * image are NOT the background colour — a quick "did anything draw"
     * sanity check.
     */
    private fun sampleNonBackgroundPixels(img: java.awt.image.BufferedImage): Int {
        val backgroundArgb = img.getRGB(0, 0)
        var nonBg = 0
        val step = (img.width / 32).coerceAtLeast(1)
        var y = 0
        while (y < img.height) {
            var x = 0
            while (x < img.width) {
                if (img.getRGB(x, y) != backgroundArgb) {
                    nonBg++
                }
                x += step
            }
            y += step
        }
        return nonBg
    }
}
