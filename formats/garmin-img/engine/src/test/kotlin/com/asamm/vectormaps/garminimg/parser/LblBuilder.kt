/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.parser

/**
 * Builder for synthetic LBL sub-file byte payloads.
 *
 * Layout produced (sub-file offsets):
 * - 0x00 — header length (UInt16 LE) = 0xC0 (covers all descriptors + codepage)
 * - 0x0D — lock flag (0 by default; 1 when [locked] is set)
 * - 0x15 — labels-section offset (UInt32) = 0xC0
 * - 0x19 — labels-section length (UInt32) = (labels-bytes-len)
 * - 0x1D — dataOffsetMultiplier exponent = 0 (multiplier 1)
 * - 0x1E — label coding byte
 * - 0xAA — codepage UInt16
 * - 0xC0+ — labels bytes
 */
internal class LblBuilder {

    private var locked: Boolean = false
    private var coding: LabelCoding = LabelCoding.EIGHT_BIT
    private var codepage: Int = 1252
    private val labels = mutableListOf<LabelSpec>()

    private data class LabelSpec(val offsetInSection: Int, val bytes: ByteArray)

    fun locked(value: Boolean) = apply { locked = value }

    fun coding(value: LabelCoding) = apply { coding = value }

    fun codepage(value: Int) = apply { codepage = value }

    /**
     * Adds a label at byte offset `offset` within the labels section, encoded
     * as null-terminated UTF-8 bytes (when [utf8] is true) or Latin-1 bytes
     * (each character's low 8 bits become a byte; matches what we expect
     * cp1252-encoded labels to look like for ASCII names).
     */
    fun label(offset: Int, value: String, utf8: Boolean = false) = apply {
        val bytes = if (utf8) {
            value.encodeToByteArray() + 0
        } else {
            value.map { it.code.toByte() }.toByteArray() + 0
        }
        labels.add(LabelSpec(offset, bytes))
    }

    /**
     * Adds a label at `offset` with already-encoded bytes (caller-controlled).
     * The provided bytes should include the trailing NUL.
     */
    fun rawLabel(offset: Int, bytes: ByteArray) = apply {
        labels.add(LabelSpec(offset, bytes))
    }

    fun build(): ByteArray {
        val labelsSectionSize = labels.maxOfOrNull { it.offsetInSection + it.bytes.size } ?: 0
        val labelsOffset = 0xC0
        val totalSize = labelsOffset + labelsSectionSize
        val out = ByteArray(maxOf(totalSize, 0xC0))

        // 0x00..0x01: headerLength UInt16 LE
        writeUInt16LE(out, 0x00, 0xC0)
        // 0x0D: lock flag
        out[0x0D] = if (locked) 0x01 else 0x00
        // 0x15..0x1E: labels descriptor + multiplier + coding
        writeUInt32LE(out, 0x15, labelsOffset.toLong())
        writeUInt32LE(out, 0x19, labelsSectionSize.toLong())
        out[0x1D] = 0 // multiplier exponent => multiplier 1
        out[0x1E] = if (coding == LabelCoding.UNKNOWN) {
            42.toByte() // any value not in {6, 9, 10}
        } else {
            coding.onDiskValue.toByte()
        }
        // 0xAA..0xAB: codepage
        writeUInt16LE(out, 0xAA, codepage)

        // Label bytes
        for (label in labels) {
            label.bytes.copyInto(out, destinationOffset = labelsOffset + label.offsetInSection)
        }
        return out
    }

    private fun writeUInt16LE(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeUInt32LE(dest: ByteArray, offset: Int, value: Long) {
        dest[offset] = (value and 0xFFL).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFFL).toByte()
        dest[offset + 2] = ((value ushr 16) and 0xFFL).toByte()
        dest[offset + 3] = ((value ushr 24) and 0xFFL).toByte()
    }
}
