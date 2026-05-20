/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.utils.io

import java.io.Closeable
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
class FileImgRandomAccessReader(file: File) : ImgRandomAccessReader, Closeable {

    private val raf: RandomAccessFile = RandomAccessFile(file, "r")

    override val size: Long = raf.length()

    override fun position(): Long {
        return raf.filePointer
    }

    override fun seek(pos: Long) {
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

    /**
     * Releases the underlying file handle. Subsequent reads throw.
     * Idempotent.
     */
    override fun close() {
        raf.close()
    }
}
