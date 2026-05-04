package com.asamm.garminimg.fixture

import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.parser.ImgArchive
import kotlin.test.Test

/**
 * Diagnostic helper — dumps the FAT directory of a fixture so we can see what
 * sub-files (TYP especially) are present. Not a real assertion — outputs to
 * stdout for human inspection during development.
 */
class FatDumpTest {

    @Test
    fun dumpOtmMonacoFat() {
        val bytes = FatDumpTest::class.java.classLoader
            ?.getResourceAsStream("fixtures/otm-monaco/otm-monaco.img")
            ?.use { it.readBytes() } ?: return
        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(bytes))
        println("=== otm-monaco FAT (blockSize=${archive.blockSize}) ===")
        for (entry in archive.subFiles) {
            println("  ${entry.fileName}.${entry.fileType}  size=${entry.fileSize}  firstBlock=${entry.firstBlock}")
        }
    }

    @Test
    fun dumpMkgmapMonacoFat() {
        val bytes = FatDumpTest::class.java.classLoader
            ?.getResourceAsStream("fixtures/mkgmap-monaco/monaco.img")
            ?.use { it.readBytes() } ?: return
        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(bytes))
        println("=== mkgmap-monaco FAT (blockSize=${archive.blockSize}) ===")
        for (entry in archive.subFiles) {
            println("  ${entry.fileName}.${entry.fileType}  size=${entry.fileSize}  firstBlock=${entry.firstBlock}")
        }
    }
}
