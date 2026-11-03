/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

import com.asamm.vectormaps.garminimg.fixtureBytes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileImgRandomAccessReaderTest {

    @Test
    fun matchesByteArrayReaderOnMonacoFixture() {
        val bytes = monacoBytes()
        val tmp = File.createTempFile("monaco-", ".img").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
        val ba = ByteArrayImgRandomAccessReader(bytes)
        val fr = FileImgRandomAccessReader(tmp)
        try {
            assertEquals(ba.size, fr.size, "size")

            // Walk a handful of deterministic offsets — small enough to
            // run fast, wide enough to cover header + middle + EOF-ish.
            val checkpoints = longArrayOf(
                0L,
                12L,
                1024L,
                bytes.size / 2L,
                bytes.size - 16L,
            )
            for (pos in checkpoints) {
                ba.seek(pos); fr.seek(pos)
                assertEquals(ba.position(), fr.position(), "position after seek($pos)")
                val baBuf = ByteArray(16)
                val frBuf = ByteArray(16)
                ba.readBlock(baBuf); fr.readBlock(frBuf)
                assertContentEquals(baBuf, frBuf, "block at $pos")
            }

            // readByte parity at a non-aligned position.
            ba.seek(2049L); fr.seek(2049L)
            assertEquals(ba.readByte(), fr.readByte(), "single byte at 2049")

            // Multibyte readers compose on top of readByte; one quick
            // cross-check that the LE decoding agrees.
            ba.seek(0L); fr.seek(0L)
            assertEquals(ba.readInt32(), fr.readInt32(), "readInt32 at 0")
        } finally {
            fr.close()
        }
    }

    @Test
    fun readsAlternatingAcrossMoreRegionsThanWindows() {
        val bytes = monacoBytes()
        val tmp = File.createTempFile("monaco-", ".img").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
        val ba = ByteArrayImgRandomAccessReader(bytes)
        FileImgRandomAccessReader(tmp).use { fr ->
            // six regions over four windows, each read twice, values crossing window ends
            // 100 + 8192 - 2 starts inside the first region's window and runs past its end
            val regions = longArrayOf(100L, 50_000L, 8_290L, 400_000L, 1_000_000L, bytes.size - 5L)
            repeat(2) {
                for (pos in regions) {
                    ba.seek(pos)
                    fr.seek(pos)
                    assertEquals(ba.readInt32(), fr.readInt32(), "readInt32 at $pos")
                    assertEquals(ba.readByte(), fr.readByte(), "readByte at ${pos + 4}")
                    val back = maxOf(0L, pos - 3)
                    ba.seek(back)
                    fr.seek(back)
                    assertEquals(ba.readUInt24(), fr.readUInt24(), "readUInt24 at $back")
                }
            }
        }
    }

    @Test
    fun readByteThrowsAtEof() {
        val tmp = File.createTempFile("eof-", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0x01, 0x02))
        }
        FileImgRandomAccessReader(tmp).use { r ->
            assertEquals(0x01, r.readByte())
            assertEquals(0x02, r.readByte())
            assertFailsWith<java.io.IOException> { r.readByte() }
        }
    }

    private fun monacoBytes(): ByteArray {
        return fixtureBytes("maps/otm-monaco/otm-monaco.img")
    }
}
