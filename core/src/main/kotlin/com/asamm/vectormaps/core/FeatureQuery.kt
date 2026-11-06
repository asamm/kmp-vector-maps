/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import com.asamm.vectormaps.core.label.LabelDisplay
import com.asamm.vectormaps.core.label.LabelText

/**
 * Resolves what a renderer's style draws for a feature without reading a map, so a host can style
 * its own points as the map does (LoPoints, D38). Thread-safe; a result holds for the renderer's
 * life, so a host caches it per tag set and zoom.
 */
interface FeatureQuery {

    /**
     * What the style draws for a point with [tags] at [zoom], in 0..[TileRequest.MAX_ZOOM] (else
     * `IllegalArgumentException`); [FeatureStyle.EMPTY] when nothing. May wait for running renders,
     * so call it off the main thread and never from a render hook; throws `IllegalStateException`
     * once the renderer is closed.
     */
    fun point(tags: Map<String, String>, zoom: Int): FeatureStyle
}

/**
 * The symbols and captions a style draws for one feature, in the style's order.
 */
data class FeatureStyle(
    val symbols: List<FeatureSymbol>,
    val captions: List<FeatureCaption>,
) {

    companion object {

        val EMPTY = FeatureStyle(symbols = emptyList(), captions = emptyList())
    }
}

/**
 * A symbol image as PNG, at the renderer's device scale; equal by every field but [png], which
 * [id] stands for.
 *
 * @param id content hash of [png], equal for equal images in every run; a host caches by it
 * @param png shared with other results: not to be modified
 * @param offsetXPx the image's top-left corner relative to the point, screen pixels
 * @param priority the style's label priority, higher drawn first
 */
class FeatureSymbol(
    val id: Long,
    val png: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val offsetXPx: Float,
    val offsetYPx: Float,
    val priority: Int,
    val display: LabelDisplay,
) {

    override fun equals(other: Any?): Boolean {
        return other is FeatureSymbol
            && other.id == id
            && other.widthPx == widthPx
            && other.heightPx == heightPx
            && other.offsetXPx == offsetXPx
            && other.offsetYPx == offsetYPx
            && other.priority == priority
            && other.display == display
    }

    override fun hashCode(): Int {
        return id.hashCode() * 31 + priority
    }
}

/**
 * A caption text with its font size and colours, placed relative to the feature's point; the
 * font weight and slant are not reported, so [LabelText.bold] and [LabelText.italic] are false.
 *
 * @param offsetXPx offset of the caption's [position] reference from the point, screen pixels
 * @param maxWidthPx the width the map wraps the text at, screen pixels
 * @param priority the style's label priority, higher drawn first
 */
data class FeatureCaption(
    val text: LabelText,
    val position: CaptionPosition,
    val offsetXPx: Float,
    val offsetYPx: Float,
    val maxWidthPx: Int,
    val priority: Int,
    val display: LabelDisplay,
)

/**
 * Where a caption sits relative to its point after the offset: centred on it, or beside it.
 */
enum class CaptionPosition {
    CENTER,
    BELOW,
    BELOW_LEFT,
    BELOW_RIGHT,
    ABOVE,
    ABOVE_LEFT,
    ABOVE_RIGHT,
    LEFT,
    RIGHT,
}
