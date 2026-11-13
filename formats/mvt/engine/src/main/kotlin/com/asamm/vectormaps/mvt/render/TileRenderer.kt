/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.Expression
import com.asamm.vectormaps.mvt.style.ExpressionCompiler
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.tile.Geometry
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Draws the background, fill, line and circle layers of a [Style] for one tile; symbols are the
 * label pass's. Tiles above [maxDataZoom] are cut from their ancestor at that zoom (overzoom).
 *
 * @param scale screen pixels per style pixel on a 512 px tile, the host's device scale
 * @param tileSource the decoded tile at (zoom, x, y), or null when there is none
 */
class TileRenderer(
    private val style: Style,
    private val maxDataZoom: Int,
    private val scale: Float = 1f,
    private val tileSource: (zoom: Int, x: Int, y: Int) -> VectorTile?,
) {

    /**
     * Ids of the layers a render skipped because evaluating them threw, such as a malformed
     * expression meeting unexpected tile data of the features drawn; the rest of the tile still
     * draws.
     */
    val failedLayers: Set<String>
        get() {
            return failed
        }

    private val failed = ConcurrentHashMap.newKeySet<String>()

    /**
     * Renders tile (`zoom`, `x`, `y`) into `target`, whose width is the tile size; the style's
     * pixel sizes apply 1:1 at 512 px tiles, as MapLibre's, times the device scale.
     *
     * @param data the tile's data tile when the caller has it, else read from the tile source
     * @param hillshade the host's elevation drawing, called at each active hillshade and
     *  colour-relief layer, which the renderer leaves to the host
     * @return false when there is no data for the tile
     */
    fun render(
        zoom: Int,
        x: Int,
        y: Int,
        target: MvtRenderTarget,
        isCancelled: () -> Boolean,
        data: VectorTile? = null,
        hillshade: ((StyleLayer) -> Unit)? = null,
    ): Boolean {
        val frame = DataTileFrame(zoom, x, y, target.widthPx, target.heightPx, maxDataZoom, scale)
        val tile = data ?: tileSource(frame.dataZoom, frame.dataX, frame.dataY) ?: return false
        val pixelScale = frame.pixelScale
        val ctx = EvalContext(zoom.toDouble())
        for (layer in style.layers) {
            if (isCancelled()) {
                return true
            }
            if (!layer.isActive(ctx.zoom)) {
                continue
            }
            if (layer.type == LayerType.HILLSHADE
                || layer.type == LayerType.COLOR_RELIEF
            ) {
                hillshade?.invoke(layer)
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
                val paint = LayerPaint(layer)
                for (feature in source.features) {
                    ctx.feature = feature
                    if (layer.filter != null
                        && layer.filter.eval(ctx) != true
                    ) {
                        continue
                    }
                    val shape = Shape(
                        geometry = feature.decodeGeometry(),
                        type = feature.type,
                        extent = source.extent,
                        frame = frame,
                    )
                    when (layer.type) {
                        LayerType.FILL -> drawFill(paint, shape, ctx, target, x, y)
                        LayerType.LINE -> drawLine(paint, shape, ctx, target, pixelScale)
                        else -> drawCircles(paint, shape, ctx, target, pixelScale)
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
        paint: LayerPaint,
        shape: Shape,
        ctx: EvalContext,
        target: MvtRenderTarget,
        x: Int,
        y: Int,
    ) {
        if (shape.type != GeometryType.POLYGON
            || !shape.meets(marginPx = 1f)
        ) {
            return
        }
        val opacity = paint.number("fill-opacity", ctx, 1.0)
        val antialias = paint.value("fill-antialias", ctx) != false
        if (paint.has("fill-pattern")) {
            val name = paint.value("fill-pattern", ctx) as? String ?: return
            target.fillPathPattern(
                shape.path(),
                name,
                opacity.toFloat(),
                antialias,
                x.toLong() * target.widthPx,
                y.toLong() * target.heightPx,
            )
            return
        }
        val color = paint.color("fill-color", ctx) ?: Color(0xff000000.toInt())
        target.fillPath(shape.path(), color.withOpacity(opacity), antialias)
    }

    private fun drawLine(
        paint: LayerPaint,
        shape: Shape,
        ctx: EvalContext,
        target: MvtRenderTarget,
        pixelScale: Float,
    ) {
        if (shape.type == GeometryType.POINT
            || paint.has("line-pattern")
        ) {
            return
        }
        val width = paint.number("line-width", ctx, 1.0).toFloat() * pixelScale
        if (width <= 0f) {
            return
        }
        val offset = paint.number("line-offset", ctx, 0.0).toFloat() * pixelScale
        // miter joins reach a width out at the default miter limit 2, an offset vertex up to
        // MAX_MITER offsets
        if (!shape.meets(marginPx = width + LineOffset.MAX_MITER * abs(offset) + 1f)) {
            return
        }
        val color = paint.color("line-color", ctx) ?: Color(0xff000000.toInt())
        val opacity = paint.number("line-opacity", ctx, 1.0)
        val cap = when (paint.layoutValue("line-cap", ctx)) {
            "round" -> LineCap.ROUND
            "square" -> LineCap.SQUARE
            else -> LineCap.BUTT
        }
        val join = when (paint.layoutValue("line-join", ctx)) {
            "round" -> LineJoin.ROUND
            "bevel" -> LineJoin.BEVEL
            else -> LineJoin.MITER
        }
        // dash lengths are in line widths
        val dash = (paint.value("line-dasharray", ctx) as? List<*>)
            ?.map { ((it as? Double ?: 0.0) * width).toFloat().coerceAtLeast(0.01f) }
            ?.toFloatArray()
        val path = shape.path()
        val drawn = if (offset != 0f) LineOffset.offset(path, offset) else path
        target.strokePath(drawn, color.withOpacity(opacity), width, cap, join, dash)
    }

    private fun drawCircles(
        paint: LayerPaint,
        shape: Shape,
        ctx: EvalContext,
        target: MvtRenderTarget,
        pixelScale: Float,
    ) {
        val radius = paint.number("circle-radius", ctx, 5.0).toFloat() * pixelScale
        val strokeWidth = paint.number("circle-stroke-width", ctx, 0.0).toFloat() * pixelScale
        if (!shape.meets(marginPx = radius + strokeWidth + 1f)) {
            return
        }
        val color = paint.color("circle-color", ctx) ?: Color(0xff000000.toInt())
        val strokeColor = paint.color("circle-stroke-color", ctx) ?: Color(0xff000000.toInt())
        val path = shape.path()
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

    // a feature's geometry, projected into the tile only once it is drawn
    private class Shape(
        private val geometry: Geometry,
        val type: GeometryType,
        private val extent: Int,
        private val frame: DataTileFrame,
    ) {

        fun meets(marginPx: Float): Boolean {
            return frame.meets(geometry = geometry, extent = extent, marginPx = marginPx)
        }

        fun path(): MvtPath {
            return MvtPath(frame.project(geometry, extent), geometry.partEnds, type == GeometryType.POLYGON)
        }
    }

    // a layer's paint and layout for one render; values that do not depend on the feature are
    // evaluated once
    private class LayerPaint(private val layer: StyleLayer) {

        private val constants = HashMap<String, Any?>()

        fun has(name: String): Boolean {
            return layer.paint.containsKey(name)
        }

        fun value(name: String, ctx: EvalContext): Any? {
            return evaluate(name = name, expression = layer.paint[name], ctx = ctx)
        }

        fun layoutValue(name: String, ctx: EvalContext): Any? {
            return evaluate(name = name, expression = layer.layout[name], ctx = ctx)
        }

        // paint and layout share no property name
        private fun evaluate(name: String, expression: Expression?, ctx: EvalContext): Any? {
            if (constants.containsKey(name)) {
                return constants[name]
            }
            expression ?: return null
            val value = expression.eval(ctx)
            if (expression.isFeatureConstant) {
                constants[name] = value
            }
            return value
        }

        fun number(name: String, ctx: EvalContext, default: Double): Double {
            return value(name, ctx) as? Double ?: default
        }

        fun color(name: String, ctx: EvalContext): Color? {
            return ExpressionCompiler.toColor(value(name, ctx))
        }
    }
}
