/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.style

import com.asamm.garminimg.model.MapObject

/**
 * Hardcoded default styles used when:
 * - the IMG has no TYP sub-file, or
 * - the TYP doesn't have an entry for a given `(type, subType)`.
 *
 * Replaces a small subset of the Java predecessor's `StyleInternal.java`
 * (1 794 LOC). We only port the most common types — enough to get a
 * recognisable map for unstyled Garmin IMGs. The full TYP-driven path
 * (Phase C.7) takes precedence whenever a TYP entry matches.
 *
 * Type codes follow the Garmin IMG specification (see also `ObjectKind`).
 */
object InternalStyles {

    /**
     * Returns a [Style] for the given map object kind + type/subtype, or a
     * sensible fallback.
     */
    fun resolve(obj: MapObject): Style {
        return when (obj) {
            is MapObject.Point -> resolvePoint(obj.type, obj.subType)
            is MapObject.PolyShape -> if (obj.closed) {
                resolvePolygon(obj.type)
            } else {
                resolveLine(obj.type)
            }
        }
    }

    fun resolvePoint(type: Int, subType: Int): Style.PointStyle {
        // City sizes (population markers) — type 0x01..0x11.
        return when (type) {
            in 0x01..0x11 -> CITY_LABEL
            in 0x40..0x4F -> POI_LABEL // restaurants, hotels, services
            in 0x60..0x66 -> POI_LABEL // transportation
            else -> DEFAULT_POINT
        }
    }

    fun resolveLine(type: Int): Style.LineStyle {
        return when (type) {
            // Major roads (motorway, principal highway, trunk)
            0x01 -> ROAD_MOTORWAY
            0x02 -> ROAD_PRINCIPAL
            0x03 -> ROAD_ARTERIAL
            // Local roads
            in 0x04..0x06 -> ROAD_LOCAL
            // Residential / minor
            in 0x07..0x09 -> ROAD_RESIDENTIAL
            // Path / track
            in 0x0A..0x0F -> ROAD_TRACK
            // Railway
            0x14 -> RAILWAY
            // Rivers / streams
            in 0x18..0x1F -> RIVER
            // Contour lines
            in 0x20..0x25 -> CONTOUR
            else -> DEFAULT_LINE
        }
    }

    fun resolvePolygon(type: Int): Style.PolygonStyle {
        return when (type) {
            // Land / built-up
            in 0x01..0x03 -> AREA_BUILT_UP
            // Parks / forest
            0x16 -> AREA_FOREST
            in 0x17..0x1A -> AREA_FOREST
            // Water bodies — Garmin's water-area type codes vary by tooling:
            //   0x28 sea/ocean (older Garmin), 0x32 sea (Mapwel),
            //   0x3B..0x44 mkgmap-style water (river/lake/etc.),
            //   0x4A..0x4C ocean-background polygons (mkgmap, OpenTopoMap).
            // Cover the whole accepted range — without TYP-driven styling
            // (Phase C.7), getting the ocean fill right is the headline
            // visual win.
            0x28, 0x32 -> AREA_WATER
            in 0x3B..0x44 -> AREA_WATER
            in 0x4A..0x4C -> AREA_OCEAN_BACKGROUND
            // Buildings
            0x0A -> BUILDING
            else -> DEFAULT_POLYGON
        }
    }

    //********************************
    // POINT STYLES
    //********************************

    private val DEFAULT_POINT = Style.PointStyle(
        drawPriority = 50,
        icon = null,
        labelColor = Color.rgb(0x33, 0x33, 0x33),
        labelOutlineColor = Color.White,
        showLabel = true,
    )

    private val CITY_LABEL = Style.PointStyle(
        drawPriority = 80,
        icon = null,
        labelColor = Color.rgb(0x00, 0x00, 0x00),
        labelOutlineColor = Color.White,
        showLabel = true,
    )

    private val POI_LABEL = Style.PointStyle(
        drawPriority = 60,
        icon = null,
        labelColor = Color.rgb(0x60, 0x40, 0x20),
        labelOutlineColor = Color.White,
        showLabel = true,
    )

    //********************************
    // LINE STYLES
    //********************************

    /**
     * Default label colour for road / street name labels — a dark grey
     * that reads against most backgrounds (white road fills, light
     * shaded built-up areas) without competing with road foreground
     * colour the way pure black would.
     */
    private val ROAD_LABEL_COLOR: Color = Color.rgb(0x33, 0x33, 0x33)

    private val DEFAULT_LINE = Style.LineStyle(
        drawPriority = 30,
        strokeColor = Color.rgb(0x80, 0x80, 0x80),
        borderColor = null,
        strokeWidth = 1.0f,
        borderWidth = 0f,
    )

