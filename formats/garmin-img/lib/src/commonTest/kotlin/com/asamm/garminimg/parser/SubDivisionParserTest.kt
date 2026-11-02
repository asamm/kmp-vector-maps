package com.asamm.garminimg.parser

import com.asamm.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.model.ObjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubDivisionParserTest {

    @Test
    fun parsesSingleLeafLevelTree() {
        // One level (level 1, the leaf), three sub-divisions (records SMALL=14
        // bytes since this is the minLevel).
        val tre = TreBuilder()
            .headerLength(0x31)
            .bounds(north = 1_000_000, east = 1_000_000, south = 0, west = 0)
            .level(level = 1, bitsPerCoord = 18, subDivisionCount = 3)
            // Single-level maps still use FULL (16-byte) records — the SMALL
            // format kicks in only at the leaf level of a multi-level tree.
            .subDivisionFull(
                dataOffset = 0x10,
                objectTypes = ObjectKind.POINT or ObjectKind.POLYLINE,
                longitudeCenter = 100,
                latitudeCenter = 200,
                halfWidth = 50,
                halfHeight = 30,
                lastInLevel = false,
            )
            .subDivisionFull(
                dataOffset = 0x40,
                objectTypes = ObjectKind.POLYGON,
                longitudeCenter = 200,
                latitudeCenter = 300,
                halfWidth = 50,
                halfHeight = 30,
                lastInLevel = false,
            )
            .subDivisionFull(
                dataOffset = 0x80,
                objectTypes = ObjectKind.POINT,
                longitudeCenter = 300,
                latitudeCenter = 400,
                halfWidth = 50,
                halfHeight = 30,
                lastInLevel = true,
            )
            .build()

        val tree = openTreSubDivisions(tre)

        // With one level, all records are roots.
        assertEquals(3, tree.roots.size)
        assertEquals(3, tree.size)

        val s1 = tree.roots[0]
        assertEquals(1, s1.index)
        assertEquals(1, s1.level)
        assertEquals(0x10L, s1.dataOffset)
        // dataEnd is back-filled from next record's dataOffset.
        assertEquals(0x40L, s1.dataEnd)
        // Children empty — we only have one level.
        assertEquals(0, s1.children.size)

        val s2 = tree.roots[1]
        assertEquals(2, s2.index)
        assertEquals(0x40L, s2.dataOffset)
        assertEquals(0x80L, s2.dataEnd)

        val s3 = tree.roots[2]
        assertEquals(3, s3.index)
        assertEquals(0x80L, s3.dataOffset)
        // Last record: dataEnd remains 0 because we passed no rgnInfo.
        assertEquals(0L, s3.dataEnd)
    }

    @Test
    fun parsesTwoLevelTreeWithChildren() {
        // Levels: 2 (max, parent) → 1 (min, leaf). One parent at maxLevel, two
        // children at minLevel.
        val tre = TreBuilder()
            .headerLength(0x31)
            .bounds(north = 1_000_000, east = 1_000_000, south = 0, west = 0)
            .level(level = 2, bitsPerCoord = 16, subDivisionCount = 1)
            .level(level = 1, bitsPerCoord = 20, subDivisionCount = 2)
            // Index 1 (FULL): the lone parent at level 2. It points to index 2
            // for its first child.
            .subDivisionFull(
                dataOffset = 0x10,
                objectTypes = ObjectKind.POLYGON,
                longitudeCenter = 500,
                latitudeCenter = 500,
                halfWidth = 1000,
                halfHeight = 1000,
                indexNextLevel = 2,
                lastInLevel = true,
            )
            // Index 2 (SMALL): first child at level 1.
            .subDivisionSmall(
                dataOffset = 0x40,
                objectTypes = ObjectKind.POINT,
                longitudeCenter = 250,
                latitudeCenter = 500,
                halfWidth = 500,
                halfHeight = 500,
                lastInLevel = false,
            )
            // Index 3 (SMALL): second/last child at level 1.
            .subDivisionSmall(
                dataOffset = 0x80,
                objectTypes = ObjectKind.POLYLINE,
                longitudeCenter = 750,
                latitudeCenter = 500,
                halfWidth = 500,
                halfHeight = 500,
                lastInLevel = true,
            )
            .build()

        val tree = openTreSubDivisions(tre)

        assertEquals(1, tree.roots.size, "one parent at maxLevel")
        assertEquals(3, tree.size, "3 records total in tree")

        val parent = tree.roots[0]
        assertEquals(2, parent.level)
        assertEquals(2, parent.indexNextLevel)
        assertEquals(2, parent.children.size)

        val child1 = parent.children[0]
        assertEquals(2, child1.index)
        assertEquals(1, child1.level)
        assertEquals(0x40L, child1.dataOffset)
        assertEquals(0x80L, child1.dataEnd)

        val child2 = parent.children[1]
        assertEquals(3, child2.index)
        assertEquals(1, child2.level)
        assertEquals(0x80L, child2.dataOffset)

        // Tree.byIndex covers every node.
        assertNotNull(tree.byIndex[1])
        assertNotNull(tree.byIndex[2])
        assertNotNull(tree.byIndex[3])
        assertNull(tree.byIndex[4])
    }

    @Test
    fun assignsLastDataEndFromRgnInfo() {
        val tre = TreBuilder()
            .headerLength(0x31)
            .bounds(north = 1_000_000, east = 1_000_000, south = 0, west = 0)
            .level(level = 1, bitsPerCoord = 18, subDivisionCount = 2)
            .subDivisionFull(dataOffset = 0x10, lastInLevel = false)
            .subDivisionFull(dataOffset = 0x40, lastInLevel = true)
            .build()

        val tree = openTreSubDivisions(tre, rgnInfo = RgnEndInfo(dataEnd = 0x100L))

        assertEquals(0x40L, tree.roots[0].dataEnd, "non-last from neighbour")
        assertEquals(0x100L, tree.roots[1].dataEnd, "last from rgnInfo")
    }

    @Test
    fun extractsObjectTypeFlagsFromHighNibble() {
        val flags = ObjectKind.POINT or ObjectKind.POLYGON or ObjectKind.POLYLINE
        val tre = TreBuilder()
            .headerLength(0x31)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 1, bitsPerCoord = 18, subDivisionCount = 1)
            .subDivisionFull(dataOffset = 0x10, objectTypes = flags, lastInLevel = true)
            .build()

        val tree = openTreSubDivisions(tre)

        val s = tree.roots[0]
        assertTrue(s.objectTypes and ObjectKind.POINT != 0)
        assertTrue(s.objectTypes and ObjectKind.POLYGON != 0)
        assertTrue(s.objectTypes and ObjectKind.POLYLINE != 0)
        assertEquals(0, s.objectTypes and ObjectKind.INDEXED_POINT)
    }

    @Test
    fun emptyTreeWhenNoLevels() {
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .build()

        val tree = openTreSubDivisions(tre)

        assertEquals(0, tree.roots.size)
        assertEquals(0, tree.size)
    }

    @Test
    fun parsesExtendedTypesPerSubDivision() {
        // 3 sub-divisions + 2 TRE7 records. Index → extIndex mapping:
        //  totalSubDivisions = 1 (root) + 3 = 4, extendedRecordCount = 2,
        //  decalage = 4 - 2 = 2, so index N → extIndex N - 2.
        //  Index 1 → extIndex -1 (skipped)
        //  Index 2 → extIndex 0 (skipped: extIndex < 1)
        //  Index 3 → extIndex 1 → record[0]
        // Matching the Java predecessor's `decalajeExtendedTypes`.
        // We keep two TRE7 records so the synthetic test exercises the back-fill
        // (index 3's ext-end takes the next record's offset, which is record[1]).
        val tre = TreBuilder()
            .headerLength(0x86)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 1, bitsPerCoord = 18, subDivisionCount = 3)
            .subDivisionFull(dataOffset = 0x10, lastInLevel = false)
            .subDivisionFull(dataOffset = 0x20, lastInLevel = false)
            .subDivisionFull(dataOffset = 0x30, lastInLevel = true)
            .tre7Record(extendedPolygonOffset = 0xA0L, extendedPolylineOffset = 0xB0L, extendedPoiOffset = 0xC0L)
            .tre7Record(extendedPolygonOffset = 0xA8L, extendedPolylineOffset = 0xB8L, extendedPoiOffset = 0xC8L)
            .build()

        val tree = openTreSubDivisions(tre)

        // Index 1, 2 — no TRE7 attached (extIndex < 1, skipped by parser).
        val s1 = tree.byIndex[1] ?: error("missing index 1")
        assertEquals(0L, s1.extendedPolygonOffset)
        val s2 = tree.byIndex[2] ?: error("missing index 2")
        assertEquals(0L, s2.extendedPolygonOffset)

        // Index 3 → record[0].
        val s3 = tree.byIndex[3] ?: error("missing index 3")
        assertEquals(0xA0L, s3.extendedPolygonOffset)
        assertEquals(0xB0L, s3.extendedPolylineOffset)
        assertEquals(0xC0L, s3.extendedPoiOffset)
        // Index 3 is the last sub-division, so its ext-ends stay 0 (no rgnInfo
        // passed). Use a synthetic next-record-offset assertion at index 2 → 3.
        assertEquals(s3.extendedPolygonOffset, s2.extendedPolygonEnd)
    }

    /**
     * Opens a synthetic TRE, parses levels + extended-types descriptor + tree,
     * and returns the tree. Hides the boilerplate so each test reads cleanly.
     */
    private fun openTreSubDivisions(
        treBytes: ByteArray,
        rgnInfo: RgnEndInfo? = null,
    ): SubDivisionTree {
        val img = SyntheticImg.builder()
            .blockSizeExponent(9, 0)
            .addFatEntry(
                fileName = "00000001",
                fileType = "TRE",
                fileSize = treBytes.size,
                firstBlock = 4,
            )
            .payloadAtBlock(blockIndex = 4, bytes = treBytes)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val tre = archive.findFirst("TRE") ?: error("missing TRE")
        val stream = archive.openSubFile(tre, container)

        val header = TreSubFile.parseHeader(stream)
        val levels = TreSubFile.parseLevels(stream)
        return SubDivisionParser.parse(
            reader = stream,
            levels = levels,
            extendedTypes = header.extendedTypes,
            rgnInfo = rgnInfo,
        )
    }
}
