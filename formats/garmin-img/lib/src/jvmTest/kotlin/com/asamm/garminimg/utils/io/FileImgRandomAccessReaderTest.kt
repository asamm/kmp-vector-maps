/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.utils.io

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
        return FileImgRandomAccessReaderTest::class.java.classLoader
            ?.getResourceAsStream("fixtures/otm-monaco/otm-monaco.img")
            ?.use { it.readBytes() } ?: error("Monaco fixture missing")
    }
}
