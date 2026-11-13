/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.render.DataTileFrame
import com.asamm.vectormaps.mvt.render.color
import com.asamm.vectormaps.mvt.render.number
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.ExpressionCompiler
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Lays out the symbol layers of a [Style] for one tile into [MvtLabel]s for the label pass, which
 * places them per screen (D39); the tiles draw none. Follows MapLibre's placement where the Asamm
 * styles use it: points at their point, lines at their first point, polygons at their pole of
 * inaccessibility, `line` placement repeated every `symbol-spacing` along lines and rings,
 * `line-center` once.
 *
 * @param sprite the sprite index icons are sized from; null lays out no icons
 * @param scale screen pixels per style pixel on a 512 px tile, the host's device scale
 * @param textScale an extra factor on text, on top of `scale`
 * @param language the preferred label language, such as `de`: text fields read `name:de` for
 *  `name` where a feature has it; null keeps the style's names
 * @param tileSource the decoded tile at (zoom, x, y), or null when there is none
 */
class SymbolLayout(
    private val style: Style,
    private val sprite: SpriteIndex?,
    private val metrics: TextMetrics,
    private val maxDataZoom: Int,
    private val scale: Float = 1f,
    private val textScale: Float = 1f,
    language: String? = null,
    private val tileSource: (zoom: Int, x: Int, y: Int) -> VectorTile?,
) {

    private val localNames = localNamesOf(language)

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
     * tile lays out the rest, so the label pass sees each anchor once; `data` is the tile's data
     * tile when the caller has it, else read from the tile source.
     */
    fun labels(zoom: Int, x: Int, y: Int, tileSizePx: Int, data: VectorTile? = null): List<MvtLabel> {
        val frame = DataTileFrame(zoom, x, y, tileSizePx, tileSizePx, maxDataZoom, scale)
        val tile = data ?: tileSource(frame.dataZoom, frame.dataX, frame.dataY) ?: return emptyList()
        val ctx = EvalContext(zoom.toDouble())
        val out = ArrayList<MvtLabel>()
        for ((index, layer) in style.layers.withIndex()) {
            if (layer.type != LayerType.SYMBOL
                || !layer.isActive(ctx.zoom)
            ) {
                continue
            }
            val source = tile.layers[layer.sourceLayer] ?: continue
            val pieces = ArrayList<LinePiece>()
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
                    // the widest margin layoutFeature allows, before projecting
                    if (!frame.meets(geometry = geometry, extent = source.extent, marginPx = tileSizePx.toFloat())) {
                        continue
                    }
                    layoutFeature(
                        layer = layer,
                        index = index,
                        type = feature.type,
                        coords = frame.project(geometry, source.extent),
                        partEnds = geometry.partEnds,
                        ctx = ctx,
                        tileSizePx = tileSizePx,
                        pixelScale = frame.pixelScale,
                        pieces = pieces,
                        out = out,
                    )
                }
                // the pieces of one road or route join first, as MapLibre's mergeLines
                for (group in pieces.groupBy { it.key }.values) {
                    for (chain in LineMerge.merge(group.map { it.coords })) {
                        layoutLine(chain.coords, group[chain.first].line, tileSizePx, out)
                    }
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
        pieces: MutableList<LinePiece>,
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
        // a feature far from the tile shows nothing in it; a line's label reaches a tile further
        val margin = if (onLine) tileSizePx.toFloat() else 0f
        if (coords.size < 2
            || !boundsMeetTile(coords, coords.indices, margin, tileSizePx)
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
            val line = LineSymbol(
                symbol = symbol,
                // the spec's minimum; the Asamm style interpolates below 0 at some zooms
                spacing = number(layer.layout["symbol-spacing"], ctx, 250.0).toFloat().coerceAtLeast(1f) * pixelScale,
                maxAngleDeg = number(layer.layout["text-max-angle"], ctx, 45.0),
                center = placement == "line-center",
                alongPath = alongPath && text != null,
                iconAlongLine = iconAlongLine,
            )
            var start = 0
            for (end in partEnds) {
                if (end - start >= 4) {
                    val part = if (type == GeometryType.POLYGON) {
                        // a ring runs back to its first point
                        coords.copyOfRange(start, end) + coords.copyOfRange(start, start + 2)
                    } else {
                        coords.copyOfRange(start, end)
                    }
                    // MapLibre joins `line` pieces with text only; each other piece keeps its own symbol
                    if (placement == "line"
                        && text != null
                    ) {
                        pieces += LinePiece("${text.lines}|${icon?.name}", part, line)
                    } else {
                        layoutLine(part, line, tileSizePx, out)
                    }
                }
                start = end
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

    private fun layoutLine(part: FloatArray, line: LineSymbol, tileSizePx: Int, out: MutableList<MvtLabel>) {
        val text = line.symbol.text
        val icon = line.symbol.icon
        val length = maxOf(text?.width ?: 0f, (icon?.width ?: 0f) * (icon?.scale ?: 1f))
        val anchors = LineAnchors.find(
            coords = part,
            start = 0,
            end = part.size,
            spacing = line.spacing,
            labelLength = length,
            center = line.center,
            maxAngleDeg = line.maxAngleDeg,
            // MapLibre's angle window, 3/5 of the text size; icons alone are not checked
            angleWindow = (text?.sizePx ?: 0f) * 0.6f,
            region = floatArrayOf(0f, 0f, tileSizePx.toFloat(), tileSizePx.toFloat()),
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

    // the bounds of `range` in `coords`, grown by `margin`, overlap the tile
    private fun boundsMeetTile(coords: FloatArray, range: IntRange, margin: Float, tileSizePx: Int): Boolean {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in range.first until range.last step 2) {
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

    // how a feature's symbol repeats along its lines
    private class LineSymbol(
        val symbol: Symbol,
        val spacing: Float,
        val maxAngleDeg: Double,
        val center: Boolean,
        val alongPath: Boolean,
        val iconAlongLine: Boolean,
    )

    // one line piece waiting to join the others showing the same text
    private class LinePiece(
        val key: String,
        val coords: FloatArray,
        val line: LineSymbol,
    )

    private fun shapeText(layer: StyleLayer, ctx: EvalContext, pixelScale: Float, wrap: Boolean): MvtText? {
        ctx.localNames = localNames
        val raw = try {
            layer.layout["text-field"]?.eval(ctx)
        } finally {
            ctx.localNames = emptyList()
        } ?: return null
        var value = ExpressionCompiler.toText(raw).trim()
        if (value.isEmpty()) {
            return null
        }
        value = when (layer.layout["text-transform"]?.eval(ctx)) {
            "uppercase" -> value.uppercase()
            "lowercase" -> value.lowercase()
            else -> value
        }
        val size = number(layer.layout["text-size"], ctx, 16.0).toFloat() * pixelScale * textScale
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
            return metrics.width(line, size, font, letterSpacing).coerceAtLeast(0f)
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
                pixelScale * textScale,
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
        val iconScale = number(layer.layout["icon-size"], ctx, 1.0).toFloat() * pixelScale
        val width = (image.width / image.pixelRatio).toFloat()
        val height = (image.height / image.pixelRatio).toFloat()
        // a malformed sprite or style, which the core rejects
        if (!(iconScale > 0f)
            || iconScale.isInfinite()
            || !(width > 0f)
            || !(height > 0f)
            || width.isInfinite()
            || height.isInfinite()
        ) {
            return null
        }
        val offset = layer.layout["icon-offset"]?.eval(ctx) as? List<*>
        val translate = layer.paint["icon-translate"]?.eval(ctx) as? List<*>
        // icon-offset counts in icon pixels, icon-translate in screen pixels
        return MvtIcon(
            name = name,
            width = width,
            height = height,
            centerX = finite(((offset?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * iconScale,
            centerY = finite(((offset?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * iconScale,
            translateX = finite(((translate?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * pixelScale,
            translateY = finite(((translate?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * pixelScale,
            scale = iconScale,
            rotateDeg = finite(number(layer.layout["icon-rotate"], ctx, 0.0).toFloat()),
            paddingPx = finite(number(layer.layout["icon-padding"], ctx, 2.0).toFloat()).coerceAtLeast(0f) * pixelScale,
        )
    }

    // a style value that evaluates to NaN or infinity falls back, as the core rejects it
    private fun finite(value: Float, default: Float = 0f): Float {
        return if (value.isFinite()) value else default
    }

    internal companion object {

        const val PRIORITY_PER_LAYER = 1000

        // Java's and Android's old codes for Hebrew, Indonesian and Yiddish
        private val LEGACY_LANGUAGES = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

        /**
         * The `name:xx` properties a label reads for `language`, as OpenMapTiles names them: the
         * language with its script first (`zh-Hant` reads `name:zh-Hant`, then `name:zh`), the
         * region dropped (`de-AT` reads `name:de`).
         */
        fun localNamesOf(language: String?): List<String> {
            val tag = language?.trim()?.replace('_', '-') ?: return emptyList()
            val locale = Locale.forLanguageTag(tag)
            val base = locale.language.lowercase().let { LEGACY_LANGUAGES[it] ?: it }
            if (base.isEmpty()) {
                return emptyList()
            }
            val script = locale.script
            if (script.isEmpty()) {
                return listOf("name:$base")
            }
            return listOf("name:$base-$script", "name:$base")
        }

        // a word with the hyphen it may break after, or a lone hyphen
        private val WORDS = Regex("[^ -]+-?|-")

        /**
         * Breaks `line` at spaces and after hyphens so no line is wider than `maxWidth`, unless one
         * word is.
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
