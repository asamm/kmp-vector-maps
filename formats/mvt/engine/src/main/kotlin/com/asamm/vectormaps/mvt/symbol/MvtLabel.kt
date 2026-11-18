/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.style.Color
import kotlin.math.ceil

/**
 * One symbol a tile's symbol layers place: a text, an icon or both, at a point or along a line.
 * Coordinates are pixels of the requested tile, from its top-left corner.
 *
 * @param priority higher places first: later style layers, then lower `symbol-sort-key`, then
 *  earlier in the data tile
 * @param path a text along a line: the stretch it runs along, x, y pairs; null for the others
 * @param allowOverlap placed whatever it overlaps (`text-allow-overlap`, `icon-allow-overlap`),
 *  still blocking the labels placed after it unless [ignorePlacement]
 * @param ignorePlacement with [allowOverlap], blocks no other label (`text-ignore-placement`,
 *  `icon-ignore-placement`); alone it is not honoured
 * @param lineAngleDeg the line's direction at the anchor, clockwise from east, for an icon that
 *  turns with its line; null for one that stays upright
 * @param keepUpright a path text flipped so it never reads upside down (`text-keep-upright`);
 *  false draws it in its line's direction
 */
class MvtLabel(
    val layerId: String,
    val priority: Int,
    val x: Float,
    val y: Float,
    val text: MvtText?,
    val icon: MvtIcon?,
    val path: FloatArray?,
    val allowOverlap: Boolean,
    val ignorePlacement: Boolean = false,
    val lineAngleDeg: Float? = null,
    val keepUpright: Boolean = true,
)

/**
 * Shaped text: its lines and the box they fill, centred at an offset from the label's anchor.
 *
 * @param width the widest line, in pixels; the full text for a path text
 * @param centerX offset of the box centre from the anchor; unused for a path text
 * @param centerY offset of the box centre from the anchor; across the line for a path text
 * @param justify where shorter lines sit in the box, 0 left to 1 right (`text-justify`)
 * @param rotateDeg `text-rotate`, clockwise: the box about the anchor, or each glyph of a path text
 *  about its centre, after the text-offset shift
 * @param haloBlurPx `text-halo-blur`: the halo's edge fades over [HALO_BLUR_FADE] times it on either
 *  side of [haloWidthPx], as MapLibre's
 */
class MvtText(
    val lines: List<String>,
    val font: MvtFont,
    val sizePx: Float,
    val color: Color,
    val haloColor: Color,
    val haloWidthPx: Float,
    val letterSpacingPx: Float,
    val lineHeightPx: Float,
    val width: Float,
    val height: Float,
    val centerX: Float,
    val centerY: Float,
    val paddingPx: Float,
    val justify: Float = 0.5f,
    val rotateDeg: Float = 0f,
    val haloBlurPx: Float = 0f,
) {

    /**
     * How far a line's baseline lies below the line's centre for a font of `ascentPx` at this size:
     * MapLibre lays a line out from 17/24 of the size above its centre and measures glyph tops from
     * the font's ascender.
     */
    fun baselinePx(ascentPx: Float): Float {
        return ascentPx - sizePx * LINE_TOP_EM
    }

    /**
     * The text has a halo to draw, one with a colour and a width; as MapLibre, a blur alone draws
     * none.
     */
    val hasHalo: Boolean
        get() {
            return haloColor.alpha > 0
                && haloWidthPx > 0f
        }

    /**
     * How many strokes draw the halo, each by [haloRingRadius] and [haloRingColor] from the
     * outermost: one for a sharp halo, more for a blurred one.
     */
    val haloRings: Int
        get() {
            if (!(haloBlurPx > 0f)) {
                return 1
            }
            val rings = ceil(2f * HALO_BLUR_FADE * haloBlurPx / HALO_RING_STEP_PX).toInt()
            return rings.coerceIn(1, MAX_HALO_RINGS)
        }

    /**
     * The stroke radius of halo `ring`, 0 the outermost; 0 or less draws nothing.
     */
    fun haloRingRadius(ring: Int): Float {
        val fade = HALO_BLUR_FADE * haloBlurPx.coerceAtLeast(0f)
        return haloWidthPx + fade - (ring + 0.5f) * 2f * fade / haloRings
    }

    /**
     * The colour of halo `ring`, so the rings drawn over each other step the halo's alpha evenly
     * from its outer edge to its full [haloColor] inside.
     */
    fun haloRingColor(ring: Int): Color {
        val alpha = haloColor.alpha / 255.0
        return haloColor.withOpacity(1.0 / (haloRings - alpha * ring))
    }

    companion object {

        /**
         * The fade of a blurred halo's edge on either side, per pixel of `text-halo-blur`, as
         * MapLibre's SDF shader.
         */
        const val HALO_BLUR_FADE = 1.19f

        // MapLibre's 24 px em laid out from -17 px
        private const val LINE_TOP_EM = 17f / 24f

        // the width of one step of a blurred halo's fade
        private const val HALO_RING_STEP_PX = 1.5f

        private const val MAX_HALO_RINGS = 6
    }
}

/**
 * A sprite image drawn at an offset from the label's anchor, `scale` times its sprite size.
 *
 * @param centerX `icon-offset` of the centre from the anchor, turned with the icon
 * @param translateX `icon-translate`, a shift in the map's frame that never turns with the icon
 * @param opacity `icon-opacity`, 0 to 1
 */
class MvtIcon(
    val name: String,
    val width: Float,
    val height: Float,
    val centerX: Float,
    val centerY: Float,
    val translateX: Float,
    val translateY: Float,
    val scale: Float,
    val rotateDeg: Float,
    val paddingPx: Float,
    val opacity: Float = 1f,
)

/**
 * The face a `text-font` stack asks for; platforms map it to the fonts they have.
 */
data class MvtFont(
    val serif: Boolean,
    val bold: Boolean,
    val italic: Boolean,
) {

    companion object {

        val REGULAR = MvtFont(serif = false, bold = false, italic = false)

        /**
         * The face of the stack's first font name, such as "Noto Serif Italic".
         */
        fun of(stack: List<*>?): MvtFont {
            val name = (stack?.firstOrNull() as? String)?.lowercase() ?: return REGULAR
            return MvtFont(
                serif = "serif" in name && "sans" !in name,
                bold = "bold" in name,
                italic = "italic" in name,
            )
        }
    }
}

/**
 * Text measuring, by the platform that draws the text later, so boxes fit what is drawn.
 */
fun interface TextMetrics {

    /**
     * The advance width of `text` in pixels as the platform shapes it, `letterSpacingPx` added
     * between its glyphs.
     */
    fun width(text: String, sizePx: Float, font: MvtFont, letterSpacingPx: Float): Float
}
