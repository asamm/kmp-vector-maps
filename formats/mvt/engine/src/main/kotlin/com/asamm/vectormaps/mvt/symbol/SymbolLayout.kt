/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.render.DataTileFrame
import com.asamm.vectormaps.mvt.render.FeatureIndex
import com.asamm.vectormaps.mvt.render.color
import com.asamm.vectormaps.mvt.render.number
import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.ExpressionCompiler
import com.asamm.vectormaps.mvt.style.LayerType
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.style.StyleLayer
import com.asamm.vectormaps.mvt.tile.Feature
import com.asamm.vectormaps.mvt.tile.Geometry
import com.asamm.vectormaps.mvt.tile.GeometryType
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil

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

    // the ranks within a sort key a layer's features get, as many as the Int priority leaves
    // room for
    private val orderSlots = (Int.MAX_VALUE / (style.layers.size + 1) / SORT_KEYS).coerceIn(1, MAX_ORDER_SLOTS)

    /**
     * Ids of the symbol layers a layout skipped because evaluating them threw, such as a malformed
     * expression meeting unexpected tile data; the other layers still lay out.
     */
    val failedLayers: Set<String>
        get() {
            return failed
        }

    private val failed = ConcurrentHashMap.newKeySet<String>()

    // the poles of a feature's polygons in data-tile units, x, y per polygon, for the sibling
    // tiles above the data zoom that share its data tile; gone with the tile
    private val poles = Collections.synchronizedMap(WeakHashMap<Feature, FloatArray>())

    /**
     * The labels whose anchors fall inside tile (`zoom`, `x`, `y`) of `tileSizePx`; a neighbour
     * tile lays out the rest, so the label pass sees each anchor once; `data` is the tile's data
     * tile when the caller has it, else read from the tile source. Once `isCancelled` it stops
     * between layers, the labels so far a part only.
     */
    fun labels(
        zoom: Int,
        x: Int,
        y: Int,
        tileSizePx: Int,
        data: VectorTile? = null,
        isCancelled: () -> Boolean = { false },
    ): List<MvtLabel> {
        val frame = DataTileFrame(zoom, x, y, tileSizePx, tileSizePx, maxDataZoom, scale)
        val tile = data ?: tileSource(frame.dataZoom, frame.dataX, frame.dataY) ?: return emptyList()
        val ctx = EvalContext(zoom.toDouble())
        val out = ArrayList<MvtLabel>()
        val features = FeatureIndex()
        // a road's or a river's pieces repeat its name
        val shaped = HashMap<Shaping, Shaped>()
        for ((index, layer) in style.layers.withIndex()) {
            if (isCancelled()) {
                return out
            }
            if (layer.type != LayerType.SYMBOL
                || !layer.isActive(ctx.zoom)
            ) {
                continue
            }
            val source = tile.layers[layer.sourceLayer] ?: continue
            val pieces = ArrayList<LinePiece>()
            val mark = out.size
            // the features' order in the data tile, the same in every tile cut from it
            var order = 0
            val extent = source.extent.toFloat()
            val bounds = floatArrayOf(
                frame.projectX(0f, source.extent),
                frame.projectY(0f, source.extent),
                frame.projectX(extent, source.extent),
                frame.projectY(extent, source.extent),
            )
            try {
                for (feature in features.candidates(layer = source, filter = layer.filter)) {
                    ctx.feature = feature
                    if (layer.filter != null
                        && layer.filter.eval(ctx) != true
                    ) {
                        continue
                    }
                    val rank = order++
                    val geometry = feature.decodeGeometry()
                    // the widest margin layoutFeature allows, before projecting
                    if (!frame.meets(geometry = geometry, extent = source.extent, marginPx = tileSizePx.toFloat())) {
                        continue
                    }
                    val pole: (Int, List<IntRange>, FloatArray) -> Pair<Float, Float> = if (frame.dz > 0) {
                        { i, _, _ -> sharedPole(feature, geometry, source.extent, frame, i) }
                    } else {
                        { _, polygon, coords -> Polylabel.find(coords, polygon, precision = 1f) }
                    }
                    layoutFeature(
                        layer = layer,
                        index = index,
                        rank = rank,
                        bounds = bounds,
                        type = feature.type,
                        pole = pole,
                        coords = frame.project(geometry, source.extent),
                        dataCoords = geometry.coords,
                        partEnds = geometry.partEnds,
                        ctx = ctx,
                        tileSizePx = tileSizePx,
                        pixelScale = frame.pixelScale,
                        overscale = 1 shl frame.dz,
                        shaped = shaped,
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
        rank: Int,
        bounds: FloatArray,
        type: GeometryType,
        pole: (Int, List<IntRange>, FloatArray) -> Pair<Float, Float>,
        coords: FloatArray,
        dataCoords: FloatArray,
        partEnds: IntArray,
        ctx: EvalContext,
        tileSizePx: Int,
        pixelScale: Float,
        overscale: Int,
        shaped: MutableMap<Shaping, Shaped>,
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
        val text = shapeText(layer = layer, ctx = ctx, pixelScale = pixelScale, wrap = !alongPath, shaped = shaped)
        val icon = iconOf(layer, ctx, pixelScale)
        if (text == null
            && icon == null
        ) {
            return
        }
        val sortKey = number(layer.layout["symbol-sort-key"], ctx, 0.0)
        val symbol = Symbol(
            layerId = layer.id,
            // later layers place first, then lower sort keys, then the tile's order, as MapLibre
            priority = index * orderSlots * SORT_KEYS -
                sortKey.coerceIn(0.0, SORT_KEYS - 1.0).toInt() * orderSlots -
                minOf(rank, orderSlots - 1),
            text = text,
            icon = icon,
            allowOverlap = (text == null || layer.layout["text-allow-overlap"]?.eval(ctx) == true)
                && (icon == null || layer.layout["icon-allow-overlap"]?.eval(ctx) == true),
            keepUpright = layer.layout["text-keep-upright"]?.eval(ctx) != false,
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
                bounds = bounds,
                overscale = overscale,
                glyphSizePx = maxTextSize(layer, ctx) * pixelScale * textScale,
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
                // rings classified on the exact data units, as the shared poles are; their ranges
                // index the projected coords alike
                for ((i, polygon) in Polylabel.polygons(dataCoords, partEnds).withIndex()) {
                    // the pole lies inside the outer ring's bounds
                    if (!boundsMeetTile(coords, polygon.first(), 0f, tileSizePx)) {
                        continue
                    }
                    val (px, py) = pole(i, polygon, coords)
                    emit(symbol, px, py, null, tileSizePx, out)
                }
            }
            GeometryType.UNKNOWN -> Unit
        }
    }

    private fun layoutLine(line: FloatArray, symbol: LineSymbol, tileSizePx: Int, out: MutableList<MvtLabel>) {
        val text = symbol.symbol.text
        val icon = symbol.symbol.icon
        val iconLength = (icon?.width ?: 0f) * (icon?.scale ?: 1f)
        val length = maxOf(text?.width ?: 0f, iconLength)
        // MapLibre lays the anchors out once for all zooms, with the text at its z18 size
        val maxLength = maxOf((text?.width ?: 0f) * symbol.glyphSizePx / (text?.sizePx ?: 1f), iconLength)
        // as MapLibre, `line` anchored on the line clipped to its data tile, `line-center` on the
        // whole line
        val parts = if (symbol.center) listOf(line) else LineAnchors.clip(line, symbol.bounds)
        for (part in parts) {
            val anchors = LineAnchors.find(
                coords = part,
                start = 0,
                end = part.size,
                spacing = symbol.spacing,
                labelLength = maxLength,
                glyphSize = symbol.glyphSizePx,
                overscale = symbol.overscale,
                center = symbol.center,
                maxAngleDeg = symbol.maxAngleDeg,
                // MapLibre's angle window, 3/5 of the text size; icons alone are not checked
                angleWindow = if (text != null) symbol.glyphSizePx * 0.6f else 0f,
                region = floatArrayOf(0f, 0f, tileSizePx.toFloat(), tileSizePx.toFloat()),
                bounds = symbol.bounds,
            )
            for (a in anchors) {
                val path = if (symbol.alongPath) {
                    LineAnchors.stretch(part, 0, part.size, a.distance - length / 2f, a.distance + length / 2f)
                        ?: continue
                } else {
                    null
                }
                emit(symbol.symbol, a.x, a.y, path, tileSizePx, out, if (symbol.iconAlongLine) a.angleDeg else null)
            }
        }
    }

    // `text-size` at MapLibre's maximum text zoom, 18, in style pixels
    private fun maxTextSize(layer: StyleLayer, ctx: EvalContext): Float {
        val zoom = ctx.zoom
        ctx.zoom = MAX_TEXT_ZOOM
        try {
            val size = number(layer.layout["text-size"], ctx, 16.0).toFloat()
            if (!(size > 0f)
                || size.isInfinite()
            ) {
                return 16f
            }
            return size
        } finally {
            ctx.zoom = zoom
        }
    }

    // above the data zoom the sibling tiles share the pole of polygon `i`, found once in data units
    // with MapLibre's precision of a 512th of the tile, then projected into the tile
    private fun sharedPole(feature: Feature, geometry: Geometry, extent: Int, frame: DataTileFrame, i: Int): Pair<Float, Float> {
        val all = poles.getOrPut(feature) { polesOf(geometry = geometry, precision = extent / 512f) }
        return Pair(frame.projectX(all[i * 2], extent), frame.projectY(all[i * 2 + 1], extent))
    }

    // the pole of each polygon of `geometry`, x, y pairs in its own units
    private fun polesOf(geometry: Geometry, precision: Float): FloatArray {
        val polygons = Polylabel.polygons(geometry.coords, geometry.partEnds)
        val out = FloatArray(polygons.size * 2)
        for ((i, polygon) in polygons.withIndex()) {
            val (x, y) = Polylabel.find(geometry.coords, polygon, precision = precision)
            out[i * 2] = x
            out[i * 2 + 1] = y
        }
        return out
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
            keepUpright = symbol.keepUpright,
        )
    }

    // what a feature's symbol shows, wherever it is anchored
    private class Symbol(
        val layerId: String,
        val priority: Int,
        val text: MvtText?,
        val icon: MvtIcon?,
        val allowOverlap: Boolean,
        val keepUpright: Boolean,
    )

    // how a feature's symbol repeats along its lines
    private class LineSymbol(
        val symbol: Symbol,
        val spacing: Float,
        val maxAngleDeg: Double,
        val center: Boolean,
        val alongPath: Boolean,
        val iconAlongLine: Boolean,
        // the data tile in the tile's pixels, the edge MapLibre clips lines to
        val bounds: FloatArray,
        // times the tile is cut from its data tile
        val overscale: Int,
        // the text size at z18, which MapLibre lays line anchors out with, icons alone too
        val glyphSizePx: Float,
    )

    // one line piece waiting to join the others showing the same text
    private class LinePiece(
        val key: String,
        val coords: FloatArray,
        val line: LineSymbol,
    )

    private fun shapeText(
        layer: StyleLayer,
        ctx: EvalContext,
        pixelScale: Float,
        wrap: Boolean,
        shaped: MutableMap<Shaping, Shaped>,
    ): MvtText? {
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
        val shaping = Shaping(
            text = value,
            sizePx = size,
            font = font,
            letterSpacingPx = letterSpacing,
            maxWidthPx = maxWidth,
            wrap = wrap,
        )
        val (lines, width) = shaped.getOrPut(shaping) {
            shape(shaping)
        }
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
        // `auto` justifies to the side the anchor puts the text on
        val justify = when (layer.layout["text-justify"]?.eval(ctx) as? String ?: "center") {
            "left" -> 0f
            "right" -> 1f
            "auto" -> when {
                anchor.endsWith("left") -> 0f
                anchor.endsWith("right") -> 1f
                else -> 0.5f
            }
            else -> 0.5f
        }
        val opacity = number(layer.paint["text-opacity"], ctx, 1.0)
        return MvtText(
            lines = lines,
            font = font,
            sizePx = size,
            color = (color(layer.paint["text-color"], ctx) ?: Color(0xff000000.toInt())).withOpacity(opacity),
            haloColor = (color(layer.paint["text-halo-color"], ctx) ?: Color(0)).withOpacity(opacity),
            haloWidthPx = finite(number(layer.paint["text-halo-width"], ctx, 0.0).toFloat()).coerceAtLeast(0f) *
                pixelScale * textScale,
            letterSpacingPx = letterSpacing,
            lineHeightPx = lineHeight,
            width = width,
            height = height,
            centerX = cx,
            centerY = cy,
            paddingPx = finite(number(layer.layout["text-padding"], ctx, 2.0).toFloat()).coerceAtLeast(0f) * pixelScale,
            justify = justify,
            rotateDeg = finite(number(layer.layout["text-rotate"], ctx, 0.0).toFloat()),
            haloBlurPx = finite(number(layer.paint["text-halo-blur"], ctx, 0.0).toFloat()).coerceAtLeast(0f) *
                pixelScale * textScale,
        )
    }

    // the lines `shaping` wraps into and the widest one's width
    private fun shape(shaping: Shaping): Shaped {
        // wrapping and the label's width measure the same lines
        val widths = HashMap<String, Float>()
        fun widthOf(line: String): Float {
            return widths.getOrPut(line) {
                metrics.width(line, shaping.sizePx, shaping.font, shaping.letterSpacingPx).coerceAtLeast(0f)
            }
        }
        val lines = if (!shaping.wrap) {
            listOf(shaping.text.replace('\n', ' '))
        } else if ('\n' !in shaping.text
            && widthOf(shaping.text) <= shaping.maxWidthPx
        ) {
            // one line, as MapLibre's line count for a text no wider than the maximum
            listOf(shaping.text)
        } else {
            breakLines(text = shaping.text, maxWidth = shaping.maxWidthPx, spacing = shaping.letterSpacingPx) {
                metrics.width(it, shaping.sizePx, shaping.font, 0f)
            }
        }
        return Shaped(lines = lines, width = lines.maxOf { widthOf(it) })
    }

    // what a text's lines and width depend on
    private data class Shaping(
        val text: String,
        val sizePx: Float,
        val font: MvtFont,
        val letterSpacingPx: Float,
        val maxWidthPx: Float,
        val wrap: Boolean,
    )

    private data class Shaped(val lines: List<String>, val width: Float)

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
        // the side of the anchor the icon sits on, as text-anchor for text
        val anchor = layer.layout["icon-anchor"]?.eval(ctx) as? String ?: "center"
        val anchorX = when {
            anchor.endsWith("left") -> width * iconScale / 2f
            anchor.endsWith("right") -> -width * iconScale / 2f
            else -> 0f
        }
        val anchorY = when {
            anchor.startsWith("top") -> height * iconScale / 2f
            anchor.startsWith("bottom") -> -height * iconScale / 2f
            else -> 0f
        }
        // icon-offset counts in icon pixels, icon-translate in screen pixels
        return MvtIcon(
            name = name,
            width = width,
            height = height,
            centerX = finite(((offset?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * iconScale + anchorX,
            centerY = finite(((offset?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * iconScale + anchorY,
            translateX = finite(((translate?.getOrNull(0) as? Double) ?: 0.0).toFloat()) * pixelScale,
            translateY = finite(((translate?.getOrNull(1) as? Double) ?: 0.0).toFloat()) * pixelScale,
            scale = iconScale,
            rotateDeg = finite(number(layer.layout["icon-rotate"], ctx, 0.0).toFloat()),
            paddingPx = finite(number(layer.layout["icon-padding"], ctx, 2.0).toFloat()).coerceAtLeast(0f) * pixelScale,
            opacity = finite(number(layer.paint["icon-opacity"], ctx, 1.0).toFloat(), 1f).coerceIn(0f, 1f),
        )
    }

    // a style value that evaluates to NaN or infinity falls back, as the core rejects it
    private fun finite(value: Float, default: Float = 0f): Float {
        return if (value.isFinite()) value else default
    }

    internal companion object {

        // symbol-sort-key values told apart, from 0
        const val SORT_KEYS = 1000

        private const val MAX_ORDER_SLOTS = 2048

        private const val MAX_TEXT_ZOOM = 18.0

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

        // where a line may break after: newline, space, &, ), +, -, /, soft hyphen, middle dot,
        // zero width space, hyphen, en dash, hyphenation point, as MapLibre's
        private val BREAKABLE = setOf(0x0a, 0x20, 0x26, 0x29, 0x2b, 0x2d, 0x2f, 0xad, 0xb7, 0x200b, 0x2010, 0x2013, 0x2027)

        private val WHITESPACE = setOf(0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20)

        /**
         * `text` broken into lines as MapLibre's `determineLineBreaks`: into as few lines as fit
         * `maxWidth`, at the breaks that make them least ragged against their average width, a last
         * line rather shorter than longer, a newline always; `advance` measures one character,
         * `spacing` adds after each.
         */
        fun breakLines(text: String, maxWidth: Float, spacing: Float, advance: (String) -> Float): List<String> {
            val points = text.codePoints().toArray()
            if (points.isEmpty()) {
                return emptyList()
            }
            val widths = HashMap<Int, Float>()
            fun advanceOf(point: Int): Float {
                return widths.getOrPut(point) { advance(String(Character.toChars(point))).coerceAtLeast(0f) } + spacing
            }
            var total = 0f
            for (point in points) {
                total += advanceOf(point)
            }
            val lineCount = maxOf(1f, ceil(total / maxWidth))
            val target = total / lineCount
            val breaks = ArrayList<LineBreak>()
            var x = 0f
            for (i in points.indices) {
                val point = points[i]
                if (point !in WHITESPACE) {
                    x += advanceOf(point)
                }
                if (i < points.size - 1
                    && (point in BREAKABLE || ideographic(point) || breaksBefore(points, i + 1))
                ) {
                    val penalty = penalty(point = point, next = points[i + 1])
                    breaks += evaluate(index = i + 1, x = x, target = target, prior = breaks, penalty = penalty, last = false)
                }
            }
            val chosen = ArrayList<Int>()
            var at: LineBreak? = evaluate(index = points.size, x = x, target = target, prior = breaks, penalty = 0f, last = true)
            while (at != null) {
                chosen.add(0, at.index)
                at = at.prior
            }
            val lines = ArrayList<String>()
            var start = 0
            // an empty line between two newlines keeps its line height, as MapLibre's
            for (end in chosen) {
                lines += String(points, start, end - start).trim()
                start = end
            }
            return lines.ifEmpty { listOf(text.trim()) }
        }

        private class LineBreak(val index: Int, val x: Float, val prior: LineBreak?, val badness: Float)

        // the least bad way to end a line at `index`, over every earlier break
        private fun evaluate(index: Int, x: Float, target: Float, prior: List<LineBreak>, penalty: Float, last: Boolean): LineBreak {
            var best: LineBreak? = null
            var bestBadness = badness(width = x, target = target, penalty = penalty, last = last)
            for (p in prior) {
                val b = badness(width = x - p.x, target = target, penalty = penalty, last = last) + p.badness
                if (b <= bestBadness) {
                    best = p
                    bestBadness = b
                }
            }
            return LineBreak(index = index, x = x, prior = best, badness = bestBadness)
        }

        private fun badness(width: Float, target: Float, penalty: Float, last: Boolean): Float {
            val raggedness = (width - target) * (width - target)
            if (last) {
                // a last line shorter than the average rather than longer
                return if (width < target) raggedness / 2f else raggedness * 2f
            }
            return raggedness + abs(penalty) * penalty
        }

        // MapLibre also breaks before an open parenthesis, unless it ends the text
        private fun breaksBefore(points: IntArray, i: Int): Boolean {
            return points[i] == 0x28
                && i != points.size - 1
        }

        private fun penalty(point: Int, next: Int): Float {
            var penalty = 0f
            if (point == 0x0a) {
                penalty -= 10000f
            }
            // an open parenthesis ends a line, a closing one starts it, only reluctantly
            if (point == 0x28
                || point == 0xff08
            ) {
                penalty += 50f
            }
            if (next == 0x29
                || next == 0xff09
            ) {
                penalty += 50f
            }
            return penalty
        }

        // scripts written without spaces, which break between any characters, as MapLibre's
        // ideographic breaking: CJK and Yi, not Hangul
        private fun ideographic(point: Int): Boolean {
            return point in 0x2e80..0x312f
                || point in 0x3190..0x9fff
                || point in 0xa000..0xa4cf
                || point in 0xf900..0xfaff
                || point in 0xff00..0xffef
                || point in 0x20000..0x2fa1f
        }
    }
}
