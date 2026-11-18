/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.sweep

import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.mvt.driver.MvtDataset
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtRenderer
import com.asamm.vectormaps.mvt.driver.MvtStyle
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AwtFonts
import com.asamm.vectormaps.mvt.render.AwtMvtGraphics
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import com.asamm.vectormaps.mvt.style.Colors
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.symbol.MvtFont
import com.asamm.vectormaps.mvt.style.Color as MvtColor
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan

/**
 * Our half of the parity sweep:
 * `compare map=<file.pmtiles> run=<dir> [tiles=<run>/tiles.txt] [maplibre=<run>/maplibre]
 * [threads=<cores>] [fonts=<dir>] [top=100]` renders every tile of `tiles` in each view MapLibre
 * rendered into `<maplibre>/<view>` (`dev/mvt/reference/sweep*.mjs`), scores the pair as the
 * goldens do and writes `<run>/results.csv` and `<run>/report.html` with the `top` worst tiles per
 * view. The fonts are the style's Noto faces (NotoSans-Regular, -Bold, -Italic, NotoSerif-Italic
 * `.ttf`), as MapLibre draws them.
 */
fun main(args: Array<String>) {
    Locale.setDefault(Locale.ROOT)
    if (args.firstOrNull() == "features") {
        features(args.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') })
        return
    }
    if (args.firstOrNull() == "trace") {
        trace(args.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') })
        return
    }
    require(args.firstOrNull() == "compare") {
        "usage: compare map=<file.pmtiles> run=<dir> [tiles=] [maplibre=] [threads=] [fonts=] [top=]"
    }
    val options = args.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
    val runDir = File(requireNotNull(options["run"]) { "run=<dir> missing" })
    Sweep(
        map = File(requireNotNull(options["map"]) { "map=<file.pmtiles> missing" }),
        runDir = runDir,
        tilesFile = options["tiles"]?.let(::File) ?: File(runDir, "tiles.txt"),
        maplibreDir = options["maplibre"]?.let(::File) ?: File(runDir, "maplibre"),
        fonts = fonts(options["fonts"]?.let(::File)),
        threads = options["threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors(),
        top = options["top"]?.toInt() ?: 100,
    ).run()
}

/**
 * `trace map=<file.pmtiles> tile=<z/x/y> px=<x,y> [work=<dir>]`: the colour of one pixel of a tile
 * as each style layer is added, so the layer that draws it, or fails to, shows.
 */
private fun trace(options: Map<String, String>) {
    val fixtures = File(requireNotNull(System.getProperty("fixturesDir")) { "-DfixturesDir=<repository>/dev/fixtures missing" }, "mvt/styles/hike_bike")
    val (z, x, y) = requireNotNull(options["tile"]).split('/').map { it.toInt() }
    val (px, py) = requireNotNull(options["px"]).split(',').map { it.toInt() }
    val work = File(options["work"] ?: "local/sweep/trace").apply { mkdirs() }
    for (name in listOf("sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png")) {
        File(fixtures, name).copyTo(File(work, name), overwrite = true)
    }
    val json = kotlinx.serialization.json.Json.parseToJsonElement(File(fixtures, "style.json").readText())
        as kotlinx.serialization.json.JsonObject
    val layers = json["layers"] as kotlinx.serialization.json.JsonArray
    val driver = MvtDriver(AwtMvtGraphics(fonts(null)))
    driver.open(listOf(File(requireNotNull(options["map"])))).use { dataset ->
        var last = -2
        for (n in 1..layers.size) {
            val cut = kotlinx.serialization.json.JsonObject(json + ("layers" to kotlinx.serialization.json.JsonArray(layers.take(n))))
            val file = File(work, "style.json").apply { writeText(cut.toString()) }
            val style = driver.loadStyle(file)
            dataset.createRenderer(style, StyleSelection.DEFAULT, RenderConfig(tileSizePx = 512)).use { renderer ->
                val target = MvtTileTarget(512)
                renderer.render(TileRequest(x = x, y = y, zoom = z), target) { false }
                val rgb = (target.canvas as? AwtMvtRenderTarget)?.image?.getRGB(px, py) ?: -1
                if (rgb != last) {
                    val layer = layers[n - 1] as kotlinx.serialization.json.JsonObject
                    println("%3d %-40s #%06x".format(n - 1, layer["id"], rgb and 0xffffff))
                    last = rgb
                }
            }
            style.close()
        }
    }
}

/**
 * `features map=<file.pmtiles> tile=<z/x/y> layer=<source layer> [key=value]`: the data tile's
 * features of `layer`, with `key` equal to `value` when given, their type, properties and bounds.
 */
private fun features(options: Map<String, String>) {
    val (z, x, y) = requireNotNull(options["tile"]).split('/').map { it.toInt() }
    val filter = options.keys.firstOrNull { it !in setOf("map", "tile", "layer") }
    com.asamm.vectormaps.mvt.source.PmTilesSource(File(requireNotNull(options["map"]))).use { source ->
        val dz = maxOf(0, z - source.maxZoom)
        val bytes = requireNotNull(source.tile(z - dz, x shr dz, y shr dz)) { "no data tile" }
        val tile = com.asamm.vectormaps.mvt.tile.VectorTile.decode(com.asamm.vectormaps.mvt.source.MvtTileSource.uncompressed(bytes))
        val layer = tile.layers[options["layer"]] ?: error("no layer ${options["layer"]}, has ${tile.layers.keys}")
        for (f in layer.features) {
            if (filter != null && f.property(filter)?.toString() != options[filter]) {
                continue
            }
            val g = f.decodeGeometry()
            val props = layer.keys.mapNotNull { k -> f.property(k)?.let { "$k=$it" } }
            println("${f.type} parts=${g.partEnds.size} x=${g.minX}..${g.maxX} y=${g.minY}..${g.maxY} $props")
        }
    }
}

private fun fonts(dir: File?): AwtFonts {
    fun load(name: String): Font {
        return Font.createFont(Font.TRUETYPE_FONT, File(dir, name))
    }
    if (dir == null) {
        val fixtures = File(requireNotNull(System.getProperty("fixturesDir")) { "-DfixturesDir=<repository>/dev/fixtures missing" }, "fonts/dejavu/DejaVuSans.ttf")
        println("no fonts dir: DejaVu Sans, labels will differ from MapLibre's Noto")
        return AwtFonts(Font.createFont(Font.TRUETYPE_FONT, fixtures))
    }
    val sans = load("NotoSans-Regular.ttf")
    val serifItalic = load("NotoSerif-Italic.ttf")
    return AwtFonts(
        sans = sans,
        serif = serifItalic,
        own = mapOf(
            MvtFont(serif = false, bold = true, italic = false) to load("NotoSans-Bold.ttf"),
            MvtFont(serif = false, bold = false, italic = true) to load("NotoSans-Italic.ttf"),
            MvtFont(serif = true, bold = false, italic = true) to serifItalic,
        ),
    )
}

/**
 * A view MapLibre rendered: the tile alone (`base`) or the label pass over its neighbourhood, the
 * screen turned by `rotationDeg`.
 */
private enum class View(val labels: Boolean, val rotationDeg: Double) {
    BASE(labels = false, rotationDeg = 0.0),
    LABELS(labels = true, rotationDeg = 0.0),
    LABELS_R30(labels = true, rotationDeg = 30.0),
    ;

    val dirName: String = name.lowercase()
}

private data class Tile(val z: Int, val x: Int, val y: Int) {

    val name = "${z}_${x}_$y"

    val lat: Double
        get() {
            val n = (1 shl z).toDouble()
            return atan(kotlin.math.sinh(PI * (1 - 2 * (y + 0.5) / n))) * 180 / PI
        }

    val lon: Double
        get() {
            return (x + 0.5) / (1 shl z) * 360 - 180
        }
}

private class Score(
    val tile: Tile,
    val view: View,
    val mean: Double,
    val offPercent: Double,
    val status: String,
)

private class Sweep(
    private val map: File,
    private val runDir: File,
    private val tilesFile: File,
    private val maplibreDir: File,
    fonts: AwtFonts,
    private val threads: Int,
    private val top: Int,
) {

    private val driver = MvtDriver(AwtMvtGraphics(fonts))

    private val style: MvtStyle = driver.loadStyle(
        File(requireNotNull(System.getProperty("fixturesDir")) { "-DfixturesDir=<repository>/dev/fixtures missing" }, "mvt/styles/hike_bike/style.json"),
    )

    fun run() {
        runDir.mkdirs()
        val tiles = tilesFile.readLines()
            .filter { it.isNotBlank() }
            .map { line -> line.trim().split('/').map { it.toInt() }.let { Tile(it[0], it[1], it[2]) } }
        val views = View.entries.filter { File(maplibreDir, it.dirName).isDirectory }
        println("${tiles.size} tiles, views ${views.map { it.dirName }}, $threads threads")
        val scores = driver.open(listOf(map)).use { dataset ->
            score(dataset, tiles, views)
        }
        writeCsv(scores)
        driver.open(listOf(map)).use { dataset ->
            writeReport(dataset, scores, views)
        }
        style.close()
    }

    private fun score(dataset: MvtDataset, tiles: List<Tile>, views: List<View>): List<Score> {
        val pool = Executors.newFixedThreadPool(threads)
        val done = AtomicInteger()
        val started = System.nanoTime()
        // one renderer per thread, so a thread's label pass sees only the tiles it rendered
        val all = ConcurrentLinkedQueue<MvtRenderer>()
        val renderers = ThreadLocal.withInitial { renderer(dataset).also { all += it } }
        try {
            val futures = tiles.map { tile ->
                pool.submit<List<Score>> {
                    val renderer = renderers.get()
                    // the labelled views share one mosaic of the tile and its neighbours
                    val mosaic = lazy { mosaic(renderer, tile) }
                    val out = views.map { view -> scoreView(renderer, tile, view, mosaic) }
                    val n = done.incrementAndGet()
                    if (n % 200 == 0) {
                        val seconds = (System.nanoTime() - started) / 1e9
                        println("$n / ${tiles.size}, %.1f tiles/s".format(n / seconds))
                    }
                    out
                }
            }
            return futures.flatMap { it.get() }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.MINUTES)
            all.forEach { it.close() }
        }
    }

    private fun renderer(dataset: MvtDataset): MvtRenderer {
        return dataset.createRenderer(
            style = style,
            selection = StyleSelection.DEFAULT,
            config = RenderConfig(tileSizePx = SIZE),
            labelStoreTiles = 32,
        )
    }

    private fun scoreView(renderer: MvtRenderer, tile: Tile, view: View, mosaic: Lazy<BufferedImage>): Score {
        val reference = File(maplibreDir, "${view.dirName}/${tile.name}.png")
        if (!reference.isFile) {
            return Score(tile, view, 0.0, 0.0, "no MapLibre render")
        }
        // a render cut short by a killed MapLibre run reads as none
        val theirs = runCatching { ImageIO.read(reference) }.getOrNull()
        if (theirs == null
            || theirs.width != SIZE
            || theirs.height != SIZE
        ) {
            return Score(tile, view, 0.0, 0.0, "no MapLibre render")
        }
        val ours = try {
            render(renderer, tile, view, mosaic)
        } catch (e: Throwable) {
            return Score(tile, view, 0.0, 100.0, "error ${e.javaClass.simpleName}: ${e.message}")
        }
        val (mean, off) = difference(ours, theirs)
        return Score(tile, view, mean, off, "ok")
    }

    // the view as the hosts show it: the tile, or the screen over it with the label pass
    private fun render(renderer: MvtRenderer, tile: Tile, view: View, mosaic: Lazy<BufferedImage>): BufferedImage {
        if (!view.labels) {
            return tile(renderer, tile.z, tile.x, tile.y) ?: blank(tile.z)
        }
        val labelView = LabelViewport(
            zoom = tile.z,
            centerX = (tile.x + 0.5) * SIZE,
            centerY = (tile.y + 0.5) * SIZE,
            widthPx = SIZE,
            heightPx = SIZE,
            rotationDeg = view.rotationDeg,
        )
        val tiles = mosaic.value
        val pass = renderer.labelPass
        val prepared = pass.prepareNow(labelView)
        val labels = pass.newTarget(SIZE, SIZE) as AwtMvtRenderTarget
        pass.draw(labels, labelView, prepared)
        labels.dispose()
        return screen(tiles, labels.image, view.rotationDeg, background(tile.z))
    }

    // the tile and its neighbours, as their labels reach in and a turned screen shows their corners;
    // rendering them also fills the label store the label pass reads
    private fun mosaic(renderer: MvtRenderer, tile: Tile): BufferedImage {
        val mosaic = BufferedImage(SIZE * 3, SIZE * 3, BufferedImage.TYPE_INT_ARGB)
        val n = 1 shl tile.z
        mosaic.createGraphics().apply {
            // beyond the data and the world MapLibre shows the style's background
            color = background(tile.z)
            fillRect(0, 0, SIZE * 3, SIZE * 3)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val ty = tile.y + dy
                    if (ty < 0 || ty >= n) {
                        continue
                    }
                    val tx = Math.floorMod(tile.x + dx, n)
                    tile(renderer, tile.z, tx, ty)?.let { drawImage(it, SIZE * (dx + 1), SIZE * (dy + 1), null) }
                }
            }
            dispose()
        }
        return mosaic
    }

    private fun tile(renderer: MvtRenderer, z: Int, x: Int, y: Int): BufferedImage? {
        val target = MvtTileTarget(SIZE)
        val result = renderer.render(TileRequest(x = x, y = y, zoom = z), target) { false }
        if (result != RenderResult.DRAWN) {
            return null
        }
        val canvas = target.canvas as AwtMvtRenderTarget
        canvas.dispose()
        return canvas.image
    }

    // the mosaic's middle tile on a white screen turned clockwise by `rotationDeg`, labels over it
    private fun screen(mosaic: BufferedImage, labels: BufferedImage, rotationDeg: Double, background: Color): BufferedImage {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            color = background
            fillRect(0, 0, SIZE, SIZE)
            val plain = transform
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            rotate(Math.toRadians(rotationDeg), SIZE / 2.0, SIZE / 2.0)
            drawImage(mosaic, -SIZE, -SIZE, null)
            transform = plain
            drawImage(labels, 0, 0, null)
            dispose()
        }
        return image
    }

    // the style's background colour at `zoom`, white without a background layer
    private fun background(zoom: Int): Color {
        val layer = style.style.layers.firstOrNull { it.type == LayerType.BACKGROUND && it.isActive(zoom.toDouble()) }
        val color = when (val value = layer?.paint?.get("background-color")?.eval(EvalContext(zoom.toDouble()))) {
            is MvtColor -> value
            is String -> Colors.parse(value)
            else -> null
        }
        return Color(color?.argb ?: -1, true)
    }

    private fun blank(zoom: Int): BufferedImage {
        return BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                color = background(zoom)
                fillRect(0, 0, SIZE, SIZE)
                dispose()
            }
        }
    }

    private fun writeCsv(scores: List<Score>) {
        File(runDir, "results.csv").printWriter().use { out ->
            out.println("view,z,x,y,lat,lon,mean,off_percent,status")
            for (s in scores) {
                out.println(
                    "%s,%d,%d,%d,%.5f,%.5f,%.2f,%.2f,%s".format(
                        s.view.dirName,
                        s.tile.z,
                        s.tile.x,
                        s.tile.y,
                        s.tile.lat,
                        s.tile.lon,
                        s.mean,
                        s.offPercent,
                        s.status.replace(',', ';'),
                    ),
                )
            }
        }
    }

    // per view a summary per zoom and the `top` worst tiles, MapLibre, ours and the difference
    private fun writeReport(dataset: MvtDataset, scores: List<Score>, views: List<View>) {
        val images = File(runDir, "report")
        images.deleteRecursively()
        images.mkdirs()
        val renderer = renderer(dataset)
        val html = StringBuilder()
        html.append(
            """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><title>MVT parity sweep</title>
            <style>body{font:13px sans-serif;margin:16px}table{border-collapse:collapse;margin:8px 0 24px}
            td,th{border:1px solid #ccc;padding:2px 6px;text-align:right}img{display:block;max-width:100%}
            .row{margin:12px 0 28px}</style></head><body>
            <h1>MVT parity sweep</h1><p>${tilesFile.readLines().count { it.isNotBlank() }} tiles of
            ${html(map.name)}; mean is the mean RGB difference (0-255), off the share of pixels off by more than $TOLERANCE.
            Strips: MapLibre | ours | difference (red: off).</p>
            """.trimIndent(),
        )
        for (view in views) {
            val ofView = scores.filter { it.view == view }
            html.append("<h2>${view.dirName}</h2><table><tr><th>zoom</th><th>tiles</th><th>mean</th>")
            html.append("<th>off median %</th><th>off &gt; ${threshold(view)} %</th><th>not compared</th></tr>")
            for ((z, ofZoom) in ofView.groupBy { it.tile.z }.toSortedMap()) {
                val ok = ofZoom.filter { it.status == "ok" }
                val offs = ok.map { it.offPercent }.sorted()
                html.append(
                    "<tr><td>$z</td><td>${ofZoom.size}</td><td>%.2f</td><td>%.2f</td><td>%d</td><td>%d</td></tr>".format(
                        ok.map { it.mean }.average().takeIf { !it.isNaN() } ?: 0.0,
                        offs.getOrElse(offs.size / 2) { 0.0 },
                        ok.count { it.offPercent > threshold(view) },
                        ofZoom.size - ok.size,
                    ),
                )
            }
            html.append("</table>")
            val worst = ofView.filter { it.status != "no MapLibre render" }.sortedByDescending { it.offPercent }.take(top)
            for ((rank, s) in worst.withIndex()) {
                val strip = File(images, "${view.dirName}_${rank + 1}_${s.tile.name}.png")
                val reference = ImageIO.read(File(maplibreDir, "${view.dirName}/${s.tile.name}.png"))
                val ours = runCatching { render(renderer, s.tile, view, lazy { mosaic(renderer, s.tile) }) }.getOrNull()
                    ?: blank(s.tile.z)
                ImageIO.write(strip(reference, ours), "png", strip)
                html.append(
                    "<div class=row><b>#${rank + 1} ${s.tile.z}/${s.tile.x}/${s.tile.y}</b> " +
                        "(%.5f, %.5f) mean %.2f, off %.2f %% %s<img src=\"report/${strip.name}\"></div>".format(
                            s.tile.lat,
                            s.tile.lon,
                            s.mean,
                            s.offPercent,
                            if (s.status == "ok") "" else html(s.status),
                        ),
                )
            }
        }
        html.append("</body></html>")
        renderer.close()
        File(runDir, "report.html").writeText(html.toString())
        println("report: ${File(runDir, "report.html").absolutePath}")
    }

    private fun strip(reference: BufferedImage, ours: BufferedImage): BufferedImage {
        val out = BufferedImage(SIZE * 3, SIZE, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            drawImage(reference, 0, 0, null)
            drawImage(ours, SIZE, 0, null)
            dispose()
        }
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val worst = worst(reference.getRGB(x, y), ours.getRGB(x, y))
                val rgb = if (worst > TOLERANCE) 0xff0000 else 0xffffff - worst * 0x030303
                out.setRGB(SIZE * 2 + x, y, rgb)
            }
        }
        return out
    }

    private fun threshold(view: View): Double {
        return if (view.labels) LABELS_OFF_PERCENT else BASE_OFF_PERCENT
    }

    companion object {

        const val SIZE = 512

        // copies of the golden harness, which lives in test sources: keep them with
        // MvtFixtures.difference and GoldenRenderTest.MAX_OFF_PERCENT (dev/mvt/golden)

        // as the goldens: a channel off by more than this counts the pixel as off
        const val TOLERANCE = 32

        // the golden's limit for tiles; labels in the same font, looser for their placement
        const val BASE_OFF_PERCENT = 6.0

        const val LABELS_OFF_PERCENT = 10.0

        fun html(text: String): String {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        }

        fun worst(p: Int, q: Int): Int {
            var worst = 0
            for (shift in intArrayOf(16, 8, 0)) {
                worst = maxOf(worst, abs((p ushr shift and 0xff) - (q ushr shift and 0xff)))
            }
            return worst
        }

        /**
         * Mean absolute RGB difference (0-255) and the percentage of pixels off by more than
         * [TOLERANCE] in a channel, as the goldens score.
         */
        fun difference(a: BufferedImage, b: BufferedImage): Pair<Double, Double> {
            var sum = 0L
            var off = 0
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val p = a.getRGB(x, y)
                    val q = b.getRGB(x, y)
                    var worst = 0
                    for (shift in intArrayOf(16, 8, 0)) {
                        val d = abs((p ushr shift and 0xff) - (q ushr shift and 0xff))
                        sum += d
                        worst = maxOf(worst, d)
                    }
                    if (worst > TOLERANCE) {
                        off++
                    }
                }
            }
            val n = SIZE * SIZE
            return sum / (n * 3.0) to off * 100.0 / n
        }
    }
}