    private val ROAD_MOTORWAY = Style.LineStyle(
        drawPriority = 90,
        strokeColor = Color.rgb(0xFF, 0xC8, 0x80), // orange
        borderColor = Color.rgb(0xC0, 0x60, 0x00),
        strokeWidth = 4.0f,
        borderWidth = 5.5f,
        labelColor = Color.rgb(0x40, 0x20, 0x00),
    )

    private val ROAD_PRINCIPAL = Style.LineStyle(
        drawPriority = 80,
        strokeColor = Color.rgb(0xFF, 0xE5, 0xA0), // light orange
        borderColor = Color.rgb(0xB0, 0x70, 0x20),
        strokeWidth = 3.0f,
        borderWidth = 4.0f,
        labelColor = Color.rgb(0x40, 0x20, 0x00),
    )

    private val ROAD_ARTERIAL = Style.LineStyle(
        drawPriority = 70,
        strokeColor = Color.rgb(0xFF, 0xFD, 0xC0), // pale yellow
        borderColor = Color.rgb(0x90, 0x90, 0x60),
        strokeWidth = 2.5f,
        borderWidth = 3.5f,
        labelColor = ROAD_LABEL_COLOR,
    )

    private val ROAD_LOCAL = Style.LineStyle(
        drawPriority = 60,
        strokeColor = Color.White,
        borderColor = Color.rgb(0x80, 0x80, 0x80),
        strokeWidth = 1.8f,
        borderWidth = 2.4f,
        labelColor = ROAD_LABEL_COLOR,
    )

    private val ROAD_RESIDENTIAL = Style.LineStyle(
        drawPriority = 50,
        strokeColor = Color.White,
        borderColor = Color.rgb(0xA0, 0xA0, 0xA0),
        strokeWidth = 1.4f,
        borderWidth = 1.8f,
        labelColor = ROAD_LABEL_COLOR,
    )

    private val ROAD_TRACK = Style.LineStyle(
        drawPriority = 40,
        strokeColor = Color.rgb(0xA0, 0x80, 0x60),
        borderColor = null,
        strokeWidth = 1.0f,
        borderWidth = 0f,
        dashPattern = floatArrayOf(4f, 3f),
        labelColor = Color.rgb(0x60, 0x40, 0x20),
    )

    private val RAILWAY = Style.LineStyle(
        drawPriority = 75,
        strokeColor = Color.rgb(0x40, 0x40, 0x40),
        borderColor = null,
        strokeWidth = 1.5f,
        borderWidth = 0f,
        dashPattern = floatArrayOf(8f, 4f),
    )

    private val RIVER = Style.LineStyle(
        drawPriority = 35,
        strokeColor = Color.rgb(0x60, 0xA0, 0xE0),
        borderColor = null,
        strokeWidth = 1.5f,
        borderWidth = 0f,
    )

    private val CONTOUR = Style.LineStyle(
        drawPriority = 20,
        strokeColor = Color.rgb(0xC0, 0x80, 0x40),
        borderColor = null,
        strokeWidth = 0.5f,
        borderWidth = 0f,
    )

    //********************************
    // POLYGON STYLES
    //********************************

    private val DEFAULT_POLYGON = Style.PolygonStyle(
        drawPriority = 10,
        fillColor = Color.rgb(0xE0, 0xE0, 0xE0),
        strokeColor = null,
    )

    private val AREA_BUILT_UP = Style.PolygonStyle(
        drawPriority = 15,
        fillColor = Color.rgb(0xE8, 0xE0, 0xD0),
        strokeColor = null,
    )

    private val AREA_FOREST = Style.PolygonStyle(
        drawPriority = 20,
        fillColor = Color.rgb(0xC0, 0xE0, 0xA0),
        strokeColor = null,
    )

    private val AREA_WATER = Style.PolygonStyle(
        drawPriority = 25,
        fillColor = Color.rgb(0xA8, 0xCC, 0xEC),
        strokeColor = Color.rgb(0x60, 0x80, 0xA0),
        strokeWidth = 0.5f,
        labelColor = Color.rgb(0x20, 0x40, 0x80),
    )

    /**
     * Ocean-background polygons are typically a single huge polygon covering
     * the whole sub-division's water area; rendered with a slightly cooler
     * blue and lower draw priority so smaller water bodies (lakes, rivers)
     * still pop on top of it.
     */
    private val AREA_OCEAN_BACKGROUND = Style.PolygonStyle(
        drawPriority = 5,
        fillColor = Color.rgb(0xA0, 0xC8, 0xE8),
        strokeColor = null,
        labelColor = null,
    )

    private val BUILDING = Style.PolygonStyle(
        drawPriority = 30,
        fillColor = Color.rgb(0xC0, 0xA0, 0x80),
        strokeColor = Color.rgb(0x80, 0x60, 0x40),
        strokeWidth = 0.5f,
    )
}
