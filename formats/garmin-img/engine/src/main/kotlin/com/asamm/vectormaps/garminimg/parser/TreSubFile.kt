/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.parser

import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader

/**
 * Parser for a `.TRE` sub-file. The TRE carries the table-of-contents for an IMG
 * map: bounding box, zoom levels, sub-division tree, and (in newer mkgmap output)
 * extended-types section pointers.
 *
 * This is the entry point for everything else — RGN / LBL / NET parsers all key
 * off offsets the TRE provides.
 *
 * # Phase B.2.3 status
 *
 * - **B.2.3.a (this commit):** common header + bbox + extended-types block.
 * - **B.2.3.b (next):** levels (zoom + bits-per-coord per level).
 * - **B.2.3.c (last):** sub-division tree and the extended-types division branch.
 *
 * Until B.2.3.b lands, [TreHeader] only carries the four boundary values and the
 * extended-types signal — enough to confirm the parser reaches the right bytes
 * against real fixtures.
 *
 * # On-disk header layout (legacy / non-NT)
 *
 * Offsets are relative to the start of the TRE sub-file:
 * - `0x00..0x01` — sub-file header length (UInt16). Common to every sub-file.
 * - `0x0D` — lock flag (must be 0; see [readSubFileCommonHeader]).
 * - `0x15..0x17` — north boundary (Int24).
 * - `0x18..0x1A` — east boundary (Int24).
 * - `0x1B..0x1D` — south boundary (Int24).
 * - `0x1E..0x20` — west boundary (Int24).
 * - `0x21..0x24` — zoom-levels section offset (UInt32). [B.2.3.b]
 * - `0x25..0x28` — zoom-levels section length (UInt32). [B.2.3.b]
 * - `0x29..0x2C` — sub-divisions section offset (UInt32). [B.2.3.c]
 * - `0x2D..0x30` — sub-divisions section length (UInt32). [B.2.3.c]
 * - `0x7C..0x7F` — TRE7 extended-types section offset (UInt32, optional).
 * - `0x80..0x83` — TRE7 extended-types section length (UInt32, optional).
 * - `0x84..0x85` — TRE7 extended-types record size (UInt16, optional).
 *
 * The TRE7 fields exist only when the sub-file's headerLength is at least
 * `0x82` (`0x7C + 4 + 4 + 2`), which is mkgmap's default for newer builds.
 */
object TreSubFile {

    /**
     * Sub-file offset where the bounding-box quartet starts. Four consecutive
     * Int24s: north, east, south, west.
     */
    private const val OFFSET_BOUNDS: Long = 0x15L

    /**
     * Sub-file offset of the levels-section descriptor: UInt32 offset + UInt32
     * length to where the per-level zoom/bits-per-coord records live.
     */
    private const val OFFSET_LEVELS_DESCRIPTOR: Long = 0x21L

    /**
     * Sub-file offset of the sub-divisions-section descriptor. Reserved for
     * B.2.3.c.
     */
    @Suppress("unused")
    private const val OFFSET_SUBDIVISIONS_DESCRIPTOR: Long = 0x29L

    /**
     * Sub-file offset of the TRE7 extended-types descriptor. The descriptor is
     * present only when the sub-file's reported header length covers it.
     */
    private const val OFFSET_TRE7: Long = 0x7CL

    /**
     * Minimum [SubFileCommonHeader.headerLength] that includes the TRE7 descriptor
     * (`OFFSET_TRE7 + 4 + 4 + 2`). Headers shorter than this don't carry the
     * extended-types pointers.
     */
    private const val MIN_HEADER_LENGTH_FOR_TRE7: Int = 0x82

    /**
     * One level record in the levels section is exactly 4 bytes wide:
     * zoom byte, bits-per-coord byte, sub-division count UInt16.
     */
    private const val LEVEL_RECORD_SIZE: Int = 4

    /**
     * Hard upper bound for `recordCount` in [parseLevels]. Real IMGs
     * carry at most ~16 levels; this is a DoS cap, not a format limit.
     */
    private const val MAX_TRE_LEVELS: Int = 256

    /**
     * Bit set on the zoom byte's high bit indicating that this level inherits its
     * objects from the next-higher level rather than carrying its own data.
     */
    private const val ZOOM_INHERITED_BIT: Int = 0x80

