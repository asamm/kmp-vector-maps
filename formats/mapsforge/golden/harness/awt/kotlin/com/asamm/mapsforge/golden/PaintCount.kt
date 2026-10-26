/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import java.lang.ref.WeakReference

/**
 * Paints created and still alive while one long-lived renderer sweeps the zoom levels twice, as
 * Locus' renderer lives through zooming; a deterministic proxy for the per-zoom Paint caches behind
 * locus-core finding F2. `live` is what a cache bound (M5.3b) has to limit.
 */
object PaintCount {

    private val SWEEP_ZOOMS = 8..20
    private const val SWEEPS = 2

    /**
     * Writes Paints created and alive after the theme parse and after each rendered tile into
     * `file`; needs the serial collector, so the full GC per step clears the same Paints each run.
     */
    fun write(
        mapFile: File,
        themeFile: File,
        tileSize: Int,
        lat: Double,
        lon: Double,
        file: File,
    ) {
        DisplayModel.setDeviceScaleFactor(1.0f)
        val graphicFactory = CountingGraphicFactory(AwtGraphicFactory.INSTANCE)
        val displayModel = DisplayModel()
        displayModel.setFixedTileSize(tileSize)
        val mapDataStore = MapFile(mapFile)
        val out = mutableListOf<String>()
        val xmlTheme = ExternalRenderTheme(themeFile, Corpus.MenuSelection("hike_bike"))
        val themeFuture = RenderThemeFuture(graphicFactory, xmlTheme, displayModel)
        try {
            themeFuture.run()
            themeFuture.get()
            out += "theme"
            out += "  parse created=${graphicFactory.takeCount()} live=${graphicFactory.liveCount()}"
            // Locus' primary mode: labels kept in a store, not drawn into tiles
            val renderer = DatabaseRenderer(
                mapDataStore,
                graphicFactory,
                null,
                TileBasedLabelStore(100),
                false,
                true,
                null,
            )
            for (sweep in 1..SWEEPS) {
                out += "sweep $sweep"
                var total = 0
                for (zoom in SWEEP_ZOOMS) {
                    val z = zoom.toByte()
                    val tile = Tile(
                        MercatorProjection.longitudeToTileX(lon, z),
                        MercatorProjection.latitudeToTileY(lat, z),
                        z,
                        tileSize,
                    )
                    val job = RendererJob(tile, mapDataStore, themeFuture, displayModel, 1.0f, false, false)
                    val bitmap = checkNotNull(renderer.executeJob(job)) {
                        "write(), no bitmap for $tile"
                    }
                    bitmap.decrementRefCount()
                    val created = graphicFactory.takeCount()
                    total += created
                    out += "  z$zoom created=$created total=$total live=${graphicFactory.liveCount()}"
                }
            }
        } finally {
            themeFuture.decrementRefCount()
            mapDataStore.close()
        }
        file.writeText(out.joinToString(separator = "\n", postfix = "\n"))
    }

    /**
     * A graphic factory that tracks every Paint it creates, new or copied, and delegates the rest.
     */
    private class CountingGraphicFactory(private val delegate: GraphicFactory) : GraphicFactory by delegate {

        private var count = 0
        private val created = mutableListOf<WeakReference<Paint>>()

        override fun createPaint(): Paint {
            return track(delegate.createPaint())
        }

        override fun createPaint(paint: Paint): Paint {
            return track(delegate.createPaint(paint))
        }

        /**
         * Paints still reachable after a full collection.
         */
        fun liveCount(): Int {
            System.gc()
            created.removeAll { it.get() == null }
            return created.size
        }

        /**
         * Paints created since the last call.
         */
        fun takeCount(): Int {
            val result = count
            count = 0
            return result
        }

        private fun track(paint: Paint): Paint {
            count++
            created += WeakReference(paint)
            return paint
        }
    }
}
