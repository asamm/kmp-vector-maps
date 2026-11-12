/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.source.PmTilesSource
import java.io.File

/**
 * One or more tile sources read as one map: a tile comes from the first source that has it, so
 * the sources should not overlap, and they share one max zoom, above which tiles are cut from
 * their ancestors. Renderers read the sources the dataset had when they were created; the sources
 * close once the dataset and all its renderers are closed.
 */
class MvtDataset internal constructor(
    override val driver: MvtDriver,
    sources: List<MvtTileSource>,
) : MapDataset {

    private val lock = Any()

    private val sources = ArrayList(sources)

    // the added files, so one is not opened twice
    private val files = HashSet<File>()

    private var renderers = 0

    private var closed = false

    override val bounds: GeoBounds
        get() {
            return synchronized(lock) { sources.toList() }.map(MvtDriver::boundsOf).reduce(GeoBounds::union)
        }

    /**
     * False for a file already added, not a PMTiles map, or of another max zoom than the dataset's.
     */
    override fun addMap(file: File): Boolean {
        return try {
            add(file)
        } catch (e: Exception) {
            false
        }
    }

    // throws when the file is not a PMTiles map or its max zoom differs; false when already added
    internal fun add(file: File): Boolean {
        val key = file.absoluteFile.normalize()
        val source = PmTilesSource(file)
        val error = synchronized(lock) {
            when {
                closed -> "add($file), dataset closed"
                key in files -> null
                sources.isNotEmpty()
                    && sources[0].maxZoom != source.maxZoom -> {
                    "add($file), max zoom ${source.maxZoom}, the dataset's ${sources[0].maxZoom}"
                }
                else -> {
                    files += key
                    sources += source
                    return true
                }
            }
        }
        source.close()
        if (error != null) {
            throw IllegalArgumentException(error)
        }
        return false
    }

    /**
     * Creates a renderer for an [MvtStyle] of this dataset's driver; the selection is unused, as
     * MapLibre styles have no layer menu. Only [RenderConfig.tileSizePx] applies so far.
     */
    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        hooks: RenderHooks,
    ): MvtRenderer {
        return createRenderer(
            style = style,
            selection = selection,
            config = config,
            labelStoreTiles = LABEL_STORE_TILES,
            hooks = hooks,
        )
    }

    /**
     * Like [createRenderer]; [labelStoreTiles] is at least the tiles a host's screen covers, or
     * labels of visible tiles drop out of the label store.
     */
    fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        labelStoreTiles: Int,
        hooks: RenderHooks = RenderHooks.NONE,
    ): MvtRenderer {
        require(labelStoreTiles > 0) {
            "createRenderer(), labelStoreTiles $labelStoreTiles"
        }
        require(style is MvtStyle) {
            "createRenderer(), style ${style::class.java.name} is not an MVT style"
        }
        val current = synchronized(lock) {
            check(!closed) {
                "createRenderer(), dataset closed"
            }
            renderers++
            sources.toList()
        }
        return MvtRenderer(
            dataset = this,
            sources = current,
            style = style,
            selection = selection,
            config = config,
            labelStoreTiles = labelStoreTiles,
            hooks = hooks,
        )
    }

    // called once by each renderer's close
    internal fun release() {
        val toClose = synchronized(lock) {
            renderers--
            if (closed
                && renderers == 0
            ) {
                sources.toList()
            } else {
                emptyList()
            }
        }
        toClose.forEach(MvtTileSource::close)
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed
                || renderers > 0
            ) {
                closed = true
                emptyList()
            } else {
                closed = true
                sources.toList()
            }
        }
        toClose.forEach(MvtTileSource::close)
    }

    private companion object {

        // the labels of more tiles than a screen shows, so a pan keeps its neighbours' labels
        const val LABEL_STORE_TILES = 128
    }
}
