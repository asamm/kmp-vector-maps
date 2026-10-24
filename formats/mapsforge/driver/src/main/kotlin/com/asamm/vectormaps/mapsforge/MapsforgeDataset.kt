/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.StyleSelection
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File

/**
 * Mapsforge `.map` files rendered as one multi-map. Each renderer opens its own readers in its
 * label language, so renderers created before [addMap] keep the earlier file set.
 */
class MapsforgeDataset internal constructor(
    override val driver: MapsforgeDriver,
    files: List<File>,
) : MapDataset {

    private val lock = Any()

    @Volatile
    private var files: List<File> = emptyList()

    @Volatile
    private var currentBounds: GeoBounds? = null

    init {
        require(files.isNotEmpty()) {
            "MapsforgeDataset(), no files"
        }
        for (file in files) {
            require(addMap(file)) {
                "MapsforgeDataset(), not a readable Mapsforge map: $file"
            }
        }
    }

    override val bounds: GeoBounds
        get() = currentBounds!!

    override fun addMap(file: File): Boolean {
        val info = try {
            driver.probe(file)
        } catch (e: Exception) {
            null
        } ?: return false
        synchronized(lock) {
            if (file in files) {
                return false
            }
            files = files + file
            currentBounds = currentBounds?.union(info.bounds) ?: info.bounds
        }
        return true
    }

    /**
     * Opens the readers and compiles the theme; the device scale goes into the renderer's own
     * `DisplayModel`, so the JVM-wide Mapsforge device scale must stay 1. Throws when the theme file
     * changed since [MapsforgeDriver.loadStyle]: load the style again.
     */
    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
    ): MapsforgeRenderer {
        require(style is MapsforgeStyle) {
            "createRenderer(), not a Mapsforge style: ${style.id}"
        }
        check(style.file.lastModified() == style.lastModified) {
            "createRenderer(), ${style.file} changed since loadStyle"
        }
        check(DisplayModel.getDeviceScaleFactor() == 1.0f) {
            "createRenderer(), Mapsforge device scale ${DisplayModel.getDeviceScaleFactor()}, " +
                "expected 1; pass it as RenderConfig.deviceScale"
        }
        // same order as a plain Mapsforge setup, which the golden test compares against
        val displayModel = DisplayModel()
        displayModel.setUserScaleFactor(config.deviceScale)
        displayModel.setFixedTileSize(config.tileSizePx)
        // larger text wraps later, as in Locus
        displayModel.setMaxTextWidthFactor(MAX_TEXT_WIDTH_FACTOR * config.textScale)
        val store = openStore(language = config.language)
        val themeFuture = try {
            RenderThemeFuture(
                driver.graphicFactory,
                ExternalRenderTheme(style.file, style.menuCallback(selection)),
                displayModel,
            )
        } catch (e: Exception) {
            store.close()
            throw e
        }
        try {
            themeFuture.run()
            // the renderer only logs a parse failure; fail here with the real cause
            themeFuture.get()
        } catch (e: Exception) {
            themeFuture.decrementRefCount()
            store.close()
            throw e
        }
        val isInTile = config.labelMode == LabelMode.IN_TILE
        return MapsforgeRenderer(
            key = RenderKey(styleId = style.id, selection = selection, config = config),
            graphicFactory = driver.graphicFactory,
            store = store,
            themeFuture = themeFuture,
            displayModel = displayModel,
            // in-tile labels look up already drawn neighbours in the tile cache
            tileCache = if (isInTile) DrawnTiles(TILE_CACHE_SIZE) else null,
            labelStore = if (isInTile) null else TileBasedLabelStore(LABEL_STORE_SIZE),
        )
    }

    override fun close() {
        // nothing open: renderers own their readers
    }

    private fun openStore(language: String?): MapDataStore {
        val files = files
        if (files.size == 1) {
            return CheckedMapFile(file = files[0], language = language)
        }
        // as Locus: deduplicating sorts the ways of every tile, which changes the draw order
        val result = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        try {
            for (file in files) {
                result.addMapDataStore(CheckedMapFile(file = file, language = language), false, false)
            }
        } catch (e: Exception) {
            result.close()
            throw e
        }
        return result
    }

    private companion object {

        const val TILE_CACHE_SIZE = 64

        const val LABEL_STORE_SIZE = 100

        // Mapsforge's default
        const val MAX_TEXT_WIDTH_FACTOR = 0.7f
    }
}
