/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderHooks
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.TileLabelStore
import com.asamm.vectormaps.mvt.render.MvtRenderTarget
import com.asamm.vectormaps.mvt.render.TileRenderer
import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import com.asamm.vectormaps.mvt.symbol.SymbolLayout
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import com.asamm.vectormaps.core.RenderTarget as CoreRenderTarget

/**
 * Renders a dataset's tiles with one MapLibre style into [MvtTileTarget]s, on as many threads as
 * there are cores; tiles above the sources' max zoom are cut from that zoom's tiles, kept
 * decoded in a small cache their children share. Tiles carry no labels: each tile's symbols go to
 * the label store for [labelPass] (D39). Its sources stay open until it is closed.
 */
class MvtRenderer internal constructor(
    private val dataset: MvtDataset,
    private val sources: List<MvtTileSource>,
    private val style: MvtStyle,
    selection: StyleSelection,
    config: RenderConfig,
    labelStoreTiles: Int,
    private val hooks: RenderHooks,
) : MapRenderer {

    override val key: RenderKey = RenderKey(styleId = style.id, selection = selection, config = config)

    override val maxParallelism: Int = Runtime.getRuntime().availableProcessors()

    private val graphics = dataset.driver.graphics

    private val maxDataZoom = sources.maxOf { it.maxZoom }

    private val closed = AtomicBoolean()

    // the data tiles decoded last; tiles above the data zoom share their ancestor
    private val decoded = object : LinkedHashMap<Long, VectorTile>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, VectorTile>): Boolean {
            return size > DECODED_TILES
        }
    }

    private val tiles = TileRenderer(style = style.style, maxDataZoom = maxDataZoom, tileSource = ::tile)

    private val layout = SymbolLayout(
        style = style.style,
        sprite = style.spriteIndex,
        metrics = graphics.metrics,
        maxDataZoom = maxDataZoom,
        tileSource = ::tile,
    )

    private val labelStore = TileLabelStore<MvtLabel>(capacity = labelStoreTiles)

    override val labelPass: MvtLabelPass = MvtLabelPass(
        source = labelStore,
        tileSize = config.tileSizePx,
        graphics = graphics,
        sprite = style.sprite,
    )

    /**
     * Ids of the style layers renders and layouts skipped because evaluating them threw, such as
     * a malformed expression meeting unexpected tile data.
     */
    val failedLayers: Set<String>
        get() {
            return tiles.failedLayers + layout.failedLayers
        }

    override fun render(tile: TileRequest, target: CoreRenderTarget, isCancelled: () -> Boolean): RenderResult {
        require(target is MvtTileTarget) {
            "render(), unsupported target ${target::class.java.name}"
        }
        val tileSize = key.config.tileSizePx
        require(target.widthPx == tileSize) {
            "render(), target ${target.widthPx} px, tile $tileSize px"
        }
        check(!closed.get()) {
            "render($tile), renderer closed"
        }
        if (isCancelled()) {
            return RenderResult.CANCELLED
        }
        // the data tile first, so a tile without data allocates no canvas
        val dz = maxOf(0, tile.zoom - maxDataZoom)
        if (tile(zoom = tile.zoom - dz, x = tile.x shr dz, y = tile.y shr dz) == null) {
            return RenderResult.EMPTY
        }
        val canvas = graphics.createTarget(widthPx = tileSize, heightPx = tileSize, sprite = style.sprite)
        val drawn = tiles.render(
            zoom = tile.zoom,
            x = tile.x,
            y = tile.y,
            target = canvas,
            isCancelled = isCancelled,
        ) {
            val hook = hooks.hillshade ?: return@render
            try {
                canvas.withPlatformCanvas { platform ->
                    hook.drawTile(canvas = platform, tile = tile, tileSizePx = tileSize)
                }
            } catch (e: Exception) {
                // the tile stays, unshaded
                LOGGER.log(Level.SEVERE, "hillshade($tile)", e)
            }
        }
        if (!drawn) {
            return RenderResult.EMPTY
        }
        if (isCancelled()) {
            return RenderResult.CANCELLED
        }
        labelStore.store(tile = tile, labels = layout.labels(tile.zoom, tile.x, tile.y, tileSize))
        target.canvas = canvas
        return RenderResult.DRAWN
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            labelPass.close()
            dataset.release()
        }
    }

    // the decoded tile of the first source that has it
    private fun tile(zoom: Int, x: Int, y: Int): VectorTile? {
        // zooms up to TileRequest.MAX_ZOOM (30) keep x and y below 2^29
        val key = (zoom.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()
        synchronized(decoded) {
            decoded[key]?.let {
                return it
            }
        }
        for (source in sources) {
            if (zoom !in source.minZoom..source.maxZoom) {
                continue
            }
            val bytes = source.tile(zoom, x, y) ?: continue
            val tile = VectorTile.decode(MvtTileSource.uncompressed(bytes))
            synchronized(decoded) {
                decoded[key] = tile
            }
            return tile
        }
        return null
    }

    private companion object {

        val LOGGER: Logger = Logger.getLogger(MvtRenderer::class.java.name)

        const val DECODED_TILES = 32
    }
}

/**
 * Receives the canvas the renderer drew through the driver's graphics. After
 * [RenderResult.DRAWN] the caller owns [canvas] and reads its pixels, such as
 * `AwtMvtRenderTarget.image`.
 */
class MvtTileTarget(sizePx: Int) : CoreRenderTarget {

    override val widthPx: Int = sizePx

    override val heightPx: Int = sizePx

    var canvas: MvtRenderTarget? = null
        internal set
}
