/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import javax.imageio.ImageIO

/**
 * Golden render corpus: a fixed tile set rendered through the public Mapsforge AWT API.
 *
 * Compiled twice — against upstream Mapsforge 0.30.0 (`:formats:mapsforge:golden-upstream`) and
 * against the vendored modules (`:formats:mapsforge:golden`) — so both renders run the same code on
 * the same machine.
 */
object GoldenCorpus {

    // fixed tile size of every golden render (ROADMAP D20)
    private const val TILE_SIZE = 512

    // Monaco harbour, centre of the fixture map
    private const val HARBOUR_LAT = 43.7350
    private const val HARBOUR_LON = 7.4230

    // Casino square, on land for the highest zooms
    private const val CASINO_LAT = 43.7396
    private const val CASINO_LON = 7.4277

    /**
     * Render configurations; together they cover each theme, menu layer, device scale and text
     * scale at least once without the full cross-product, plus Locus' primary label mode.
     */
    private val SETS = listOf(
        RenderSet(theme = "theme", menuLayer = "hike_bike", deviceScale = 1.0f, textScale = 1.0f),
        RenderSet(theme = "theme", menuLayer = "car", deviceScale = 2.625f, textScale = 1.0f),
        RenderSet(theme = "theme_dark", menuLayer = "hike_bike", deviceScale = 3.0f, textScale = 1.5f),
        RenderSet(theme = "theme_dark", menuLayer = "car", deviceScale = 1.0f, textScale = 1.5f),
        RenderSet(
            theme = "theme",
            menuLayer = "hike_bike",
            deviceScale = 2.625f,
            textScale = 1.0f,
            labelsInTile = false,
        ),
    )

    /**
     * Tiles in render order; the order matters because labels crossing tile edges depend on
     * already rendered neighbours.
     */
    val tiles: List<Tile> by lazy {
        buildTiles()
    }

    /**
     * Renders every set × tile combination into `outDir` as PNG files, plus a label dump for each
     * set that keeps labels out of the tiles, the reader and theme-callback dumps and the Paint
     * counts.
     */
    fun renderAll(fixturesDir: File, outDir: File) {
        outDir.mkdirs()
        val mapFile = File(fixturesDir, "maps/monaco.osm.map")
        // the device scale is a JVM-wide static in Mapsforge; leave it as found
        val originalDeviceScale = DisplayModel.getDeviceScaleFactor()
        try {
            for (set in SETS) {
                renderSet(
                    set = set,
                    mapFile = mapFile,
                    themeFile = File(fixturesDir, "themes/lomaps/${set.theme}.xml"),
                    outDir = outDir,
                )
            }
            writeDumps(
                mapFile = mapFile,
                themeFile = File(fixturesDir, "themes/lomaps/theme.xml"),
                outDir = outDir,
            )
            PaintCount.write(
                mapFile = mapFile,
                themeFile = File(fixturesDir, "themes/lomaps/theme.xml"),
                tileSize = TILE_SIZE,
                lat = HARBOUR_LAT,
                lon = HARBOUR_LON,
                file = File(outDir, "paint_counts.txt"),
            )
        } finally {
            DisplayModel.setDeviceScaleFactor(originalDeviceScale)
        }
    }

