/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.style

import com.asamm.garminimg.model.MapObject

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
     */
    data class PointStyle(
        override val drawPriority: Int,
        /** Optional bitmap icon — `null` for "no icon, label only". */
        val icon: Bitmap?,
        /** Label colour (ARGB). */
        val labelColor: Color,
        /** Optional secondary colour for label outlines / haloes. */
        val labelOutlineColor: Color?,
        /** Whether to render the point's label (when one exists). */
        val showLabel: Boolean,
    ) : Style

    /**
     * Style for an open polyline — road, river, contour, trail.
     */
    data class LineStyle(
        override val drawPriority: Int,
        /** Foreground stroke colour. */
        val strokeColor: Color,
        /** Optional border colour drawn beneath the foreground stroke. */
        val borderColor: Color?,
        /** Foreground stroke width in logical pixels at level 1.0 zoom. */
        val strokeWidth: Float,
        /** Border stroke width (must be > [strokeWidth] to be visible). */
        val borderWidth: Float,
        /** Optional dash pattern: alternating on/off lengths in pixels. */
        val dashPattern: FloatArray? = null,
        /** Label colour for road names; null hides labels. */
        val labelColor: Color? = null,
        /**
         * Optional 32×height RGBA tile to paint as the line's foreground
         * stroke instead of a solid colour. The renderer tiles this
         * along the line direction (X = travel, Y = perpendicular) so
         * dashed/striped patterns follow the polyline's path.
         */
        val pattern: Bitmap? = null,
    ) : Style {

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
     */
    data class PolygonStyle(
        override val drawPriority: Int,
        /** Fill colour (interior). */
        val fillColor: Color,
        /** Optional outline stroke colour. */
        val strokeColor: Color?,
        /** Outline stroke width when [strokeColor] is non-null. */
        val strokeWidth: Float = 0.5f,
        /** Optional fill pattern (e.g. forest stipple). */
        val pattern: Bitmap? = null,
        /** Label colour for area names; null hides labels. */
        val labelColor: Color? = null,
    ) : Style
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
         * Builds a [Color] from individual channels. Channels are clamped to
         * 0..255.
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
 */
data class Bitmap(
    val width: Int,
    val height: Int,
    /**
     * RGBA bytes, row-major, top-to-bottom. Length is `4 * width * height`.
     */
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
