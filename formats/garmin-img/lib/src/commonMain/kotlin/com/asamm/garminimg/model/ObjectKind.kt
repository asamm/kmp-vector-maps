/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.model

/**
 * Bit-flag constants identifying the kind of objects available in an IMG sub-division
 * and the kind of the map itself.
 *
 * The values are OR-able: a sub-division typically advertises any of `POINT`,
 * `INDEXED_POINT`, `POLYLINE`, `POLYGON` (or their extended variants) when its RGN
 * region contains data of that kind. `BASE_MAP` and `NORMAL_MAP` discriminate the
 * coarse overview map from the detailed level inside an IMG family.
 *
 * Ported verbatim from the Asamm-fork `org.free.garminimg.ObjectKind`. Constant
 * values match the on-disk semantics — do not renumber.
 */
object ObjectKind {

    //********************************
    // OBJECT KINDS
    //********************************

    const val POINT: Int = 0x10
    const val INDEXED_POINT: Int = 0x20
    const val POLYLINE: Int = 0x40
    const val POLYGON: Int = 0x80

    const val UNKNOWN1: Int = 0x01
    const val UNKNOWN2: Int = 0x02
    const val UNKNOWN3: Int = 0x04
    const val UNKNOWN4: Int = 0x08

    const val EXTENDED_POLYGON: Int = 0x100
    const val EXTENDED_POLYLINE: Int = 0x200
    const val EXTENDED_POINT: Int = 0x400

    //********************************
    // MAP KINDS
    //********************************

    const val ALL_MAPS: Int = 0xF000
    const val BASE_MAP: Int = 0x1000
    const val NORMAL_MAP: Int = 0x2000

    //********************************
    // CATCH-ALL
    //********************************

    const val ALL: Int = 0xFFFFF
}
