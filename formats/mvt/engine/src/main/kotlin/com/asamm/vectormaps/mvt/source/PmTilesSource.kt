/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.source

import com.asamm.vectormaps.mvt.tile.PbfReader
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Vector tiles from a PMTiles v3 file (protomaps/PMTiles spec): a 127-byte header, a root
 * directory, leaf directories and the tile data, addressed by Hilbert tile ids. Reads through one
 * channel with positional reads, so render threads share it; directories are kept once read.
 * Interrupting a reading thread closes the channel for every reader.
 */
class PmTilesSource(file: File) : MvtTileSource {

    private val channel: FileChannel = RandomAccessFile(file, "r").channel

    private val header: Header

    // directories by offset in the file; the root and the leaves a render has walked through
    private val directories = object : LinkedHashMap<Long, List<Entry>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Entry>>): Boolean {
            return size > MAX_DIRECTORIES
        }
    }

    init {
        try {
            header = Header.parse(read(0, HEADER_SIZE))
        } catch (e: Throwable) {
            channel.close()
            throw e
        }
    }

    override val minZoom: Int = header.minZoom

    override val maxZoom: Int = header.maxZoom

    override val bounds: DoubleArray
        get() {
            return doubleArrayOf(header.minLon, header.minLat, header.maxLon, header.maxLat)
        }

    override fun tile(zoom: Int, x: Int, y: Int): ByteArray? {
        if (zoom !in minZoom..maxZoom
            || x < 0
            || y < 0
            || x >= 1L shl zoom
            || y >= 1L shl zoom
        ) {
            return null
        }
        val id = tileId(zoom, x, y)
        var offset = header.rootOffset
        var length = header.rootLength
        // the spec bounds the depth; a damaged file must not loop
        repeat(MAX_DEPTH) {
            val entries = directory(offset, length)
            val entry = find(entries, id) ?: return null
            if (entry.runLength > 0) {
                val bytes = read(header.dataOffset + entry.offset, entry.length.toInt())
                return if (header.tileCompression == COMPRESSION_GZIP) MvtTileSource.uncompressed(bytes) else bytes
            }
            offset = header.leafOffset + entry.offset
            length = entry.length
        }
        return null
    }

    override fun close() {
        channel.close()
    }

    private fun directory(offset: Long, length: Long): List<Entry> {
        synchronized(directories) {
            directories[offset]?.let {
                return it
            }
        }
        require(length in 0..MAX_DIRECTORY_BYTES) {
            "directory(), $length bytes at $offset"
        }
        val raw = read(offset, length.toInt())
        val bytes = when (header.internalCompression) {
            COMPRESSION_GZIP -> MvtTileSource.uncompressed(raw, limit = MAX_DIRECTORY_BYTES)
            else -> raw
        }
        val entries = parseDirectory(bytes)
        synchronized(directories) {
            directories[offset] = entries
        }
        return entries
    }

    private fun read(offset: Long, length: Int): ByteArray {
        require(length >= 0
            && offset >= 0
            && offset + length <= channel.size()
        ) {
            "read(), $length bytes at $offset beyond ${channel.size()}"
        }
        val buffer = ByteBuffer.allocate(length)
        var at = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            require(read >= 0) {
                "read(), end of file at $at"
            }
            at += read
        }
        return buffer.array()
    }

    internal class Entry(
        val tileId: Long,
        val offset: Long,
        val length: Long,
        val runLength: Long,
    )

    private class Header(
        val rootOffset: Long,
        val rootLength: Long,
        val leafOffset: Long,
        val dataOffset: Long,
        val internalCompression: Int,
        val tileCompression: Int,
        val tileType: Int,
        val minZoom: Int,
        val maxZoom: Int,
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double,
    ) {

        companion object {

            fun parse(bytes: ByteArray): Header {
                require(String(bytes, 0, 7, Charsets.US_ASCII) == "PMTiles") {
                    "parse(), not a PMTiles file"
                }
                require(bytes[7].toInt() == 3) {
                    "parse(), PMTiles version ${bytes[7]}, only 3 is read"
                }
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val header = Header(
                    rootOffset = b.getLong(8),
                    rootLength = b.getLong(16),
                    leafOffset = b.getLong(40),
                    dataOffset = b.getLong(56),
                    internalCompression = bytes[97].toInt(),
                    tileCompression = bytes[98].toInt(),
                    tileType = bytes[99].toInt(),
                    minZoom = bytes[100].toInt() and 0xff,
                    maxZoom = bytes[101].toInt() and 0xff,
                    minLon = b.getInt(102) / 1e7,
                    minLat = b.getInt(106) / 1e7,
                    maxLon = b.getInt(110) / 1e7,
                    maxLat = b.getInt(114) / 1e7,
                )
                // the spec keeps the root directory within the first 16 KB
                require(header.rootLength in 0..ROOT_SPACE - HEADER_SIZE) {
                    "parse(), root directory of ${header.rootLength} bytes"
                }
                require(header.minZoom <= header.maxZoom
                    && header.maxZoom <= MAX_ZOOM
                ) {
                    "parse(), zooms ${header.minZoom}-${header.maxZoom}"
                }
                require(header.tileType == TILE_TYPE_MVT) {
                    "parse(), tile type ${header.tileType}, only vector tiles are read"
                }
                require(header.internalCompression in READABLE_COMPRESSION
                    && header.tileCompression in READABLE_COMPRESSION
                ) {
                    "parse(), compression ${header.internalCompression}/${header.tileCompression}, " +
                        "only none and gzip are read"
                }
                return header
            }
        }
    }

    internal companion object {

        const val HEADER_SIZE = 127

        const val COMPRESSION_NONE = 1

        const val COMPRESSION_GZIP = 2

        const val TILE_TYPE_MVT = 1

        private const val MAX_DEPTH = 4

        private const val MAX_DIRECTORIES = 64

        private const val ROOT_SPACE = 16384

        // as the core's TileRequest.MAX_ZOOM; tile ids of deeper zooms overflow
        private const val MAX_ZOOM = 30

        // far above a planet's leaf directories, so a crafted one cannot exhaust the heap
        private const val MAX_DIRECTORY_BYTES = 8 shl 20

        // unknown (0) counts as none, as the reference readers do
        private val READABLE_COMPRESSION = setOf(0, COMPRESSION_NONE, COMPRESSION_GZIP)

        /**
         * The spec's tile id: the tiles of all lower zooms, then the tile's position along the
         * zoom's Hilbert curve.
         */
        fun tileId(zoom: Int, x: Int, y: Int): Long {
            var id = 0L
            for (z in 0 until zoom) {
                id += 1L shl (2 * z)
            }
            val n = 1L shl zoom
            var tx = x.toLong()
            var ty = y.toLong()
            var d = 0L
            var s = n / 2
            while (s > 0) {
                val rx = if (tx and s > 0) 1L else 0L
                val ry = if (ty and s > 0) 1L else 0L
                d += s * s * ((3 * rx) xor ry)
                // rotate the quadrant
                if (ry == 0L) {
                    if (rx == 1L) {
                        tx = s - 1 - tx
                        ty = s - 1 - ty
                    }
                    val t = tx
                    tx = ty
                    ty = t
                }
                s /= 2
            }
            return id + d
        }

        /**
         * The spec's directory: entry count, then tile id deltas, run lengths, lengths and offsets
         * as varint columns; an offset of 0 after the first entry continues the previous entry.
         */
        fun parseDirectory(bytes: ByteArray): List<Entry> {
            val reader = PbfReader(bytes, 0, bytes.size)
            val count = reader.readVarint()
            // each entry takes at least four bytes, one per column
            require(count in 0..(bytes.size - 1) / 4L) {
                "parseDirectory(), $count entries in ${bytes.size} bytes"
            }
            val ids = LongArray(count.toInt())
            var last = 0L
            for (i in ids.indices) {
                last += reader.readVarint()
                ids[i] = last
            }
            val runs = LongArray(ids.size) { reader.readVarint() }
            val lengths = LongArray(ids.size) { reader.readVarint() }
            val offsets = LongArray(ids.size)
            for (i in ids.indices) {
                val value = reader.readVarint()
                offsets[i] = if (value == 0L && i > 0) offsets[i - 1] + lengths[i - 1] else value - 1
            }
            return List(ids.size) { Entry(ids[it], offsets[it], lengths[it], runs[it]) }
        }

        // the entry holding `id`: the last one at or before it, a tile run covering it or a leaf
        fun find(entries: List<Entry>, id: Long): Entry? {
            var low = 0
            var high = entries.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val c = entries[mid].tileId.compareTo(id)
                if (c == 0) {
                    return entries[mid]
                }
                if (c < 0) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (high < 0) {
                return null
            }
            val entry = entries[high]
            return when {
                entry.runLength == 0L -> entry
                id - entry.tileId < entry.runLength -> entry
                else -> null
            }
        }
    }
}
