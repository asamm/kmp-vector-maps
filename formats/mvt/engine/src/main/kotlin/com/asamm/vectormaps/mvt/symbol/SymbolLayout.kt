/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.Expression
import com.asamm.vectormaps.mvt.style.ExpressionCompiler
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Lays out the symbol layers of a [Style] for one tile into [MvtLabel]s for the label pass, which
 * places them per screen (D39); the tiles draw none. Follows MapLibre's placement where the Asamm
 * styles use it: points at their point, lines at their first point, polygons at their pole of
 * inaccessibility, `line` placement repeated every `symbol-spacing`, `line-center` once.
 *
 * @param sprite the sprite index icons are sized from; null lays out no icons
 * @param tileSource the decoded tile at (zoom, x, y), or null when there is none
 */
class SymbolLayout(
    private val style: Style,
    private val sprite: SpriteIndex?,
    private val metrics: TextMetrics,
    private val maxDataZoom: Int,
    private val tileSource: (zoom: Int, x: Int, y: Int) -> VectorTile?,
) {

    /**
     * Ids of the symbol layers a layout skipped because evaluating them threw, such as a malformed
     * expression meeting unexpected tile data; the other layers still lay out.
     */
    val failedLayers: Set<String>
        get() {
            return failed
        }

    private val failed = ConcurrentHashMap.newKeySet<String>()

    /**
     * The labels whose anchors fall inside tile (`zoom`, `x`, `y`) of `tileSizePx`; a neighbour
     * tile lays out the rest, so the label pass sees each anchor once.
     */
    fun labels(zoom: Int, x: Int, y: Int, tileSizePx: Int): List<MvtLabel> {
        val dataZoom = minOf(zoom, maxDataZoom)
        val dz = zoom - dataZoom
        val tile = tileSource(dataZoom, x shr dz, y shr dz) ?: return emptyList()
        val offsetX = (x - ((x shr dz) shl dz)) * tileSizePx
        val offsetY = (y - ((y shr dz) shl dz)) * tileSizePx
        val scaleUnits = tileSizePx * (1 shl dz).toFloat()
        val pixelScale = tileSizePx / 512f
        val ctx = EvalContext(zoom.toDouble())
        val out = ArrayList<MvtLabel>()
        for ((index, layer) in style.layers.withIndex()) {
            if (layer.type != LayerType.SYMBOL
                || !layer.isActive(ctx.zoom)
            ) {
                continue
            }
            val source = tile.layers[layer.sourceLayer] ?: continue
            val scale = scaleUnits / source.extent
            val lines = LinkedHashMap<String, LineSymbol>()
            val mark = out.size
            try {
                for (feature in source.features) {
                    ctx.feature = feature
                    if (layer.filter != null
                        && layer.filter.eval(ctx) != true
                    ) {
                        continue
                    }
                    val geometry = feature.decodeGeometry()
                    val coords = FloatArray(geometry.coords.size)
                    for (i in coords.indices step 2) {
                        coords[i] = geometry.coords[i] * scale - offsetX
                        coords[i + 1] = geometry.coords[i + 1] * scale - offsetY
                    }
                    layoutFeature(
                        layer = layer,
                        index = index,
                        type = feature.type,
                        coords = coords,
                        partEnds = geometry.partEnds,
                        ctx = ctx,
                        tileSizePx = tileSizePx,
                        pixelScale = pixelScale,
                        lines = lines,
                        out = out,
                    )
                }
                for (line in lines.values) {
                    layoutLine(line, tileSizePx, out)
                }
            } catch (e: RuntimeException) {
                // a malformed style or tile leaves this whole layer out
                while (out.size > mark) {
                    out.removeAt(out.lastIndex)
                }
                failed.add(layer.id)
            }
        }
        return out
    }

    private fun layoutFeature(
        layer: StyleLayer,
        index: Int,
        type: GeometryType,
        coords: FloatArray,
        partEnds: IntArray,
        ctx: EvalContext,
        tileSizePx: Int,
        pixelScale: Float,
        lines: MutableMap<String, LineSymbol>,
        out: MutableList<MvtLabel>,
    ) {
        val placement = layer.layout["symbol-placement"]?.eval(ctx) as? String ?: "point"
        val onLine = placement != "point"
        // MapLibre places line symbols along lines and polygon rings, never at points
        if (onLine
            && type != GeometryType.LINESTRING
            && type != GeometryType.POLYGON
        ) {
            return
        }
        // `auto` alignment follows the line for line placement
        val alongPath = onLine
            && layer.layout["text-rotation-alignment"]?.eval(ctx) as? String != "viewport"
        val iconAlongLine = onLine
            && layer.layout["icon-rotation-alignment"]?.eval(ctx) as? String != "viewport"
        val text = shapeText(layer, ctx, pixelScale, wrap = !alongPath)
        val icon = iconOf(layer, ctx, pixelScale)
        if (text == null
            && icon == null
        ) {
            return
        }
        val sortKey = number(layer.layout["symbol-sort-key"], ctx, 0.0)
        val symbol = Symbol(
            layerId = layer.id,
            // later layers place first, then lower sort keys, as MapLibre
            priority = index * PRIORITY_PER_LAYER - sortKey.coerceIn(0.0, PRIORITY_PER_LAYER - 1.0).toInt(),
            text = text,
            icon = icon,
            allowOverlap = (text == null || layer.layout["text-allow-overlap"]?.eval(ctx) == true)
                && (icon == null || layer.layout["icon-allow-overlap"]?.eval(ctx) == true),
        )
        if (onLine) {
            fun lineSymbol(): LineSymbol {
                return LineSymbol(
                    symbol = symbol,
                    // the spec's minimum; the Asamm style interpolates below 0 at some zooms
                    spacing = number(layer.layout["symbol-spacing"], ctx, 250.0).toFloat().coerceAtLeast(1f) *
                        pixelScale,
                    maxAngleDeg = number(layer.layout["text-max-angle"], ctx, 45.0),
                    center = placement == "line-center",
                    alongPath = alongPath && text != null,
                    iconAlongLine = iconAlongLine,
                )
            }
            // the pieces of one road or route join first, as MapLibre's mergeLines, which joins
            // only symbols with text
            val line = if (text != null) {
                lines.getOrPut("${text.lines}|${icon?.name}") { lineSymbol() }
            } else {
                lineSymbol()
            }
            var start = 0
            for (end in partEnds) {
                if (end - start >= 4) {
                    line.parts += if (type == GeometryType.POLYGON) {
                        // a ring runs back to its first point
                        coords.copyOfRange(start, end) + coords.copyOfRange(start, start + 2)
                    } else {
                        coords.copyOfRange(start, end)
                    }
                }
                start = end
            }
            if (text == null) {
                layoutLine(line, tileSizePx, out)
            }
            return
        }
        when (type) {
            GeometryType.POINT -> {
                for (i in coords.indices step 2) {
                    emit(symbol, coords[i], coords[i + 1], null, tileSizePx, out)
                }
            }
            GeometryType.LINESTRING -> {
                // MapLibre's point placement on a line: its first point
                var start = 0
                for (end in partEnds) {
                    if (end > start) {
                        emit(symbol, coords[start], coords[start + 1], null, tileSizePx, out)
                    }
                    start = end
                }
            }
            GeometryType.POLYGON -> {
                for (polygon in Polylabel.polygons(coords, partEnds)) {
                    // the pole lies inside the outer ring's bounds
                    if (!boundsMeetTile(coords, polygon.first(), 0f, tileSizePx)) {
                        continue
                    }
                    val (px, py) = Polylabel.find(coords, polygon, precision = 1f)
                    emit(symbol, px, py, null, tileSizePx, out)
                }
            }
            GeometryType.UNKNOWN -> Unit
        }
    }

    private fun layoutLine(line: LineSymbol, tileSizePx: Int, out: MutableList<MvtLabel>) {
        val text = line.symbol.text
        val icon = line.symbol.icon
        val length = maxOf(text?.width ?: 0f, (icon?.width ?: 0f) * (icon?.scale ?: 1f))
        val parts = if (line.parts.size > 1) LineMerge.merge(line.parts) else line.parts
        for (part in parts) {
            // a tile above the data zoom holds a small part of each line; skip the rest early
            if (!boundsMeetTile(part, 0 until part.size, length, tileSizePx)) {
                continue
            }
            val anchors = LineAnchors.find(
                coords = part,
                start = 0,
                end = part.size,
                spacing = line.spacing,
                labelLength = length,
                center = line.center,
                maxAngleDeg = line.maxAngleDeg,
                // MapLibre's angle window, 3/5 of the text size; icons alone are not checked
                angleWindow = if (line.alongPath) text!!.sizePx * 0.6f else 0f,
                accept = { x, y -> x >= 0f && y >= 0f && x < tileSizePx && y < tileSizePx },
            )
            for (a in anchors) {
                val path = if (line.alongPath) {
                    LineAnchors.stretch(part, 0, part.size, a.distance - length / 2f, a.distance + length / 2f)
                        ?: continue
                } else {
                    null
                }
                emit(line.symbol, a.x, a.y, path, tileSizePx, out, if (line.iconAlongLine) a.angleDeg else null)
            }
        }
    }

    // the bounds of `range` in `coords`, grown by `margin`, overlap the tile
    private fun boundsMeetTile(coords: FloatArray, range: IntRange, margin: Float, tileSizePx: Int): Boolean {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in range step 2) {
            minX = minOf(minX, coords[i])
            minY = minOf(minY, coords[i + 1])
            maxX = maxOf(maxX, coords[i])
            maxY = maxOf(maxY, coords[i + 1])
        }
        return maxX >= -margin
            && maxY >= -margin
            && minX < tileSizePx + margin
            && minY < tileSizePx + margin
    }

    private fun emit(
        symbol: Symbol,
        x: Float,
        y: Float,
        path: FloatArray?,
        tileSizePx: Int,
        out: MutableList<MvtLabel>,
        lineAngleDeg: Float? = null,
    ) {
        if (x < 0f
            || y < 0f
            || x >= tileSizePx
            || y >= tileSizePx
        ) {
            return
        }
        out += MvtLabel(
            layerId = symbol.layerId,
            priority = symbol.priority,
            x = x,
            y = y,
            text = symbol.text,
            icon = symbol.icon,
            path = path,
            allowOverlap = symbol.allowOverlap,
            lineAngleDeg = lineAngleDeg,
        )
    }

    // what a feature's symbol shows, wherever it is anchored
    private class Symbol(
        val layerId: String,
        val priority: Int,
        val text: MvtText?,
        val icon: MvtIcon?,
        val allowOverlap: Boolean,
    )

    // the line pieces of one layer showing the same symbol, joined before anchoring
    private class LineSymbol(
        val symbol: Symbol,
        val spacing: Float,
        val maxAngleDeg: Double,
        val center: Boolean,
        val alongPath: Boolean,
        val iconAlongLine: Boolean,
    ) {
        val parts = ArrayList<FloatArray>()
    }

    private fun shapeText(layer: StyleLayer, ctx: EvalContext, pixelScale: Float, wrap: Boolean): MvtText? {
        val raw = layer.layout["text-field"]?.eval(ctx) ?: return null
        var value = ExpressionCompiler.toText(raw).trim()
        if (value.isEmpty()) {
            return null
        }
        value = when (layer.layout["text-transform"]?.eval(ctx)) {
            "uppercase" -> value.uppercase()
            "lowercase" -> value.lowercase()
            else -> value
        }
        val size = number(layer.layout["text-size"], ctx, 16.0).toFloat() * pixelScale
        if (!(size > 0f)
            || size.isInfinite()
        ) {
            return null
        }
        val font = MvtFont.of(layer.layout["text-font"]?.eval(ctx) as? List<*>)
        val letterSpacing = finite(number(layer.layout["text-letter-spacing"], ctx, 0.0).toFloat()) * size
        val lineHeight = finite(number(layer.layout["text-line-height"], ctx, 1.2).toFloat(), 1.2f)
            .coerceAtLeast(0f) * size
        val maxWidth = finite(number(layer.layout["text-max-width"], ctx, 10.0).toFloat(), 10f) * size
        fun widthOf(line: String): Float {
            return metrics.width(line, size, font) + letterSpacing * maxOf(0, line.length - 1)
        }
        val lines = if (wrap) {
            value.split('\n').flatMap { wrapLine(it, maxWidth, ::widthOf) }
        } else {
            listOf(value.replace('\n', ' '))
        }
        val width = lines.maxOf { widthOf(it) }
        val height = lines.size * lineHeight
        val offset = layer.layout["text-offset"]?.eval(ctx) as? List<*>
        val ox = finite(((offset?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * size
        val oy = finite(((offset?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * size
        val anchor = layer.layout["text-anchor"]?.eval(ctx) as? String ?: "center"
        val cx = ox + when {
            anchor.endsWith("left") -> width / 2f
            anchor.endsWith("right") -> -width / 2f
            else -> 0f
        }
        val cy = oy + when {
            anchor.startsWith("top") -> height / 2f
            anchor.startsWith("bottom") -> -height / 2f
            else -> 0f
        }
        return MvtText(
            lines = lines,
            font = font,
            sizePx = size,
            color = color(layer.paint["text-color"], ctx) ?: Color(0xff000000.toInt()),
            haloColor = color(layer.paint["text-halo-color"], ctx) ?: Color(0),
            haloWidthPx = finite(number(layer.paint["text-halo-width"], ctx, 0.0).toFloat()).coerceAtLeast(0f) *
                pixelScale,
            letterSpacingPx = letterSpacing,
            lineHeightPx = lineHeight,
            width = width,
            height = height,
            centerX = cx,
            centerY = cy,
            paddingPx = finite(number(layer.layout["text-padding"], ctx, 2.0).toFloat()).coerceAtLeast(0f) * pixelScale,
        )
    }

    private fun iconOf(layer: StyleLayer, ctx: EvalContext, pixelScale: Float): MvtIcon? {
        val name = layer.layout["icon-image"]?.eval(ctx) as? String ?: return null
        val image = sprite?.images?.get(name) ?: return null
        val scale = number(layer.layout["icon-size"], ctx, 1.0).toFloat() * pixelScale
        if (!(scale > 0f)
            || scale.isInfinite()
        ) {
            return null
        }
        val width = (image.width / image.pixelRatio).toFloat()
        val height = (image.height / image.pixelRatio).toFloat()
        val offset = layer.layout["icon-offset"]?.eval(ctx) as? List<*>
        val translate = layer.paint["icon-translate"]?.eval(ctx) as? List<*>
        // icon-offset counts in icon pixels, icon-translate in screen pixels
        val cx = finite(((offset?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * scale +
            finite(((translate?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * pixelScale
        val cy = finite(((offset?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * scale +
            finite(((translate?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * pixelScale
        return MvtIcon(
            name = name,
            width = width,
            height = height,
            centerX = cx,
            centerY = cy,
            scale = scale,
            rotateDeg = finite(number(layer.layout["icon-rotate"], ctx, 0.0).toFloat()),
            paddingPx = finite(number(layer.layout["icon-padding"], ctx, 2.0).toFloat()).coerceAtLeast(0f) * pixelScale,
        )
    }

    // a style value that evaluates to NaN or infinity falls back, as the core rejects it
    private fun finite(value: Float, default: Float = 0f): Float {
        return if (value.isFinite()) value else default
    }

    private fun number(e: Expression?, ctx: EvalContext, default: Double): Double {
        return e?.eval(ctx) as? Double ?: default
    }

    private fun color(e: Expression?, ctx: EvalContext): Color? {
        return ExpressionCompiler.toColor(e?.eval(ctx))
    }

    internal companion object {

        const val PRIORITY_PER_LAYER = 1000

        // a word with the hyphen it may break after, or a lone hyphen
        private val WORDS = Regex("[^ -]+-?|-")

        /**
         * Breaks `line` at spaces so no line is wider than `maxWidth`, unless one word is.
         */
        fun wrapLine(line: String, maxWidth: Float, widthOf: (String) -> Float): List<String> {
            if (widthOf(line) <= maxWidth) {
                return listOf(line)
            }
            val out = ArrayList<String>()
            var current = ""
            // a break after a space drops it, after a hyphen keeps it
            for (word in WORDS.findAll(line).map { it.value }) {
                val glue = if (current.isEmpty() || current.endsWith("-")) "" else " "
                val candidate = current + glue + word
                if (current.isNotEmpty()
                    && widthOf(candidate) > maxWidth
                ) {
                    out += current
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) {
                out += current
            }
            return out
        }
    }
}

/**
 * Anchors along a line part, as distances from its start.
 */
internal object LineAnchors {

    class Anchor(
        val x: Float,
        val y: Float,
        val distance: Float,
        val angleDeg: Float,
    )

    /**
     * Anchors every `spacing` from half a spacing in (or the middle of a line shorter than one
     * spacing, or only the middle for `center`), where a label of `labelLength` fits and the line
     * turns less than `maxAngleDeg` within any `angleWindow` of the label (no check for a window of
     * 0); only anchors `accept` takes are checked and kept.
     */
    fun find(
        coords: FloatArray,
        start: Int,
        end: Int,
        spacing: Float,
        labelLength: Float,
        center: Boolean,
        maxAngleDeg: Double,
        angleWindow: Float,
        accept: (x: Float, y: Float) -> Boolean = { _, _ -> true },
    ): List<Anchor> {
        val total = length(coords, start, end)
        if (total < labelLength
            || total <= 0f
        ) {
            return emptyList()
        }
        val distances = ArrayList<Float>()
        if (center
            || total < spacing
        ) {
            distances += total / 2f
        } else {
            var d = spacing / 2f
            while (d <= total) {
                distances += d
                d += spacing
            }
        }
        val out = ArrayList<Anchor>()
        for (d in distances) {
            if (d - labelLength / 2f < 0f
                || d + labelLength / 2f > total
            ) {
                continue
            }
            val (x, y, angle) = pointAt(coords, start, end, d)
            if (!accept(x, y)) {
                continue
            }
            if (angleWindow > 0f
                && !angleFits(coords, start, end, d - labelLength / 2f, d + labelLength / 2f, maxAngleDeg, angleWindow)
            ) {
                continue
            }
            out += Anchor(x, y, d, angle)
        }
        return out
    }

    /**
     * The part of the line between distances `from` and `to`, as x, y pairs; null when empty.
     */
    fun stretch(coords: FloatArray, start: Int, end: Int, from: Float, to: Float): FloatArray? {
        val out = ArrayList<Float>()
        val (fx, fy) = pointAt(coords, start, end, from)
        out += fx
        out += fy
        var at = 0f
        var i = start + 2
        while (i < end) {
            at += hypot(coords[i] - coords[i - 2], coords[i + 1] - coords[i - 1])
            if (at > from
                && at < to
            ) {
                out += coords[i]
                out += coords[i + 1]
            }
            i += 2
        }
        val (tx, ty) = pointAt(coords, start, end, to)
        out += tx
        out += ty
        return if (out.size >= 4) out.toFloatArray() else null
    }

    private fun length(coords: FloatArray, start: Int, end: Int): Float {
        var total = 0f
        var i = start + 2
        while (i < end) {
            total += hypot(coords[i] - coords[i - 2], coords[i + 1] - coords[i - 1])
            i += 2
        }
        return total
    }

    // the point at `distance` along the line and its segment's angle in degrees
    private fun pointAt(coords: FloatArray, start: Int, end: Int, distance: Float): Triple<Float, Float, Float> {
        var at = 0f
        var angle = 0f
        var i = start + 2
        while (i < end) {
            val x0 = coords[i - 2]
            val y0 = coords[i - 1]
            val dx = coords[i] - x0
            val dy = coords[i + 1] - y0
            val segment = hypot(dx, dy)
            if (segment > 0f) {
                angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (distance <= at + segment) {
                    val t = ((distance - at) / segment).coerceIn(0f, 1f)
                    return Triple(x0 + dx * t, y0 + dy * t, angle)
                }
            }
            at += segment
            i += 2
        }
        return Triple(coords[end - 2], coords[end - 1], angle)
    }

    // MapLibre's checkMaxAngle: the turning within any window of the label stays below the limit
    private fun angleFits(
        coords: FloatArray,
        start: Int,
        end: Int,
        from: Float,
        to: Float,
        maxAngleDeg: Double,
        window: Float,
    ): Boolean {
        class Turn(val at: Float, val angle: Double)

        val turns = ArrayList<Turn>()
        var at = 0f
        var i = start + 2
        var previous = Double.NaN
        while (i < end) {
            val dx = coords[i] - coords[i - 2]
            val dy = coords[i + 1] - coords[i - 1]
            val segment = hypot(dx, dy)
            if (segment > 0f) {
                val angle = atan2(dy.toDouble(), dx.toDouble())
                if (!previous.isNaN()
                    && at > from
                    && at < to
                ) {
                    var turn = angle - previous
                    while (turn > Math.PI) {
                        turn -= 2 * Math.PI
                    }
                    while (turn < -Math.PI) {
                        turn += 2 * Math.PI
                    }
                    turns += Turn(at, turn)
                }
                previous = angle
            }
            at += segment
            i += 2
        }
        val max = Math.toRadians(maxAngleDeg)
        for (a in turns.indices) {
            var sum = 0.0
            for (b in a until turns.size) {
                if (turns[b].at - turns[a].at > window) {
                    break
                }
                sum += turns[b].angle
                if (abs(sum) > max) {
                    return false
                }
            }
        }
        return true
    }
}
