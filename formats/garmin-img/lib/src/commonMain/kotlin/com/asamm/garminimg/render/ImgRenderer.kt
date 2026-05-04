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
import com.asamm.garminimg.style.Color
import com.asamm.garminimg.style.InternalStyles
import com.asamm.garminimg.style.Style

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
        target.beginFrame(backgroundColor)

        for (obj in img.readMapInBounds(
            minLong = viewport.minLon,
            maxLong = viewport.maxLon,
            minLat = viewport.minLat,
            maxLat = viewport.maxLat,
            objectKindFilter = objectKindFilter,
        )) {
            drawObject(obj, projection, target, labels)
        }

        // Second pass: emit non-overlapping labels in priority order so the
        // most important ones survive when text would otherwise pile up.
        labels.drawAccepted { cand ->
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

        target.endFrame()
    }

    private fun drawObject(
        obj: MapObject,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
    ) {
        when (obj) {
            is MapObject.Point -> drawPoint(obj, projection, target, labels)
            is MapObject.PolyShape -> drawPoly(obj, projection, target, labels)
        }
    }

    private fun drawPoint(
        point: MapObject.Point,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
    ) {
        val style = styleResolver.resolve(point) as? Style.PointStyle ?: return
        val (x, y) = projection.project(point.longitude, point.latitude).run { x to y }
        if (!isOnScreen(x, y, target)) {
            return
        }
        target.drawPoint(x, y, style)
        if (style.showLabel && !point.label.isNullOrEmpty()) {
            // Estimate text bounds from char count + font size — close enough
            // for collision testing without dragging in font-metrics from the
            // platform layer. Each char averages 0.55 * fontSize in width for
            // typical sans fonts.
            val w = point.label.length * DEFAULT_POINT_FONT_SIZE * 0.55f
            val h = DEFAULT_POINT_FONT_SIZE * 1.2f
            labels.submit(
                x = x,
                y = y + DEFAULT_POINT_LABEL_OFFSET,
                widthPx = w,
                heightPx = h,
                priority = style.drawPriority,
                text = point.label,
                color = style.labelColor,
                outlineColor = style.labelOutlineColor,
                fontSize = DEFAULT_POINT_FONT_SIZE,
                anchor = TextAnchor.CENTER,
            )
        }
    }

    private fun drawPoly(
        poly: MapObject.PolyShape,
        projection: Projection,
        target: RenderTarget,
        labels: LabelLayout,
    ) {
        if (poly.longitudes.isEmpty()) {
            return
        }
        val n = poly.longitudes.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        projection.projectBulk(poly.longitudes, poly.latitudes, xs, ys)
        // Skip polylines/polygons whose every vertex sits well outside the
        // visible viewport — these are typically features from sub-divisions
        // whose extent reaches past the rendered bbox. Drawing them produces
        // artifacts at the screen edge.
        if (allOffScreen(xs, ys, target)) {
            return
        }

        if (poly.closed) {
            val style = styleResolver.resolve(poly) as? Style.PolygonStyle ?: return
            target.drawPolygon(xs, ys, style)
            if (style.labelColor != null && !poly.label.isNullOrEmpty()) {
                val (cx, cy) = centroid(xs, ys)
                val w = poly.label.length * DEFAULT_AREA_FONT_SIZE * 0.55f
                labels.submit(
                    x = cx,
                    y = cy,
                    widthPx = w,
                    heightPx = DEFAULT_AREA_FONT_SIZE * 1.2f,
                    priority = style.drawPriority,
                    text = poly.label,
                    color = style.labelColor,
                    outlineColor = null,
                    fontSize = DEFAULT_AREA_FONT_SIZE,
                    anchor = TextAnchor.CENTER,
                )
            }
        } else {
            val style = styleResolver.resolve(poly) as? Style.LineStyle ?: return
            target.drawPolyline(xs, ys, style)
            if (style.labelColor != null && !poly.label.isNullOrEmpty()) {
                val midIdx = n / 2
                val w = poly.label.length * DEFAULT_LINE_FONT_SIZE * 0.55f
                labels.submit(
                    x = xs[midIdx],
                    y = ys[midIdx],
                    widthPx = w,
                    heightPx = DEFAULT_LINE_FONT_SIZE * 1.2f,
                    priority = style.drawPriority,
                    text = poly.label,
                    color = style.labelColor,
                    outlineColor = null,
                    fontSize = DEFAULT_LINE_FONT_SIZE,
                    anchor = TextAnchor.CENTER,
                )
            }
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

    private fun centroid(xs: FloatArray, ys: FloatArray): FloatPair {
        // Simple arithmetic mean — good enough for label placement on small
        // convex-ish polygons. Polygon-area-weighted centroid is a Phase C.9
        // refinement.
        var sx = 0f
        var sy = 0f
        for (i in xs.indices) {
            sx += xs[i]
            sy += ys[i]
        }
        return FloatPair(sx / xs.size, sy / ys.size)
    }

    companion object {
        private const val DEFAULT_POINT_LABEL_OFFSET: Float = 12f
        private const val DEFAULT_POINT_FONT_SIZE: Float = 11f
        private const val DEFAULT_LINE_FONT_SIZE: Float = 10f
        private const val DEFAULT_AREA_FONT_SIZE: Float = 12f
        private const val OFFSCREEN_MARGIN: Int = 32
        private const val OFFSCREEN_LINE_MARGIN: Int = 64
    }
}

/**
 * Resolves `(type, subType)` to a [Style]. Two implementations:
 *
 * - [internalDefaults] — uses [InternalStyles] hardcoded fallbacks. The
 *   bootstrap path used until the TYP body parser (Phase C.7) lands.
 * - [withTyp] — TYP-first lookup with InternalStyles fallback (Phase C.7).
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
    }
}
