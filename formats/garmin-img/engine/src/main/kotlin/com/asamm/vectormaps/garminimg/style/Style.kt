/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.style

import com.asamm.vectormaps.garminimg.model.MapObject

/**
 * Platform-neutral style records resolved from `(type, subType)` pairs by a
 * [StyleResolver]. Each branch covers one of the three [MapObject] subtypes;
 * a [RenderTarget] consumes the matching style when drawing each object.
 *
 * Style records are immutable and cheap to share. The resolver caches them
 * by `(type, subType)` to avoid re-allocating per-object.
 */
sealed interface Style {

    /** Z-order priority within an [ObjectKind]. Higher draws on top. */
    val drawPriority: Int

    /**
     * Style for a [MapObject.Point] — POI marker, indexed point, etc.
     *
     * @param icon optional bitmap icon — `null` for "no icon, label only".
     * @param labelColor label colour (ARGB).
     * @param labelOutlineColor optional secondary colour for label outlines / haloes.
     * @param showLabel whether to render the point's label (when one exists).
     */
    // Non-data class so we can grow fields (night-mode palette, zoom-
    // bracket overrides) without breaking `.copy()` ABI for consumers.
    // Explicit `copy()` below covers the internal call sites.
    class PointStyle(
        override val drawPriority: Int,
        val icon: Bitmap?,
        val labelColor: Color,
        val labelOutlineColor: Color?,
        val showLabel: Boolean,
    ) : Style {

        /**
         * Returns a copy with the named fields overridden. Adding a new
         * field is binary-compatible as long as it carries a default —
         * unlike the data-class auto-generated `.copy()` whose
         * signature shifts on every field addition.
         */
        fun copy(
            drawPriority: Int = this.drawPriority,
            icon: Bitmap? = this.icon,
            labelColor: Color = this.labelColor,
            labelOutlineColor: Color? = this.labelOutlineColor,
            showLabel: Boolean = this.showLabel,
        ): PointStyle {
            return PointStyle(
                drawPriority = drawPriority,
                icon = icon,
                labelColor = labelColor,
                labelOutlineColor = labelOutlineColor,
                showLabel = showLabel,
            )
        }
    }

    /**
     * Style for an open polyline — road, river, contour, trail.
     *
     * @param strokeColor foreground stroke colour.
     * @param borderColor optional border colour drawn beneath the foreground stroke.
     * @param strokeWidth foreground stroke width in logical pixels at level 1.0 zoom.
     * @param borderWidth border stroke width (must be > [strokeWidth] to be visible).
     * @param dashPattern optional dash pattern: alternating on/off lengths in pixels.
     * @param labelColor label colour for road names; null hides labels.
     * @param pattern optional 32×height RGBA tile to paint as the line's foreground stroke
     *   instead of a solid colour. The renderer tiles this along the line direction (X = travel,
     *   Y = perpendicular) so dashed/striped patterns follow the polyline's path.
     */
    // Non-data class — see [PointStyle].
    class LineStyle(
        override val drawPriority: Int,
        val strokeColor: Color,
        val borderColor: Color?,
        val strokeWidth: Float,
        val borderWidth: Float,
        val dashPattern: FloatArray? = null,
        val labelColor: Color? = null,
        val pattern: Bitmap? = null,
    ) : Style {

        fun copy(
            drawPriority: Int = this.drawPriority,
            strokeColor: Color = this.strokeColor,
            borderColor: Color? = this.borderColor,
            strokeWidth: Float = this.strokeWidth,
            borderWidth: Float = this.borderWidth,
            dashPattern: FloatArray? = this.dashPattern,
            labelColor: Color? = this.labelColor,
            pattern: Bitmap? = this.pattern,
        ): LineStyle {
            return LineStyle(
                drawPriority = drawPriority,
                strokeColor = strokeColor,
                borderColor = borderColor,
                strokeWidth = strokeWidth,
                borderWidth = borderWidth,
                dashPattern = dashPattern,
                labelColor = labelColor,
                pattern = pattern,
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is LineStyle) {
                return false
            }
            if (drawPriority != other.drawPriority) return false
            if (strokeColor != other.strokeColor) return false
            if (borderColor != other.borderColor) return false
            if (strokeWidth != other.strokeWidth) return false
            if (borderWidth != other.borderWidth) return false
            if (labelColor != other.labelColor) return false
            if (pattern != other.pattern) return false
            if (dashPattern == null) {
                return other.dashPattern == null
            }
            if (other.dashPattern == null) {
                return false
            }
            return dashPattern.contentEquals(other.dashPattern)
        }

        override fun hashCode(): Int {
            var h = drawPriority
            h = 31 * h + strokeColor.hashCode()
            h = 31 * h + (borderColor?.hashCode() ?: 0)
            h = 31 * h + strokeWidth.hashCode()
            h = 31 * h + borderWidth.hashCode()
            h = 31 * h + (labelColor?.hashCode() ?: 0)
            h = 31 * h + (dashPattern?.contentHashCode() ?: 0)
            h = 31 * h + (pattern?.hashCode() ?: 0)
            return h
        }
    }

