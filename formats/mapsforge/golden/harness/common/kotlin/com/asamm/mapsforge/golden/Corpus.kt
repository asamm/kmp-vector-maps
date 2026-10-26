/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
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

/**
 * The golden corpus, independent of a graphics backend: render sets, tiles and the plain Mapsforge
 * render loop, used by the upstream AWT side and both Android sides; the vendored AWT side renders
 * through the driver.
 */
object Corpus {

    // fixed tile size of every golden render (ROADMAP D20)
    const val TILE_SIZE = 512

    // Monaco harbour, centre of the fixture map
    const val HARBOUR_LAT = 43.7350
    const val HARBOUR_LON = 7.4230

    // Casino square, on land for the highest zooms
    private const val CASINO_LAT = 43.7396
    private const val CASINO_LON = 7.4277

    /**
     * Render configurations; together they cover each theme, menu layer, device scale and text
     * scale at least once without the full cross-product, plus Locus' primary label mode.
     */
    val SETS = listOf(
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
     * Corpus tiles plus z3 and z5, which read the map's two low-zoom subfiles; dumped, not rendered.
     */
    val dumpTiles: List<Tile> by lazy {
        tiles + listOf(3, 5).map { zoom ->
            tileAt(lat = HARBOUR_LAT, lon = HARBOUR_LON, zoom = zoom, dx = 0, dy = 0)
        }
    }

    fun mapFile(fixturesDir: File): File {
        return File(fixturesDir, "maps/monaco.osm.map")
    }

    fun themeFile(fixturesDir: File, theme: String): File {
        return File(fixturesDir, "themes/lomaps/$theme.xml")
    }

    /**
     * Renders every corpus tile of `set` and hands each bitmap to `onTile`, which owns writing it;
     * returns the label store when the set keeps labels out of the tiles. Sets the JVM-wide device
     * scale; callers restore it.
     */
    fun renderSet(
        set: RenderSet,
        fixturesDir: File,
        graphicFactory: GraphicFactory,
        onTile: (Tile, TileBitmap) -> Unit,
    ): TileBasedLabelStore? {
        IdentityHashes.align("${set.name} theme")
        // a static upstream, read when the theme is parsed and when tiles are drawn; vendored
        // models copy it when created (C4)
        DisplayModel.setDeviceScaleFactor(set.deviceScale)
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(TILE_SIZE)
        // as Locus and the driver
        displayModel.setMaxTextWidthFactor(0.7f * set.textScale)
        val mapDataStore = MapFile(mapFile(fixturesDir))
        val xmlTheme = ExternalRenderTheme(themeFile(fixturesDir, set.theme), MenuSelection(set.menuLayer))
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
                IdentityHashes.align("${set.name} z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}")
                val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, set.textScale, false, false)
                val bitmap = renderer.executeJob(job)
                    ?: throw IllegalStateException("renderSet(${set.name}), no bitmap for $tile")
                onTile(tile, bitmap)
                bitmap.decrementRefCount()
            }
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
        return labelStore
    }

    fun tileFileName(set: RenderSet, tile: Tile, extension: String): String {
        return "${set.name}_z${tile.zoomLevel}_${tile.tileX}_${tile.tileY}.$extension"
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
    data class RenderSet(
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
