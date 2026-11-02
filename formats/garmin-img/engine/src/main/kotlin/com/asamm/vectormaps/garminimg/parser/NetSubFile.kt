/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.parser

import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader

/**
 * Parser for an `.NET` sub-file. NET carries road definitions used to resolve
 * polyline labels via indirection through LBL.
 *
 * # Phase B.2.6 scope
 *
 * Routing data is out of scope per the CLAUDE.md scope table ("NET routing — never
 * used by the app's router"). We only port the minimum needed to resolve a
 * polyline's `dataInNet` label reference: read the road-definitions header
 * and provide [resolveRoadName] that follows a NET offset to the underlying
 * LBL label offset and decodes it.
 */
object NetSubFile {

    private const val OFFSET_ROAD_DEFINITIONS_DESCRIPTOR: Long = 0x15L

    /**
     * Reads the NET header.
     */
    fun parseHeader(reader: ImgRandomAccessReader): NetHeader {
        val common = reader.readSubFileCommonHeader()
        require(common.headerLength >= 0x1E) {
            "NET header length 0x${common.headerLength.toString(16)} too small"
        }

        reader.seek(OFFSET_ROAD_DEFINITIONS_DESCRIPTOR)
        val offset = reader.readUInt32()
        val length = reader.readUInt32()
        val multiplierExp = reader.readByte()
        // Match LblSubFile.parseHeader's guard — `1 shl 32` wraps to 1
        // on Kotlin's masked shift count, so a corrupt header would
        // silently misalign every road-definition lookup.
        require(multiplierExp in 0..15) {
            "NET road-offset multiplier exponent $multiplierExp out of range [0, 15]"
        }
        return NetHeader(
            headerLength = common.headerLength,
            roadDefinitionsOffset = offset,
            roadDefinitionsLength = length,
            roadOffsetMultiplier = 1 shl multiplierExp,
        )
    }

    /**
     * Resolves a NET-side polyline label offset to its decoded String. Returns
     * null when the offset is out of the road-definitions section.
     */
    fun resolveRoadName(
        netReader: ImgRandomAccessReader,
        netHeader: NetHeader,
        offset: Long,
        lblReader: ImgRandomAccessReader,
        lblHeader: LblHeader,
    ): String? {
        val multOffset = netHeader.roadOffsetMultiplier * offset
        // `>` was incorrect — `multOffset == roadDefinitionsLength`
        // sits at section EOF and the subsequent read would walk one
        // record past the section. Also guard the negative case (a
        // corrupt RGN record can wrap into one via Int arithmetic).
        if (multOffset < 0L || multOffset >= netHeader.roadDefinitionsLength) {
            return null
        }
        netReader.seek(netHeader.roadDefinitionsOffset + multOffset)
        // First 24 bits of the road record are the LBL label offset (mask
        // 0x3FFFFF — high two bits are flags we ignore for label resolution).
        val labelOffset = (netReader.readUInt24() and 0x3FFFFF).toLong()
        // labelOffset = 0 means "no label" — same convention as RGN
        // records. Returning null here keeps the dump format aligned
        // with the legacy parser (which produces a `label=""` for these).
        if (labelOffset == 0L) {
            return null
        }
        return try {
            LblSubFile.readLabel(lblReader, lblHeader, labelOffset)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Parsed NET header (B.2.6 fields only).
 *
 * @param roadOffsetMultiplier multiplier applied to a logical road offset to convert it to a
 *   byte offset within the road-definitions section.
 */
data class NetHeader(
    val headerLength: Int,
    val roadDefinitionsOffset: Long,
    val roadDefinitionsLength: Long,
    val roadOffsetMultiplier: Int,
)