    /**
     * Style for a closed polygon — lake, park, building, area fill.
     *
     * @param fillColor fill colour (interior).
     * @param strokeColor optional outline stroke colour.
     * @param strokeWidth outline stroke width when [strokeColor] is non-null.
     * @param pattern optional fill pattern (e.g. forest stipple).
     * @param labelColor label colour for area names; null hides labels.
     */
    // Non-data class — see [PointStyle].
    class PolygonStyle(
        override val drawPriority: Int,
        val fillColor: Color,
        val strokeColor: Color?,
        val strokeWidth: Float = 0.5f,
        val pattern: Bitmap? = null,
        val labelColor: Color? = null,
    ) : Style {

        fun copy(
            drawPriority: Int = this.drawPriority,
            fillColor: Color = this.fillColor,
            strokeColor: Color? = this.strokeColor,
            strokeWidth: Float = this.strokeWidth,
            pattern: Bitmap? = this.pattern,
            labelColor: Color? = this.labelColor,
        ): PolygonStyle {
            return PolygonStyle(
                drawPriority = drawPriority,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                pattern = pattern,
                labelColor = labelColor,
            )
        }
    }
}

/**
 * 32-bit ARGB colour. Channels are packed `0xAARRGGBB`.
 */
@JvmInline
value class Color(val argb: Int) {

    val alpha: Int get() = (argb ushr 24) and 0xFF
    val red: Int get() = (argb ushr 16) and 0xFF
    val green: Int get() = (argb ushr 8) and 0xFF
    val blue: Int get() = argb and 0xFF

    companion object {

        /** Common colours used by [InternalStyles]; named for readability. */
        val Transparent: Color = Color(0x00000000.toInt())
        val Black: Color = Color(0xFF000000.toInt())
        val White: Color = Color(0xFFFFFFFF.toInt())

        /**
         * Builds a [Color] from individual channels. Each channel is masked to
         * its low 8 bits.
         */
        fun rgb(r: Int, g: Int, b: Int, a: Int = 0xFF): Color {
            return Color(
                ((a and 0xFF) shl 24) or
                    ((r and 0xFF) shl 16) or
                    ((g and 0xFF) shl 8) or
                    (b and 0xFF),
            )
        }
    }
}

/**
 * Platform-neutral bitmap referenced by [Style.PointStyle.icon] or
 * [Style.PolygonStyle.pattern]. Backed by RGBA pixels in row-major order.
 *
 * Loaded once per TYP icon record at parse time; rendered targets that need
 * to upload to GPU / Canvas / `BufferedImage` do so when first encountered.
 *
 * @param rgba RGBA bytes, row-major, top-to-bottom. Length is `4 * width * height`.
 */
data class Bitmap(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
) {

    init {
        require(width > 0 && height > 0) { "bitmap has zero dimension" }
        require(rgba.size == 4 * width * height) {
            "rgba size ${rgba.size} != 4 * $width * $height"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Bitmap) {
            return false
        }
        if (width != other.width) return false
        if (height != other.height) return false
        return rgba.contentEquals(other.rgba)
    }

    override fun hashCode(): Int {
        var h = width
        h = 31 * h + height
        h = 31 * h + rgba.contentHashCode()
        return h
    }
}
