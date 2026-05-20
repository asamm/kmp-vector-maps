/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.render

import com.asamm.garminimg.style.Color

/**
 * Two-pass label placement: collect candidate labels during the geometry
 * draw pass, then sort by priority and emit only those whose bounding box
 * doesn't overlap a higher-priority label that's already been placed.
 *
 * Replaces the Java predecessor's `LabelDeClutteringFilter` (which used a
 * centre-point overlap check that mis-handled long road names). The
 * implementation here uses bbox overlap + a small inflation margin to
 * leave breathing room.
 *
 * # Algorithm
 *
 * 1. Caller adds candidates with [submit]. Each carries a draw priority and
 *    an estimated text-bounds rectangle.
 * 2. [drawAccepted] sorts by descending priority and walks; for each
 *    candidate it tests bbox overlap against every already-accepted label.
 *    Non-overlapping → emit; overlapping → drop.
 *
 * The collision test is O(n²) where n is the number of labels per frame.
 * For Monaco-scale renders n is ~200, so 40k comparisons per frame is fine.
 * Spatial-hash optimisation can come later if a full-country render becomes
 * too slow.
 */
class LabelLayout {

    private val candidates = mutableListOf<LabelCandidate>()

    /**
     * Register a label candidate. Estimated bounds drive the overlap test;
     * the renderer should use a font-metrics-based estimate, not a guess —
     * AWT and Canvas both expose this cheaply.
     */
    fun submit(
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        priority: Int,
        text: String,
        color: Color,
        outlineColor: Color?,
        fontSize: Float,
        anchor: TextAnchor,
        pathXs: FloatArray? = null,
        pathYs: FloatArray? = null,
    ) {
        candidates.add(
            LabelCandidate(
                x = x,
                y = y,
                widthPx = widthPx,
                heightPx = heightPx,
                priority = priority,
                text = text,
                color = color,
                outlineColor = outlineColor,
                fontSize = fontSize,
                anchor = anchor,
                pathXs = pathXs,
                pathYs = pathYs,
            ),
        )
    }

    /**
     * Sorts candidates by descending priority and emits the survivors of
     * two filters via [drawCallback], in this order:
     *
     * 1. **Same-text dedup.** Garmin IMGs frequently emit the same place
     *    name on every adjacent sub-division boundary ("Gare de Nice-Ville"
     *    × 8, "Le 19" × 2, etc.). Once a label with text T has been kept,
     *    later candidates with the same T within [DEDUP_RADIUS_PX] are
     *    dropped. Sub-pixel coords use a hash of `(text, gridCellX, gridCellY)`
     *    so the test is O(1) per candidate, not O(n²).
     * 2. **Bbox overlap.** Same as before — keep the highest-priority of
     *    every overlapping group.
     *
     * Clears the queue afterwards.
     */
    fun drawAccepted(drawCallback: (LabelCandidate) -> Unit) {
        if (candidates.isEmpty()) {
            return
        }
        // Stable descending sort by priority — labels with the same priority
        // keep parser yield order so consecutive renders are deterministic.
        candidates.sortByDescending { it.priority }

        // Track accepted bounds for overlap tests. Use parallel arrays
        // instead of a list of objects to avoid GC pressure during long
        // renders.
        val accLeft = FloatArray(candidates.size)
        val accTop = FloatArray(candidates.size)
        val accRight = FloatArray(candidates.size)
        val accBottom = FloatArray(candidates.size)
        var accCount = 0

        // Dedup grid: text → set of grid cells that already host an accepted
        // copy. A candidate is a duplicate when one of its 9 neighbour
        // cells already holds a same-text accepted label, which approximates
        // a circular radius without per-pair distance math.
        val acceptedCells = HashMap<String, MutableSet<Long>>()

        for (cand in candidates) {
            val text = cand.text
            if (text.isNotEmpty()) {
                val cellX = (cand.x / DEDUP_RADIUS_PX).toInt()
                val cellY = (cand.y / DEDUP_RADIUS_PX).toInt()
                val cells = acceptedCells[text]
                if (cells != null) {
                    var seen = false
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val key = ((cellX + dx).toLong() shl 32) or
                                ((cellY + dy).toLong() and 0xFFFFFFFFL)
                            if (cells.contains(key)) {
                                seen = true
                                break
                            }
                        }
                        if (seen) break
                    }
                    if (seen) {
                        continue
                    }
                }
            }

            val left = cand.left
            val top = cand.top
            val right = cand.right
            val bottom = cand.bottom

            var overlaps = false
            for (i in 0 until accCount) {
                if (right < accLeft[i] || left > accRight[i]) {
                    continue
                }
                if (bottom < accTop[i] || top > accBottom[i]) {
                    continue
                }
                overlaps = true
                break
            }
            if (!overlaps) {
                accLeft[accCount] = left
                accTop[accCount] = top
                accRight[accCount] = right
                accBottom[accCount] = bottom
                accCount++
                if (text.isNotEmpty()) {
                    val cellX = (cand.x / DEDUP_RADIUS_PX).toInt()
                    val cellY = (cand.y / DEDUP_RADIUS_PX).toInt()
                    val key = (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
                    acceptedCells.getOrPut(text) { HashSet() }.add(key)
                }
                drawCallback(cand)
            }
        }
        candidates.clear()
    }

    private companion object {
        /**
         * Radius (in screen pixels) inside which two same-text labels are
         * considered duplicates. Picked at 200 px to swallow the typical
         * "label repeated on each sub-division boundary" pattern but stay
         * small enough that distinct same-named places (e.g., two
         * "Pharmacie" POIs in different blocks) survive.
         */
        const val DEDUP_RADIUS_PX: Float = 200f
    }

    /** True when [drawAccepted] would emit nothing. */
    val isEmpty: Boolean get() = candidates.isEmpty()
}

/**
 * One label awaiting placement. Bounds are inflated by a small margin so
 * labels never touch each other.
 *
 * @param pathXs optional polyline x-coords the label should curve along. When present, the
 *   renderer calls [RenderTarget.drawTextOnPath] instead of the straight [RenderTarget.drawText]
 *   — that's how road names follow the road's bend.
 * @param pathYs y-coords paired with [pathXs].
 */
data class LabelCandidate(
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val heightPx: Float,
    val priority: Int,
    val text: String,
    val color: Color,
    val outlineColor: Color?,
    val fontSize: Float,
    val anchor: TextAnchor,
    val pathXs: FloatArray? = null,
    val pathYs: FloatArray? = null,
) {

    private val halfW: Float = widthPx / 2f + LABEL_PADDING
    private val halfH: Float = heightPx / 2f + LABEL_PADDING

    val left: Float get() = x - halfW
    val right: Float get() = x + halfW
    val top: Float get() = y - halfH
    val bottom: Float get() = y + halfH

    private companion object {
        /** Inflation in pixels on each side. */
        private const val LABEL_PADDING: Float = 2f
    }
}
