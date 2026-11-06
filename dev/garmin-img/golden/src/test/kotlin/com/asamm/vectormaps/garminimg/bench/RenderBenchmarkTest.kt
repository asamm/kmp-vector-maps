/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.bench

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget
import com.asamm.vectormaps.garminimg.render.ImgRenderer
import com.asamm.vectormaps.garminimg.render.StyleResolver
import com.asamm.vectormaps.garminimg.render.Viewport
import com.asamm.vectormaps.garminimg.utils.coord.CoordUtils
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sinh
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.Test

/**
 * Per-phase wall-clock benchmark for a single tile render, on request only. Default
 * config renders a Monaco-z17 tile from the bundled fixture; pass `bench.garmin.*`
 * Gradle properties to point at a different IMG / tile / size:
 *
 * ```
 * ./gradlew :dev:garmin-img:golden:test --tests "*.RenderBenchmarkTest" -Pbench.garmin=true \
 *   -Pbench.garmin.img="<path/to/map.img>" \
 *   -Pbench.garmin.x=71381 -Pbench.garmin.y=45019 -Pbench.garmin.z=17 \
 *   -Pbench.garmin.w=512 -Pbench.garmin.h=512 -Pbench.garmin.iters=10
 * ```
 *
 * Gradle's `--tests` only pre-warms the JIT a little, so two warm-up
 * renders run before the measured iterations. Output is plain `println`
 * so it shows up in the test stdout regardless of the test report
 * format.
 *
 * The benchmark does NOT assert anything — it just prints. Treat the
 * test as a tool to run on demand, compare runs after changes, and
 * point at suspected hot regions with a real profiler (e.g. JFR via
 * `-XX:StartFlightRecording=...`).
 */
@EnabledIfSystemProperty(named = "bench.garmin", matches = "true")
class RenderBenchmarkTest {

    @Test
    fun benchmarkSingleTile() {
        val imgPath = System.getProperty("bench.garmin.img")
        // Defaults pick a Monaco-z17 tile so a no-args run still hits
        // populated geography in the bundled fixture.
        val tileX = System.getProperty("bench.garmin.x", "68227").toInt()
        val tileY = System.getProperty("bench.garmin.y", "47776").toInt()
        val zoom = System.getProperty("bench.garmin.z", "17").toInt()
        val widthPx = System.getProperty("bench.garmin.w", "512").toInt()
        val heightPx = System.getProperty("bench.garmin.h", "512").toInt()
        val iterations = System.getProperty("bench.garmin.iters", "10").toInt()
        val antiAliased = System.getProperty("bench.garmin.aa", "true").toBoolean()

        println("[bench] ── config ──────────────────────────────────────────")
        println("[bench]   img      = ${imgPath ?: "(bundled Monaco fixture)"}")
        println("[bench]   tile     = ($tileX, $tileY) @ z$zoom")
        println("[bench]   tile px  = ${widthPx}x${heightPx}")
        println("[bench]   AA       = $antiAliased")
        println("[bench]   iters    = $iterations (+ 2 warm-up)")

        // ── open + parse ────────────────────────────────────────────
        val t0 = System.nanoTime()
        val img = openImg(imgPath)
        val t1 = System.nanoTime()
        val openMs = (t1 - t0) / 1_000_000.0
        println("[bench]")
        println("[bench] ── one-time costs ──────────────────────────────────")
        println("[bench]   open + parse:                %7.1f ms".format(openMs))

        val rt0 = System.nanoTime()
        val resolver = img.typStyles?.let { StyleResolver.typAware(it) }
            ?: StyleResolver.internalDefaults()
        val rt1 = System.nanoTime()
        println("[bench]   StyleResolver build:         %7.1f ms".format((rt1 - rt0) / 1_000_000.0))
        println("[bench]   typStyles present:           ${img.typStyles != null}")

        // ── viewport ────────────────────────────────────────────────
        val bbox = tileToBboxDeg(tileX, tileY, zoom)
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = CoordUtils.fromWGS84(bbox[0]),
            maxLon = CoordUtils.fromWGS84(bbox[2]),
            minLat = CoordUtils.fromWGS84(bbox[1]),
            maxLat = CoordUtils.fromWGS84(bbox[3]),
        )
        val pickedLevel = ImgRenderer.pickRenderLevelWithCoverage(viewport, img)
        println("[bench]   viewport WGS84: [%.5f, %.5f → %.5f, %.5f]".format(
            bbox[0], bbox[1], bbox[2], bbox[3],
        ))
        println("[bench]   picked TRE level:            $pickedLevel")

        // ── warm-up ─────────────────────────────────────────────────
        repeat(2) {
            val warm = AwtRenderTarget(widthPx, heightPx, antiAliased = antiAliased)
            ImgRenderer(img, resolver).render(viewport, warm)
        }

