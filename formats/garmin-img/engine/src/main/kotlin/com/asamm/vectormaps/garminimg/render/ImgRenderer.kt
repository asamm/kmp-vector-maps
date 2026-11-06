/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.model.MapObject
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.parser.TreLevels
import com.asamm.vectormaps.garminimg.style.Color
import com.asamm.vectormaps.garminimg.style.InternalStyles
import com.asamm.vectormaps.garminimg.style.Style
import kotlin.math.abs
import kotlin.math.ln

/**
 * Walks an [ImgFile]'s map objects and dispatches them to a [RenderTarget]
 * after consulting a [StyleResolver]. This is the central rendering entry
 * point of the Garmin engine, used by the golden tests today and by the
 * driver from M4.2.
 *
 * # Render order
 *
 * Objects are emitted by [ImgFile.readMap] in the polygon-first order that
 * matches the Java predecessor's `readMapForDrawing`:
 *
 *   POLYGON → EXTENDED_POLYGON → POLYLINE → EXTENDED_POLYLINE → POINT
 *   → INDEXED_POINT → EXTENDED_POINT
 *
 * Polygons and polylines are stable-sorted by the resolved [Style]'s draw
 * priority (TYP DRAWORDER when present), so records of equal priority keep
 * their parser order — sub-divisions in tree order, records on-disk order.
 *
 * # Labels
 *
 * Labels are not drawn: each one goes to the caller's `labels` sink as a [MapLabel], for a label
 * pass that places them apart from the tiles (the core label model).
 *
 * # Thread-safety
 *
 * **Not thread-safe.** `render()` calls `seek()` on the shared
 * sub-file readers of its maps, so two concurrent invocations on the same
 * `ImgRenderer` — or two `ImgRenderer`s sharing one [ImgFile] — race
 * on the seek position and silently produce garbled tiles. Give each
 * worker thread its own renderer over [ImgFile.reopen] or [ImgFile.reopenOn] copies.
 */
