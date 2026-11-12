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

        // far above any real tile or directory, below what a render thread can hold
        private const val MAX_INFLATED = 64 shl 20

        /**
         * `bytes` inflated when gzip-compressed, as tiles usually are, else as they are. Throws
         * when they inflate past 64 MB.
         */
        fun uncompressed(bytes: ByteArray): ByteArray {
            if (bytes.size < 2
                || bytes[0] != 0x1f.toByte()
                || bytes[1] != 0x8b.toByte()
            ) {
                return bytes
            }
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val out = ByteArrayOutputStream(bytes.size * 4)
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) {
                        return out.toByteArray()
                    }
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_INFLATED) {
                        throw IOException("uncompressed(), inflates past $MAX_INFLATED bytes")
                    }
                }
            }
        }
    }
}
