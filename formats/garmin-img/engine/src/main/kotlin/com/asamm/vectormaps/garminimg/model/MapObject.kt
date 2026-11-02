/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.model

/**
 * One object emitted by an RGN sub-division parse: a point, polyline, or polygon.
 *
 * Replaces the Java predecessor's `MapListener.addPoint` / `addPoly` callback
 * shape with a Kotlin-idiomatic sealed hierarchy. The renderer iterates the
 * sequence and dispatches by branch.
 *
 * @property type Primary type byte, e.g. [ObjectKind] flag's "real" value
 *   (`0x01..0x7F` for plain types; for extended types this carries the
 *   16-bit type with the high byte set).
 * @property subType Secondary classification byte (0 unless the on-disk
 *   record carries a sub-type).
 * @property label Resolved label text (already decoded via the LBL codepage),
 *   or null if the record had no label.
 */
sealed interface MapObject {

    val type: Int
    val subType: Int
    val label: String?

    /**
     * A point feature (POI, marker, label-only feature). Coordinates are in
     * absolute Garmin fixed-point units, computed from the sub-division
     * centre + record's deltas.
     */
    data class Point(
        override val type: Int,
        override val subType: Int,
        val longitude: Int,
        val latitude: Int,
        override val label: String?,
        /**
         * True for `INDEXED_POINT` records (city/town markers indexed by RGN
         * for label lookups), false for plain `POINT` records.
         */
        val indexed: Boolean,
        /**
         * True if the point's label-offset bit-22 was set on disk
         * indicating a POI record. POIs additionally carry the
         * structured [poi] block (street / city / zip / phone).
         */
        val isPoi: Boolean = false,
        /**
         * Structured POI metadata — present only when the LBL has a
         * POI properties section AND this record is flagged [isPoi].
         * The legacy parser exposed these via lazy [POILabel] getters;
         * we surface them eagerly so consumers don't have to re-seek
         * into LBL.
         */
        val poi: PoiMetadata? = null,
    ) : MapObject

    /**
     * A polyline (road, river, trail) or polygon (lake, park, building),
     * discriminated by [closed]: `false` = polyline, `true` = polygon.
     */
    data class PolyShape(
        override val type: Int,
        override val subType: Int,
        val longitudes: IntArray,
        val latitudes: IntArray,
        override val label: String?,
        /**
         * `true` for polygons (closed shape, area), `false` for polylines
         * (open path, road).
         */
        val closed: Boolean,
        /**
         * Direction flag for polylines (one-way streets, contour direction).
         * Always `false` for polygons.
         */
        val direction: Boolean = false,
        /**
         * Bit 22 of the on-disk `info2` field. Indicates the polyline
         * uses an extra precision bit on its longitude coordinates;
         * already consumed by the geometry decoder, surfaced here for
         * completeness and so consumers can validate against the
         * source IMG.
         */
        val extraBit: Boolean = false,
        /**
         * Bit 23 of the on-disk `info2` field. When set on a polyline,
         * the `labelOffset` indexes the NET sub-file's road-definitions
         * table (which redirects to the actual LBL string). Already
         * applied during label resolution; surfaced so renderers /
         * downstream tooling can tell which records were resolved
         * through NET.
         */
        val dataInNet: Boolean = false,
    ) : MapObject {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is PolyShape) {
                return false
            }
            if (type != other.type) return false
            if (subType != other.subType) return false
            if (label != other.label) return false
            if (closed != other.closed) return false
            if (direction != other.direction) return false
            if (extraBit != other.extraBit) return false
            if (dataInNet != other.dataInNet) return false
            if (!longitudes.contentEquals(other.longitudes)) return false
            if (!latitudes.contentEquals(other.latitudes)) return false
            return true
        }

        override fun hashCode(): Int {
            var h = type
            h = 31 * h + subType
            h = 31 * h + (label?.hashCode() ?: 0)
            h = 31 * h + closed.hashCode()
            h = 31 * h + direction.hashCode()
            h = 31 * h + extraBit.hashCode()
            h = 31 * h + dataInNet.hashCode()
            h = 31 * h + longitudes.contentHashCode()
            h = 31 * h + latitudes.contentHashCode()
            return h
        }
    }
}