class ImgRenderer(
    private val layers: List<Layer>,
) {

    /**
     * Renders one map.
     */
    constructor(
        img: ImgFile,
        styleResolver: StyleResolver = StyleResolver.internalDefaults(),
    ) : this(listOf(Layer(img = img, styleResolver = styleResolver)))

    /**
     * One map of a render with its styles; maps draw into one frame, sorted and labelled together.
     */
    class Layer(
        val img: ImgFile,
        val styleResolver: StyleResolver,
    )

    /**
     * Renders every map into [target] using [viewport] for projection.
     *
     * @param viewport screen-pixel + Garmin-coord rectangle to render.
     * @param target receives every draw call.
     * @param backgroundColor passed to [RenderTarget.beginFrame].
     * @param objectKindFilter bitmask restricting which [ObjectKind]s are
     *   drawn. Defaults to all.
     * @param level TRE level to draw in the maps that have it; null, and every map without it,
     *   picks the level matching the viewport's resolution.
     * @param textScale factor on label sizes, on top of the target's render scale
     * @param labels receives every label of the render, in render order
     */
    fun render(
        viewport: Viewport,
        target: RenderTarget,
        backgroundColor: Color = Color.rgb(0xF8, 0xF6, 0xF0),
        objectKindFilter: Int = ObjectKind.ALL,
        level: Int? = null,
        textScale: Float = 1f,
        labels: (MapLabel) -> Unit = {},
        hillshade: () -> Unit = {},
    ) {
        require(target.widthPx == viewport.widthPx) {
            "RenderTarget width ${target.widthPx} != viewport width ${viewport.widthPx}"
        }
        require(target.heightPx == viewport.heightPx) {
            "RenderTarget height ${target.heightPx} != viewport height ${viewport.heightPx}"
        }

        val projection = Projection(viewport)
        target.beginFrame(backgroundColor)

        // First pass: bucket every map object by kind. Polygons need to be
        // sorted by draw priority (TYP's DRAWORDER section, when present)
        // so background polygons paint first and small foreground features
        // sit on top. Polylines and points draw in parser order — RGN's
        // own ordering is fine for those.
        val polygons = ArrayList<Pair<MapObject.PolyShape, Style.PolygonStyle>>()
        val polylines = ArrayList<Pair<MapObject.PolyShape, Style.LineStyle>>()
        val points = ArrayList<Pair<MapObject.Point, Style.PointStyle>>()

        for (layer in layers) {
            // a gmapsupp holds many maps; most miss a given tile
            if (!intersects(layer.img, viewport)) {
                continue
            }
            // Level-of-detail per map, whose levels are its own: the level matching the screen
            // resolution, or the forced one where the map has it. A picked level without content in
            // the viewport (sparse outside the cartographer's "detail area") falls back to the
            // coarser levels, then the finer ones from the nearest.
            val forced = level?.takeIf { l -> layer.img.tre.levels.levels.any { it.level == l } }
            val targetLevel = forced ?: pickRenderLevelWithCoverage(viewport, layer.img)
            for (obj in layer.img.readMapInBounds(
                minLong = viewport.minLon,
                maxLong = viewport.maxLon,
                minLat = viewport.minLat,
                maxLat = viewport.maxLat,
                objectKindFilter = objectKindFilter,
                levelFilter = targetLevel,
                poiDetails = false,
            )) {
                val style = layer.styleResolver.resolve(obj)
                when (obj) {
                    is MapObject.Point -> {
                        points.add(obj to (style as? Style.PointStyle ?: continue))
                    }
                    is MapObject.PolyShape -> if (obj.closed) {
                        polygons.add(obj to (style as? Style.PolygonStyle ?: continue))
                    } else {
                        polylines.add(obj to (style as? Style.LineStyle ?: continue))
                    }
                }
            }
        }

        // Stable-sort polygons ascending by drawPriority — lower = draws
        // first (background). `Int.MAX_VALUE` sentinel = "no DRAWORDER
        // entry matched" → drawn last, which is the conservative default.
        polygons.sortBy { it.second.drawPriority }

        // Sort polylines by priority too — lower draws first (minor
        // roads beneath highways at junctions). `drawPriority` defaults
        // to Int.MAX_VALUE when not set, so types without an explicit
        // priority pile up at the end (visually on top).
        polylines.sortBy { it.second.drawPriority }

        // Polygons paint as a single pass (each polygon's stroke is
        // local to its own outline).
        for ((poly, style) in polygons) {
            drawPolygon(poly, style, projection, target, labels, textScale)
        }
        // the host's hillshading over the areas, under the lines and points
        hillshade()

        // Two-pass polyline rendering: every border first, then every
        // centre stroke. Single-pass would let A's border draw over B's
        // centre at intersections, leaving dark blobs at every junction.
        // Two-pass produces continuous roads through junctions — same
        // technique mkgmap / QMapShack use.
        // projected once for both passes; null when empty or off screen
        val projected = polylines.map { (poly, _) -> projectLine(poly, projection, target) }
        for ((i, line) in polylines.withIndex()) {
            val xy = projected[i] ?: continue
            target.drawPolylineBorder(xy.first, xy.second, line.second)
        }
        for ((i, line) in polylines.withIndex()) {
            val xy = projected[i] ?: continue
            drawPolylineCentre(line.first, xy.first, xy.second, line.second, target, labels, textScale)
        }

        for ((point, style) in points) {
            drawPoint(point, style, projection, target, labels, textScale)
        }

        target.endFrame()
    }

    private fun intersects(img: ImgFile, viewport: Viewport): Boolean {
        val header = img.tre.header
        return header.westBoundary <= viewport.maxLon
            && header.eastBoundaryUnwrapped >= viewport.minLon
            && header.southBoundary <= viewport.maxLat
            && header.northBoundary >= viewport.minLat
    }

    private fun drawPoint(
        point: MapObject.Point,
        style: Style.PointStyle,
        projection: Projection,
        target: RenderTarget,
        labels: (MapLabel) -> Unit,
        textScale: Float,
    ) {
        val x = projection.projectX(point.longitude)
        val y = projection.projectY(point.latitude)
        if (!isOnScreen(x, y, target)) {
            return
        }
        target.drawPoint(x, y, style)
        val pointLabel = point.label
        if (style.showLabel && isDisplayableLabel(pointLabel)) {
            val fontSize = DEFAULT_POINT_FONT_SIZE * style.fontScale * textScale
            val height = labelHeight(fontSize, target)
            labels(
                MapLabel(
                    kind = MapLabel.Kind.POINT,
                    x = x,
                    y = y,
                    // below the icon, however large the label grows
                    offsetYPx = pointHalfHeight(style, target) + POINT_LABEL_GAP + height / 2f,
                    widthPx = target.measureText(pointLabel!!, fontSize),
                    heightPx = height,
                    priority = style.drawPriority,
                    text = pointLabel,
                    color = style.labelColor,
                    outlineColor = style.labelOutlineColor,
                    fontSize = fontSize,
                ),
            )
        }
    }

    private fun drawPolygon(
        poly: MapObject.PolyShape,
        style: Style.PolygonStyle,
        projection: Projection,
        target: RenderTarget,
        labels: (MapLabel) -> Unit,
        textScale: Float,
    ) {
        if (poly.longitudes.isEmpty()) {
            return
        }
        val xs = FloatArray(poly.longitudes.size)
        val ys = FloatArray(poly.longitudes.size)
        projection.projectBulk(poly.longitudes, poly.latitudes, xs, ys)
        if (allOffScreen(xs, ys, target)) {
            return
        }
        target.drawPolygon(xs, ys, style)
        val polyLabel = poly.label
        if (style.labelColor != null && isDisplayableLabel(polyLabel)) {
            val (cx, cy) = centroid(xs, ys)
            val fontSize = DEFAULT_AREA_FONT_SIZE * style.fontScale * textScale
            labels(
                MapLabel(
                    kind = MapLabel.Kind.AREA,
                    x = cx,
                    y = cy,
                    offsetYPx = 0f,
                    widthPx = target.measureText(polyLabel!!, fontSize),
                    heightPx = labelHeight(fontSize, target),
                    priority = style.drawPriority,
                    text = polyLabel,
                    color = style.labelColor,
                    outlineColor = null,
                    fontSize = fontSize,
                ),
            )
        }
    }

    private fun projectLine(
        poly: MapObject.PolyShape,
        projection: Projection,
        target: RenderTarget,
    ): Pair<FloatArray, FloatArray>? {
        if (poly.longitudes.isEmpty()) {
            return null
        }
        val xs = FloatArray(poly.longitudes.size)
        val ys = FloatArray(poly.longitudes.size)
        projection.projectBulk(poly.longitudes, poly.latitudes, xs, ys)
        if (allOffScreen(xs, ys, target)) {
            return null
        }
        return xs to ys
    }

    private fun drawPolylineCentre(
        poly: MapObject.PolyShape,
        xs: FloatArray,
        ys: FloatArray,
        style: Style.LineStyle,
        target: RenderTarget,
        labels: (MapLabel) -> Unit,
        textScale: Float,
    ) {
        val n = xs.size
        target.drawPolylineStroke(xs, ys, style)
        val polyLabel = poly.label
        if (style.labelColor != null && isDisplayableLabel(polyLabel)) {
            val midIdx = n / 2
            val fontSize = DEFAULT_LINE_FONT_SIZE * style.fontScale * textScale
            labels(
                MapLabel(
                    kind = MapLabel.Kind.LINE,
                    x = xs[midIdx],
                    y = ys[midIdx],
                    offsetYPx = 0f,
                    widthPx = target.measureText(polyLabel!!, fontSize),
                    heightPx = labelHeight(fontSize, target),
                    priority = style.drawPriority,
                    text = polyLabel,
                    color = style.labelColor,
                    outlineColor = null,
                    fontSize = fontSize,
                    pathXs = xs,
                    pathYs = ys,
                ),
            )
        }
    }

    // a line of text with its leading, at the targets' minimum; widths are measured by the target
    private fun labelHeight(fontSize: Float, target: RenderTarget): Float {
        return (fontSize * target.renderScale).coerceAtLeast(RenderTarget.MIN_TEXT_PX) * 1.2f
    }

    // as the targets draw it: the icon at its render scale, never smaller, else a 2 px dot
    private fun pointHalfHeight(style: Style.PointStyle, target: RenderTarget): Float {
        val icon = style.icon ?: return POINT_DOT_RADIUS
        return (icon.height * target.renderScale).coerceAtLeast(icon.height.toFloat()) / 2f
    }

    private fun isOnScreen(x: Float, y: Float, target: RenderTarget): Boolean {
        return x >= -OFFSCREEN_MARGIN &&
            x <= target.widthPx + OFFSCREEN_MARGIN &&
            y >= -OFFSCREEN_MARGIN &&
            y <= target.heightPx + OFFSCREEN_MARGIN
    }

    private fun allOffScreen(xs: FloatArray, ys: FloatArray, target: RenderTarget): Boolean {
        // A polyline is "all off-screen" if every vertex sits past one common
        // edge by more than a comfortable margin. Lines that cross the
        // viewport (one vertex inside, one outside) still pass and clip
        // naturally at the AWT/Canvas level.
        val limit = OFFSCREEN_LINE_MARGIN
        var allLeft = true
        var allRight = true
        var allTop = true
        var allBottom = true
        val w = target.widthPx
        val h = target.heightPx
        for (i in xs.indices) {
            val x = xs[i]
            val y = ys[i]
            if (x > -limit) allLeft = false
            if (x < w + limit) allRight = false
            if (y > -limit) allTop = false
            if (y < h + limit) allBottom = false
            if (!allLeft && !allRight && !allTop && !allBottom) {
                return false
            }
        }
        return allLeft || allRight || allTop || allBottom
    }

    /**
     * Area-weighted polygon centroid. Mathematically the "geometric centroid"
     * of a non-self-intersecting polygon: produces a label anchor that sits
     * inside concave or elongated shapes (lakes, fjords, narrow buildings)
     * where the simple arithmetic-mean centroid would drift outside the
     * polygon.
     *
     * Falls back to the arithmetic mean when the signed area is zero
     * (degenerate / collinear polygons) — guards against divide-by-zero.
     */
    private fun centroid(xs: FloatArray, ys: FloatArray): FloatPair {
        val n = xs.size
        if (n < 3) {
            return arithmeticMean(xs, ys)
        }
        // Signed area + first-moment accumulation in one pass.
        // Using doubles internally avoids float-precision blow-up on long
        // polygons (e.g. coastlines with hundreds of vertices).
        var area2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in 0 until n) {
            val j = if (i + 1 < n) i + 1 else 0
            val cross = xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
            area2 += cross
            cx += (xs[i] + xs[j]) * cross
            cy += (ys[i] + ys[j]) * cross
        }
        if (area2 == 0.0) {
            return arithmeticMean(xs, ys)
        }
        val factor = 1.0 / (3.0 * area2)
        return FloatPair((cx * factor).toFloat(), (cy * factor).toFloat())
    }

    private fun arithmeticMean(xs: FloatArray, ys: FloatArray): FloatPair {
        var sx = 0f
        var sy = 0f
        for (i in xs.indices) {
            sx += xs[i]
            sy += ys[i]
        }
        return FloatPair(sx / xs.size, sy / ys.size)
    }

    companion object {

        /**
         * Picks the TRE level whose `bitsPerCoord` matches the viewport's
         * pixel resolution best. Returns null if [levels] is empty —
         * caller should then render every level.
         *
         * Math: at level L with `b` bits-per-coord, one delta unit equals
         * `1 shl (24 - b)` Garmin coords. We want one delta unit to map
         * to roughly one screen pixel, i.e.:
         *
         *     (1 shl (24 - b)) * pixelsPerCoord ≈ 1
         *     b ≈ 24 + log2(pixelsPerCoord)
         *
         * `pixelsPerCoord = widthPx / (maxLon - minLon)` (in Garmin
         * coords). We pick the finest level whose `bitsPerCoord` is at
         * most `targetBits + DETAIL_BIAS` — a slight over-pick that
         * keeps small harbour / coastline detail (breakwaters, jetties)
         * visible without dragging in the noisiest finest level. Falls
         * back to the level with the lowest bitsPerCoord above the cap
         * if nothing fits, then to the finest available.
         */
        fun pickRenderLevel(viewport: Viewport, levels: TreLevels): Int? {
            return pickRenderLevel(viewport, levels, materialisedLevels = null)
        }

        /**
         * Variant that takes the set of levels actually present in the
         * sub-division tree. Some IMGs declare a level descriptor (often
         * level=0 / bits=24) with a non-zero `subDivisionCount` but ship
         * no parseable sub-divisions in it — picking that level would
         * yield an empty render. Restricting to actually-materialised
         * levels avoids the issue without weakening the level-matcher's
         * other invariants.
         */
        internal fun pickRenderLevel(
            viewport: Viewport,
            levels: TreLevels,
            materialisedLevels: Set<Int>?,
        ): Int? {
            if (levels.levels.isEmpty()) {
                return null
            }
            // the exact span: `ofDegrees` pads the Int bounds, which would drop a level at tile zooms
            val lonRange = (viewport.maxLonDeg - viewport.minLonDeg) / 360.0 * (1 shl 24)
            if (lonRange <= 0.0) {
                return null
            }
            val pixelsPerCoord = viewport.widthPx.toDouble() / lonRange
            // log2(x) = ln(x) / ln(2)
            val targetBits = 24.0 + ln(pixelsPerCoord) / LN2
            val available = levels.levels
                .filterNot { it.inherited }
                .filter { it.subDivisionCount > 0 }
                .filter { materialisedLevels == null || materialisedLevels.contains(it.level) }
            if (available.isEmpty()) {
                return null
            }
            // mkgmap-style level authoring puts the geometry meant for a
            // given zoom into the level whose `bitsPerCoord ≈ targetBits
            // - DETAIL_BIAS`. Each level coarser drops one bit of
            // resolution AND simplifies geometry (residential streets,
            // footpaths, building outlines fade out as you go up). Our
            // bias of -1 picks finest at deep zooms, level 2-3 at city
            // overview, level 4-5 at district, matching what reference
            // renderers (mkgmap, GPSMapEdit, old Locus) produce on the
            // same fixtures.
            val cap = targetBits + DETAIL_BIAS
            // Pick the finest level (highest bitsPerCoord) whose detail
            // doesn't exceed cap. Falls back to the closest level above
            // cap if all levels are coarser, then the finest available.
            var fineFit: Int? = null
            var fineFitBits = Int.MIN_VALUE
            var bestAbove: Int? = null
            var bestAboveScore = Double.MAX_VALUE
            for (lvl in available) {
                if (lvl.bitsPerCoord <= cap) {
                    if (lvl.bitsPerCoord > fineFitBits) {
                        fineFitBits = lvl.bitsPerCoord
                        fineFit = lvl.level
                    }
                } else {
                    val score = lvl.bitsPerCoord - cap
                    if (score < bestAboveScore) {
                        bestAboveScore = score
                        bestAbove = lvl.level
                    }
                }
            }
            return fineFit ?: bestAbove
        }

        /**
         * Offset between `targetBits` (the bits-per-coord that would
         * give 1 pixel per delta unit) and the level we actually pick.
         * Sign convention: positive → keep finer levels longer when
         * zooming out (less aggressive simplification); negative →
         * step up to coarser levels sooner. The right value is fixture-
         * dependent; verify a tuning choice against a reference
         * renderer (QMS / GPSMapEdit) before changing this default.
         */
        private const val DETAIL_BIAS: Double = -1.0

        /**
         * Picks the LoD level *and* validates that the chosen level has
         * sub-divisions intersecting the viewport. Steps coarser, then
         * finer from the nearest, when the first pick is sparse — this matters for IMGs
         * (like OpenTopo Monaco) where the finest level only contains
         * data inside a narrow "detail area" smaller than the viewer's
         * tight bbox.
         */
        fun pickRenderLevelWithCoverage(
            viewport: Viewport,
            img: ImgFile,
        ): Int? {
            val first = pickRenderLevel(viewport, img.tre.levels, img.materialisedLevels) ?: return null
            // the first level with intersecting sub-divisions wins
            val candidates = img.tre.levels.levels
                .filterNot { it.inherited }
                .sortedByDescending { it.bitsPerCoord }
            // Start at `first` and go coarser; also try finer if needed.
            val firstBits = img.tre.levels.bitsPerCoordOf(first) ?: return first
            val ordered = candidates
                .partition { it.bitsPerCoord <= firstBits }
                // coarser levels first, then the finer ones from the nearest
                .let { (atOrCoarser, finer) -> atOrCoarser + finer.reversed() }
            for (lvl in ordered) {
                if (hasSubDivisions(img, lvl.level, viewport)) {
                    return lvl.level
                }
            }
            return first
        }

        private fun hasSubDivisions(
            img: ImgFile,
            level: Int,
            viewport: Viewport,
        ): Boolean {
            // Bbox-overlap, not centre-containment. At deep zoom the
            // viewport can be much smaller than a sub-division — its
            // centre then falls outside the viewport even though the
            // sub-division covers it. Centre-only check returned false
            // for every level → the renderer drew nothing.
            return img.hasDataInBounds(
                level = level,
                minLong = viewport.minLon,
                maxLong = viewport.maxLon,
                minLat = viewport.minLat,
                maxLat = viewport.maxLat,
            )
        }

        /**
         * Sanity-only check on the raw label string: drop empty / null /
         * whitespace-only labels. We *do not* filter URL-shaped, hour-
         * shaped, or fragment-shaped strings here — old Locus and
         * GPSMapEdit show every label faithfully and rely on the
         * collision + same-text dedup of the label pass to keep
         * clutter under control. Re-introduce content filters only if a
         * specific IMG genuinely overwhelms the layout.
         */
        internal fun isDisplayableLabel(label: String?): Boolean {
            return !label.isNullOrBlank()
        }

        private const val LN2: Double = 0.6931471805599453
        private const val POINT_LABEL_GAP: Float = 2f
        private const val POINT_DOT_RADIUS: Float = 2f
        private const val DEFAULT_POINT_FONT_SIZE: Float = 13f
        private const val DEFAULT_LINE_FONT_SIZE: Float = 12f
        private const val DEFAULT_AREA_FONT_SIZE: Float = 14f
        private const val OFFSCREEN_MARGIN: Int = 32
        private const val OFFSCREEN_LINE_MARGIN: Int = 64
    }
}

