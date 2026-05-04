/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.io

/**
 * In-memory [ImgRandomAccessReader] backed by a [ByteArray].
 *
 * Suitable for unit tests, small fixtures, or assets pre-loaded into RAM. A
 * file-backed implementation lands later (in `androidMain` / `jvmMain` via expect/
 * actual) when sub-file readers need to operate on real on-disk IMGs.
 */
class ByteArrayImgRandomAccessReader(private val data: ByteArray) : ImgRandomAccessReader {

    private var pos: Int = 0

    override val size: Long
        get() = data.size.toLong()

    override fun position(): Long {
        return pos.toLong()
    }

    override fun seek(pos: Long) {
        require(pos in 0L..data.size.toLong()) {
            "seek($pos) out of bounds for size=${data.size}"
        }
        this.pos = pos.toInt()
    }

    override fun readByte(): Int {
        if (pos >= data.size) {
            throw IndexOutOfBoundsException("readByte() at EOF: pos=$pos, size=${data.size}")
        }
        return data[pos++].toInt() and 0xFF
    }

    override fun readBlock(buffer: ByteArray) {
        if (pos + buffer.size > data.size) {
            throw IndexOutOfBoundsException(
                "readBlock(${buffer.size}) past EOF: pos=$pos, size=${data.size}",
            )
        }
        data.copyInto(buffer, destinationOffset = 0, startIndex = pos, endIndex = pos + buffer.size)
        pos += buffer.size
    }
}