    /**
     * Reads the TRE common header, bounds, and (when present) the extended-types
     * section descriptor. Leaves `reader` positioned arbitrarily; callers seek
     * explicitly for further fields.
     *
     * @throws IllegalArgumentException if the sub-file is locked, has a header too
     *   short to carry the bounding box, or has an out-of-range bounds quartet.
     */
    fun parseHeader(reader: ImgRandomAccessReader): TreHeader {
        val common = reader.readSubFileCommonHeader()
        require(common.headerLength >= 0x21) {
            "TRE header length 0x${common.headerLength.toString(16)} is too small for " +
                "the bounding box (need >= 0x21)."
        }

        reader.seek(OFFSET_BOUNDS)
        val north = reader.readInt24()
        val east = reader.readInt24()
        val south = reader.readInt24()
        val west = reader.readInt24()
        require(north >= south) {
            "TRE bounds invalid: north=$north < south=$south"
        }

        val extendedTypes = if (common.headerLength >= MIN_HEADER_LENGTH_FOR_TRE7) {
            reader.seek(OFFSET_TRE7)
            val offset = reader.readUInt32()
            val length = reader.readUInt32()
            val recordSize = reader.readUInt16()
            if (length > 0L) {
                ExtendedTypesInfo(offset = offset, length = length, recordSize = recordSize)
            } else {
                null
            }
        } else {
            null
        }

        return TreHeader(
            headerLength = common.headerLength,
            northBoundary = north,
            eastBoundary = east,
            southBoundary = south,
            westBoundary = west,
            extendedTypes = extendedTypes,
        )
    }

    /**
     * Reads the zoom-levels section of the TRE.
     *
     * Each level record is 4 bytes: zoom byte (low nibble = level 0..15, high bit
     * = inherited flag), bits-per-coord byte (typically 0..24), sub-division
     * count UInt16. The number of records is implicit — keep reading until the
     * descriptor's `length` is exhausted.
     *
     * The `bits-per-coord` value drives [TreSubFile.convertMapUnits]-equivalent
     * scaling: at level L with bitsPerCoord B, a coordinate delta is scaled by
     * `1 shl (24 - B - additionalAccuracy)`.
     *
     * @throws IllegalArgumentException if the descriptor's length is not a clean
     *   multiple of [LEVEL_RECORD_SIZE].
     */
    fun parseLevels(reader: ImgRandomAccessReader): TreLevels {
        reader.seek(OFFSET_LEVELS_DESCRIPTOR)
        val levelsOffset = reader.readUInt32()
        val levelsLength = reader.readUInt32()

        require(levelsLength % LEVEL_RECORD_SIZE == 0L) {
            "Levels section length $levelsLength is not a multiple of $LEVEL_RECORD_SIZE"
        }
        // Bound the on-disk length against the sub-file. A corrupt /
        // attacker-crafted TRE header carrying levelsLength = 2^32-1
        // would otherwise drive an ~8 GB ArrayList allocation before
        // the first record read fails. mkgmap-authored maps ship
        // ≤ 16 levels; the cap below is several orders of magnitude
        // larger than anything seen in the wild.
        require(levelsLength in 0L..reader.size) {
            "TRE levels length $levelsLength outside sub-file (size=${reader.size})"
        }
        val recordCount = (levelsLength / LEVEL_RECORD_SIZE)
            .coerceAtMost(MAX_TRE_LEVELS.toLong())
            .toInt()
        val levels = ArrayList<TreLevel>(recordCount)
        reader.seek(levelsOffset)
        repeat(recordCount) {
            val zoom = reader.readByte()
            val bitsPerCoord = reader.readByte()
            val subDivisionCount = reader.readUInt16()
            levels.add(
                TreLevel(
                    level = zoom and 0x0F,
                    bitsPerCoord = bitsPerCoord,
                    subDivisionCount = subDivisionCount,
                    inherited = (zoom and ZOOM_INHERITED_BIT) != 0,
                ),
            )
        }

        // Java predecessor seeds totalSubDivisions = 1 (the implicit root entry)
        // before summing per-level counts. We mirror so `decalajeExtendedTypes`
        // (the offset by which TRE7 records start past plain sub-divisions)
        // matches the original.
        val totalSubDivisions = 1 + levels.sumOf { it.subDivisionCount }
        return TreLevels(levels = levels, totalSubDivisions = totalSubDivisions)
    }
}

/**
 * Parsed TRE header: bounds + extended-types signal.
 *
 * Bounds are in Garmin fixed-point coordinates (24-bit signed; one unit ≈ 2.4 m
 * at the equator). Use `com.asamm.vectormaps.garminimg.utils.coord.CoordUtils.toWGS84` to convert.
 *
 * @property fullSurface Cached `(north - south) * (east - west)` of the bounding
 *   box in Garmin-coord units squared. Used by `ImgFilesBag.FileComparator` in the
 *   Java predecessor to sort overlapping IMG tiles by descending coverage area.
 */
