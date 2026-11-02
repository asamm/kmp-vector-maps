/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sanity checks on [ObjectKind] constants — values are on-disk semantics and must
 * match the Java predecessor. Renumbering would silently break sub-division parsing.
 */
class ObjectKindTest {

    @Test
    fun objectKindValuesMatchOnDiskSemantics() {
        // Object kinds (low byte = unknown flags, high nibble = real kinds)
        assertEquals(0x10, ObjectKind.POINT)
        assertEquals(0x20, ObjectKind.INDEXED_POINT)
        assertEquals(0x40, ObjectKind.POLYLINE)
        assertEquals(0x80, ObjectKind.POLYGON)
        assertEquals(0x100, ObjectKind.EXTENDED_POLYGON)
        assertEquals(0x200, ObjectKind.EXTENDED_POLYLINE)
        assertEquals(0x400, ObjectKind.EXTENDED_POINT)
    }

    @Test
    fun mapKindValuesMatchOnDiskSemantics() {
        assertEquals(0x1000, ObjectKind.BASE_MAP)
        assertEquals(0x2000, ObjectKind.NORMAL_MAP)
        assertEquals(0xF000, ObjectKind.ALL_MAPS)
        // ALL_MAPS is the OR of all map-kind bits; both BASE_MAP and NORMAL_MAP
        // must fit inside it.
        assertTrue((ObjectKind.BASE_MAP and ObjectKind.ALL_MAPS) == ObjectKind.BASE_MAP)
        assertTrue((ObjectKind.NORMAL_MAP and ObjectKind.ALL_MAPS) == ObjectKind.NORMAL_MAP)
    }

    @Test
    fun objectKindFlagsAreOrable() {
        // Real-world usage: a sub-division advertises POINT|POLYLINE|POLYGON.
        val mask = ObjectKind.POINT or ObjectKind.POLYLINE or ObjectKind.POLYGON
        assertTrue(mask and ObjectKind.POINT != 0)
        assertTrue(mask and ObjectKind.POLYLINE != 0)
        assertTrue(mask and ObjectKind.POLYGON != 0)
        assertEquals(0, mask and ObjectKind.EXTENDED_POINT)
    }

    @Test
    fun allBitMaskCoversEverything() {
        // ALL is the catch-all selector used by callers asking for "every kind".
        val everything = listOf(
            ObjectKind.POINT, ObjectKind.INDEXED_POINT, ObjectKind.POLYLINE, ObjectKind.POLYGON,
            ObjectKind.EXTENDED_POINT, ObjectKind.EXTENDED_POLYLINE, ObjectKind.EXTENDED_POLYGON,
            ObjectKind.BASE_MAP, ObjectKind.NORMAL_MAP,
        )
        for (kind in everything) {
            assertEquals(
                kind,
                kind and ObjectKind.ALL,
                "ALL should mask through ${kind.toString(16)}",
            )
        }
    }
}
