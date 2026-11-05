/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.MapsforgeLabelPass
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import com.asamm.vectormaps.mapsforge.core.graphics.Color
import com.asamm.vectormaps.mapsforge.core.graphics.FontFamily
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import java.awt.Font
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.floor

/**
 * The label pass goldens (M6.1b): the Mapsforge driver's `SEPARATE` pass placed and drawn for a few
 * screens, straight and turned, on a white background, plus a dump of what it placed, for the
 * hike_bike day theme at device scale 2.625. Vendored side only, as upstream has no such pass;
 * compared by `LabelPassGoldenTest` with `labels/pass`.
 */
object LabelPassRender {

    // Locus' primary configuration
    private val SET = Corpus.RenderSet(
        theme = "theme",
        menuLayer = "hike_bike",
        deviceScale = 2.625f,
        textScale = 1.0f,
        labelsInTile = false,
    )

    private const val SCREEN_PX = Corpus.TILE_SIZE

    private val ROTATIONS = listOf(0.0, 30.0)

    // around the corners of the corpus' 2x2 blocks, where labels cross tile edges
    private val SCREENS = listOf(15 to 0, 17 to 2)

    fun renderAll(fixturesDir: File, outDir: File) {
        outDir.mkdirs()
        val font = Font.createFont(Font.TRUETYPE_FONT, Corpus.labelFontFile(fixturesDir))
        val graphicFactory = AwtGraphicFactory.withFonts(FontFamily.entries.associateWith { font })
        val driver = MapsforgeDriver(graphicFactory)
        val style = driver.loadStyle(Corpus.themeFile(fixturesDir, SET.theme))
        val config = RenderConfig(
            tileSizePx = Corpus.TILE_SIZE,
            deviceScale = SET.deviceScale,
            textScale = SET.textScale,
            labelMode = LabelMode.SEPARATE,
        )
        driver.open(listOf(Corpus.mapFile(fixturesDir))).use { dataset ->
            dataset.createRenderer(style, StyleSelection(layerId = SET.menuLayer), config).use { renderer ->
                val pass = renderer.labelPass as MapsforgeLabelPass
                for ((zoom, offset) in SCREENS) {
                    val views = ROTATIONS.map { rotation ->
                        screen(zoom = zoom, offset = offset, rotationDeg = rotation)
                    }
                    for (tile in tilesUnder(views)) {
                        val target = TileBitmapTarget(Corpus.TILE_SIZE)
                        check(renderer.render(tile, target) { false } == RenderResult.DRAWN) {
                            "renderAll(), $tile not drawn"
                        }
                        target.bitmap!!.decrementRefCount()
                    }
                    for (view in views) {
                        val prepared = prepare(pass = pass, view = view)
                        val name = "pass_z${view.zoom}_r${view.rotationDeg.toInt()}"
                        writeImage(
                            pass = pass,
                            view = view,
                            graphicFactory = graphicFactory,
                            file = File(outDir, "$name.png"),
                        )
                        writeDump(
                            prepared = prepared,
                            file = File(outDir, "$name${LabelGoldens.LABEL_DUMP_SUFFIX}"),
                        )
                    }
                }
            }
        }
    }

    /**
     * A screen centered on the corner shared by the corpus block's four tiles.
     */
    private fun screen(zoom: Int, offset: Int, rotationDeg: Double): LabelViewport {
        val z = zoom.toByte()
        val tileX = MercatorProjection.longitudeToTileX(Corpus.HARBOUR_LON, z) + offset
        val tileY = MercatorProjection.latitudeToTileY(Corpus.HARBOUR_LAT, z) + offset
        return LabelViewport(
            zoom = zoom,
            centerX = tileX.toDouble() * Corpus.TILE_SIZE,
            centerY = tileY.toDouble() * Corpus.TILE_SIZE,
            widthPx = SCREEN_PX,
            heightPx = SCREEN_PX,
            rotationDeg = rotationDeg,
        )
    }

    private fun tilesUnder(views: List<LabelViewport>): List<TileRequest> {
        val tiles = LinkedHashSet<TileRequest>()
        for (view in views) {
            val bounds = view.worldBounds()
            val minX = floor(bounds.minX / Corpus.TILE_SIZE).toInt()
            val maxX = floor(bounds.maxX / Corpus.TILE_SIZE).toInt()
            val minY = floor(bounds.minY / Corpus.TILE_SIZE).toInt()
            val maxY = floor(bounds.maxY / Corpus.TILE_SIZE).toInt()
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    tiles += TileRequest(x = x, y = y, zoom = view.zoom)
                }
            }
        }
        return tiles.toList()
    }

    private fun prepare(pass: MapsforgeLabelPass, view: LabelViewport): PreparedLabels {
        val done = CountDownLatch(1)
        pass.onPrepared = {
            done.countDown()
        }
        pass.prepare(view)
        check(done.await(60, TimeUnit.SECONDS)) {
            "prepare($view), timed out"
        }
        return pass.prepared!!
    }

    private fun writeImage(
        pass: MapsforgeLabelPass,
        view: LabelViewport,
        graphicFactory: GraphicFactory,
        file: File,
    ) {
        val bitmap = graphicFactory.createBitmap(view.widthPx, view.heightPx)
        val canvas = graphicFactory.createCanvas()
        canvas.setBitmap(bitmap)
        canvas.fillColor(Color.WHITE)
        pass.draw(canvas = canvas, view = view)
        ImageIO.write(AwtGraphicFactory.getBitmap(bitmap), "png", file)
        canvas.destroy()
        bitmap.decrementRefCount()
    }

    /**
     * One line per placed label, in draw order.
     */
    private fun writeDump(prepared: PreparedLabels, file: File) {
        val view = prepared.view
        val out = mutableListOf(
            "view z${view.zoom} center=${view.centerX},${view.centerY} rotation=${view.rotationDeg}",
        )
        for (placed in prepared.placed) {
            val candidate = placed.candidate
            out += String.format(
                Locale.ROOT,
                "  %s %s p=%d xy=%.2f,%.2f boxes=%d reversed=%b \"%s\"",
                candidate.kind,
                candidate.display,
                candidate.priority,
                candidate.x,
                candidate.y,
                candidate.boxes.size,
                placed.isReversed,
                candidate.text?.value ?: "",
            )
        }
        file.writeText(out.joinToString(separator = "\n", postfix = "\n"))
    }
}

/**
 * Renders the label pass goldens; arguments are the fixtures directory and the output directory.
 */
fun main(args: Array<String>) {
    require(args.size == 2) {
        "usage: <fixturesDir> <outDir>"
    }
    LabelPassRender.renderAll(fixturesDir = File(args[0]), outDir = File(args[1]))
}
