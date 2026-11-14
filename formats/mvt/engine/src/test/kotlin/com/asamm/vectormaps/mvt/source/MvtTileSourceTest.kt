/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.source

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class MvtTileSourceTest {

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    @Test
    fun inflatesGzipAndPassesOtherBytes() {
        val plain = ByteArray(100_000) { (it % 251).toByte() }

        assertContentEquals(plain, MvtTileSource.uncompressed(gzip(plain)))
        assertSame(plain, MvtTileSource.uncompressed(plain))
    }

    @Test
    fun inflatesConcatenatedMembers() {
        val first = ByteArray(5_000) { 1 }
        val second = ByteArray(7_000) { 2 }
        // the trailer names only the last member's size
        assertContentEquals(first + second, MvtTileSource.uncompressed(gzip(first) + gzip(second)))
    }
}