    /**
     * Reader and theme-callback dumps for `dumpTiles`, with the day theme, `hike_bike` and device
     * scale 1.
     */
    private fun writeDumps(mapFile: File, themeFile: File, outDir: File) {
        DisplayModel.setDeviceScaleFactor(1.0f)
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(TILE_SIZE)
        val mapDataStore = MapFile(mapFile)
        val xmlTheme = ExternalRenderTheme(themeFile, MenuSelection("hike_bike"))
        val themeFuture = RenderThemeFuture(AwtGraphicFactory.INSTANCE, xmlTheme, displayModel)
        themeFuture.run()
        themeFuture.get()
        try {
            MapDataDump.writeReader(store = mapDataStore, tiles = dumpTiles, file = File(outDir, "reader.txt"))
            MapDataDump.writeTheme(
                store = mapDataStore,
                themeFuture = themeFuture,
                displayModel = displayModel,
                tiles = dumpTiles,
                file = File(outDir, "theme_callbacks.txt"),
            )
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }

    private fun renderSet(set: RenderSet, mapFile: File, themeFile: File, outDir: File) {
        // a static in Mapsforge, read when the theme is parsed and when tiles are drawn
        DisplayModel.setDeviceScaleFactor(set.deviceScale)
        val graphicFactory = AwtGraphicFactory.INSTANCE
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(TILE_SIZE)
        val mapDataStore = MapFile(mapFile)
        val xmlTheme = ExternalRenderTheme(themeFile, MenuSelection(set.menuLayer))
        val themeFuture = RenderThemeFuture(graphicFactory, xmlTheme, displayModel)
        themeFuture.run()
        // the renderer only logs a parse failure and returns null tiles; fail with the real cause
        themeFuture.get()
        // labels outside the tile: no tile cache, labels kept in a store, as Locus' primary mode
        val labelStore = if (set.labelsInTile) null else TileBasedLabelStore(100)
        val renderer = DatabaseRenderer(
            mapDataStore,
            graphicFactory,
            if (set.labelsInTile) InMemoryTileCache(64) else null,
            labelStore,
            set.labelsInTile,
            true,
            null,
        )
        try {
            for (tile in tiles) {
                val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, set.textScale, false, false)
                val bitmap = renderer.executeJob(job)
                    ?: throw IllegalStateException("renderSet(${set.name}), no bitmap for $tile")
                val file = File(outDir, "${set.name}_z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}.png")
                ImageIO.write(AwtGraphicFactory.getBitmap(bitmap), "png", file)
                bitmap.decrementRefCount()
            }
            if (labelStore != null) {
                LabelDump.write(store = labelStore, tiles = tiles, file = File(outDir, "${set.name}_labels.txt"))
            }
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
    }

    /**
     * Corpus tiles plus z3 and z5, which read the map's two low-zoom subfiles; dumped, not rendered.
     */
    private val dumpTiles: List<Tile> by lazy {
        tiles + listOf(3, 5).map { zoom ->
            tileAt(lat = HARBOUR_LAT, lon = HARBOUR_LON, zoom = zoom, dx = 0, dy = 0)
        }
    }

    private fun buildTiles(): List<Tile> {
        val result = mutableListOf<Tile>()
        // one tile per zoom: harbour up to z17, Casino square above, where the harbour is sea
        for (zoom in listOf(8, 10, 12, 13, 14, 15, 16, 17)) {
            result += tileAt(lat = HARBOUR_LAT, lon = HARBOUR_LON, zoom = zoom, dx = 0, dy = 0)
        }
        for (zoom in listOf(18, 19, 20)) {
            result += tileAt(lat = CASINO_LAT, lon = CASINO_LON, zoom = zoom, dx = 0, dy = 0)
        }
        // 2x2 blocks where labels cross tile edges
        for ((zoom, offset) in listOf(15 to -1, 17 to 1)) {
            for (dy in 0..1) {
                for (dx in 0..1) {
                    result += tileAt(
                        lat = HARBOUR_LAT,
                        lon = HARBOUR_LON,
                        zoom = zoom,
                        dx = dx + offset,
                        dy = dy + offset,
                    )
                }
            }
        }
        return result.distinct()
    }

    private fun tileAt(lat: Double, lon: Double, zoom: Int, dx: Int, dy: Int): Tile {
        val z = zoom.toByte()
        val x = MercatorProjection.longitudeToTileX(lon, z) + dx
        val y = MercatorProjection.latitudeToTileY(lat, z) + dy
        return Tile(x, y, z, TILE_SIZE)
    }

    /**
     * One render configuration; `name` prefixes its output files and must stay unique per set.
     *
     * @param labelsInTile false renders like Locus' primary mode: tiles without labels, labels
     * kept in a `TileBasedLabelStore` for a separate pass
     */
    private data class RenderSet(
        val theme: String,
        val menuLayer: String,
        val deviceScale: Float,
        val textScale: Float,
        val labelsInTile: Boolean = true,
    ) {

        val name: String
            get() = "${theme}_${menuLayer}_ds${deviceScale}_ts$textScale" + if (labelsInTile) "" else "_separate"
    }

    /**
     * Selects one style-menu layer plus its overlays enabled by default, as Locus does with its
     * default preferences.
     */
    class MenuSelection(private val layerId: String) : XmlRenderThemeMenuCallback {

        override fun getCategories(style: XmlRenderThemeStyleMenu): Set<String> {
            val layer = style.getLayer(layerId)
                ?: throw IllegalStateException("getCategories($layerId), no such layer in ${style.id}")
            val result = HashSet(layer.categories)
            for (overlay in layer.overlays) {
                if (overlay.isEnabled) {
                    result.addAll(overlay.categories)
                }
            }
            return result
        }
    }
}