data class TreHeader(
    val headerLength: Int,
    val northBoundary: Int,
    val eastBoundary: Int,
    val southBoundary: Int,
    val westBoundary: Int,
    val extendedTypes: ExtendedTypesInfo?,
) {

    /**
     * [eastBoundary] with 180°, stored as 0x800000 and read as -0x800000, back at 0x800000.
     */
    val eastBoundaryUnwrapped: Int
        get() = if (eastBoundary == -0x800000) 0x800000 else eastBoundary

    val fullSurface: Long
        get() = (northBoundary.toLong() - southBoundary) *
            (eastBoundary.toLong() - westBoundary)
}

/**
 * Pointers to the TRE7 extended-types section. Present in mkgmap output when at
 * least one extended-type sub-division is emitted; absent in the simplest IMGs
 * and in older Garmin tooling output.
 *
 * @property offset Absolute byte offset (within the IMG container) of the start
 *   of the extended-types data block.
 * @property length Total byte length of the extended-types data block.
 * @property recordSize Size, in bytes, of one extended-types record. The total
 *   record count is `length / recordSize` when `recordSize > 0`.
 */
data class ExtendedTypesInfo(
    val offset: Long,
    val length: Long,
    val recordSize: Int,
) {

    val recordCount: Int
        get() = if (recordSize > 0) {
            (length / recordSize).toInt()
        } else {
            0
        }
}

/**
 * One zoom level inside a TRE.
 *
 * Garmin maps are tree-structured by zoom: at the coarsest level (highest
 * `level` number) the whole map is one or a few sub-divisions; at finer levels
 * each parent sub-division splits into children. [bitsPerCoord] sets the
 * coordinate precision available at this level — a coordinate delta with `b`
 * bits represents `1 shl (24 - b)` Garmin-coord units.
 *
 * @property level Zoom level identifier, 0..15. Higher = coarser.
 * @property bitsPerCoord Number of bits used for relative coordinates within
 *   this level's sub-divisions, typically 0..24.
 * @property subDivisionCount Count of sub-divisions belonging to this level.
 * @property inherited If true, this level borrows its objects from the next
 *   higher level rather than carrying its own data.
 */
data class TreLevel(
    val level: Int,
    val bitsPerCoord: Int,
    val subDivisionCount: Int,
    val inherited: Boolean,
)

/**
 * Parsed levels section: ordered list of level records plus aggregates.
 *
 * Sub-division parsing (B.2.3.c) keys off [maxLevel] (top-of-tree) and
 * [minLevel] (leaves); rendering walks `maxLevel..minLevel` and selects the
 * appropriate level by comparing the requested resolution against
 * [bitsPerCoordOf].
 *
 * @param totalSubDivisions total sub-division count seeded from the implicit root (1) plus the
 *   sum of [TreLevel.subDivisionCount]. Matches the Java predecessor's `subdivisionss` running
 *   total.
 */
data class TreLevels(
    val levels: List<TreLevel>,
    val totalSubDivisions: Int,
) {

    /**
     * Highest level number in [levels] (the coarsest zoom). Defaults to 0 if
     * the section is empty — same behaviour as the Java seed.
     */
    val maxLevel: Int by lazy { levels.maxOfOrNull { it.level } ?: 0 }

    /**
     * Lowest level number in [levels] (the finest zoom). Defaults to 16 if the
     * section is empty — matches the Java seed.
     */
    val minLevel: Int by lazy { levels.minOfOrNull { it.level } ?: 16 }

    /**
     * Returns the bits-per-coord for `level`, or null if no level record with
     * that number exists. Used by sub-division parsing to know how many bits
     * to decode per coordinate delta.
     */
    fun bitsPerCoordOf(level: Int): Int? {
        return levels.firstOrNull { it.level == level }?.bitsPerCoord
    }

    /**
     * True if [levels] contains a record for `level`. Sub-division descent
     * skips absent levels.
     */
    fun isPresent(level: Int): Boolean {
        return levels.any { it.level == level }
    }

    /**
     * True if `level`'s objects are inherited from the next-higher level. False
     * for absent levels.
     */
    fun isInherited(level: Int): Boolean {
        return levels.firstOrNull { it.level == level }?.inherited ?: false
    }

    /**
     * Offset by which extended-types sub-division records start past plain
     * sub-division records. Mirrors `decalajeExtendedTypes` in the Java
     * predecessor.
     */
    fun extendedTypesDecalage(extendedTypesRecordCount: Int): Int {
        return totalSubDivisions - extendedTypesRecordCount
    }
}
