/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.render.ImgRenderer
import com.asamm.vectormaps.garminimg.render.RenderTarget
import com.asamm.vectormaps.garminimg.render.StyleResolver
import com.asamm.vectormaps.garminimg.render.Viewport
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import com.asamm.vectormaps.core.RenderTarget as CoreRenderTarget

/**
 * Renders through the engine's `ImgRenderer` into [GarminTileTarget]s, one tile at a time: the
 * engine reads through shared positions until M4.3. Labels stop at the tile edge until M6.1.
 */
class GarminRenderer internal constructor(
    override val key: RenderKey,
    private val img: ImgFile,
    styleResolver: StyleResolver,
    private val canvasFactory: GarminCanvasFactory,
    private val options: GarminOptions,
    private val bounds: GeoBounds,
) : MapRenderer {

    override val maxParallelism: Int = 1

    private val lock = Any()

    private val closed = AtomicBoolean(false)

    private val renderer = ImgRenderer(img, styleResolver)

    override fun render(tile: TileRequest, target: CoreRenderTarget, isCancelled: () -> Boolean): RenderResult {
        require(target is GarminTileTarget) {
            "render(), unsupported target ${target::class.java.name}"
        }
        val tileSize = key.config.tileSizePx
        require(target.widthPx == tileSize) {
            "render(), target ${target.widthPx} px, tile $tileSize px"
        }
        synchronized(lock) {
            check(!closed.get()) {
                "render($tile), renderer closed"
            }
            if (isCancelled()) {
                return RenderResult.CANCELLED
            }
            val viewport = viewportOf(tile, tileSize)
            if (viewport.maxLonDeg <= bounds.minLon
                || viewport.minLonDeg >= bounds.maxLon
                || viewport.maxLatDeg <= bounds.minLat
                || viewport.minLatDeg >= bounds.maxLat
            ) {
                return RenderResult.EMPTY
            }
            val canvas = canvasFactory.create(
                sizePx = tileSize,
                antiAliased = options.antiAliasing,
                renderScale = key.config.deviceScale,
            )
            renderer.render(
                viewport = viewport,
                target = canvas,
                objectKindFilter = options.objectKinds,
                level = options.level,
            )
            target.canvas = canvas
            return RenderResult.DRAWN
        }
    }

    /**
     * Renders queued behind a running one fail once it ends; waits for that one.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        synchronized(lock) {
            img.close()
        }
    }

    private fun viewportOf(tile: TileRequest, sizePx: Int): Viewport {
        val n = (1 shl tile.zoom).toDouble()
        return Viewport.ofDegrees(
            widthPx = sizePx,
            heightPx = sizePx,
            minLonDeg = tile.x / n * 360.0 - 180.0,
            maxLonDeg = (tile.x + 1) / n * 360.0 - 180.0,
            minLatDeg = latitudeOf(tile.y + 1, n),
            maxLatDeg = latitudeOf(tile.y, n),
        )
    }

    private fun latitudeOf(y: Int, n: Double): Double {
        return atan(sinh(PI * (1.0 - 2.0 * y / n))) * 180.0 / PI
    }
}

/**
 * Creates the engine canvas a tile is drawn into, such as `AwtRenderTarget` or
 * `AndroidRenderTarget`.
 */
fun interface GarminCanvasFactory {

    /**
     * @param renderScale factor on TYP sizes, strokes and text; the renderer's device scale
     */
    fun create(sizePx: Int, antiAliased: Boolean, renderScale: Float): RenderTarget
}

/**
 * Receives the canvas the renderer created through its [GarminCanvasFactory]. After
 * [RenderResult.DRAWN] the caller owns [canvas] and reads its pixels, such as
 * `AwtRenderTarget.image`; the canvas holds nothing else, so dropping it releases it (recycle an
 * `AndroidRenderTarget.bitmap` to free it early).
 */
class GarminTileTarget(sizePx: Int) : CoreRenderTarget {

    override val widthPx: Int = sizePx

    override val heightPx: Int = sizePx

    var canvas: RenderTarget? = null
        internal set
}
