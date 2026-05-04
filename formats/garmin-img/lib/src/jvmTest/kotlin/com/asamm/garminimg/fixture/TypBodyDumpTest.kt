package com.asamm.garminimg.fixture

import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.parser.ImgArchive
import com.asamm.garminimg.parser.TypBodyParser
import com.asamm.garminimg.parser.TypSubFile
import kotlin.test.Test

/**
 * Diagnostic dump of OPENTOPO.TYP body — used during TYP body parser
 * iteration to inspect what records the parser is producing. Not a real
 * test; prints to stdout for human inspection.
 */
class TypBodyDumpTest {

    @Test
    fun dumpOpenTopoTypBody() {
        val bytes = TypBodyDumpTest::class.java.classLoader
            ?.getResourceAsStream("fixtures/otm-monaco/otm-monaco.img")
            ?.use { it.readBytes() } ?: return
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val typEntry = archive.findFirst("TYP") ?: return

        val typStream = archive.openSubFile(typEntry, container)
        val header = TypSubFile.parseHeader(typStream)

        println("=== OPENTOPO.TYP body dump ===")
        println("polygonIndex: ${header.polygonIndex}")
        println("lineIndex:    ${header.lineIndex}")
        println("pointIndex:   ${header.pointIndex}")
        println("polygonData:  ${header.polygons}")
        println("lineData:     ${header.lines}")

        val table = TypBodyParser.parse(typStream, header)
        println("--- Polygon styles (${table.polygons.size} entries) ---")
        for ((typeForFile, style) in table.polygons.entries.sortedBy { it.key }) {
            val type = typeForFile ushr 5
            val subType = typeForFile and 0x1F
            println(
                "  type=0x%02x subType=0x%02x (typeForFile=0x%04x) -> fill=#%06x stroke=%s".format(
                    type, subType, typeForFile,
                    style.fillColor.argb and 0xFFFFFF,
                    style.strokeColor?.let { "#%06x".format(it.argb and 0xFFFFFF) } ?: "null",
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
