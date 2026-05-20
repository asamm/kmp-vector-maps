package com.asamm.garminimg.render

import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.utils.io.ByteArrayImgRandomAccessReader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

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
            minLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.40),
            maxLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.46),
            minLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.72),
            maxLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.79),
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
            minLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2423),
            maxLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2819),
            minLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.6993),
            maxLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.7111),
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
            kept.sorted().forEach { w.write("+ ${dump(it)}\n") }
            w.write("=== DROPPED (${dropped.size}) ===\n")
            dropped.sorted().forEach { w.write("- ${dump(it)}\n") }
        }
        println("[label audit] wrote ${auditFile.absolutePath}")

        // Locate "Pompiers de Nice" and "Distilleries Idéales" by scanning
        // every Point in the IMG; print their WGS84 coords so we can size a
        // matching deep-zoom viewport.
        val targets = setOf("Pompiers de Nice", "Distilleries Idéales", "Nice-1")
        val hits = mutableListOf<Triple<String, Double, Double>>()
        for (obj in img.readMap()) {
            if (obj is com.asamm.garminimg.model.MapObject.Point) {
                val l = obj.label?.trim() ?: continue
                if (targets.any { l.contains(it) }) {
                    hits.add(Triple(
                        l,
                        com.asamm.garminimg.utils.coord.CoordUtils.toWGS84(obj.longitude),
                        com.asamm.garminimg.utils.coord.CoordUtils.toWGS84(obj.latitude),
                    ))
                }
            }
        }
        for (h in hits.take(20)) {
            println("[poi] ${h.first}  lon=${h.second}  lat=${h.third}")
        }

        // Render the deep-zoom area from the QMS screenshot — railway corridor
        // through Nice-Ville station, ~1.2km wide.
        val zoomVp = Viewport(
            widthPx = 2000,
            heightPx = 800,
            minLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2595),
            maxLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2785),
            minLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.7037),
            maxLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.7094),
        )
        val zoomTarget = AwtRenderTarget(zoomVp.widthPx, zoomVp.heightPx)
        ImgRenderer(img, resolverFor(img)).render(zoomVp, zoomTarget)
        zoomTarget.writePng(File(outDir, "otm-monaco-nice-zoom.png"))
        println("[render] wrote nice-zoom.png")

        // Diagnostic: per-level count of objects in the zoom viewport,
        // including a separate column for "looks like a building" polygons
        // (type 0x0A in our InternalStyles → likely the OSM building tag).
        val diagFile = File(outDir, "nice-zoom-diag.txt")
        val diagOut = StringBuilder()
        // Sub-division extended-types stats: how many sub-divs in the
        // zoom viewport carry non-empty extendedPolygon ranges?
        var sdTotal = 0
        var sdWithExtPoly = 0
        var sdWithExtLine = 0
        var sdWithExtPoi = 0
        for (sd in img.tre.tree.all()) {
            sdTotal++
            if (sd.extendedPolygonEnd > sd.extendedPolygonOffset) sdWithExtPoly++
            if (sd.extendedPolylineEnd > sd.extendedPolylineOffset) sdWithExtLine++
            if (sd.extendedPoiEnd > sd.extendedPoiOffset) sdWithExtPoi++
        }
        diagOut.append("[diag] sub-divisions: total=$sdTotal extPoly=$sdWithExtPoly extLine=$sdWithExtLine extPoi=$sdWithExtPoi\n")
        // Per-level sub-division count (incl. inherited).
        val sdPerLevel = HashMap<Int, Int>()
        for (sd in img.tre.tree.all()) {
            sdPerLevel.merge(sd.level, 1) { a, b -> a + b }
        }
        diagOut.append("[diag] sub-div counts per level (raw):\n")
        for ((lvl, c) in sdPerLevel.toSortedMap()) {
            val info = img.tre.levels.levels.firstOrNull { it.level == lvl }
            diagOut.append("[diag]   level=$lvl count=$c bits=${info?.bitsPerCoord} inherited=${info?.inherited}\n")
        }
        val tre7 = img.tre.header.extendedTypes
        diagOut.append("[diag] TRE7 extendedTypes header: $tre7\n")
        // Polygon-section presence in RGN header.
        diagOut.append("[diag] RGN extendedPolygons section: ${img.rgn.header.extendedPolygons}\n")
        diagOut.append("[diag] RGN extendedPolylines section: ${img.rgn.header.extendedPolylines}\n")
        diagOut.append("[diag] RGN extendedPois section: ${img.rgn.header.extendedPois}\n")
        diagOut.append("[diag] per-level object counts in zoom viewport:\n")
        val pickedLevel = ImgRenderer.pickRenderLevelWithCoverage(zoomVp, img)
        val pickedLevelRaw = ImgRenderer.pickRenderLevel(zoomVp, img.tre.levels)
        diagOut.append("[diag] picked level = $pickedLevel (raw pickRenderLevel = $pickedLevelRaw)\n")
        diagOut.append("[diag] zoomVp lonRange = ${zoomVp.maxLon - zoomVp.minLon} pxPerCoord = ${zoomVp.widthPx.toDouble() / (zoomVp.maxLon - zoomVp.minLon)}\n")
        // Sanity: pick level for several reference viewport widths to
        // confirm the new DETAIL_BIAS keeps deep zooms at finest.
        for (pxPerCoord in listOf(0.3, 1.0, 2.26, 5.0, 7.7, 30.0)) {
            val testWidth = (pxPerCoord * (zoomVp.maxLon - zoomVp.minLon)).toInt().coerceAtLeast(1)
            val testVp = Viewport(
                widthPx = testWidth, heightPx = 600,
                minLon = zoomVp.minLon, maxLon = zoomVp.maxLon,
                minLat = zoomVp.minLat, maxLat = zoomVp.maxLat,
            )
            val pl = ImgRenderer.pickRenderLevelWithCoverage(testVp, img)
            diagOut.append("[diag] pxPerCoord=$pxPerCoord → level=$pl\n")
        }
        diagOut.append("[diag] level table:\n")
        for (lvl in img.tre.levels.levels) {
            diagOut.append("[diag]   level=${lvl.level} bits=${lvl.bitsPerCoord} subDivCount=${lvl.subDivisionCount} inherited=${lvl.inherited}\n")
        }
        for (lvl in img.tre.levels.levels.sortedByDescending { it.bitsPerCoord }) {
            if (lvl.inherited) continue
            var polys = 0
            var lines = 0
            var pts = 0
            var buildings = 0
            for (obj in img.readMapInBounds(
                minLong = zoomVp.minLon,
                maxLong = zoomVp.maxLon,
                minLat = zoomVp.minLat,
                maxLat = zoomVp.maxLat,
                levelFilter = lvl.level,
            )) {
                when (obj) {
                    is com.asamm.garminimg.model.MapObject.Point -> pts++
                    is com.asamm.garminimg.model.MapObject.PolyShape -> if (obj.closed) {
                        polys++
                        if (obj.type == 0x0A) buildings++
                    } else {
                        lines++
                    }
                }
            }
            diagOut.append(
                "[diag]   level=${lvl.level} bits=${lvl.bitsPerCoord} " +
                    "polys=$polys (buildings=$buildings) lines=$lines points=$pts\n",
            )
        }
        // Also dump distinct polygon types found at the picked level so
        // we can see whether buildings are present under a non-0x0A type.
        val polyTypes = HashMap<Int, Int>()
        for (obj in img.readMapInBounds(
            minLong = zoomVp.minLon,
            maxLong = zoomVp.maxLon,
            minLat = zoomVp.minLat,
            maxLat = zoomVp.maxLat,
            levelFilter = pickedLevel,
        )) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && obj.closed) {
                polyTypes.merge(obj.type, 1) { a, b -> a + b }
            }
        }
        diagOut.append("[diag] picked-level polygon types (type=count):\n")
        for ((t, c) in polyTypes.toSortedMap()) {
            diagOut.append("[diag]   0x%02X = $c\n".format(t))
        }
        // Scan ALL levels for any polygon type so we can confirm whether
        // buildings exist in this IMG at all.
        val typeByLevel = HashMap<Int, HashMap<Int, Int>>()
        for (lvl in img.tre.levels.levels) {
            if (lvl.inherited) continue
            val perType = HashMap<Int, Int>()
            for (obj in img.readMapInBounds(
                minLong = zoomVp.minLon,
                maxLong = zoomVp.maxLon,
                minLat = zoomVp.minLat,
                maxLat = zoomVp.maxLat,
                levelFilter = lvl.level,
            )) {
                if (obj is com.asamm.garminimg.model.MapObject.PolyShape && obj.closed) {
                    perType.merge(obj.type, 1) { a, b -> a + b }
                }
            }
            typeByLevel[lvl.level] = perType
        }
        diagOut.append("\n[diag] all-level polygon-type histogram:\n")
        for ((lvl, hist) in typeByLevel.toSortedMap()) {
            diagOut.append("[diag] level=$lvl: ")
            for ((t, c) in hist.toSortedMap()) {
                diagOut.append("0x%02X=$c ".format(t))
            }
            diagOut.append("\n")
        }
        // Diagnostic: TYP styles & icon coverage. For each Point in the
        // zoom viewport, log whether the TYP table has a matching record
        // with an icon attached.
        diagOut.append("\n[diag] TYP icon coverage for points in zoom viewport:\n")
        val typ = img.typStyles
        if (typ == null) {
            diagOut.append("[diag] !! IMG has no TYP sub-file — no icons available !!\n")
        } else {
            var withIcon = 0
            var withoutIcon = 0
            val examples = mutableListOf<String>()
            for (obj in img.readMapInBounds(
                minLong = zoomVp.minLon,
                maxLong = zoomVp.maxLon,
                minLat = zoomVp.minLat,
                maxLat = zoomVp.maxLat,
                levelFilter = pickedLevel,
            )) {
                if (obj is com.asamm.garminimg.model.MapObject.Point) {
                    val t = typ.lookupPoint(obj.type, obj.subType)
                    if (t?.icon != null) {
                        withIcon++
                    } else {
                        withoutIcon++
                        if (examples.size < 12) {
                            examples.add(
                                "type=0x%02X sub=0x%02X label=%s typFound=%s".format(
                                    obj.type, obj.subType,
                                    obj.label?.take(40) ?: "<null>",
                                    t != null,
                                ),
                            )
                        }
                    }
                }
            }
            diagOut.append("[diag] points with icon: $withIcon, without: $withoutIcon\n")
            for (e in examples) diagOut.append("[diag]   $e\n")
        }
        // Diagnostic: railway TYP line styling. mkgmap-built railway uses
        // type 0x14 by default; OTM may use 0x14 too. Check whether the
        // TYP line table has a bitmap pattern for that type — without one
        // we render flat dashes instead of cross-ties.
        diagOut.append("\n[diag] TYP line records by type:\n")
        if (typ != null) {
            for (railwayType in listOf(0x14, 0x16, 0x17, 0x18, 0x19, 0x1A)) {
                for (sub in 0..4) {
                    val rec = typ.lookupLine(railwayType, sub) ?: continue
                    diagOut.append(
                        "[diag]   line type=0x%02X sub=0x%02X stroke=%s border=%s widthPx=%.1f totalWidthPx=%.1f pattern=%s%s\n"
                            .format(
                                railwayType, sub,
                                rec.strokeColor, rec.borderColor,
                                rec.strokeWidthPx, rec.totalWidthPx,
                                rec.pattern != null,
                                if (rec.pattern != null) " (${rec.pattern!!.width}x${rec.pattern!!.height})" else "",
                            ),
                    )
                }
            }
        }
        // Dump TYP polygon style for every plain-polygon type we found
        // in the zoom viewport — verifies we're not rendering buildings
        // as transparent / hiding them via low priority.
        diagOut.append("\n[diag] TYP polygon styles for visible types:\n")
        if (typ != null) {
            for (t in setOf(0x04, 0x10, 0x17, 0x1A, 0x32, 0x3C, 0x4B, 0x50, 0x55, 0x12, 0x13, 0x0A)) {
                for (sub in 0..1) {
                    val rec = typ.lookupPolygon(t, sub) ?: continue
                    diagOut.append(
                        "[diag]   poly type=0x%02X sub=0x%02X fill=%s stroke=%s pattern=%s prio=%s\n"
                            .format(t, sub, rec.fillColor, rec.strokeColor, rec.pattern != null, rec.drawPriority),
                    )
                }
            }
        }
        // Also dump area of each plain polygon at picked level so we can
        // classify them: many tiny ones = buildings, few large ones =
        // built-up areas.
        // Include subType in the histogram — mkgmap-style buildings often
        // ship as (mainType, subType) pairs, and our previous histogram
        // only counted main type.
        diagOut.append("\n[diag] (type,subType) histogram with vertex stats (picked level):\n")
        val pairCount = HashMap<Pair<Int, Int>, Int>()
        val pairVerts = HashMap<Pair<Int, Int>, Int>()
        for (obj in img.readMapInBounds(
            minLong = zoomVp.minLon,
            maxLong = zoomVp.maxLon,
            minLat = zoomVp.minLat,
            maxLat = zoomVp.maxLat,
            levelFilter = pickedLevel,
        )) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && obj.closed) {
                val key = obj.type to obj.subType
                pairCount.merge(key, 1) { a, b -> a + b }
                pairVerts.merge(key, obj.longitudes.size) { a, b -> a + b }
            }
        }
        for ((k, c) in pairCount.toSortedMap(compareBy({ it.first }, { it.second }))) {
            val avgV = pairVerts[k]!! / c
            diagOut.append("[diag]   (0x%02X, 0x%02X) count=$c avgVerts=$avgV\n".format(k.first, k.second))
        }

        diagOut.append("\n[diag] type=0x10 polygon vertex counts (picked level):\n")
        var sample = 0
        for (obj in img.readMapInBounds(
            minLong = zoomVp.minLon,
            maxLong = zoomVp.maxLon,
            minLat = zoomVp.minLat,
            maxLat = zoomVp.maxLat,
            levelFilter = pickedLevel,
        )) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && obj.closed && obj.type == 0x10) {
                if (sample++ < 12) {
                    diagOut.append("[diag]   verts=${obj.longitudes.size} sub=0x%02X label=%s\n".format(obj.subType, obj.label?.take(30) ?: "<null>"))
                }
            }
        }
        // Per-sub-division polygon count at picked level — verifies we
        // parse all sub-divisions inside the viewport.
        diagOut.append("\n[diag] picked-level sub-div count + poly count per sub-div:\n")
        var subdivCount = 0
        for (sd in img.tre.tree.all()) {
            if (sd.level != pickedLevel) continue
            val bits = img.tre.levels.bitsPerCoordOf(sd.level) ?: continue
            val scale = 1 shl (24 - bits).coerceAtLeast(0)
            val halfW = sd.halfWidth.toLong() * scale
            val halfH = sd.halfHeight.toLong() * scale
            if (sd.longitudeCenter + halfW < zoomVp.minLon || sd.longitudeCenter - halfW > zoomVp.maxLon) continue
            if (sd.latitudeCenter + halfH < zoomVp.minLat || sd.latitudeCenter - halfH > zoomVp.maxLat) continue
            subdivCount++
        }
        diagOut.append("[diag] sub-divs at picked level overlapping viewport: $subdivCount\n")
        // Full TYP polygon table dump: every entry, regardless of whether
        // we have it in the IMG. Helps spot whether OTM uses an unusual
        // subtype to mean "building".
        // Resolve a real type-0x10 polygon through the same resolver the
        // renderer uses, and dump the resulting Style so we can check
        // strokeColor / strokeWidth.
        val resolver = resolverFor(img)
        var dumped = 0
        diagOut.append("\n[diag] resolved styles for first 5 type=0x10 polygons:\n")
        for (obj in img.readMapInBounds(
            minLong = zoomVp.minLon,
            maxLong = zoomVp.maxLon,
            minLat = zoomVp.minLat,
            maxLat = zoomVp.maxLat,
            levelFilter = pickedLevel,
        )) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && obj.closed && obj.type == 0x10) {
                val s = resolver.resolve(obj)
                diagOut.append("[diag]   resolved=$s\n")
                if (++dumped >= 5) break
            }
        }
        diagOut.append("\n[diag] full TYP polygon table:\n")
        if (typ != null) {
            for ((key, style) in typ.polygons.toSortedMap()) {
                val type = (key shr 5) and 0xFF
                val sub = key and 0x1F
                diagOut.append(
                    "[diag]   poly (0x%02X,0x%02X) fill=%s stroke=%s pattern=%s prio=%s\n"
                        .format(type, sub, style.fillColor, style.strokeColor, style.pattern != null, style.drawPriority),
                )
            }
        }
        // Save the railway pattern bitmap so we can visually inspect what
        // our renderer is tiling along railway lines.
        if (typ != null) {
            val rec = typ.lookupLine(0x14, 0)
            val bmp = rec?.pattern
            if (bmp != null) {
                val raw = java.awt.image.BufferedImage(bmp.width, bmp.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bmp.height) {
                    for (x in 0 until bmp.width) {
                        val o = (y * bmp.width + x) * 4
                        val r = bmp.rgba[o].toInt() and 0xFF
                        val g = bmp.rgba[o + 1].toInt() and 0xFF
                        val b = bmp.rgba[o + 2].toInt() and 0xFF
                        val a = bmp.rgba[o + 3].toInt() and 0xFF
                        raw.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                    }
                }
                val scaled = java.awt.image.BufferedImage(raw.width * 8, raw.height * 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val gg = scaled.createGraphics()
                gg.drawImage(raw, 0, 0, scaled.width, scaled.height, null)
                gg.dispose()
                javax.imageio.ImageIO.write(scaled, "PNG", File(outDir, "railway-pattern.png"))
            }
            // And the minor-rail (type 0x16) pattern.
            val minor = typ.lookupLine(0x16, 0)?.pattern
            if (minor != null) {
                val raw = java.awt.image.BufferedImage(minor.width, minor.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                for (yy in 0 until minor.height) {
                    for (xx in 0 until minor.width) {
                        val o = (yy * minor.width + xx) * 4
                        val r = minor.rgba[o].toInt() and 0xFF
                        val gc = minor.rgba[o + 1].toInt() and 0xFF
                        val b = minor.rgba[o + 2].toInt() and 0xFF
                        val a = minor.rgba[o + 3].toInt() and 0xFF
                        raw.setRGB(xx, yy, (a shl 24) or (r shl 16) or (gc shl 8) or b)
                    }
                }
                val scaled = java.awt.image.BufferedImage(raw.width * 16, raw.height * 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val gg = scaled.createGraphics()
                gg.drawImage(raw, 0, 0, scaled.width, scaled.height, null)
                gg.dispose()
                javax.imageio.ImageIO.write(scaled, "PNG", File(outDir, "minor-rail-pattern.png"))
            }
        }
        // Per (type,subType) histogram for POLYLINES (open shapes) at the
        // picked level, restricted to the rail-yard area.
        // Wider rail yard — Nice-Ville train station, including the
        // rails *west* of the station building.
        val railVp = Viewport(
            widthPx = 600, heightPx = 400,
            minLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2540),
            maxLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.2700),
            minLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.7030),
            maxLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.7080),
        )
        val lineHist = HashMap<Pair<Int, Int>, Int>()
        for (obj in img.readMapInBounds(
            minLong = railVp.minLon,
            maxLong = railVp.maxLon,
            minLat = railVp.minLat,
            maxLat = railVp.maxLat,
            levelFilter = pickedLevel,
        )) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && !obj.closed) {
                lineHist.merge(obj.type to obj.subType, 1) { a, b -> a + b }
            }
        }
        diagOut.append("\n[diag] polyline (type,subType) histogram in rail yard:\n")
        for ((k, c) in lineHist.toSortedMap(compareBy({ it.first }, { it.second }))) {
            diagOut.append("[diag]   (0x%02X, 0x%02X) = $c\n".format(k.first, k.second))
        }
        // Look at all sub-divisions overlapping the rail yard at every
        // level — count plain & extended polylines by type, including
        // checking the EXTENDED_POLYLINE bit. Rails might live there.
        val plainLines = HashMap<Int, Int>()
        val extLines = HashMap<Int, Int>()
        for (lvl in img.tre.levels.levels.filterNot { it.inherited }) {
            for (obj in img.readMapInBounds(
                minLong = railVp.minLon, maxLong = railVp.maxLon,
                minLat = railVp.minLat, maxLat = railVp.maxLat,
                levelFilter = lvl.level,
            )) {
                if (obj is com.asamm.garminimg.model.MapObject.PolyShape && !obj.closed) {
                    if (obj.type >= 0x100) {
                        extLines.merge(obj.type, 1) { a, b -> a + b }
                    } else {
                        plainLines.merge(obj.type, 1) { a, b -> a + b }
                    }
                }
            }
        }
        // Global line-type histogram — searches the WHOLE IMG so we
        // find what code rails actually use, regardless of sub-division
        // bounds.
        val globalLines = HashMap<Int, Int>()
        for (obj in img.readMap()) {
            if (obj is com.asamm.garminimg.model.MapObject.PolyShape && !obj.closed) {
                globalLines.merge(obj.type, 1) { a, b -> a + b }
            }
        }
        // Full TYP line table — every entry, every type/subtype pair.
        // Brute-force: walk EVERY sub-division in the tree and count
        // type 0x14 (railroad) polylines. Bypasses readMapInBounds to
        // catch any bbox-rejection corner case.
        var t14 = 0
        var t19 = 0
        var t10010 = 0
        for (sd in img.tre.tree.all()) {
            for (obj in com.asamm.garminimg.parser.RgnSubFile.parseSubDivision(
                reader = img.rgn.reader,
                header = img.rgn.header,
                subDivision = sd,
                levels = img.tre.levels,
                lblReader = img.lbl.reader,
                lblHeader = img.lbl.header,
            )) {
                if (obj is com.asamm.garminimg.model.MapObject.PolyShape && !obj.closed) {
                    if (obj.type == 0x14) t14++
                    if (obj.type == 0x19) t19++
                    if (obj.type == 0x100 && obj.subType == 0x10) t10010++
                }
            }
        }
        diagOut.append("\n[diag] brute-force railroad polyline counts: type=0x14:$t14 type=0x19:$t19 ext-0x10010:$t10010\n")
        // List all sub-file entries in the archive — may reveal that this
        // IMG is a multi-tile container and we're only parsing one tile.
        diagOut.append("\n[diag] archive sub-files (raw):\n")
        // List EVERY sub-file in the FAT regardless of type. Some Garmin
        // IMGs ship rails-only sub-files (e.g., extra RGN/TRE pairs in
        // a GMP container) that we'd miss by just looking at the
        // canonical types.
        val allEntries = img.archive.subFiles
        for (e in allEntries) {
            diagOut.append("[diag]   ${e.fileName}.${e.fileType}  size=${e.fileSize}  block=${e.firstBlock}\n")
        }
        diagOut.append("\n[diag] full TYP line table:\n")
        if (typ != null) {
            for ((key, style) in typ.lines.toSortedMap()) {
                val type = (key shr 5) and 0xFFFF
                val sub = key and 0x1F
                diagOut.append(
                    "[diag]   line (0x%04X,0x%02X) stroke=%s border=%s widthPx=%.1f pattern=%s\n"
                        .format(type, sub, style.strokeColor, style.borderColor, style.strokeWidthPx, style.pattern != null),
                )
            }
        }
        diagOut.append("\n[diag] GLOBAL polyline type histogram:\n")
        for ((t, c) in globalLines.toSortedMap()) {
            diagOut.append("[diag]   0x%04X = $c\n".format(t))
        }
        diagOut.append("\n[diag] rail yard polyline types across ALL levels:\n")
        diagOut.append("[diag]   plain (type → count):\n")
        for ((t, c) in plainLines.toSortedMap()) {
            diagOut.append("[diag]     0x%02X = $c\n".format(t))
        }
        diagOut.append("[diag]   extended (type → count):\n")
        for ((t, c) in extLines.toSortedMap()) {
            diagOut.append("[diag]     0x%04X = $c\n".format(t))
        }
        diagFile.writeText(diagOut.toString())

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
     *
     * Restricted to Windows: AWT font rendering differs across OS/JDK
     * (Linux's "SansSerif" → DejaVu Sans, Windows → Arial) and the
     * golden was authored on Windows. Running this on the Linux CI
     * runner would always trip the 1 % per-channel-8 tolerance even
     * with no real regression. Local Windows dev keeps the test live;
     * other OSes get parser-side coverage from the rest of the suite.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun tightMonacoMatchesGoldenPng() {
        val img = openMonaco()
        val widthPx = 1024
        val heightPx = 1024
        val viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.40),
            maxLon = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(7.46),
            minLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.72),
            maxLat = com.asamm.garminimg.utils.coord.CoordUtils.fromWGS84(43.79),
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

    /**
     * Renders the label as printable text and an explicit hex view for
     * each non-printable / non-ASCII byte so the audit file shows what
     * the LBL decoder actually produced.
     */
    private fun dump(label: String): String {
        val printable = StringBuilder(label.length + 16)
        val raw = StringBuilder()
        for (c in label) {
            val code = c.code
            if (code in 0x20..0x7E) {
                printable.append(c)
            } else {
                printable.append('?')
            }
            raw.append(" %04X".format(code))
        }
        return "[$label] →$raw"
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
