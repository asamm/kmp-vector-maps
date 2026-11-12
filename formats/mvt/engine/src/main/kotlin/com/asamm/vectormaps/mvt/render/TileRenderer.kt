/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws the background, fill, line and circle layers of a [Style] for one tile; symbols are the
 * label pass's. Tiles above [maxDataZoom] are cut from their ancestor at that zoom (overzoom).
 *
 * @param tileSource the decoded tile at (zoom, x, y), or null when there is none
 */
class TileRenderer(
    private val style: Style,
    private val maxDataZoom: Int,
    private val tileSource: (zoom: Int, x: Int, y: Int) -> VectorTile?,
) {

    /**
     * Ids of the layers a render skipped because evaluating them threw, such as a malformed
     * expression meeting unexpected tile data; the rest of the tile still draws.
     */
    val failedLayers: Set<String>
        get() {
            return failed
        }

    private val failed = ConcurrentHashMap.newKeySet<String>()

    /**
     * Renders tile (`zoom`, `x`, `y`) into `target`, whose width is the tile size; the style's
     * pixel sizes apply 1:1 at 512 px tiles, as MapLibre's.
     *
     * @return false when there is no data for the tile
     */
    fun render(
        zoom: Int,
        x: Int,
        y: Int,
        target: MvtRenderTarget,
        isCancelled: () -> Boolean,
    ): Boolean {
        val frame = DataTileFrame(zoom, x, y, target.widthPx, target.heightPx, maxDataZoom)
        val tile = tileSource(frame.dataZoom, frame.dataX, frame.dataY) ?: return false
        val pixelScale = frame.pixelScale
        val ctx = EvalContext(zoom.toDouble())
        for (layer in style.layers) {
            if (isCancelled()) {
                return true
            }
            if (!layer.isActive(ctx.zoom)) {
                continue
            }
            if (layer.type == LayerType.BACKGROUND) {
                ctx.feature = null
                val color = color(layer.paint["background-color"], ctx) ?: continue
                val opacity = number(layer.paint["background-opacity"], ctx, 1.0)
                target.drawBackground(color.withOpacity(opacity))
                continue
            }
            if (layer.type != LayerType.FILL
                && layer.type != LayerType.LINE
                && layer.type != LayerType.CIRCLE
            ) {
                continue
            }
            val source = tile.layers[layer.sourceLayer] ?: continue
            try {
                for (feature in source.features) {
                    ctx.feature = feature
                    if (layer.filter != null
                        && layer.filter.eval(ctx) != true
                    ) {
                        continue
                    }
                    val geometry = feature.decodeGeometry()
                    val screen = frame.project(geometry, source.extent)
                    val path = MvtPath(screen, geometry.partEnds, feature.type == GeometryType.POLYGON)
                    when (layer.type) {
                        LayerType.FILL -> drawFill(layer, path, feature.type, ctx, target, x, y)
                        LayerType.LINE -> drawLine(layer, path, feature.type, ctx, target, pixelScale)
                        else -> drawCircles(layer, path, ctx, target, pixelScale)
                    }
                }
            } catch (e: RuntimeException) {
                // a malformed style or tile skips this layer, not the tile
                failed.add(layer.id)
            }
        }
        return true
    }

    private fun drawFill(
        layer: StyleLayer,
        path: MvtPath,
        type: GeometryType,
        ctx: EvalContext,
        target: MvtRenderTarget,
        x: Int,
        y: Int,
    ) {
        if (type != GeometryType.POLYGON) {
            return
        }
        val opacity = number(layer.paint["fill-opacity"], ctx, 1.0)
        val antialias = layer.paint["fill-antialias"]?.eval(ctx) != false
        val pattern = layer.paint["fill-pattern"]
        if (pattern != null) {
            val name = pattern.eval(ctx) as? String ?: return
            target.fillPathPattern(
                path,
                name,
                opacity.toFloat(),
                antialias,
                x.toLong() * target.widthPx,
                y.toLong() * target.heightPx,
            )
            return
        }
        val color = color(layer.paint["fill-color"], ctx) ?: Color(0xff000000.toInt())
        target.fillPath(path, color.withOpacity(opacity), antialias)
    }

    private fun drawLine(
        layer: StyleLayer,
        path: MvtPath,
        type: GeometryType,
        ctx: EvalContext,
        target: MvtRenderTarget,
        pixelScale: Float,
    ) {
        if (type == GeometryType.POINT
            || layer.paint.containsKey("line-pattern")
        ) {
            return
        }
        val width = number(layer.paint["line-width"], ctx, 1.0).toFloat() * pixelScale
        if (width <= 0f) {
            return
        }
        val color = color(layer.paint["line-color"], ctx) ?: Color(0xff000000.toInt())
        val opacity = number(layer.paint["line-opacity"], ctx, 1.0)
        val cap = when (layer.layout["line-cap"]?.eval(ctx)) {
            "round" -> LineCap.ROUND
            "square" -> LineCap.SQUARE
            else -> LineCap.BUTT
        }
        val join = when (layer.layout["line-join"]?.eval(ctx)) {
            "round" -> LineJoin.ROUND
            "bevel" -> LineJoin.BEVEL
            else -> LineJoin.MITER
        }
        // dash lengths are in line widths
        val dash = (layer.paint["line-dasharray"]?.eval(ctx) as? List<*>)
            ?.map { ((it as? Double ?: 0.0) * width).toFloat().coerceAtLeast(0.01f) }
            ?.toFloatArray()
        val offset = number(layer.paint["line-offset"], ctx, 0.0).toFloat() * pixelScale
        val drawn = if (offset != 0f) LineOffset.offset(path, offset) else path
        target.strokePath(drawn, color.withOpacity(opacity), width, cap, join, dash)
    }

    private fun drawCircles(
        layer: StyleLayer,
        path: MvtPath,
        ctx: EvalContext,
        target: MvtRenderTarget,
        pixelScale: Float,
    ) {
        val radius = number(layer.paint["circle-radius"], ctx, 5.0).toFloat() * pixelScale
        val color = color(layer.paint["circle-color"], ctx) ?: Color(0xff000000.toInt())
        val strokeWidth = number(layer.paint["circle-stroke-width"], ctx, 0.0).toFloat() * pixelScale
        val strokeColor = color(layer.paint["circle-stroke-color"], ctx) ?: Color(0xff000000.toInt())
        var i = 0
        while (i + 1 < path.coords.size) {
            target.drawCircle(
                path.coords[i],
                path.coords[i + 1],
                radius,
                color,
                strokeWidth,
                strokeColor,
            )
            i += 2
        }
    }
}
