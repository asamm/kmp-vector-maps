/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phase B.1 tests for [ImgRandomAccessReader]. Behavioural parity with the Java
 * `ImgFileInputStream` minus the XOR (encrypted-map) logic.
 */
class ImgRandomAccessReaderTest {

    @Test
    fun readByteAdvancesPosition() {
        val r = reader(0x00, 0x7F, 0x80, 0xFF)
        assertEquals(0L, r.position())
        assertEquals(0x00, r.readByte())
        assertEquals(0x7F, r.readByte())
        assertEquals(2L, r.position())
        assertEquals(0x80, r.readByte())
        assertEquals(0xFF, r.readByte())
        assertEquals(4L, r.position())
    }

    @Test
    fun readUInt16IsLittleEndian() {
        // bytes 0x34 0x12 → 0x1234
        val r = reader(0x34, 0x12)
        assertEquals(0x1234, r.readUInt16())
    }

    @Test
    fun readUInt16HandlesFullRange() {
        // bytes 0xFF 0xFF → 0xFFFF (65535), unsigned
        val r = reader(0xFF, 0xFF)
        assertEquals(0xFFFF, r.readUInt16())
    }

    @Test
    fun readInt32IsLittleEndian() {
        // bytes 0x78 0x56 0x34 0x12 → 0x12345678
        val r = reader(0x78, 0x56, 0x34, 0x12)
        assertEquals(0x12345678, r.readInt32())
    }

    @Test
    fun readInt32HandlesNegative() {
        // bytes 0x00 0x00 0x00 0x80 → 0x80000000 = Int.MIN_VALUE
        val r = reader(0x00, 0x00, 0x00, 0x80)
        assertEquals(Int.MIN_VALUE, r.readInt32())
    }

    @Test
    fun seekAllowsRandomAccess() {
        val r = reader(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF)
        r.seek(3L)
        assertEquals(3L, r.position())
        assertEquals(0xDD, r.readByte())
        r.seek(0L)
        assertEquals(0xAA, r.readByte())
        r.seek(5L)
        assertEquals(0xFF, r.readByte())
        assertEquals(6L, r.position())
    }

    @Test
    fun seekRejectsOutOfBounds() {
        val r = reader(0x00, 0x01)
        assertFailsWith<IllegalArgumentException> { r.seek(-1L) }
        assertFailsWith<IllegalArgumentException> { r.seek(3L) }
        // seeking exactly to size is allowed (positions at EOF) — the next readByte
        // is what fails.
        r.seek(2L)
        assertEquals(2L, r.position())
    }

    @Test
    fun readByteAtEofThrows() {
        val r = reader(0x42)
        assertEquals(0x42, r.readByte())
        assertFailsWith<IndexOutOfBoundsException> { r.readByte() }
    }

    @Test
    fun readBlockCopiesContiguousBytes() {
        val r = reader(0x10, 0x20, 0x30, 0x40, 0x50)
        r.seek(1L)
        val buf = ByteArray(3)
        r.readBlock(buf)
        assertEquals(0x20.toByte(), buf[0])
        assertEquals(0x30.toByte(), buf[1])
        assertEquals(0x40.toByte(), buf[2])
        assertEquals(4L, r.position())
    }

    @Test
    fun readStringDecodesAscii() {
        val r = reader(0x47, 0x41, 0x52, 0x4D, 0x49, 0x4E) // "GARMIN"
        assertEquals("GARMIN", r.readString(6))
    }

    @Test
    fun sizeReportsTotalBytes() {
        val r = reader(0x00, 0x01, 0x02)
        assertEquals(3L, r.size)
        r.readByte()
        assertEquals(3L, r.size, "size is invariant of position")
    }

    @Test
    fun readBlockPastEofThrows() {
        val r = reader(0x00, 0x01)
        val buf = ByteArray(3)
        assertTrue(r.position() == 0L)
        assertFailsWith<IndexOutOfBoundsException> { r.readBlock(buf) }
    }

    private fun reader(vararg unsignedBytes: Int): ImgRandomAccessReader {
        val data = ByteArray(unsignedBytes.size) { unsignedBytes[it].toByte() }
        return ByteArrayImgRandomAccessReader(data)
    }
}
