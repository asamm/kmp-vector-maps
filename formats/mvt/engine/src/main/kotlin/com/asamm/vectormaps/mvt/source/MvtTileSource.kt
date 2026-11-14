/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.source

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Where vector tiles come from: a PMTiles file, or for online maps the host's own downloader and
 * cache. Called from several render threads at once.
 */
interface MvtTileSource : Closeable {

    /**
     * The lowest and highest zoom with tiles; deeper zooms are cut from `maxZoom` tiles.
     */
    val minZoom: Int

    val maxZoom: Int

    /**
     * West, south, east, north in degrees; null when unknown, such as for the whole planet.
     */
    val bounds: DoubleArray?

    /**
     * The tile's bytes, gzip-compressed or not; null when the source has no tile there. Throws
     * when the source cannot be read.
     */
    fun tile(zoom: Int, x: Int, y: Int): ByteArray?

    override fun close() {
        // nothing to release by default
    }

    companion object {

        // far above any real tile, below what a render thread can hold
        private const val MAX_INFLATED = 64 shl 20

        private const val EXACT_RATIO = 32

        /**
         * `bytes` inflated when gzip-compressed, as tiles usually are, else as they are. Throws
         * when they inflate past [limit] bytes, 64 MB by default.
         */
        fun uncompressed(bytes: ByteArray, limit: Int = MAX_INFLATED): ByteArray {
            if (bytes.size < 2
                || bytes[0] != 0x1f.toByte()
                || bytes[1] != 0x8b.toByte()
            ) {
                return bytes
            }
            exactly(bytes = bytes, limit = limit)?.let {
                return it
            }
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val out = ByteArrayOutputStream(minOf(bytes.size.toLong() * 4, limit.toLong()).toInt())
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) {
                        return out.toByteArray()
                    }
                    out.write(buffer, 0, n)
                    if (out.size() > limit) {
                        throw IOException("uncompressed(), inflates past $limit bytes")
                    }
                }
            }
        }

        // a single gzip member ends with its inflated size (mod 2^32), so it inflates into one
        // array of that size; null when the stream turns out longer, as concatenated members do
        private fun exactly(bytes: ByteArray, limit: Int): ByteArray? {
            val n = bytes.size
            if (n < 4) {
                return null
            }
            val size = (bytes[n - 4].toInt() and 0xff) or
                ((bytes[n - 3].toInt() and 0xff) shl 8) or
                ((bytes[n - 2].toInt() and 0xff) shl 16) or
                ((bytes[n - 1].toInt() and 0xff) shl 24)
            // a damaged trailer must not allocate much before inflating proves it wrong: tiles inflate
            // a few times, so a larger claim takes the growing path
            if (size !in 0..limit
                || size > n.toLong() * EXACT_RATIO
            ) {
                return null
            }
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val out = ByteArray(size)
                var at = 0
                while (at < size) {
                    val read = input.read(out, at, size - at)
                    if (read < 0) {
                        return null
                    }
                    at += read
                }
                return if (input.read() < 0) out else null
            }
        }
    }
}
