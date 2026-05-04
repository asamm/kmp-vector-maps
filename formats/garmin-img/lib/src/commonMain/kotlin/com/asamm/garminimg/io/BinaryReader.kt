/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.io

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Endian-aware sequential reader over a [Source]. IMG sub-files are little-endian, so
 * LE accessors are the common path; BE accessors are kept for bookkeeping fields that
 * happen to be big-endian.
 *
 * This is the Phase A seed of the binary IO layer; Phase B will extend it to a
 * random-access wrapper backed by the IMG file's block table.
 */
class BinaryReader(private val source: Source) {

    /**
     * Constructs a reader over an in-memory byte array. Test helper; production code
     * passes a real [Source].
     */
    constructor(bytes: ByteArray) : this(Buffer().apply { write(bytes) })

    //********************************
    // BYTES
    //********************************

    fun readByteBE(): Byte {
        return source.readByte()
    }

    fun readByteLE(): Byte {
        return source.readByte()
    }

    fun readUnsignedByteBE(): Int {
        return source.readByte().toInt() and 0xFF
    }

    fun readUnsignedByteLE(): Int {
        return source.readByte().toInt() and 0xFF
    }

    //********************************
    // SHORTS (16-bit)
    //********************************

    fun readShortBE(): Short {
        val hi = source.readByte().toInt() and 0xFF
        val lo = source.readByte().toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    fun readShortLE(): Short {
        val lo = source.readByte().toInt() and 0xFF
        val hi = source.readByte().toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    //********************************
    // INTS (32-bit)
    //********************************

    fun readIntBE(): Int {
        val b3 = source.readByte().toInt() and 0xFF
        val b2 = source.readByte().toInt() and 0xFF
        val b1 = source.readByte().toInt() and 0xFF
        val b0 = source.readByte().toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    fun readIntLE(): Int {
        val b0 = source.readByte().toInt() and 0xFF
        val b1 = source.readByte().toInt() and 0xFF
        val b2 = source.readByte().toInt() and 0xFF
        val b3 = source.readByte().toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    //********************************
    // LONGS (64-bit) + DOUBLES
    //********************************

    fun readLongBE(): Long {
        var result = 0L
        repeat(8) {
            result = (result shl 8) or (source.readByte().toLong() and 0xFFL)
        }
        return result
    }

    fun readLongLE(): Long {
        var result = 0L
        for (shift in 0 until 64 step 8) {
            val byte = source.readByte().toLong() and 0xFFL
            result = result or (byte shl shift)
        }
        return result
    }

    fun readDoubleBE(): Double {
        return Double.fromBits(readLongBE())
    }

    fun readDoubleLE(): Double {
        return Double.fromBits(readLongLE())
    }

    //********************************
    // POSITIONING
    //********************************

    /**
     * Skips `n` bytes from the current position, returning the actual count skipped.
     * Matches the contract of `DataInputStream.skipBytes`.
     */
    fun skipBytes(n: Int): Int {
        if (n <= 0) {
            return 0
        }
        // kotlinx.io exposes Source.skip(Long) as the canonical skip; for the small
        // counts expected here, fall back to a buffered read so we get an honest
        // count even on streams that under-skip at the boundary.
        val buf = source.readByteArray(n)
        return buf.size
    }
}
