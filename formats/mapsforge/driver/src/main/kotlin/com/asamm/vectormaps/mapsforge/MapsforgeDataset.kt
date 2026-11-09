/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.mapsforge.map.datastore.MapDataStore
import com.asamm.vectormaps.mapsforge.map.datastore.MultiMapDataStore
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
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
            // a relative and an absolute path to one file are the same map
            if (files.any { it.absoluteFile.normalize() == file.absoluteFile.normalize() }) {
                return false
            }
            files = files + file
            currentBounds = currentBounds?.union(info.bounds) ?: info.bounds
        }
        return true
    }

    /**
     * Opens the readers and shares the style's compiled theme, compiling it when the style has none
     * for these settings; the device scale goes into the theme's own `DisplayModel`, whatever the
     * Mapsforge statics hold. Throws when the theme file changed since [MapsforgeDriver.loadStyle]
     * or the style is closed: load the style again.
     */
    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        hooks: RenderHooks,
    ): MapsforgeRenderer {
        return createRenderer(
            style = style,
            selection = selection,
            config = config,
            labelStoreTiles = LABEL_STORE_SIZE,
            hooks = hooks,
        )
    }

    /**
     * As [createRenderer], with a label store of [labelStoreTiles] tiles: at least the
     * tiles a host's screen covers, or labels of visible tiles drop out of the store.
     */
    fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        labelStoreTiles: Int,
        hooks: RenderHooks = RenderHooks.NONE,
    ): MapsforgeRenderer {
        require(labelStoreTiles > 0) {
            "createRenderer(), labelStoreTiles $labelStoreTiles"
        }
        require(style is MapsforgeStyle) {
            "createRenderer(), not a Mapsforge style: ${style.id}"
        }
        check(style.file.lastModified() == style.lastModified) {
            "createRenderer(), ${style.file} changed since loadStyle"
        }
        val store = openStore(language = config.language)
        val theme = try {
            style.acquireTheme(
                graphicFactory = driver.graphicFactory,
                selection = selection,
                config = config,
            )
        } catch (e: Exception) {
            store.close()
            throw e
        }
        try {
            return MapsforgeRenderer(
                key = RenderKey(styleId = style.id, selection = selection, config = config),
                graphicFactory = driver.graphicFactory,
                store = store,
                themeFuture = theme.themeFuture,
                displayModel = theme.displayModel,
                labelStore = TileBasedLabelStore(labelStoreTiles),
                hooks = hooks,
            )
        } catch (e: Exception) {
            theme.themeFuture.decrementRefCount()
            store.close()
            throw e
        }
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

        const val LABEL_STORE_SIZE = 100
    }
}
