package com.asamm.garminimg.parser

import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [ImgArchive] driven by synthetic byte streams. Each test builds
 * the smallest IMG that exercises one parser branch (header validation, FAT scan,
 * entry decoding). The Monaco fixture in `jvmTest` covers the realistic case.
 */
class ImgArchiveTest {

    @Test
    fun parsesMinimalImgWithSingleTreEntry() {
        val img = SyntheticImg.builder()
            .description("test-map")
            .blockSizeExponent(9, 0) // 512 bytes
            .addFatEntry(
                marker = ImgArchive.FAT_ENTRY_MARKER,
                fileName = "00000001",
                fileType = "TRE",
                fileSize = 0x1234,
                partNumber = 0,
                firstBlock = 4,
            )
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals("test-map", archive.description)
        assertEquals(512, archive.blockSize)
        assertEquals(1, archive.subFiles.size)
        val entry = archive.subFiles[0]
        assertEquals("00000001", entry.fileName)
        assertEquals("TRE", entry.fileType)
        assertEquals(0x1234, entry.fileSize)
        assertEquals(4, entry.firstBlock)
        assertEquals(SyntheticImg.FIRST_ENTRY_OFFSET, entry.fatOffset)
    }

    @Test
    fun parsesMultipleSubFilesInDirectoryOrder() {
        val img = SyntheticImg.builder()
            .description("mkgmap-tiny")
            .blockSizeExponent(11, 0) // 2048 bytes — typical mkgmap output
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .addFatEntry(fileName = "00000001", fileType = "RGN", firstBlock = 10)
            .addFatEntry(fileName = "00000001", fileType = "LBL", firstBlock = 18)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals(2048, archive.blockSize)
        assertEquals(3, archive.subFiles.size)
        assertEquals(listOf("TRE", "RGN", "LBL"), archive.subFiles.map { it.fileType })
        assertEquals(setOf("00000001"), archive.subFiles.map { it.fileName }.toSet())
    }

    @Test
    fun skipsPaddingBlocksAtFatStart() {
        // The FAT region is preceded by N blocks of padding (firstByte != 0x01).
        // The parser scans forward in 512-byte steps until it finds the real FAT
        // header. mkgmap puts 1-3 padding blocks; some Garmin tooling emits more.
        val img = SyntheticImg.builder()
            .description("padded")
            .leadingFatPaddingBlocks(3)
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 5)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals(1, archive.subFiles.size)
        assertEquals("TRE", archive.subFiles[0].fileType)
    }

    @Test
    fun skipsContinuationEntriesWithNonZeroPartNumber() {
        val img = SyntheticImg.builder()
            .description("with-cont")
            .addFatEntry(fileName = "00000001", fileType = "TRE", partNumber = 0, firstBlock = 4)
            // a continuation record for the same TRE (partNumber=1) — must be ignored
            .addFatEntry(fileName = "00000001", fileType = "TRE", partNumber = 1, firstBlock = 80)
            .addFatEntry(fileName = "00000001", fileType = "RGN", partNumber = 0, firstBlock = 12)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals(2, archive.subFiles.size)
        assertEquals(listOf("TRE", "RGN"), archive.subFiles.map { it.fileType })
        assertEquals(listOf(4, 12), archive.subFiles.map { it.firstBlock })
    }

    @Test
    fun fileTypeIsUppercasedForLookup() {
        // Older mkgmap builds occasionally emit lowercase filetype bytes; the public
        // API normalises to uppercase so callers don't have to care.
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "tre", firstBlock = 4)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals("TRE", archive.subFiles[0].fileType)
        assertNotNull(archive.findFirst("tre"))
        assertNotNull(archive.findFirst("TRE"))
        assertNull(archive.findFirst("RGN"))
    }

    @Test
    fun rejectsEncryptedImgByFirstByte() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .build()
        // Flip the first byte to a non-zero value to simulate an encrypted IMG.
        img[0] = 0x47.toByte()

        assertFailsWith<IllegalArgumentException> {
            ImgArchive.open(ByteArrayImgRandomAccessReader(img))
        }
    }

    @Test
    fun rejectsBadDskimgSignature() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .build()
        // Corrupt the DSKIMG signature.
        img[0x11] = 'X'.code.toByte()

        assertFailsWith<IllegalArgumentException> {
            ImgArchive.open(ByteArrayImgRandomAccessReader(img))
        }
    }

    @Test
    fun rejectsBadPartitionTableEndMarker() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .build()
        // Wipe the 0xAA55 marker.
        img[0x1FE] = 0
        img[0x1FF] = 0

        assertFailsWith<IllegalArgumentException> {
            ImgArchive.open(ByteArrayImgRandomAccessReader(img))
        }
    }

    @Test
    fun findFirstReturnsNullForUnknownType() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertNotNull(archive.findFirst("TRE"))
        assertNull(archive.findFirst("MDR"))
    }

    @Test
    fun findAllReturnsEveryMatch() {
        val img = SyntheticImg.builder()
            .addFatEntry(fileName = "00000001", fileType = "TRE", firstBlock = 4)
            .addFatEntry(fileName = "00000002", fileType = "TRE", firstBlock = 12)
            .addFatEntry(fileName = "00000001", fileType = "RGN", firstBlock = 20)
            .build()

        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(img))

        assertEquals(2, archive.findAll("TRE").size)
        assertEquals(1, archive.findAll("RGN").size)
        assertEquals(0, archive.findAll("LBL").size)
    }
}
