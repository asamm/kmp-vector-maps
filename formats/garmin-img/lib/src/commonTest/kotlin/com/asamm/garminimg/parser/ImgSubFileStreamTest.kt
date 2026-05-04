package com.asamm.garminimg.parser

import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for [ImgSubFileStream] using [SyntheticImg] to inject sub-file payloads
 * at known block offsets, then opening a stream over the FAT entry and reading
 * back the bytes.
 */
class ImgSubFileStreamTest {

    @Test
    fun readsSubFilePayloadAtFirstBlock() {
        // Block size 512, sub-file at block 4 (offset 0x800), 16 bytes long.
        val payload = byteArrayOf(
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(),
            'o'.code.toByte(), ','.code.toByte(), ' '.code.toByte(), 'I'.code.toByte(),
            'M'.code.toByte(), 'G'.code.toByte(), '!'.code.toByte(), 0, 0, 0, 0, 0,
        )
        val img = SyntheticImg.builder()
            .blockSizeExponent(9, 0) // 512
            .addFatEntry(
                fileName = "00000001",
                fileType = "TRE",
                fileSize = payload.size,
                firstBlock = 4,
            )
            .payloadAtBlock(blockIndex = 4, bytes = payload)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val tre = archive.findFirst("TRE")!!
        val stream = archive.openSubFile(tre, container)

        assertEquals(payload.size.toLong(), stream.size)
        assertEquals(0L, stream.position())
        val first = stream.readByte()
        assertEquals('H'.code, first)
        assertEquals(1L, stream.position())

        val rest = ByteArray(10)
        stream.readBlock(rest)
        assertEquals("ello, IMG!", rest.decodeToString())
        assertEquals(11L, stream.position())
    }

    @Test
    fun seekTranslatesLogicalToPhysicalCorrectly() {
        // 2048-byte blocks; sub-file at block 6 (physical offset 0x3000).
        val blockSize = 2048
        val subFileLen = blockSize * 2
        val payload = ByteArray(subFileLen) { (it and 0xFF).toByte() } // 0..255 ramp

        val img = SyntheticImg.builder()
            .blockSizeExponent(11, 0) // 2048
            .addFatEntry(fileName = "00000001", fileType = "RGN", fileSize = subFileLen, firstBlock = 6)
            .payloadAtBlock(blockIndex = 6, bytes = payload)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val rgn = archive.findFirst("RGN")!!
        val stream = archive.openSubFile(rgn, container)

        // Spot-check a few logical offsets — the value at logicalPos N is N % 256.
        for (pos in listOf(0L, 1L, 255L, 256L, 1023L, 2048L, subFileLen.toLong() - 1)) {
            stream.seek(pos)
            val expected = (pos % 256L).toInt() and 0xFF
            assertEquals(expected, stream.readByte(), "wrong byte at logical pos $pos")
        }
    }

    @Test
    fun multipleStreamsOverSameContainerDoNotCorruptEachOther() {
        // Two sub-files at non-overlapping blocks. Read them interleaved and verify
        // each stream's logical position is unaffected by the other's reads.
        val a = ByteArray(64) { (it * 2).toByte() } // even ramp
        val b = ByteArray(64) { (it * 2 + 1).toByte() } // odd ramp

        val img = SyntheticImg.builder()
            .blockSizeExponent(9, 0) // 512
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = a.size, firstBlock = 4)
            .addFatEntry(fileName = "00000001", fileType = "RGN", fileSize = b.size, firstBlock = 5)
            .payloadAtBlock(blockIndex = 4, bytes = a)
            .payloadAtBlock(blockIndex = 5, bytes = b)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val streamA = archive.openSubFile(archive.findFirst("TRE")!!, container)
        val streamB = archive.openSubFile(archive.findFirst("RGN")!!, container)

        // Interleave reads from A and B — each stream sees its own bytes.
        for (i in 0 until 32) {
            assertEquals((i * 2) and 0xFF, streamA.readByte(), "A diverged at byte $i")
            assertEquals((i * 2 + 1) and 0xFF, streamB.readByte(), "B diverged at byte $i")
        }
        assertEquals(32L, streamA.position())
        assertEquals(32L, streamB.position())
    }

    @Test
    fun readByteAtSubFileEndThrows() {
        val payload = byteArrayOf(0x42, 0x43)
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = 2, firstBlock = 4)
            .payloadAtBlock(blockIndex = 4, bytes = payload)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val stream = archive.openSubFile(archive.findFirst("TRE")!!, container)

        assertEquals(0x42, stream.readByte())
        assertEquals(0x43, stream.readByte())
        assertFailsWith<IndexOutOfBoundsException> { stream.readByte() }
    }

    @Test
    fun seekRejectsNegativeOrPastEnd() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = 16, firstBlock = 4)
            .payloadAtBlock(blockIndex = 4, bytes = ByteArray(16))
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val stream = archive.openSubFile(archive.findFirst("TRE")!!, container)

        assertFailsWith<IllegalArgumentException> { stream.seek(-1L) }
        assertFailsWith<IllegalArgumentException> { stream.seek(17L) }
        // Seeking exactly to size is allowed — places stream at logical EOF.
        stream.seek(16L)
        assertEquals(16L, stream.position())
        // The next readByte fails with EOF, not IllegalArgumentException.
        assertFailsWith<IndexOutOfBoundsException> { stream.readByte() }
    }

    @Test
    fun readBlockPastSubFileEndThrows() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = 4, firstBlock = 4)
            .payloadAtBlock(blockIndex = 4, bytes = byteArrayOf(1, 2, 3, 4))
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val stream = archive.openSubFile(archive.findFirst("TRE")!!, container)

        // Trying to read more than fileSize fails even at start — readBlock
        // must not silently truncate.
        assertFailsWith<IndexOutOfBoundsException> { stream.readBlock(ByteArray(5)) }

        // Even after partial consumption, a too-large request fails cleanly.
        stream.readBlock(ByteArray(2))
        assertFailsWith<IndexOutOfBoundsException> { stream.readBlock(ByteArray(3)) }
    }

    @Test
    fun readSubFileCommonHeaderParsesLengthAndUnlocksOk() {
        // Build a sub-file header: bytes 0-1 = headerLength = 0x49 (typical TRE),
        // bytes 2-0xC = padding, byte 0xD = 0 (unlocked).
        val header = ByteArray(0x40)
        header[0] = 0x49
        header[1] = 0x00
        // (header[0xD] defaults to 0 — unlocked)

        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = 0x40, firstBlock = 4)
            .payloadAtBlock(blockIndex = 4, bytes = header)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val stream = archive.openSubFile(archive.findFirst("TRE")!!, container)

        val common = stream.readSubFileCommonHeader()
        assertEquals(0x49, common.headerLength)
        // Position must be at offset 2 ready for callers to continue.
        assertEquals(2L, stream.position())
    }

    @Test
    fun readSubFileCommonHeaderRejectsLockedSubFile() {
        val header = ByteArray(0x40)
        header[0] = 0x49
        header[1] = 0x00
        header[0x0D] = 0x01 // locked!

        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", fileSize = 0x40, firstBlock = 4)
            .payloadAtBlock(blockIndex = 4, bytes = header)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val stream = archive.openSubFile(archive.findFirst("TRE")!!, container)

        assertFailsWith<IllegalArgumentException> { stream.readSubFileCommonHeader() }
    }
}
