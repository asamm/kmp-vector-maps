/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.HillshadeLayer
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
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.Colors
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.symbol.MvtLabel
import com.asamm.vectormaps.mvt.symbol.SymbolLayout
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import com.asamm.vectormaps.core.RenderTarget as CoreRenderTarget

/**
 * Renders a dataset's tiles with one MapLibre style into [MvtTileTarget]s, on as many threads as
 * there are cores; tiles above the sources' max zoom are cut from that zoom's tiles, kept
 * decoded in a small cache their children share. Tiles carry no labels: each tile's symbols go to
 * the label store for [labelPass] (D39). Its sources stay open until it is closed; [close] waits
 * for running renders, so it must not be called from inside one.
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

    private val permits = Semaphore(maxParallelism)

    private val closed = AtomicBoolean()

    // the renders in flight and a few neighbours, so a render's draw and layout find its tile
    private val decodedTiles = maxOf(DECODED_TILES, maxParallelism + 4)

    // the data tiles decoded last; tiles above the data zoom share their ancestor
    private val decoded = object : LinkedHashMap<Long, VectorTile>(decodedTiles, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, VectorTile>): Boolean {
            return size > decodedTiles
        }
    }

    // data tiles being read and decoded, by cache key
    private val loading = ConcurrentHashMap<Long, CompletableFuture<VectorTile?>>()

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
        permits.acquireUninterruptibly()
        try {
            check(!closed.get()) {
                "render($tile), renderer closed"
            }
            return renderTile(tile = tile, target = target, isCancelled = isCancelled)
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
            dataset.release()
        } finally {
            permits.release(maxParallelism)
        }
    }

    private fun renderTile(tile: TileRequest, target: MvtTileTarget, isCancelled: () -> Boolean): RenderResult {
        if (isCancelled()) {
            return RenderResult.CANCELLED
        }
        // the data tile first, so a tile without data allocates no canvas
        val dz = maxOf(0, tile.zoom - maxDataZoom)
        if (tile(zoom = tile.zoom - dz, x = tile.x shr dz, y = tile.y shr dz) == null) {
            return RenderResult.EMPTY
        }
        val tileSize = key.config.tileSizePx
        val canvas = graphics.createTarget(widthPx = tileSize, heightPx = tileSize, sprite = style.sprite)
        val drawn = tiles.render(
            zoom = tile.zoom,
            x = tile.x,
            y = tile.y,
            target = canvas,
            isCancelled = isCancelled,
        ) { layer ->
            val hook = hooks.hillshade ?: return@render
            try {
                val hillshade = hillshadeLayer(layer = layer, zoom = tile.zoom)
                canvas.withPlatformCanvas { platform ->
                    hook.drawTile(canvas = platform, tile = tile, tileSizePx = tileSize, layer = hillshade)
                }
            } catch (e: Exception) {
                // the tile stays, unshaded
                LOGGER.log(Level.SEVERE, "hillshade($tile, ${layer.id})", e)
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

    // the style layer with its source and its paint at the zoom, as the host reads them
    private fun hillshadeLayer(layer: StyleLayer, zoom: Int): HillshadeLayer {
        val source = layer.source?.let { style.style.sources[it] }
        val ctx = EvalContext(zoom.toDouble())
        val paint = HashMap<String, Any>()
        for ((name, expression) in layer.paint) {
            val value = hostValue(name = name, value = expression.eval(ctx)) ?: continue
            paint[name] = value
        }
        val type = when (layer.type) {
            LayerType.HILLSHADE -> HillshadeLayer.Type.HILLSHADE
            else -> HillshadeLayer.Type.COLOR_RELIEF
        }
        return HillshadeLayer(
            id = layer.id,
            type = type,
            source = layer.source ?: "",
            sourceUrl = source?.url,
            tiles = source?.tiles.orEmpty(),
            encoding = source?.encoding ?: "mapbox",
            paint = paint,
        )
    }

    // the decoded tile of the first source that has it; threads missing the same tile at once
    // wait for one read and decode
    private fun tile(zoom: Int, x: Int, y: Int): VectorTile? {
        // the tiles of all lower zooms, then the tile's row-major index; unique up to
        // TileRequest.MAX_ZOOM (30), below 2^61
        val key = ((1L shl 2 * zoom) - 1) / 3 + (y.toLong() shl zoom) + x
        synchronized(decoded) {
            decoded[key]?.let {
                return it
            }
        }
        val mine = CompletableFuture<VectorTile?>()
        val running = loading.putIfAbsent(key, mine)
        if (running != null) {
            try {
                return running.join()
            } catch (e: CompletionException) {
                throw e.cause ?: e
            }
        }
        try {
            // another thread may have stored it since the miss
            val tile = synchronized(decoded) { decoded[key] } ?: read(zoom = zoom, x = x, y = y)
            if (tile != null) {
                synchronized(decoded) {
                    decoded[key] = tile
                }
            }
            mine.complete(tile)
            return tile
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            loading.remove(key)
        }
    }

    private fun read(zoom: Int, x: Int, y: Int): VectorTile? {
        for (source in sources) {
            if (zoom !in source.minZoom..source.maxZoom) {
                continue
            }
            val bytes = source.tile(zoom, x, y) ?: continue
            return VectorTile.decode(MvtTileSource.uncompressed(bytes))
        }
        return null
    }

    private companion object {

        val LOGGER: Logger = Logger.getLogger(MvtRenderer::class.java.name)

        // a dense data tile keeps ~2 MB with its geometry (docs/performance.md M10.3a)
        const val DECODED_TILES = 12

        // colours as ARGB, the colour-relief stops as metres to ARGB
        fun hostValue(name: String, value: Any?): Any? {
            return when {
                value is Color -> value.argb
                value is String
                    && name.endsWith("-color") -> Colors.parse(value)?.argb
                value is List<*> -> value.map {
                    if (it is Pair<*, *>) it.first to hostValue(name = name, value = it.second) else it
                }
                else -> value
            }
        }
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
