/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.fixture

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import kotlin.test.Test

/**
 * Diagnostic helper — dumps the FAT directory of a fixture so we can see what
 * sub-files (TYP especially) are present. Not a real assertion — outputs to
 * stdout for human inspection during development.
 */
class FatDumpTest {

    @Test
    fun dumpOtmMonacoFat() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(bytes))
        println("=== otm-monaco FAT (blockSize=${archive.blockSize}) ===")
        for (entry in archive.subFiles) {
            println("  ${entry.fileName}.${entry.fileType}  size=${entry.fileSize}  firstBlock=${entry.firstBlock}")
        }
    }

    @Test
    fun dumpMkgmapMonacoFat() {
        val bytes = fixtureBytes("maps/mkgmap-monaco/monaco.img")
        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(bytes))
        println("=== mkgmap-monaco FAT (blockSize=${archive.blockSize}) ===")
        for (entry in archive.subFiles) {
            println("  ${entry.fileName}.${entry.fileType}  size=${entry.fileSize}  firstBlock=${entry.firstBlock}")
        }
    }
}
