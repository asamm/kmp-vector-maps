/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.utils.io

/**
 * Random-access reader over the bytes of an IMG container.
 *
 * Replaces the Java `ImgFileInputStream` from libGarminImg. The original carried an
 * XOR "decryption" byte for locked / DRM-protected maps; that path is intentionally
 * dropped — kmp-garmin-img only handles unencrypted IMGs (legal + format scope per
 * `CLAUDE.md`).
 *
 * The reader is single-threaded: callers must serialise access. IMG sub-file readers
 * sit on top of this and translate logical offsets into seeks here.
 */
interface ImgRandomAccessReader {

    /**
     * Total number of bytes available.
     */
    val size: Long

    /**
     * Current read position, in bytes from the start.
     */
    fun position(): Long

    /**
     * Moves the read position to `pos`. Subsequent reads start there.
     */
    fun seek(pos: Long)

    /**
     * Reads the next byte as an unsigned int (0..255). Throws if the position is at EOF.
     */
    fun readByte(): Int

    /**
     * Reads the next 16-bit little-endian unsigned integer (0..65535).
     */
    fun readUInt16(): Int {
        return readByte() or (readByte() shl 8)
    }

    /**
     * Reads the next 32-bit little-endian signed integer.
     */
    fun readInt32(): Int {
        return readByte() or
            (readByte() shl 8) or
            (readByte() shl 16) or
            (readByte() shl 24)
    }

    /**
     * Reads the next 32-bit little-endian *unsigned* integer as a [Long]. Used for
     * IMG fields like sub-file section offsets that span the full 0..2^32 range.
     */
    fun readUInt32(): Long {
        return readInt32().toLong() and 0xFFFFFFFFL
    }

    /**
     * Reads the next 24-bit little-endian unsigned integer (0..16_777_215). Garmin
     * IMG stores coordinates and offsets as 24-bit values; the result fits cleanly
     * in [Int].
     */
    fun readUInt24(): Int {
        return readByte() or (readByte() shl 8) or (readByte() shl 16)
    }

    /**
     * Reads the next 24-bit little-endian *signed* integer (-8_388_608..8_388_607).
     * IMG bounding-box coordinates use this width — a positive value is northern
     * latitude / eastern longitude, a negative one is southern / western.
     */
    fun readInt24(): Int {
        val value = readUInt24()
        return if (value > 0x7FFFFF) {
            (value and 0x7FFFFF) - 0x800000
        } else {
            value
        }
    }

    /**
     * Reads `len` bytes and decodes them as ASCII (Garmin's primary string encoding for
     * sub-file headers). Trailing zero bytes are kept; callers strip them where needed.
     */
    fun readString(len: Int): String {
        val buf = ByteArray(len)
        readBlock(buf)
        return buf.decodeToString()
    }

    /**
     * Fills `buffer` with bytes starting at the current position, advancing the position.
     */
    fun readBlock(buffer: ByteArray)
}
