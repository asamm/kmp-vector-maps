/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.TileRequest
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Renders through Mapsforge's `DatabaseRenderer` into [TileBitmapTarget]s. Never returns
 * [RenderResult.EMPTY]: Mapsforge fills tiles without data with the theme background. [close]
 * waits for running renders, so it must not be called from inside [render].
 */
class MapsforgeRenderer internal constructor(
    override val key: RenderKey,
    graphicFactory: GraphicFactory,
    private val store: MapDataStore,
    private val themeFuture: RenderThemeFuture,
    private val displayModel: DisplayModel,
    private val tileCache: TileCache?,
    labelStore: TileBasedLabelStore?,
) : MapRenderer {

    /**
     * Labels of the rendered tiles for a separate label pass; null for in-tile labels.
     */
    val labelStore: TileBasedLabelStore? = labelStore

    override val maxParallelism: Int = Runtime.getRuntime().availableProcessors()

    private val permits = Semaphore(maxParallelism)

    private val closed = AtomicBoolean(false)

    private val renderer = DatabaseRenderer(
        store,
        graphicFactory,
        tileCache,
        labelStore,
        tileCache != null,
        true,
        null,
    )

    override fun render(tile: TileRequest, target: RenderTarget, isCancelled: () -> Boolean): RenderResult {
        require(target is TileBitmapTarget) {
            "render(), unsupported target ${target::class.java.name}"
        }
        val tileSize = key.config.tileSizePx
        require(target.widthPx == tileSize) {
            "render(), target ${target.widthPx} px, tile $tileSize px"
        }
        permits.acquireUninterruptibly()
        try {
            check(!closed.get()) {
                "render($tile), renderer closed"
            }
            if (isCancelled()) {
                return RenderResult.CANCELLED
            }
            val job = RendererJob(
                Tile(tile.x, tile.y, tile.zoom.toByte(), tileSize),
                store,
                themeFuture,
                displayModel,
                key.config.textScale,
                false,
                false,
            )
            // Mapsforge logs the cause and returns null
            target.bitmap = renderer.executeJob(job)
                ?: throw IllegalStateException("render($tile), Mapsforge drew no tile, see its log")
            return RenderResult.DRAWN
        } finally {
            permits.release()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        // waits for running renders; later ones fail the closed check
        permits.acquireUninterruptibly(maxParallelism)
        try {
            tileCache?.destroy()
            themeFuture.decrementRefCount()
            store.close()
        } finally {
            permits.release(maxParallelism)
        }
    }
}

/**
 * Receives the tile bitmap Mapsforge allocates; Mapsforge cannot draw into a host bitmap yet (M5).
 * After [RenderResult.DRAWN] the caller owns one reference to [bitmap] and releases it with
 * `decrementRefCount()`.
 */
class TileBitmapTarget(sizePx: Int) : RenderTarget {

    override val widthPx: Int = sizePx

    override val heightPx: Int = sizePx

    var bitmap: TileBitmap? = null
        internal set
}
