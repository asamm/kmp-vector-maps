package com.asamm.garminimg.parser

import com.asamm.garminimg.coord.CoordUtils
import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [TreSubFile.parseHeader] using synthetic TRE byte payloads
 * injected into a [SyntheticImg]. Each test crafts the smallest TRE that
 * exercises one parser branch.
 */
class TreSubFileTest {

    @Test
    fun parsesShortHeaderWithBoundsOnly() {
        val tre = TreBuilder()
            .headerLength(0x21) // exactly enough for bounds
            .bounds(north = 1_000_000, east = 500_000, south = 800_000, west = 300_000)
            .build()

        val header = openTre(tre)

        assertEquals(0x21, header.headerLength)
        assertEquals(1_000_000, header.northBoundary)
        assertEquals(500_000, header.eastBoundary)
        assertEquals(800_000, header.southBoundary)
        assertEquals(300_000, header.westBoundary)
        assertNull(header.extendedTypes, "no TRE7 expected for header < 0x82")
    }

    @Test
    fun handlesNegativeBoundsForSouthernHemisphere() {
        // Sydney area: ~-33.86° lat, 151.21° lon
        val north = CoordUtils.fromWGS84(-33.85)
        val east = CoordUtils.fromWGS84(151.22)
        val south = CoordUtils.fromWGS84(-33.87)
        val west = CoordUtils.fromWGS84(151.20)

        val tre = TreBuilder()
            .headerLength(0x21)
            .bounds(north = north, east = east, south = south, west = west)
            .build()

        val header = openTre(tre)

        // Round-trip: decoded bounds within tolerance of inputs.
        assertEquals(-33.85, CoordUtils.toWGS84(header.northBoundary), 1e-4)
        assertEquals(-33.87, CoordUtils.toWGS84(header.southBoundary), 1e-4)
        assertEquals(151.22, CoordUtils.toWGS84(header.eastBoundary), 1e-4)
        assertEquals(151.20, CoordUtils.toWGS84(header.westBoundary), 1e-4)
    }

    @Test
    fun parsesExtendedTypesDescriptorWhenHeaderIncludesTre7() {
        val tre = TreBuilder()
            .headerLength(0x90) // covers TRE7
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .extendedTypes(offset = 0x12345678L, length = 0x100L, recordSize = 0x10)
            .build()

        val header = openTre(tre)

        val ext = assertNotNull(header.extendedTypes, "TRE7 should be present for header 0x90")
        assertEquals(0x12345678L, ext.offset)
        assertEquals(0x100L, ext.length)
        assertEquals(0x10, ext.recordSize)
        assertEquals(0x10, ext.recordCount)
    }

    @Test
    fun absentExtendedTypesIfTre7LengthZero() {
        // Header is long enough to read the TRE7 fields, but length=0 means the
        // map has no extended-types data. Treat as "no extended types" (no Some).
        val tre = TreBuilder()
            .headerLength(0x90)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .extendedTypes(offset = 0L, length = 0L, recordSize = 0)
            .build()

        val header = openTre(tre)

        assertNull(header.extendedTypes)
    }

    @Test
    fun extendedTypesRecordCountIsZeroIfRecordSizeZero() {
        // Pathological but legal: length > 0 but recordSize = 0. The Java predecessor
        // guards against divide-by-zero by skipping the count; we expose 0.
        val tre = TreBuilder()
            .headerLength(0x90)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .extendedTypes(offset = 0x100L, length = 0x100L, recordSize = 0)
            .build()

        val header = openTre(tre)

        val ext = assertNotNull(header.extendedTypes)
        assertEquals(0, ext.recordCount)
    }

    @Test
    fun fullSurfaceComputesBoundsArea() {
        val tre = TreBuilder()
            .headerLength(0x21)
            .bounds(north = 100, east = 200, south = 50, west = 150)
            .build()

        val header = openTre(tre)

        // (100 - 50) * (200 - 150) = 2500
        assertEquals(2_500L, header.fullSurface)
    }

    @Test
    fun rejectsHeaderTooShortForBounds() {
        // headerLength of 0x10 is too short for the bounds at 0x15.
        val tre = TreBuilder()
            .headerLength(0x10)
            .bounds(north = 1, east = 1, south = 0, west = 0)
            .build()

        assertFailsWith<IllegalArgumentException> { openTre(tre) }
    }

    @Test
    fun rejectsLockedSubFile() {
        val tre = TreBuilder()
            .headerLength(0x21)
            .bounds(north = 1, east = 1, south = 0, west = 0)
            .locked(true)
            .build()

        assertFailsWith<IllegalArgumentException> { openTre(tre) }
    }

    @Test
    fun parsesEmptyLevelsSection() {
        // No level records — descriptor present but length=0.
        val tre = TreBuilder()
            .headerLength(0x29) // covers levels descriptor at 0x21..0x28
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .build()

        val levels = openTreLevels(tre)

        assertEquals(0, levels.levels.size)
        assertEquals(1, levels.totalSubDivisions, "implicit root counts as 1")
        // Defaults from Java seed.
        assertEquals(0, levels.maxLevel)
        assertEquals(16, levels.minLevel)
    }

