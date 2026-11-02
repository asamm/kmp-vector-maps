/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.parser

import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader

/**
 * Parser for a `.TYP` sub-file. TYP holds custom styling — point / line /
 * polygon icons and colours keyed by `(type, subType)`. The Java predecessor
 * fork only ever read the header (filename, codepage, signed family/product
 * identifiers); the actual styling-table parsing was never written. We mirror
 * that scope: the header surfaces what's there, and a TODO points at where
 * the rest needs to land.
 *
 * # Phase B.2.6 scope
 *
 * Header-only. Section descriptors for points, lines, polygons, and the
 * draw-priority / icons sub-tables are parsed but their contents are NOT
 * decoded — [TypBodyParser] does that. The renderer can short-circuit to the
 * built-in `StyleInternal` defaults when the TYP body is unreadable here.
 *
 * # On-disk layout
 *
 * - byte 0 — header length (UInt8). Note: TYP uses an 8-bit header length,
 *   unlike TRE/RGN/LBL/NET which all use UInt16 at offset 0.
 * - byte 1 — zero
 * - bytes 2..0xB — 10-byte ASCII signature ("GARMIN TYP" or "GARMIN " etc.)
 * - bytes 0xC, 0xD — version-marker bytes (typically 1, 0)
 * - bytes 0xE..0x14 — build timestamp (year UInt16, month, day, hour, minute, second bytes)
 * - bytes 0x15..0x16 — codepage (UInt16)
 * - bytes 0x17..0x32 — six section descriptors (points / lines / polygons),
 *   each one UInt32 offset + UInt32 length.
 * - bytes 0x33..0x36 — family ID (UInt16), product code (UInt16).
 *
 * No common-sub-file-header (UInt16 length + lock at 0xD) at the start.
 */
object TypSubFile {

    /**
     * Reads the TYP header.
     */
    fun parseHeader(reader: ImgRandomAccessReader): TypHeader {
        reader.seek(0L)
        val headerLength = reader.readByte()
        require(headerLength > 0) { "TYP header length is zero" }
        reader.seek(1L)
        require(reader.readByte() == 0) { "TYP header byte 1 is non-zero (corrupt?)" }

        // Signature "GARMIN TYP" (10 bytes ASCII).
        reader.seek(2L)
        val signature = reader.readString(10).trimEnd(' ', '\u0000')

        // Version markers at 0xC, 0xD — read but unused.
        reader.seek(0xCL)
        reader.readByte()
        reader.readByte()

        // Build timestamp at 0xE..0x14.
        reader.seek(0xEL)
        val year = reader.readUInt16()
        val month = reader.readByte()
        val day = reader.readByte()
        val hour = reader.readByte()
        val minute = reader.readByte()
        val second = reader.readByte()

        // Codepage at 0x15.
        reader.seek(0x15L)
        val codepage = reader.readUInt16()

        // Section descriptors at 0x17.
        reader.seek(0x17L)
        val points = SectionRange(reader.readUInt32(), reader.readUInt32()).nullIfEmpty()
        val lines = SectionRange(reader.readUInt32(), reader.readUInt32()).nullIfEmpty()
        val polygons = SectionRange(reader.readUInt32(), reader.readUInt32()).nullIfEmpty()

        // Family / product at 0x2F.
        reader.seek(0x2FL)
        val familyId = reader.readUInt16()
        val productCode = reader.readUInt16()

        // Index descriptors at 0x33: each index has UInt32 offset + UInt16
        // itemSize + UInt32 size (10 bytes per index, 3 indexes total).
        // The itemSize tells us how many bytes per (typeForFile + recordOffset)
        // entry — typically 4 or 5 (UInt16 type + 2 or 3 byte record offset).
        val pointIndex = readIndexDescriptor(reader, headerLength, 0x33L)
        val lineIndex = readIndexDescriptor(reader, headerLength, 0x3DL)
        val polygonIndex = readIndexDescriptor(reader, headerLength, 0x47L)

        // Shape-stacking (DRAWORDER) descriptor at 0x51 — same 10-byte
        // shape. Items are 5 bytes each (byte type + UInt32 subTypeMask)
        // and stored in *draw order*: the entry's file position is its
        // priority. Lower position = drawn first (background); later =
        // drawn on top. An empty [0,0,0,0,0] entry separates zoom levels.
        val shapeStacking = readIndexDescriptor(reader, headerLength, 0x51L)

        return TypHeader(
            headerLength = headerLength,
            signature = signature,
            buildYear = year,
            buildMonth = month,
            buildDay = day,
            buildHour = hour,
            buildMinute = minute,
            buildSecond = second,
            codepage = codepage,
            familyId = familyId,
            productCode = productCode,
            points = points,
            lines = lines,
            polygons = polygons,
            pointIndex = pointIndex,
            lineIndex = lineIndex,
            polygonIndex = polygonIndex,
            shapeStacking = shapeStacking,
        )
    }

    private fun readIndexDescriptor(
        reader: ImgRandomAccessReader,
        headerLength: Int,
        offset: Long,
    ): TypIndexDescriptor? {
        if (headerLength < offset + 10) {
            return null
        }
        reader.seek(offset)
        val sectionOffset = reader.readUInt32()
        val itemSize = reader.readUInt16()
        val sectionSize = reader.readUInt32()
        // Empty section is a normal "no records of this kind" case;
        // zero itemSize is a malformed header that would drive a
        // divide-by-zero in the consumer's `range.length / itemSize`
        // entry-count calculation. Refuse both.
        if (sectionSize == 0L || itemSize <= 0) {
            return null
        }
        return TypIndexDescriptor(
            range = SectionRange(sectionOffset, sectionSize),
            itemSize = itemSize,
        )
    }
}

/**
 * Parsed TYP header. Section bodies (points/lines/polygons) are deferred to
 * Phase C — the renderer rewrite — when the actual styling resolution lands.
 */
data class TypHeader(
    val headerLength: Int,
    val signature: String,
    val buildYear: Int,
    val buildMonth: Int,
    val buildDay: Int,
    val buildHour: Int,
    val buildMinute: Int,
    val buildSecond: Int,
    val codepage: Int,
    val familyId: Int,
    val productCode: Int,
    /** Body section ranges. Records inside are looked up by index. */
    val points: SectionRange?,
    val lines: SectionRange?,
    val polygons: SectionRange?,
    /**
     * Index sections — each entry maps a `(type, subType)` pair (encoded as
     * `(type << 5) | (subType and 0x1F)`) to a record offset within the
     * matching body section. `itemSize` is typically 4 or 5 bytes
     * (UInt16 typeForFile + 2- or 3-byte offset).
     */
    val pointIndex: TypIndexDescriptor?,
    val lineIndex: TypIndexDescriptor?,
    val polygonIndex: TypIndexDescriptor?,
    /**
     * Shape-stacking (DRAWORDER) section. 5-byte entries
     * (byte type + UInt32 subTypeMask) in draw order — file position is
     * the priority. Drives polygon z-ordering.
     */
    val shapeStacking: TypIndexDescriptor?,
)

/**
 * Index descriptor for one TYP element kind (points / lines / polygons).
 */
data class TypIndexDescriptor(
    val range: SectionRange,
    /** Bytes per index entry (typeForFile + recordOffset). */
    val itemSize: Int,
)
