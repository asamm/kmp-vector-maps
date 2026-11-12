/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.tile

/**
 * Minimal protobuf reader over `bytes[pos, end)`, only the wire types MVT uses.
 */
internal class PbfReader(
    private val bytes: ByteArray,
    private var pos: Int,
    private val end: Int,
) {

    fun hasNext(): Boolean {
        return pos < end
    }

    fun readTag(): Int {
        return readVarint().toInt()
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(pos < end) {
                "readVarint(), truncated at $pos"
            }
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) {
                return result
            }
            shift += 7
            require(shift < 64) {
                "readVarint(), too long at $pos"
            }
        }
    }

    fun readSVarint(): Long {
        val v = readVarint()
        return (v ushr 1) xor -(v and 1)
    }

    fun readFloat(): Float {
        return Float.fromBits(readFixed32())
    }

    fun readDouble(): Double {
        val low = readFixed32().toLong() and 0xffffffffL
        val high = readFixed32().toLong() and 0xffffffffL
        return Double.fromBits(low or (high shl 32))
    }

    fun readString(): String {
        val length = readLength()
        val s = String(bytes, pos, length, Charsets.UTF_8)
        pos += length
        return s
    }

    fun readMessage(): PbfReader {
        val length = readLength()
        val reader = PbfReader(bytes, pos, pos + length)
        pos += length
        return reader
    }

    fun readPackedVarints(): IntArray {
        val length = readLength()
        val stop = pos + length
        var out = IntArray(length)
        var n = 0
        while (pos < stop) {
            if (n == out.size) {
                out = out.copyOf(out.size * 2 + 1)
            }
            var result = 0
            var shift = 0
            while (true) {
                require(pos < stop) {
                    "readPackedVarints(), truncated at $pos"
                }
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0) {
                    break
                }
                shift += 7
                // a uint32 takes at most 5 bytes
                require(shift <= 28) {
                    "readPackedVarints(), too long at $pos"
                }
            }
            out[n++] = result
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    fun skip(tag: Int) {
        when (tag and 0x7) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> advance(8)
            WIRE_LENGTH -> pos += readLength()
            WIRE_FIXED32 -> advance(4)
            else -> throw IllegalArgumentException("skip(), wire type ${tag and 0x7} at $pos")
        }
    }

    private fun advance(n: Int) {
        require(n <= end - pos) {
            "advance(), $n at $pos beyond $end"
        }
        pos += n
    }

    private fun readLength(): Int {
        val length = readVarint()
        require(length >= 0
            && length <= end - pos
        ) {
            "readLength(), $length at $pos beyond $end"
        }
        return length.toInt()
    }

    private fun readFixed32(): Int {
        require(pos + 4 <= end) {
            "readFixed32(), truncated at $pos"
        }
        val v = (bytes[pos].toInt() and 0xff) or
            ((bytes[pos + 1].toInt() and 0xff) shl 8) or
            ((bytes[pos + 2].toInt() and 0xff) shl 16) or
            ((bytes[pos + 3].toInt() and 0xff) shl 24)
        pos += 4
        return v
    }

    private companion object {

        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_LENGTH = 2
        private const val WIRE_FIXED32 = 5
    }
}
