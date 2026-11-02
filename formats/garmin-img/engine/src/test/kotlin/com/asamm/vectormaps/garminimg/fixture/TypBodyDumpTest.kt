/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.fixture

import com.asamm.vectormaps.garminimg.fixtureBytes
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.parser.TypBodyParser
import com.asamm.vectormaps.garminimg.parser.TypSubFile
import kotlin.test.Test

/**
 * Diagnostic dump of OPENTOPO.TYP body — used during TYP body parser
 * iteration to inspect what records the parser is producing. Not a real
 * test; prints to stdout for human inspection.
 */
class TypBodyDumpTest {

    @Test
    fun dumpOpenTopoTypBody() {
        val bytes = fixtureBytes("maps/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val typEntry = archive.findFirst("TYP") ?: return

        val typStream = archive.openSubFile(typEntry, container)
        val header = TypSubFile.parseHeader(typStream)

        println("=== OPENTOPO.TYP body dump ===")
        println("polygonIndex:  ${header.polygonIndex}")
        println("lineIndex:     ${header.lineIndex}")
        println("pointIndex:    ${header.pointIndex}")
        println("polygonData:   ${header.polygons}")
        println("lineData:      ${header.lines}")
        println("shapeStacking: ${header.shapeStacking}")

        val table = TypBodyParser.parse(typStream, header)
        println("--- Polygon styles (${table.polygons.size} entries) — sorted by drawPriority ---")
        for ((typeForFile, style) in table.polygons.entries.sortedBy { it.value.drawPriority }) {
            val type = typeForFile ushr 5
            val subType = typeForFile and 0x1F
            println(
                "  prio=%5d type=0x%02x subType=0x%02x -> fill=#%06x stroke=%s".format(
                    style.drawPriority, type, subType,
                    style.fillColor.argb and 0xFFFFFF,
                    style.strokeColor?.let { "#%06x".format(it.argb and 0xFFFFFF) } ?: "null",
                ),
            )
        }
        println("--- Point styles (${table.points.size} entries) ---")
        for ((typeForFile, style) in table.points.entries.sortedBy { it.key }) {
            val type = typeForFile ushr 5
            val subType = typeForFile and 0x1F
            println(
                "  type=0x%02x subType=0x%02x -> icon=%dx%d".format(
                    type, subType, style.icon.width, style.icon.height,
                ),
            )
        }
        println("--- Line styles (${table.lines.size} entries) ---")
        for ((typeForFile, style) in table.lines.entries.sortedBy { it.key }) {
            val type = typeForFile ushr 5
            val subType = typeForFile and 0x1F
            println(
                "  type=0x%02x subType=0x%02x -> stroke=#%06x border=%s w=%.1f totW=%.1f".format(
                    type, subType,
                    style.strokeColor.argb and 0xFFFFFF,
                    style.borderColor?.let { "#%06x".format(it.argb and 0xFFFFFF) } ?: "null",
                    style.strokeWidthPx, style.totalWidthPx,
                ),
            )
        }
    }
}
