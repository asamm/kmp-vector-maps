/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import android.content.res.AssetManager
import androidx.test.platform.app.InstrumentationRegistry
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File

/**
 * Map, theme and one long-lived renderer in Locus' primary label mode, set up like the JVM
 * benchmarks in `dev/bench/` (512 px tiles, device scale 1) so the two baselines compare.
 */
class BenchScene : AutoCloseable {

    val store = MapFile(mapFile())
    private val displayModel = newDisplayModel()
    private val themeFuture = parseTheme(displayModel = displayModel)

    // no tile cache, a label store, renderLabels false, cacheLabels true, no hillshading
    private val renderer = DatabaseRenderer(
        store,
        AndroidGraphicFactory.INSTANCE,
        null,
        TileBasedLabelStore(100),
        false,
        true,
        null,
    )

    /**
     * Renders one tile; Mapsforge only logs a failed render and returns null, which must not be
     * timed as a fast result.
     */
    fun render(tile: Tile): TileBitmap {
        // text scale 1, opaque, not labels only
        val job = RendererJob(tile, store, themeFuture, displayModel, 1.0f, false, false)
        return checkNotNull(renderer.executeJob(job)) {
            "render($tile), failed"
        }
    }

    override fun close() {
        themeFuture.decrementRefCount()
        store.close()
    }

    companion object {

        const val TILE_SIZE = 512

        private val fixtures: File by lazy {
            prepare()
        }

        fun newDisplayModel(): DisplayModel {
            return DisplayModel().apply {
                setFixedTileSize(TILE_SIZE)
            }
        }

        fun parseTheme(displayModel: DisplayModel): RenderThemeFuture {
            // before reading INSTANCE: the first access to fixtures creates the graphic factory
            val themeFile = themeFile()
            val future = RenderThemeFuture(
                AndroidGraphicFactory.INSTANCE,
                ExternalRenderTheme(themeFile),
                displayModel,
            )
            future.run()
            future.get()
            return future
        }

        /**
         * The tile at the `bench.center` argument if given, else at the map's start position or
         * bounding-box centre; it must lie inside the map, or the render is just background.
         */
        fun centerTile(store: MapFile, zoom: Byte): Tile {
            val center = argument("bench.center")
                ?.split(",")
                ?.let { LatLong(it[0].trim().toDouble(), it[1].trim().toDouble()) }
                ?: store.startPosition()
                ?: store.boundingBox().centerPoint
            check(store.boundingBox().contains(center)) {
                "centerTile($center), outside the map ${store.boundingBox()}"
            }
            return Tile(
                MercatorProjection.longitudeToTileX(center.longitude, zoom),
                MercatorProjection.latitudeToTileY(center.latitude, zoom),
                zoom,
                TILE_SIZE,
            )
        }

        /**
         * The `bench.mapFile` argument, else the bundled Monaco map; prepares the fixtures and
         * the graphic factory on first use.
         */
        fun mapFile(): File {
            val dir = fixtures
            return argument("bench.mapFile")?.let(::File) ?: File(dir, "maps/monaco.osm.map")
        }

        fun themeFile(): File {
            return File(fixtures, "themes/lomaps/theme.xml")
        }

        private fun argument(name: String): String? {
            return InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
        }

        /**
         * Copies the bundled fixtures to the cache dir once and sets up Mapsforge's graphics for the
         * test process at device scale 1.
         */
        private fun prepare(): File {
            val context = InstrumentationRegistry.getInstrumentation().context
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            AndroidGraphicFactory.createInstance(app as android.app.Application)
            // createInstance sets the device density; the baseline compares at scale 1
            DisplayModel.setDeviceScaleFactor(1.0f)
            val target = File(app.cacheDir, "fixtures")
            copyAssets(assets = context.assets, path = "maps", target = File(target, "maps"))
            copyAssets(assets = context.assets, path = "themes", target = File(target, "themes"))
            return target
        }

        private fun copyAssets(assets: AssetManager, path: String, target: File) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    assets.open(path).use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
                return
            }
            for (child in children) {
                copyAssets(assets = assets, path = "$path/$child", target = File(target, child))
            }
        }
    }
}