        // ── parse-only (no draw) ────────────────────────────────────
        val parseTimes = LongArray(iterations)
        var objectCount = 0
        var polygonCountP = 0
        var polylineCountP = 0
        var pointCountP = 0
        for (i in 0 until iterations) {
            val pt0 = System.nanoTime()
            var total = 0
            var polys = 0
            var lines = 0
            var points = 0
            for (obj in img.readMapInBounds(
                minLong = viewport.minLon, maxLong = viewport.maxLon,
                minLat = viewport.minLat, maxLat = viewport.maxLat,
                objectKindFilter = ObjectKind.ALL,
                levelFilter = pickedLevel,
            )) {
                total++
                when (obj) {
                    is com.asamm.vectormaps.garminimg.model.MapObject.Point -> points++
                    is com.asamm.vectormaps.garminimg.model.MapObject.PolyShape ->
                        if (obj.closed) polys++ else lines++
                }
            }
            parseTimes[i] = System.nanoTime() - pt0
            if (i == 0) {
                objectCount = total
                polygonCountP = polys
                polylineCountP = lines
                pointCountP = points
            }
        }

        // ── full-render with TimingRenderTarget ─────────────────────
        val renderTimes = LongArray(iterations)
        var firstTimingBreakdown: TimingRenderTarget? = null
        for (i in 0 until iterations) {
            val target = AwtRenderTarget(widthPx, heightPx, antiAliased = antiAliased)
            val wrap = TimingRenderTarget(target)
            val tt0 = System.nanoTime()
            ImgRenderer(img, resolver).render(viewport, wrap)
            renderTimes[i] = System.nanoTime() - tt0
            if (i == 0) {
                firstTimingBreakdown = wrap
            }
        }
        img.close()

        // ── output ──────────────────────────────────────────────────
        println("[bench]")
        println("[bench] ── per-tile work ───────────────────────────────────")
        println("[bench]   objects in viewport:         $objectCount " +
            "(polys=$polygonCountP, polylines=$polylineCountP, points=$pointCountP)")

        println("[bench]")
        println("[bench] readMapInBounds (parse only, no draw):")
        printStats("[bench]   ", parseTimes)

        println("[bench]")
        println("[bench] full ImgRenderer.render() (parse + style + project + draw; labels measured, not drawn):")
        printStats("[bench]   ", renderTimes)

        firstTimingBreakdown?.let { t ->
            println("[bench]")
            println("[bench] iteration 1 — per-method breakdown (TimingRenderTarget):")
            fun ms(n: Long): String = "%7.2f ms".format(n / 1_000_000.0)
            println("[bench]   beginFrame + endFrame:        " + ms(t.beginEndNanos))
            println("[bench]   drawPolygon (${"%5d".format(t.polygonCount)}):           " + ms(t.polygonNanos))
            println("[bench]   drawPolylineBorder (${"%5d".format(t.polylineBorderCount)}):    " + ms(t.polylineBorderNanos))
            println("[bench]   drawPolylineStroke (${"%5d".format(t.polylineStrokeCount)}):    " + ms(t.polylineStrokeNanos))
            println("[bench]   drawPoint (${"%5d".format(t.pointCount)}):             " + ms(t.pointNanos))
            println("[bench]   drawBitmap (${"%5d".format(t.bitmapCount)}):            " + ms(t.bitmapNanos))
            println("[bench]   drawText (${"%5d".format(t.textCount)}):              " + ms(t.textNanos))
            println("[bench]   drawTextOnPath (${"%5d".format(t.textOnPathCount)}):        " + ms(t.textOnPathNanos))
            println("[bench]   ─ sum (draw only):            " + ms(t.drawNanos))
        }
    }

    /** Opens the IMG either from the property-supplied path or from the bundled Monaco fixture. */
    private fun openImg(imgPath: String?): ImgFile {
        return if (imgPath != null) {
            ImgFile.open { FileImgRandomAccessReader(File(imgPath)) }
        } else {
            val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
            ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
        }
    }

    /**
     * Converts a slippy-map tile (x, y, z) to its WGS84 bbox in degrees:
     * `[west, south, east, north]`. Matches the convention OSM /
     * Locus uses for tile-server zoom.
     */
    private fun tileToBboxDeg(x: Int, y: Int, z: Int): DoubleArray {
        val n = 2.0.pow(z.toDouble())
        val west = x / n * 360.0 - 180.0
        val east = (x + 1) / n * 360.0 - 180.0
        val north = atan(sinh(PI * (1 - 2 * y / n))) * 180.0 / PI
        val south = atan(sinh(PI * (1 - 2 * (y + 1) / n))) * 180.0 / PI
        return doubleArrayOf(west, south, east, north)
    }

    private fun printStats(prefix: String, samples: LongArray) {
        val ms = samples.map { it / 1_000_000.0 }.sorted()
        val avg = ms.average()
        val min = ms.first()
        val max = ms.last()
        val median = ms[ms.size / 2]
        val p90 = ms[(ms.size * 9 / 10).coerceAtMost(ms.size - 1)]
        println(prefix + "avg %7.1f  min %7.1f  median %7.1f  p90 %7.1f  max %7.1f  ms (n=%d)".format(
            avg, min, median, p90, max, samples.size,
        ))
    }
}
