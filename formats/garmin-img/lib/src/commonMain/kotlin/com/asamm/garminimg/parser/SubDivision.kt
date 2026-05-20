/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

/**
 * One entry in the TRE sub-division tree. A sub-division covers a rectangular
 * patch of the map at a specific zoom level; it points into RGN sub-file at
 * [dataOffset] for its plain-types objects (POINT / POLYLINE / POLYGON), and
 * (when the IMG has TRE7) into the extended-types arrays for EXT_POI / EXT_POLY.
 *
 * Sub-divisions form a tree: the coarsest level lives at the root, finer
 * levels descend via [children]. Each sub-division is identified by a unique
 * 1-based [index] within the TRE's sub-divisions section.
 *
 * # Mutability
 *
 * The fields parsed directly from the on-disk record (everything except the
 * `*End` group) are immutable. The end offsets are computed in a second pass
 * once neighbour records are known — they're carried as constructor parameters
 * with default 0 so the parser can build the record up incrementally and
 * downstream code can still treat instances as values.
 *
 * @property index 1-based unique identifier within the TRE.
 * @property level Zoom level (0..15).
 * @property dataOffset RGN logical byte offset of this sub-division's plain
 *   data (POINT / INDEXED_POINT / POLYLINE / POLYGON records).
 * @property dataEnd RGN logical byte offset where this sub-division's plain
 *   data ends (exclusive). 0 until back-filled by neighbour info.
 * @property objectTypes Bit-flag set of [ObjectKind.POINT]…[ObjectKind.EXTENDED_POINT]
 *   describing what kinds of objects this sub-division contains. Top nibble of
 *   the on-disk byte is the dataOffset overflow.
 * @property longitudeCenter Garmin-coord longitude of the sub-division centre.
 * @property latitudeCenter Garmin-coord latitude of the sub-division centre.
 * @property halfWidth Half-width in level-local coord units. Cast to Garmin
 *   coords by multiplying by `1 shl (24 - bitsPerCoord(level))`.
 * @property halfHeight Half-height in level-local coord units; same scaling.
 * @property indexNextLevel 1-based index of this sub-division's first child at
 *   the next finer level. 0 for leaf sub-divisions.
 * @property children Recursive children at finer levels. Empty for leaves.
 * @property extendedPolygonOffset Logical RGN offset of this sub-division's
 *   extended-polygon data (TRE7 only). 0 if the IMG has no extended types.
 * @property extendedPolygonEnd End offset of extended-polygon range (exclusive).
 * @property extendedPolylineOffset Logical RGN offset of this sub-division's
 *   extended-polyline data.
 * @property extendedPolylineEnd End offset of extended-polyline range.
 * @property extendedPoiOffset Logical RGN offset of this sub-division's
 *   extended-POI data.
 * @property extendedPoiEnd End offset of extended-POI range.
 */
// Non-data class so the (already-16-arg) primary constructor can grow
// without breaking `.copy()` for downstream consumers. Each field is
// `val` and not mutated post-construction, so equality / hash semantics
// are unchanged from the previous `data class` — we never compared
// SubDivision instances by value.
class SubDivision(
    val index: Int,
    val level: Int,
    val dataOffset: Long,
    val dataEnd: Long,
    val objectTypes: Int,
    val longitudeCenter: Int,
    val latitudeCenter: Int,
    val halfWidth: Int,
    val halfHeight: Int,
    val indexNextLevel: Int,
    val children: List<SubDivision>,
    val extendedPolygonOffset: Long = 0L,
    val extendedPolygonEnd: Long = 0L,
    val extendedPolylineOffset: Long = 0L,
    val extendedPolylineEnd: Long = 0L,
    val extendedPoiOffset: Long = 0L,
    val extendedPoiEnd: Long = 0L,
) {

    /**
     * True when this sub-division has at least one byte of plain RGN data.
     */
    val hasData: Boolean
        get() = dataEnd > dataOffset
}
