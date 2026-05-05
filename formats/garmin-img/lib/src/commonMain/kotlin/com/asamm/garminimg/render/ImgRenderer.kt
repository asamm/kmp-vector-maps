/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.model.MapObject
import com.asamm.garminimg.model.ObjectKind
import com.asamm.garminimg.parser.TreLevels
import com.asamm.garminimg.style.Color
import com.asamm.garminimg.style.InternalStyles
import com.asamm.garminimg.style.Style
import kotlin.math.abs
import kotlin.math.ln

/**
 * Walks an [ImgFile]'s map objects and dispatches them to a [RenderTarget]
 * after consulting a [StyleResolver]. This is the central rendering entry
 * point used by the desktop CLI (Phase C.5) and (later) the Android Locus
 * adapter (Phase C.7+).
 *
 * # Render order
 *
 * Objects are emitted by [ImgFile.readMap] in the polygon-first order that
 * matches the Java predecessor's `readMapForDrawing`:
 *
 *   POLYGON → EXTENDED_POLYGON → POLYLINE → EXTENDED_POLYLINE → POINT
 *   → INDEXED_POINT → EXTENDED_POINT
 *
 * Within each kind, objects are drawn in the order the parser yields them
 * — sub-divisions in tree order, records in their on-disk order. The
 * renderer adds a per-object draw priority via the resolved [Style], so a
 * stable sort by priority within each ObjectKind would be a refinement
 * (Phase C.9).
 *
 * # Labels
 *
 * Labels are drawn after the geometry of each [MapObject]. A future polish
 * pass (label de-cluttering) will hold labels in a queue, sort by priority,
 * and only draw those that don't overlap higher-priority labels — that's
 * Phase C.9.
 */
