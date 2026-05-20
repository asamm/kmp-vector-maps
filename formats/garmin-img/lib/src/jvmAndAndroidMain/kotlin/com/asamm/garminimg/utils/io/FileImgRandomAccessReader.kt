/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.utils.io

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * [ImgRandomAccessReader] backed by [java.io.RandomAccessFile].
 *
 * Use this over [ByteArrayImgRandomAccessReader] whenever the IMG file is
 * larger than a handful of megabytes — country-scale Garmin maps run
 * 200 MB to multi-GB and loading the whole file into a heap `ByteArray`
 * will OOM on phones and stress JVM heap on desktop. The byte-array
 * variant remains useful for tests (Monaco fixture is ~8 MB), for IMGs
 * embedded as classpath resources, and for unit tests of the parser.
 *
 * Single-threaded by design — the same contract as the interface.
 * Callers must serialise access and call [close] when done so the
 * underlying file handle is released.
 *
 * @param file IMG file to read. Opened in read-only mode for the
 *   lifetime of this reader.
 */
class FileImgRandomAccessReader(file: File) : ImgRandomAccessReader {

    private val raf: RandomAccessFile = RandomAccessFile(file, "r")

    override val size: Long

    init {
        // Guard against raf.length() throwing after the file is open
        // (rare but possible — e.g., concurrent truncation, disk error).
        // Without this the file descriptor leaks because the constructor
        // bails before `this` escapes and the caller can't see the
        // reader to close it.
        size = try {
            raf.length()
        } catch (e: Throwable) {
            try {
                raf.close()
            } catch (_: Throwable) {
                // Ignore close-during-construction failure; propagate
                // the original cause.
            }
            throw e
        }
    }

    override fun position(): Long {
        return raf.filePointer
    }

    override fun seek(pos: Long) {
        // Bounds check matches ByteArrayImgRandomAccessReader so a
        // corrupt FAT (or any other on-disk pointer that wraps past
        // the file) fails loudly here instead of producing
        // blank-tile silent corruption downstream — RandomAccessFile
        // happily seeks past EOF and the first read then returns
        // empty bytes.
        require(pos in 0L..size) {
            "seek($pos) out of bounds for size=$size"
        }
        raf.seek(pos)
    }

    override fun readByte(): Int {
        val b = raf.read()
        if (b < 0) {
            throw IOException("EOF at position ${raf.filePointer} (size=$size)")
        }
        return b
    }

    override fun readBlock(buffer: ByteArray) {
        raf.readFully(buffer)
    }

    // Multi-byte primitives: the interface composes these out of
    // [readByte], but per-byte `RandomAccessFile.read()` is a JNI
    // call. For a 200 MB IMG with millions of multi-byte fields that's
    // many millions of crossings. Reusing a 4-byte scratch buffer and
    // pulling the bytes via `readFully` cuts the crossings to one
    // per primitive.

    private val scratch4: ByteArray = ByteArray(4)

    override fun readUInt16(): Int {
        raf.readFully(scratch4, 0, 2)
        return (scratch4[0].toInt() and 0xFF) or
            ((scratch4[1].toInt() and 0xFF) shl 8)
    }

    override fun readUInt24(): Int {
        raf.readFully(scratch4, 0, 3)
        return (scratch4[0].toInt() and 0xFF) or
            ((scratch4[1].toInt() and 0xFF) shl 8) or
            ((scratch4[2].toInt() and 0xFF) shl 16)
    }

    override fun readInt32(): Int {
        raf.readFully(scratch4, 0, 4)
        return (scratch4[0].toInt() and 0xFF) or
            ((scratch4[1].toInt() and 0xFF) shl 8) or
            ((scratch4[2].toInt() and 0xFF) shl 16) or
            ((scratch4[3].toInt() and 0xFF) shl 24)
    }

    override fun readUInt32(): Long {
        return readInt32().toLong() and 0xFFFFFFFFL
    }

    /**
     * Releases the underlying file handle. Subsequent reads throw.
     * Idempotent.
     */
    override fun close() {
        raf.close()
    }
}
