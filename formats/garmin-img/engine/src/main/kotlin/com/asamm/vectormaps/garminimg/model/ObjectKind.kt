/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.model

/**
 * Bit-flag constants identifying the kind of objects available in an IMG sub-division.
 *
 * The values are OR-able: a sub-division typically advertises any of `POINT`,
 * `INDEXED_POINT`, `POLYLINE`, `POLYGON` (or their extended variants) when its RGN
 * region contains data of that kind. Constant values match the on-disk semantics — do not
 * renumber.
 */
object ObjectKind {

    //********************************
    // OBJECT KINDS
    //********************************

    const val POINT: Int = 0x10
    const val INDEXED_POINT: Int = 0x20
    const val POLYLINE: Int = 0x40
    const val POLYGON: Int = 0x80

    const val EXTENDED_POLYGON: Int = 0x100
    const val EXTENDED_POLYLINE: Int = 0x200
    const val EXTENDED_POINT: Int = 0x400

    //********************************
    // CATCH-ALL
    //********************************

    const val ALL: Int = 0xFFFFF
}