class ImgRenderer(
    private val img: ImgFile,
    private val styleResolver: StyleResolver = StyleResolver.internalDefaults(),
) {

    /**
     * Renders the entire IMG into [target] using [viewport] for projection.
     *
     * @param viewport screen-pixel + Garmin-coord rectangle to render.
     * @param target receives every draw call.
     * @param backgroundColor passed to [RenderTarget.beginFrame].
     * @param objectKindFilter bitmask restricting which [ObjectKind]s are
     *   drawn. Defaults to all.
     */
    fun render(
        viewport: Viewport,
        target: RenderTarget,
        backgroundColor: Color = Color.rgb(0xF8, 0xF6, 0xF0),
        objectKindFilter: Int = ObjectKind.ALL,
    ) {
        require(target.widthPx == viewport.widthPx) {
            "RenderTarget width ${target.widthPx} != viewport width ${viewport.widthPx}"
        }
        require(target.heightPx == viewport.heightPx) {
            "RenderTarget height ${target.heightPx} != viewport height ${viewport.heightPx}"
        }

        val projection = Projection(viewport)
        val labels = LabelLayout()
        val labelScale = target.renderScale
        target.beginFrame(backgroundColor)

        // Level-of-detail: pick the level whose detail best matches
        // screen pixel resolution. Skipping finer levels at wide
        // viewports drops noise (sub-division frames, over-detailed
        // forests). If the picked level has no sub-divisions intersecting
        // the viewport (some IMGs are sparse at the finest level outside
        // the cartographer's chosen "detail area"), fall back to a
        // coarser level that does have content.
        val targetLevel = pickRenderLevelWithCoverage(viewport, img)

        // First pass: bucket every map object by kind. Polygons need to be
        // sorted by draw priority (TYP's DRAWORDER section, when present)
        // so background polygons paint first and small foreground features
        // sit on top. Polylines and points draw in parser order — RGN's
        // own ordering is fine for those.
        val polygons = ArrayList<Pair<MapObject.PolyShape, Style.PolygonStyle>>()
        val polylines = ArrayList<Pair<MapObject.PolyShape, Style.LineStyle>>()
        val points = ArrayList<MapObject.Point>()

        for (obj in img.readMapInBounds(
            minLong = viewport.minLon,
            maxLong = viewport.maxLon,
            minLat = viewport.minLat,
            maxLat = viewport.maxLat,
            objectKindFilter = objectKindFilter,
            levelFilter = targetLevel,
        )) {
            when (obj) {
                is MapObject.Point -> points.add(obj)
                is MapObject.PolyShape -> if (obj.closed) {
                    val s = styleResolver.resolve(obj) as? Style.PolygonStyle ?: continue
                    polygons.add(obj to s)
                } else {
                    val s = styleResolver.resolve(obj) as? Style.LineStyle ?: continue
                    polylines.add(obj to s)
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
            drawPolygon(poly, style, projection, target, labels, labelScale)
        }

        // Two-pass polyline rendering: every border first, then every
        // centre stroke. Single-pass would let A's border draw over B's
        // centre at intersections, leaving dark blobs at every junction.
        // Two-pass produces continuous roads through junctions — same
        // technique mkgmap / QMapShack use.
        for ((poly, style) in polylines) {
            drawPolylineBorder(poly, style, projection, target)
        }
        for ((poly, style) in polylines) {
            drawPolylineCentre(poly, style, projection, target, labels, labelScale)
        }

        for (point in points) {
            drawPoint(point, projection, target, labels, labelScale)
        }

        // Second pass: emit non-overlapping labels in priority order so the
        // most important ones survive when text would otherwise pile up.
        // Labels with an associated polyline path render curve-following so
        // road names bend with the road; everything else stays straight.
        labels.drawAccepted { cand ->
            val px = cand.pathXs
            val py = cand.pathYs
            if (px != null && py != null && px.size >= 2) {
                target.drawTextOnPath(
                    xs = px,
                    ys = py,
                    text = cand.text,
                    color = cand.color,
                    outlineColor = cand.outlineColor,
                    fontSize = cand.fontSize,
                )
            } else {
                target.drawText(
                    x = cand.x,
                    y = cand.y,
                    text = cand.text,
                    color = cand.color,
                    outlineColor = cand.outlineColor,
                    fontSize = cand.fontSize,
                    anchor = cand.anchor,
                )
            }
        }

        target.endFrame()
    }

    private fun drawPoint(
        point: MapObject.Point,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
        labelScale: Float,
    ) {
        val style = styleResolver.resolve(point) as? Style.PointStyle ?: return
        val (x, y) = projection.project(point.longitude, point.latitude).run { x to y }
        if (!isOnScreen(x, y, target)) {
            return
        }
        target.drawPoint(x, y, style)
        val pointLabel = point.label
        if (style.showLabel && isDisplayableLabel(pointLabel)) {
            // Estimate text bounds from char count + font size — close enough
            // for collision testing without dragging in font-metrics from the
            // platform layer. Each char averages 0.55 * fontSize in width for
            // typical sans fonts.
            val w = pointLabel!!.length * DEFAULT_POINT_FONT_SIZE * 0.55f * labelScale
            val h = DEFAULT_POINT_FONT_SIZE * 1.2f * labelScale
            labels.submit(
                x = x,
                y = y + DEFAULT_POINT_LABEL_OFFSET,
                widthPx = w,
                heightPx = h,
                priority = style.drawPriority,
                text = pointLabel,
                color = style.labelColor,
                outlineColor = style.labelOutlineColor,
                fontSize = DEFAULT_POINT_FONT_SIZE,
                anchor = TextAnchor.CENTER,
            )
        }
    }

    private fun drawPolygon(
        poly: MapObject.PolyShape,
        style: Style.PolygonStyle,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
        labelScale: Float,
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
            val w = polyLabel!!.length * DEFAULT_AREA_FONT_SIZE * 0.55f * labelScale
            labels.submit(
                x = cx,
                y = cy,
                widthPx = w,
                heightPx = DEFAULT_AREA_FONT_SIZE * 1.2f * labelScale,
                priority = style.drawPriority,
                text = polyLabel,
                color = style.labelColor,
                outlineColor = null,
                fontSize = DEFAULT_AREA_FONT_SIZE,
                anchor = TextAnchor.CENTER,
            )
        }
    }

    private fun drawPolylineBorder(
        poly: MapObject.PolyShape,
        style: Style.LineStyle,
        projection: Projection,
        target: RenderTarget,
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
        target.drawPolylineBorder(xs, ys, style)
    }

    private fun drawPolylineCentre(
        poly: MapObject.PolyShape,
        style: Style.LineStyle,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
        labelScale: Float,
    ) {
        if (poly.longitudes.isEmpty()) {
            return
        }
        val n = poly.longitudes.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        projection.projectBulk(poly.longitudes, poly.latitudes, xs, ys)
        if (allOffScreen(xs, ys, target)) {
            return
        }
        target.drawPolylineStroke(xs, ys, style)
        val polyLabel = poly.label
        if (style.labelColor != null && isDisplayableLabel(polyLabel)) {
            val midIdx = n / 2
            val w = polyLabel!!.length * DEFAULT_LINE_FONT_SIZE * 0.55f * labelScale
            labels.submit(
                x = xs[midIdx],
                y = ys[midIdx],
                widthPx = w,
                heightPx = DEFAULT_LINE_FONT_SIZE * 1.2f * labelScale,
                priority = style.drawPriority,
                text = polyLabel,
                color = style.labelColor,
                outlineColor = null,
                fontSize = DEFAULT_LINE_FONT_SIZE,
                anchor = TextAnchor.CENTER,
                pathXs = xs,
                pathYs = ys,
            )
        }
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
            val lonRange = (viewport.maxLon - viewport.minLon).toDouble()
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
            // bias of -4 picks finest at deep zooms, level 2-3 at city
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
         * Negative → we pick coarser geometry than mathematically perfect;
         * mkgmap authors each level expecting it to render at roughly
         * (1 << -DETAIL_BIAS) px per delta = 16 px per delta, which is
         * the "comfortable detail" zoom each level was built for. -4 is
         * empirically the right value for OpenTopoMap and standard
         * mkgmap output; very different IMG generators may want a
         * different default exposed via the renderer constructor.
         */
        private const val DETAIL_BIAS: Double = -2.0

        /**
         * Picks the LoD level *and* validates that the chosen level has
         * sub-divisions intersecting the viewport. Steps progressively
         * coarser when the first pick is sparse — this matters for IMGs
         * (like OpenTopo Monaco) where the finest level only contains
         * data inside a narrow "detail area" smaller than the viewer's
         * tight bbox.
         */
        fun pickRenderLevelWithCoverage(
            viewport: Viewport,
            img: ImgFile,
        ): Int? {
            val materialised = HashSet<Int>()
            for (sd in img.tre.tree.all()) {
                materialised.add(sd.level)
            }
            val first = pickRenderLevel(viewport, img.tre.levels, materialised) ?: return null
            // Walk levels from finest-passing-the-cap down to coarsest;
            // first one with intersecting sub-divisions wins.
            val candidates = img.tre.levels.levels
                .filterNot { it.inherited }
                .sortedByDescending { it.bitsPerCoord }
            // Start at `first` and go coarser; also try finer if needed.
            val firstBits = img.tre.levels.bitsPerCoordOf(first) ?: return first
            val ordered = candidates
                .partition { it.bitsPerCoord <= firstBits }
                .let { (atOrCoarser, finer) -> atOrCoarser + finer }
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
            val bits = img.tre.levels
            for (sd in img.tre.tree.all()) {
                if (sd.level != level) {
                    continue
                }
                if (!img.subDivisionHasAnyDataInternal(sd)) {
                    continue
                }
                val bpc = bits.bitsPerCoordOf(sd.level) ?: continue
                val scale = 1 shl (24 - bpc).coerceAtLeast(0)
                val halfW = sd.halfWidth.toLong() * scale
                val halfH = sd.halfHeight.toLong() * scale
                val sdMinLon = sd.longitudeCenter - halfW
                val sdMaxLon = sd.longitudeCenter + halfW
                val sdMinLat = sd.latitudeCenter - halfH
                val sdMaxLat = sd.latitudeCenter + halfH
                if (sdMaxLon < viewport.minLon || sdMinLon > viewport.maxLon) {
                    continue
                }
                if (sdMaxLat < viewport.minLat || sdMinLat > viewport.maxLat) {
                    continue
                }
                return true
            }
            return false
        }

        /**
         * Sanity-only check on the raw label string: drop empty / null /
         * whitespace-only labels. We *do not* filter URL-shaped, hour-
         * shaped, or fragment-shaped strings here — old Locus and
         * GPSMapEdit show every label faithfully and rely on the
         * collision + same-text dedup pass in [LabelLayout] to keep
         * clutter under control. Re-introduce content filters only if a
         * specific IMG genuinely overwhelms the layout.
         */
        internal fun isDisplayableLabel(label: String?): Boolean {
            return !label.isNullOrBlank()
        }

        private const val LN2: Double = 0.6931471805599453
        private const val DEFAULT_POINT_LABEL_OFFSET: Float = 12f
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
 *   has a TYP sub-file (Phase C.7).
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
        fun typAware(table: com.asamm.garminimg.parser.TypStyleTable): StyleResolver {
            return StyleResolver { obj ->
                when (obj) {
                    is MapObject.Point -> {
                        val typPoint = table.lookupPoint(obj.type, obj.subType)
                        val internal = InternalStyles.resolve(obj) as? Style.PointStyle
                        if (typPoint != null && internal != null) {
                            internal.copy(
                                icon = typPoint.icon,
                                labelColor = typPoint.font?.dayColour ?: internal.labelColor,
                            )
                        } else {
                            internal
                        }
                    }
                    is MapObject.PolyShape -> if (obj.closed) {
                        val typ = table.lookupPolygon(obj.type, obj.subType)
                        if (typ != null) {
                            val internal = InternalStyles.resolvePolygon(obj.type)
                            // Use the TYP's DRAWORDER priority when present
                            // (lower → draw first → background); fall back
                            // to InternalStyles' static priority otherwise.
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
                                labelColor = typ.font?.dayColour ?: internal.labelColor,
                            )
                        } else {
                            InternalStyles.resolve(obj)
                        }
                    } else {
                        val typ = table.lookupLine(obj.type, obj.subType)
                        if (typ != null) {
                            val internal = InternalStyles.resolveLine(obj.type)
                            internal.copy(
                                strokeColor = typ.strokeColor,
                                borderColor = typ.borderColor ?: internal.borderColor,
                                strokeWidth = typ.strokeWidthPx,
                                borderWidth = typ.totalWidthPx,
                                pattern = typ.pattern,
                                labelColor = typ.font?.dayColour ?: internal.labelColor,
                            )
                        } else {
                            InternalStyles.resolve(obj)
                        }
                    }
                }
            }
        }
    }
}
