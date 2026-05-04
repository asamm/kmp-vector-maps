package com.asamm.garminimg.io

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase A smoke tests for [BinaryReader].
 * IMG sub-files are little-endian; correctness here is foundational.
 */
class BinaryReaderTest {

    @Test
    fun readUnsignedByteLE() {
        val s = bytes(0x00, 0x7F, 0x80, 0xFF)
        assertEquals(0x00, s.readUnsignedByteLE())
        assertEquals(0x7F, s.readUnsignedByteLE())
        assertEquals(0x80, s.readUnsignedByteLE())
        assertEquals(0xFF, s.readUnsignedByteLE())
    }

    @Test
    fun readShortLEReversesByteOrder() {
        // bytes 0x34 0x12 → little-endian short 0x1234
        val s = bytes(0x34, 0x12)
        assertEquals(0x1234.toShort(), s.readShortLE())
    }

    @Test
    fun readShortLEHandlesNegative() {
        // bytes 0xFF 0xFF → short -1
        val s = bytes(0xFF, 0xFF)
        assertEquals((-1).toShort(), s.readShortLE())
    }

    @Test
    fun readIntLEReversesByteOrder() {
        // bytes 0x78 0x56 0x34 0x12 → int 0x12345678
        val s = bytes(0x78, 0x56, 0x34, 0x12)
        assertEquals(0x12345678, s.readIntLE())
    }

    @Test
    fun readIntLEHandlesHighBit() {
        // bytes 0x00 0x00 0x00 0x80 → int 0x80000000 (Int.MIN_VALUE)
        val s = bytes(0x00, 0x00, 0x00, 0x80)
        assertEquals(Int.MIN_VALUE, s.readIntLE())
    }

    @Test
    fun readLongLE() {
        // 0x0807060504030201 written little-endian
        val s = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        assertEquals(0x0807060504030201L, s.readLongLE())
    }

    @Test
    fun readShortBEUsesNaturalNetworkOrder() {
        // bytes 0x12 0x34 → short 0x1234 in BE
        val s = bytes(0x12, 0x34)
        assertEquals(0x1234.toShort(), s.readShortBE())
    }

    @Test
    fun mixedOrderReadAdvancesPositionCorrectly() {
        // 1 byte + 2-byte LE + 4-byte LE = 7 bytes consumed in order
        val s = bytes(0xAB, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12)
        assertEquals(0xAB, s.readUnsignedByteLE())
        assertEquals(0x1234.toShort(), s.readShortLE())
        assertEquals(0x12345678, s.readIntLE())
    }

    private fun bytes(vararg unsignedBytes: Int): BinaryReader {
        val data = ByteArray(unsignedBytes.size) { unsignedBytes[it].toByte() }
        return BinaryReader(data)
    }
}