/**
 * Resolves `(type, subType)` to a [Style].
 *
 * - [internalDefaults] — uses [InternalStyles] hardcoded fallbacks.
 * - [typAware] — TYP-first lookup; falls back to `InternalStyles` for
 *   `(type, subType)` pairs the TYP doesn't define. Use this when an IMG
 *   has a TYP sub-file.
 */
fun interface StyleResolver {

    fun resolve(obj: MapObject): Style?

    companion object {

        /**
         * Returns a resolver that always falls back to [InternalStyles].
         */
        fun internalDefaults(): StyleResolver {
            return StyleResolver { obj -> InternalStyles.resolve(obj) }
        }

        /**
         * Returns a resolver that consults the supplied [TypStyleTable]
         * first and falls back to [InternalStyles] for `(type, subType)`
         * pairs the TYP doesn't carry. Default widths / labels / borders
         * from `InternalStyles` are preserved when the TYP doesn't
         * override them.
         */
        fun typAware(table: com.asamm.vectormaps.garminimg.parser.TypStyleTable): StyleResolver {
            // Per-resolver cache keyed by `(type shl 16) or (subType and 0xFF)
            // shl 8) or (closed ? 1 : 0)`. The resolver previously
            // allocated a fresh Style.* per MapObject — on the order of
            // 10 000 allocations per tile per frame on Android. With a
            // few hundred unique (type, subType, closed) triples per
            // IMG the cache becomes a hot path after warmup.
            //
            // `getOrPut` does not store a `null` result: a miss is resolved again on every call.
            val cache = HashMap<Int, Style?>()
            return StyleResolver { obj ->
                when (obj) {
                    is MapObject.Point -> {
                        val key = (obj.type shl 16) or (obj.subType and 0xFF shl 8) or POINT_KEY
                        cache.getOrPut(key) {
                            val typPoint = table.lookupPoint(obj.type, obj.subType)
                            val internal = InternalStyles.resolve(obj) as? Style.PointStyle
                            if (typPoint != null && internal != null) {
                                internal.copy(
                                    icon = typPoint.icon,
                                    labelColor = typPoint.font?.dayColour ?: internal.labelColor,
                                    showLabel = internal.showLabel && !hidesLabel(typPoint.font),
                                    fontScale = fontScaleOf(typPoint.font),
                                )
                            } else {
                                internal
                            }
                        }
                    }
                    is MapObject.PolyShape -> if (obj.closed) {
                        val key = (obj.type shl 16) or (obj.subType and 0xFF shl 8) or POLYGON_KEY
                        cache.getOrPut(key) {
                            val typ = table.lookupPolygon(obj.type, obj.subType)
                            if (typ != null) {
                                val internal = InternalStyles.resolvePolygon(obj.type)
                                // Use the TYP's DRAWORDER priority when
                                // present (lower → draw first →
                                // background); fall back to
                                // InternalStyles' static priority.
                                val priority = if (typ.drawPriority != Int.MAX_VALUE) {
                                    typ.drawPriority
                                } else {
                                    internal.drawPriority
                                }
                                internal.copy(
                                    drawPriority = priority,
                                    fillColor = typ.fillColor,
                                    strokeColor = typ.strokeColor ?: internal.strokeColor,
                                    pattern = typ.pattern,
                                    labelColor = labelColorOf(typ.font, internal.labelColor),
                                    fontScale = fontScaleOf(typ.font),
                                )
                            } else {
                                InternalStyles.resolve(obj)
                            }
                        }
                    } else {
                        val key = (obj.type shl 16) or (obj.subType and 0xFF shl 8) or LINE_KEY
                        cache.getOrPut(key) {
                            val typ = table.lookupLine(obj.type, obj.subType)
                            if (typ != null) {
                                val internal = InternalStyles.resolveLine(obj.type)
                                internal.copy(
                                    strokeColor = typ.strokeColor,
                                    borderColor = typ.borderColor ?: internal.borderColor,
                                    strokeWidth = typ.strokeWidthPx,
                                    borderWidth = typ.totalWidthPx,
                                    pattern = typ.pattern,
                                    labelColor = labelColorOf(typ.font, internal.labelColor),
                                    fontScale = fontScaleOf(typ.font),
                                )
                            } else {
                                InternalStyles.resolve(obj)
                            }
                        }
                    }
                }
            }
        }

        // the TYP's label colour; null hides the label
        private fun labelColorOf(font: com.asamm.vectormaps.garminimg.parser.TypFontInfo?, default: Color?): Color? {
            if (hidesLabel(font)) {
                return null
            }
            return font?.dayColour ?: default
        }

        // mkgmap `FontStyle`: 1 is NoLabel
        private fun hidesLabel(font: com.asamm.vectormaps.garminimg.parser.TypFontInfo?): Boolean {
            return font?.style == 1
        }

        // mkgmap `FontStyle` small (2) and large (4); our sizes, as Garmin publishes none
        private fun fontScaleOf(font: com.asamm.vectormaps.garminimg.parser.TypFontInfo?): Float {
            return when (font?.style) {
                2 -> SMALL_FONT_SCALE
                4 -> LARGE_FONT_SCALE
                else -> 1f
            }
        }

        private const val SMALL_FONT_SCALE: Float = 0.85f
        private const val LARGE_FONT_SCALE: Float = 1.2f

        // Cache key tags so the three MapObject kinds don't collide on
        // shared `(type, subType)` triples.
        private const val POINT_KEY = 1
        private const val LINE_KEY = 2
        private const val POLYGON_KEY = 3
    }
}