    @Test
    fun parsesSingleLevelRecord() {
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 3, bitsPerCoord = 18, subDivisionCount = 5, inherited = false)
            .build()

        val levels = openTreLevels(tre)

        assertEquals(1, levels.levels.size)
        val lvl = levels.levels[0]
        assertEquals(3, lvl.level)
        assertEquals(18, lvl.bitsPerCoord)
        assertEquals(5, lvl.subDivisionCount)
        assertEquals(false, lvl.inherited)

        assertEquals(3, levels.maxLevel)
        assertEquals(3, levels.minLevel)
        assertEquals(18, levels.bitsPerCoordOf(3))
        assertNull(levels.bitsPerCoordOf(4))
        assertTrue(levels.isPresent(3))
        assertFalse(levels.isPresent(4))
        // 1 (root) + 5 (this level)
        assertEquals(6, levels.totalSubDivisions)
    }

    @Test
    fun parsesMultipleLevelsCorrectMinMax() {
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 5, bitsPerCoord = 12, subDivisionCount = 1)
            .level(level = 3, bitsPerCoord = 16, subDivisionCount = 4)
            .level(level = 1, bitsPerCoord = 20, subDivisionCount = 12)
            .build()

        val levels = openTreLevels(tre)

        assertEquals(3, levels.levels.size)
        assertEquals(5, levels.maxLevel)
        assertEquals(1, levels.minLevel)
        assertEquals(20, levels.bitsPerCoordOf(1))
        assertEquals(16, levels.bitsPerCoordOf(3))
        assertEquals(12, levels.bitsPerCoordOf(5))
        assertNull(levels.bitsPerCoordOf(0))
        assertNull(levels.bitsPerCoordOf(2))
        // Implicit root (1) + 1 + 4 + 12 = 18
        assertEquals(18, levels.totalSubDivisions)
    }

    @Test
    fun parsesInheritedLevelFlag() {
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 4, bitsPerCoord = 14, subDivisionCount = 8, inherited = true)
            .build()

        val levels = openTreLevels(tre)

        assertEquals(true, levels.levels[0].inherited)
        assertTrue(levels.isInherited(4))
        assertFalse(levels.isInherited(5))
    }

    @Test
    fun extractsBitsPerCoordZeroAt15ForCoarseLevel() {
        // Real-world IMGs often have level 15 as a placeholder with bitsPerCoord
        // set to 0 (coarsest, used as a sentinel by the renderer). Make sure that's
        // round-trippable.
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 15, bitsPerCoord = 0, subDivisionCount = 0)
            .build()

        val levels = openTreLevels(tre)

        assertEquals(15, levels.maxLevel)
        assertEquals(0, levels.bitsPerCoordOf(15))
    }

    @Test
    fun extendedTypesDecalageMatchesJavaFormula() {
        // decalajeExtendedTypes = totalSubDivisions - extendedTypesNumber. Mirror
        // the same numerical relation the Java predecessor used.
        val tre = TreBuilder()
            .headerLength(0x29)
            .bounds(north = 1_000, east = 1_000, south = 0, west = 0)
            .level(level = 1, bitsPerCoord = 20, subDivisionCount = 9)
            .level(level = 2, bitsPerCoord = 18, subDivisionCount = 3)
            .build()

        val levels = openTreLevels(tre)

        // 1 (root) + 9 + 3 = 13
        assertEquals(13, levels.totalSubDivisions)
        // With 5 extended-types records, decalage = 13 - 5 = 8.
        assertEquals(8, levels.extendedTypesDecalage(extendedTypesRecordCount = 5))
    }

    @Test
    fun rejectsBoundsWithNorthLessThanSouth() {
        // North < South is geometrically impossible — flag a corrupt TRE loudly
        // rather than silently producing degenerate coverage.
        val tre = TreBuilder()
            .headerLength(0x21)
            .bounds(north = 100, east = 200, south = 500, west = 150)
            .build()

        assertFailsWith<IllegalArgumentException> { openTre(tre) }
    }

    /**
     * Opens a synthetic IMG containing a single TRE sub-file and returns the
     * parsed header. Hides the Archive→stream→parseHeader boilerplate.
     */
    private fun openTre(treBytes: ByteArray): TreHeader {
        val stream = openTreStream(treBytes)
        return TreSubFile.parseHeader(stream)
    }

    private fun openTreLevels(treBytes: ByteArray): TreLevels {
        val stream = openTreStream(treBytes)
        return TreSubFile.parseLevels(stream)
    }

    private fun openTreStream(treBytes: ByteArray) = run {
        val img = SyntheticImg.builder()
            .blockSizeExponent(9, 0) // 512
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
        val tre = archive.findFirst("TRE")
        assertTrue(tre != null, "synthetic IMG missing TRE entry")
        archive.openSubFile(tre, container)
    }
}
