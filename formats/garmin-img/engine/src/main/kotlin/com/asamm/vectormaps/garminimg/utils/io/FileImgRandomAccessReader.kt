/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * [ImgRandomAccessReader] backed by [java.io.RandomAccessFile] with a
 * small in-process read-ahead buffer.
 *
 * Use this over [ByteArrayImgRandomAccessReader] whenever the IMG file
 * is larger than a handful of megabytes — country-scale Garmin maps
 * run 200 MB to multi-GB and loading the whole file into a heap
 * `ByteArray` will OOM on phones and stress JVM heap on desktop. The
 * byte-array variant remains useful for tests (Monaco fixture is ~8 MB),
 * for IMGs embedded as classpath resources, and for unit tests of the
 * parser.
 *
 * The buffer drops the per-byte JNI cost of `RandomAccessFile.read()`
 * in the parser hot paths (RGN's bit-stream decoder reads byte-at-a-time
 * for thousands of bytes per polyline). One refill = one JNI crossing
 * for `BUFFER_SIZE` bytes, served from the heap thereafter.
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

    /**
     * Logical position the consumer thinks they're at. The underlying
     * `raf.filePointer` is only synchronised with this on cache miss.
     */
    private var logicalPos: Long = 0L

    /**
     * Read-ahead cache. `buffer[0]` corresponds to absolute file offset
     * [bufferStart]; bytes are valid up to (but not including)
     * [bufferStart] + [bufferLen]. Initially empty.
     */
    private val buffer: ByteArray = ByteArray(BUFFER_SIZE)
    private var bufferStart: Long = 0L
    private var bufferLen: Int = 0

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
        return logicalPos
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
        // Lazy: don't touch raf yet. The next read either serves from
        // the buffer (if pos lies inside the loaded window) or
        // re-fills around `pos`.
        logicalPos = pos
    }

    override fun readByte(): Int {
        // Fast path: byte already in the buffer.
        val rel = logicalPos - bufferStart
        if (rel in 0 until bufferLen) {
            val b = buffer[rel.toInt()].toInt() and 0xFF
            logicalPos++
            return b
        }
        // Refill around logicalPos.
        if (logicalPos >= size) {
            throw IOException("EOF at position $logicalPos (size=$size)")
        }
        refill(logicalPos)
        val b = buffer[0].toInt() and 0xFF
        logicalPos++
        return b
    }

    override fun readBlock(buffer: ByteArray) {
        // Large reads bypass the cache entirely — copying through an
        // intermediate buffer would double the byte-shuffle cost.
        // Small reads come out of the cache.
        if (buffer.size > BUFFER_SIZE) {
            if (logicalPos + buffer.size > size) {
                throw IOException(
                    "readBlock(${buffer.size}) past EOF at $logicalPos (size=$size)",
                )
            }
            raf.seek(logicalPos)
            raf.readFully(buffer)
            logicalPos += buffer.size
            // Invalidate the cache — raf.filePointer is no longer
            // related to bufferStart.
            bufferLen = 0
            return
        }
        // compared as Long: an Int cast wraps for deltas past 2^31
        val rel = logicalPos - bufferStart
        if (rel >= 0
            && rel <= bufferLen - buffer.size
        ) {
            System.arraycopy(this.buffer, rel.toInt(), buffer, 0, buffer.size)
            logicalPos += buffer.size
            return
        }
        // Refill then serve. If the request doesn't fit one buffer,
        // recurse into the large-read path above.
        if (logicalPos + buffer.size > size) {
            throw IOException(
                "readBlock(${buffer.size}) past EOF at $logicalPos (size=$size)",
            )
        }
        refill(logicalPos)
        if (buffer.size <= bufferLen) {
            System.arraycopy(this.buffer, 0, buffer, 0, buffer.size)
            logicalPos += buffer.size
        } else {
            // Buffer didn't fill enough — straight raf read.
            raf.seek(logicalPos)
            raf.readFully(buffer)
            logicalPos += buffer.size
            bufferLen = 0
        }
    }

    // Multi-byte primitives — implemented against the buffer so a
    // poly bit-stream reading thousands of bytes touches one refill.
    // The default ImgRandomAccessReader composition would still work
    // (readByte goes through the cache), but the explicit overrides
    // skip the branch + autobox per call.

    override fun readUInt16(): Int {
        ensureBuffered(2)
        val p = (logicalPos - bufferStart).toInt()
        val v = (buffer[p].toInt() and 0xFF) or
            ((buffer[p + 1].toInt() and 0xFF) shl 8)
        logicalPos += 2
        return v
    }

    override fun readUInt24(): Int {
        ensureBuffered(3)
        val p = (logicalPos - bufferStart).toInt()
        val v = (buffer[p].toInt() and 0xFF) or
            ((buffer[p + 1].toInt() and 0xFF) shl 8) or
            ((buffer[p + 2].toInt() and 0xFF) shl 16)
        logicalPos += 3
        return v
    }

    override fun readInt32(): Int {
        ensureBuffered(4)
        val p = (logicalPos - bufferStart).toInt()
        val v = (buffer[p].toInt() and 0xFF) or
            ((buffer[p + 1].toInt() and 0xFF) shl 8) or
            ((buffer[p + 2].toInt() and 0xFF) shl 16) or
            ((buffer[p + 3].toInt() and 0xFF) shl 24)
        logicalPos += 4
        return v
    }

    override fun readUInt32(): Long {
        return readInt32().toLong() and 0xFFFFFFFFL
    }

    /**
     * Ensures `n` bytes starting at [logicalPos] are loaded in the
     * read-ahead buffer. Throws on EOF.
     */
    private fun ensureBuffered(n: Int) {
        val rel = logicalPos - bufferStart
        if (rel >= 0 && rel + n <= bufferLen) {
            return
        }
        if (logicalPos + n > size) {
            throw IOException(
                "ensureBuffered($n) past EOF at $logicalPos (size=$size)",
            )
        }
        refill(logicalPos)
        if (n > bufferLen) {
            // Hit EOF before reaching the requested count — shouldn't
            // happen given the size check above, but defensive.
            throw IOException(
                "short read: requested $n got $bufferLen at $logicalPos (size=$size)",
            )
        }
    }

    /**
     * Refills the read-ahead buffer so it covers [absStart, …).
     * Reads up to [BUFFER_SIZE] bytes — fewer near EOF. Always
     * synchronises `raf.filePointer` with the requested position.
     */
    private fun refill(absStart: Long) {
        raf.seek(absStart)
        val want = minOf(BUFFER_SIZE.toLong(), size - absStart).toInt()
        var read = 0
        while (read < want) {
            val n = raf.read(buffer, read, want - read)
            if (n <= 0) {
                break
            }
            read += n
        }
        bufferStart = absStart
        bufferLen = read
    }

    /**
     * Releases the underlying file handle. Subsequent reads throw.
     * Idempotent.
     */
    override fun close() {
        raf.close()
    }

    private companion object {

        /**
         * Read-ahead buffer size in bytes. 8 KB matches OS page size
         * on most modern platforms and is small enough to keep per-
         * reader memory negligible (one buffer per open IMG sub-file
         * × 5 sub-files = 40 KB / map). Larger buffers help less than
         * the JNI-amortisation gain that the first 4–8 KB already
         * provides.
         */
        private const val BUFFER_SIZE: Int = 8 * 1024
    }
}
