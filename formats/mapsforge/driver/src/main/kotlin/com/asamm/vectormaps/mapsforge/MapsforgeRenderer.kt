/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.graphics.TileBitmap
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.datastore.MapDataStore
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
import com.asamm.vectormaps.mapsforge.map.layer.renderer.DatabaseRenderer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.HillshadingDrawer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Renders through Mapsforge's `DatabaseRenderer` into [TileBitmapTarget]s without labels, which go
 * to [labelStore] for [labelPass] (D39). Never returns [RenderResult.EMPTY]: Mapsforge fills tiles
 * without data with the theme background. [close] waits for running renders, so it must not be
 * called from inside [render].
 *
 * @param labelStore labels of the rendered tiles, for [labelPass]
 * @param hooks host drawing at the format's anchors, such as hillshading
 */
class MapsforgeRenderer internal constructor(
    override val key: RenderKey,
    graphicFactory: GraphicFactory,
    private val store: MapDataStore,
    private val themeFuture: RenderThemeFuture,
    internal val displayModel: DisplayModel,
    val labelStore: TileBasedLabelStore,
    hooks: RenderHooks,
) : MapRenderer {

    override val labelPass: MapsforgeLabelPass = MapsforgeLabelPass(
        labelStore = labelStore,
        tileSize = key.config.tileSizePx,
        graphicFactory = graphicFactory,
    )

    override val maxParallelism: Int = Runtime.getRuntime().availableProcessors()

    private val permits = Semaphore(maxParallelism)

    init {
        // shared theme Paints are locked while drawing when more than one thread renders
        displayModel.numberOfThreads = maxParallelism
    }

    private val closed = AtomicBoolean(false)

    private val renderer = DatabaseRenderer(
        store,
        graphicFactory,
        null,
        labelStore,
        false,
        true,
        hooks.hillshade?.let { hook ->
            HillshadingDrawer { canvas, tile ->
                canvas.withPlatformCanvas { platform ->
                    hook.drawTile(
                        canvas = platform,
                        tile = TileRequest(x = tile.tileX, y = tile.tileY, zoom = tile.zoomLevel.toInt()),
                        tileSizePx = tile.tileSize,
                    )
                }
            }
        },
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
        labelPass.close()
        // waits for running renders; later ones fail the closed check
        permits.acquireUninterruptibly(maxParallelism)
        try {
            themeFuture.decrementRefCount()
            store.close()
        } finally {
            permits.release(maxParallelism)
        }
    }
}

/**
 * Receives the tile bitmap Mapsforge allocates; drawing into a host bitmap comes with M8.1.
 * After [RenderResult.DRAWN] the caller owns one reference to [bitmap] and releases it with
 * `decrementRefCount()`.
 */
class TileBitmapTarget(sizePx: Int) : RenderTarget {

    override val widthPx: Int = sizePx

    override val heightPx: Int = sizePx

    var bitmap: TileBitmap? = null
        internal set
}
