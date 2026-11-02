/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.fixture

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.model.MapObject
import com.asamm.vectormaps.garminimg.model.ObjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end test of the top-level [ImgFile] API against the Monaco fixture.
 * Exercises archive open + sub-file enumeration + TRE/RGN/LBL parse + sub-
 * division tree walk + map-object iteration through one entry point.
 */
class OtmMonacoImgFileTest {

    @Test
    fun reopenedCopyReadsTheSameMapThroughItsOwnReaders() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
        val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
        val expected = img.readMap().toList()
        img.close()

        img.reopen { ByteArrayImgRandomAccessReader(bytes) }.use { copy ->
            assertSame(img.tre.tree, copy.tre.tree)
            assertSame(img.typStyles, copy.typStyles)
            assertEquals(expected, copy.readMap().toList())
        }
    }

    @Test
    fun openAndReadEverythingMonaco() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")

        val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }

        // Header sanity.
        assertEquals(2048, img.archive.blockSize)
        assertTrue(img.archive.description.startsWith("OpenTopoMap"))
        assertNotNull(img.tre.header.extendedTypes, "Monaco has TRE7 extended types")
        assertTrue(img.tre.tree.size > 0, "sub-division tree is empty")

        // Walk the whole map. Bound the count check at a reasonable lower
        // bound; if the parser is misaligned we'd see drastically fewer.
        val all = img.readMap().toList()
        assertTrue(
            all.size > 100,
            "Monaco yielded too few features: ${all.size}",
        )
        val points = all.count { it is MapObject.Point }
        val polys = all.count { it is MapObject.PolyShape }
        val polylines = all.count { it is MapObject.PolyShape && !it.closed }
        val polygons = all.count { it is MapObject.PolyShape && it.closed }
        assertTrue(points > 0, "no points emitted")
        assertTrue(polys > 0, "no polygons / polylines emitted")
        assertTrue(
            polylines > 0,
            "no polylines (roads, contours) — RGN polyline parser misfired",
        )
        assertTrue(
            polygons > 0,
            "no polygons (areas) — RGN polygon parser misfired",
        )
    }

    @Test
    fun objectKindFilterRestrictsOutput() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")

        val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }

        val pointsOnly = img.readMap(objectKindFilter = ObjectKind.POINT).toList()
        // Every emitted object should be a point with `indexed=false` (POINT,
        // not INDEXED_POINT, not extended).
        for (obj in pointsOnly) {
            assertTrue(obj is MapObject.Point, "non-point leaked through filter: $obj")
            assertEquals(false, (obj as MapObject.Point).indexed)
        }
        // Non-zero count — Monaco has plenty of POIs.
        assertTrue(pointsOnly.isNotEmpty(), "POINT filter yielded nothing")
    }
}
