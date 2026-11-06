/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import com.asamm.vectormaps.core.label.LabelText

/**
 * Resolves what a renderer's style draws for a feature without reading a map, so a host can style
 * its own points as the map does (LoPoints, D38). Thread-safe; a result holds for the renderer's
 * life, so a host caches it per tag set and zoom.
 */
interface FeatureQuery {

    /**
     * What the style draws for a point with [tags] at [zoom]; [FeatureStyle.EMPTY] when nothing.
     * Throws `IllegalStateException` once the renderer is closed.
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
 * A symbol image as PNG, at the renderer's device scale; equal by [id].
 *
 * @param id content hash of [png], equal for equal images in every run; a host caches by it
 * @param png shared with other results: not to be modified
 * @param priority the style's label priority, higher drawn first
 */
class FeatureSymbol(
    val id: Long,
    val png: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val priority: Int,
) {

    override fun equals(other: Any?): Boolean {
        return other is FeatureSymbol
            && other.id == id
            && other.widthPx == widthPx
            && other.heightPx == heightPx
            && other.priority == priority
    }

    override fun hashCode(): Int {
        return id.hashCode() * 31 + priority
    }
}

/**
 * A caption text with its size and colours, placed relative to the feature's point; the font
 * weight and slant are not reported.
 *
 * @param offsetXPx offset of the caption's [position] reference from the point, screen pixels
 * @param priority the style's label priority, higher drawn first
 */
data class FeatureCaption(
    val text: LabelText,
    val position: CaptionPosition,
    val offsetXPx: Float,
    val offsetYPx: Float,
    val priority: Int,
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
