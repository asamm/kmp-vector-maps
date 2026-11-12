/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.symbol

import com.asamm.vectormaps.mvt.style.Color

/**
 * One symbol a tile's symbol layers place: a text, an icon or both, at a point or along a line.
 * Coordinates are pixels of the requested tile, from its top-left corner.
 *
 * @param priority higher places first: later style layers, then lower `symbol-sort-key`
 * @param path a text along a line: the stretch it runs along, x, y pairs; null for the others
 * @param allowOverlap placed whatever it overlaps (`text-allow-overlap`, `icon-allow-overlap`)
 * @param lineAngleDeg the line's direction at the anchor, clockwise from east, for an icon that
 *  turns with its line; null for one that stays upright
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
    val lineAngleDeg: Float? = null,
)

/**
 * Shaped text: its lines and the box they fill, centred at an offset from the label's anchor.
 *
 * @param width the widest line, in pixels; the full text for a path text
 * @param centerX offset of the box centre from the anchor; unused for a path text
 * @param centerY offset of the box centre from the anchor; across the line for a path text
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
)

/**
 * A sprite image drawn at an offset from the label's anchor, `scale` times its sprite size.
 *
 * @param centerX `icon-offset` of the centre from the anchor, turned with the icon
 * @param translateX `icon-translate`, a shift in the map's frame that never turns with the icon
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
