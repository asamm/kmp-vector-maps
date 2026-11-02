/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.fixture

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.parser.TypSubFile
import kotlin.test.Test

/**
 * Diagnostic dump of OPENTOPO.TYP — used during TYP body parser
 * development to inspect on-disk structure. Not a real test.
 */
class TypDumpTest {

    @Test
    fun dumpOpenTopoTypHeader() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")

        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val typEntry = archive.findFirst("TYP")
        if (typEntry == null) {
            println("[typ-dump] no TYP in fixture")
            return
        }

        val typStream = archive.openSubFile(typEntry, container)
        val header = TypSubFile.parseHeader(typStream)

        println("=== OPENTOPO.TYP (${typEntry.fileSize} bytes) ===")
        println("  signature: '${header.signature}'")
        println("  build: ${header.buildYear}-${header.buildMonth}-${header.buildDay}")
        println("  codepage: ${header.codepage}")
        println("  family/product: ${header.familyId}/${header.productCode}")
        println("  points:   ${header.points}")
        println("  lines:    ${header.lines}")
        println("  polygons: ${header.polygons}")

        // Dump first 256 bytes of each primary section.
        for ((name, range) in listOf(
            "points" to header.points,
            "lines" to header.lines,
            "polygons" to header.polygons,
        )) {
            range ?: continue
            println("--- first 64 bytes of $name section (offset=${range.offset.toString(16)}) ---")
            typStream.seek(range.offset)
            val limit = 64L.coerceAtMost(range.length).toInt()
            val buf = ByteArray(limit)
            typStream.readBlock(buf)
            print("  ")
            for ((i, b) in buf.withIndex()) {
                print("%02x ".format(b.toInt() and 0xFF))
                if (i % 16 == 15) {
                    print("\n  ")
                }
            }
            println()
        }
    }
}
