package com.asamm.garminimg.fixture

import com.asamm.garminimg.coord.CoordUtils
import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.parser.ImgArchive
import com.asamm.garminimg.model.MapObject
import com.asamm.garminimg.parser.LabelCoding
import com.asamm.garminimg.parser.LblSubFile
import com.asamm.garminimg.parser.RgnSubFile
import com.asamm.garminimg.parser.SubDivisionParser
import com.asamm.garminimg.parser.TreSubFile
import com.asamm.garminimg.parser.readSubFileCommonHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test that the `otm-monaco` fixture loads from test resources, the IMG header
 * has the expected unencrypted-DSKIMG signature, and the embedded map description
 * matches what we documented in NOTES.md. Acts as a canary if the fixture is ever
 * regenerated with a different name or the test-resource wiring breaks.
 *
 * Lives in `jvmTest` (not `commonTest`) for now because resource loading needs
 * platform glue; a `commonTest` variant lands when the file-backed
 * `ImgRandomAccessReader` is implemented across targets.
 */
class OtmMonacoFixtureTest {

    @Test
    fun fixtureIsReadableAndUnencrypted() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        // Sanity: file size should match what NOTES.md records (8 503 296 bytes).
        assertEquals(8_503_296, bytes.size, "Monaco fixture size drifted from NOTES.md")

        val r = ByteArrayImgRandomAccessReader(bytes)

        // Encrypted IMGs XOR every byte with the first byte of the file. Unencrypted
        // ones start with 0x00. CLAUDE.md / PLAN.md scope is unencrypted-only — fail
        // loudly if a fixture ever ships in the wrong shape.
        assertEquals(0, r.readByte(), "fixture must be unencrypted (first byte != 0)")

        // "DSKIMG" signature lives at offset 0x10.
        r.seek(0x10L)
        assertEquals("DSKIMG", r.readString(6))
    }

    @Test
    fun archiveOpensWithExpectedStructure() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val archive = ImgArchive.open(ByteArrayImgRandomAccessReader(bytes))

        // Description trimmed of trailing spaces; build counter (the trailing digit
        // OpenTopoMap appends, e.g. "Monaco 2") may shift on regeneration but the
        // prefix is stable.
        assertTrue(
            archive.description.startsWith("OpenTopoMap Monaco"),
            "unexpected description: '${archive.description}'",
        )

        // mkgmap defaults to a 2048-byte block (exponent 0xB). Anything else would
        // be a regeneration with non-default flags and worth flagging.
        assertEquals(2048, archive.blockSize, "unexpected block size for Monaco fixture")

        // mkgmap produces NT (GMP-packed) IMGs nowadays — TRE/RGN/LBL/NET live
        // inside a single GMP sub-file rather than as separate FAT entries. Either
        // layout is valid; we just require the FAT to be non-empty and to contain
        // at least one of the recognised types so we know parsing reached the data.
        assertTrue(archive.subFiles.isNotEmpty(), "Monaco FAT is empty")

        // Monaco uses the legacy mkgmap layout: separate FAT entries for each
        // sub-file type. Spot-check that the four sub-files we'll need to parse in
        // B.2.2+ are all present as primary records.
        for (type in listOf("TRE", "RGN", "LBL")) {
            assertNotNull(
                archive.findFirst(type),
                "Monaco FAT missing primary $type entry — got ${archive.subFiles.map { it.fileType }}",
            )
        }

        // Continuation parts (partNumber != 0) must have been filtered out — only
        // primary entries reach the public list. The Monaco RGN spans many blocks
        // and would otherwise show up multiple times.
        assertEquals(
            1,
            archive.findAll("RGN").size,
            "RGN should appear once; continuation parts must be skipped",
        )
    }

    @Test
    fun treSubFileOpensWithValidCommonHeader() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val tre = archive.findFirst("TRE") ?: error("Monaco TRE entry missing")

        val stream = archive.openSubFile(tre, container)

        // The TRE common header must be readable: header length is non-zero and
        // the lock flag at 0xD is 0 (unencrypted). If readSubFileCommonHeader
        // throws here we know either our block-demux is wrong or the fixture is
        // not what we documented.
        val header = stream.readSubFileCommonHeader()
        assertTrue(
            header.headerLength in 0x10..0x200,
            "TRE headerLength out of plausible range: ${header.headerLength}",
        )

        // Sanity: the stream's logical size matches what the FAT recorded.
        assertEquals(tre.fileSize.toLong(), stream.size)
    }

    @Test
    fun treBoundsCoverMonacoCountry() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val treEntry = archive.findFirst("TRE") ?: error("Monaco TRE entry missing")
        val stream = archive.openSubFile(treEntry, container)

        val header = TreSubFile.parseHeader(stream)

        // Monaco lies around 43.72°..43.78°N / 7.40°..7.45°E. OpenTopoMap builds
        // tiles with substantial padding into the surrounding French Riviera so
        // boundary roads/POIs render fully, so we accept a generous bbox (±0.3° N/S
        // and ~1° E to cover the padding).
        val northDeg = CoordUtils.toWGS84(header.northBoundary)
        val southDeg = CoordUtils.toWGS84(header.southBoundary)
        val eastDeg = CoordUtils.toWGS84(header.eastBoundary)
        val westDeg = CoordUtils.toWGS84(header.westBoundary)

        assertTrue(
            northDeg in 43.5..44.0,
            "TRE north out of Monaco range: ${northDeg}° (raw ${header.northBoundary})",
        )
        assertTrue(
            southDeg in 43.5..44.0,
            "TRE south out of Monaco range: ${southDeg}° (raw ${header.southBoundary})",
        )
        assertTrue(
            eastDeg in 6.5..8.5,
            "TRE east out of Monaco range: ${eastDeg}° (raw ${header.eastBoundary})",
        )
        assertTrue(
            westDeg in 6.5..8.5,
            "TRE west out of Monaco range: ${westDeg}° (raw ${header.westBoundary})",
        )

        // OpenTopoMap Monaco is built with extended types (TRE7 present in newer
        // mkgmap output). If a regenerated fixture ever lacks it, this assertion
        // catches that drift.
        val extendedTypes = header.extendedTypes
        assertTrue(
            extendedTypes != null,
            "expected TRE7 extended-types block to be present (header=0x${header.headerLength.toString(16)})",
        )
    }

    @Test
    fun treLevelsAreReadableAndSpanReasonableZoomRange() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val treEntry = archive.findFirst("TRE") ?: error("Monaco TRE entry missing")
        val stream = archive.openSubFile(treEntry, container)

        val levels = TreSubFile.parseLevels(stream)

        // mkgmap typically emits 3..6 levels for a small region.
        assertTrue(
            levels.levels.size in 1..16,
            "Monaco level count out of range: ${levels.levels.size}",
        )
        // bitsPerCoord should sit in the realistic range; the coarsest level
        // sometimes has bitsPerCoord 0 (sentinel), the finest is usually 18..24.
        for (lvl in levels.levels) {
            assertTrue(
                lvl.bitsPerCoord in 0..24,
                "bitsPerCoord out of range at level ${lvl.level}: ${lvl.bitsPerCoord}",
            )
            assertTrue(
                lvl.level in 0..15,
                "level number out of nibble range: ${lvl.level}",
            )
        }
        // minLevel < maxLevel for any non-trivial map.
        assertTrue(
            levels.minLevel <= levels.maxLevel,
            "minLevel(${levels.minLevel}) > maxLevel(${levels.maxLevel})",
        )
        // At least the implicit root counts in the total.
        assertTrue(levels.totalSubDivisions >= 1)
    }

    @Test
    fun subDivisionTreeWalksToLeavesAndCarriesData() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val treEntry = archive.findFirst("TRE") ?: error("Monaco TRE entry missing")
        val stream = archive.openSubFile(treEntry, container)

        val header = TreSubFile.parseHeader(stream)
        val levels = TreSubFile.parseLevels(stream)
        val tree = SubDivisionParser.parse(
            reader = stream,
            levels = levels,
            extendedTypes = header.extendedTypes,
        )

        // Monaco has at least one root sub-division at maxLevel.
        assertTrue(tree.roots.isNotEmpty(), "Monaco TRE has no root sub-divisions")
        assertTrue(tree.size >= tree.roots.size, "tree byIndex should cover roots")
        for (root in tree.roots) {
            assertEquals(levels.maxLevel, root.level)
        }
        // At least some sub-division has a non-zero dataOffset (real RGN data).
        val withData = tree.all().count { it.dataOffset > 0 }
        assertTrue(
            withData > 0,
            "Monaco tree has no sub-divisions with data — parser likely misaligned",
        )
        // At least some sub-division advertises POINT/POLYLINE/POLYGON.
        val withObjects = tree.all().count { it.objectTypes != 0 }
        assertTrue(
            withObjects > 0,
            "Monaco tree has no sub-divisions with object-type flags",
        )
    }

    @Test
    fun lblHeaderParsesAndCanReadFirstLabel() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)
        val lblEntry = archive.findFirst("LBL") ?: error("Monaco LBL entry missing")
        val stream = archive.openSubFile(lblEntry, container)

        val header = LblSubFile.parseHeader(stream)

        // mkgmap-built and OpenTopoMap-built maps both use coding 9 (8-bit
        // codepage) or coding 6 (6-bit packed). Coding 10 is rare. Reject UNKNOWN
        // — that would mean we read garbage somewhere.
        assertTrue(
            header.coding != LabelCoding.UNKNOWN,
            "Monaco LBL has unknown coding (${header.coding}) — header parse misaligned",
        )
        // Codepage is in the realistic range — typically 1252 for Western maps,
        // 65001 (UTF-8) for newer mkgmap output.
        assertTrue(
            header.codepage in 0..65535,
            "Monaco LBL codepage out of range: ${header.codepage}",
        )
        // Labels section is non-empty and starts past the header.
        assertTrue(header.labelsLength > 0, "Monaco LBL has empty labels section")
        assertTrue(
            header.labelsOffset >= header.headerLength,
            "Monaco LBL labels section overlaps the header",
        )

        // Read the very first label at offset 0 (which by mkgmap convention is
        // an empty label that other records use as a "no name" sentinel). Both
        // empty and non-empty labels are acceptable here — we just need the
        // decoder to return without throwing.
        val firstLabel = LblSubFile.readLabel(stream, header, 0L)
        assertEquals("", firstLabel, "expected mkgmap's empty-label sentinel at offset 0")

        // Try a label further in (offset 1) — it should decode to something
        // (possibly empty depending on the underlying byte). We're sanity-
        // checking that the decoder doesn't throw on a real labels section.
        if (header.labelsLength > 1) {
            // Labels are byte-addressed in coding-9/10 fixtures (multiplier=1),
            // so offset 1 is the second byte; for multiplier-2 fixtures it's
            // logical-index 1 = byte 2. Either way, the decoder should not
            // throw.
            val secondLabel = LblSubFile.readLabel(stream, header, 1L)
            assertNotNull(secondLabel)
        }
    }

    @Test
    fun rgnSubDivisionParseEmitsFeatures() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val container = ByteArrayImgRandomAccessReader(bytes)
        val archive = ImgArchive.open(container)

        val treEntry = archive.findFirst("TRE") ?: error("Monaco TRE missing")
        val rgnEntry = archive.findFirst("RGN") ?: error("Monaco RGN missing")
        val lblEntry = archive.findFirst("LBL") ?: error("Monaco LBL missing")

        // Each sub-file needs its own container reader so seeks don't trample
        // each other. ByteArrayImgRandomAccessReader is backed by the same
        // bytes; cheap to instantiate three.
        val treStream = archive.openSubFile(treEntry, ByteArrayImgRandomAccessReader(bytes))
        val rgnStream = archive.openSubFile(rgnEntry, ByteArrayImgRandomAccessReader(bytes))
        val lblStream = archive.openSubFile(lblEntry, ByteArrayImgRandomAccessReader(bytes))

        val treHeader = TreSubFile.parseHeader(treStream)
        val levels = TreSubFile.parseLevels(treStream)
        val rgnHeader = RgnSubFile.parseHeader(rgnStream)
        val lblHeader = LblSubFile.parseHeader(lblStream)
        val tree = SubDivisionParser.parse(
            reader = treStream,
            levels = levels,
            extendedTypes = treHeader.extendedTypes,
            rgnInfo = RgnSubFile.endInfo(rgnHeader),
        )

        // Walk every sub-division; collect totals across the whole map.
        var pointCount = 0
        var polyCount = 0
        var withLabel = 0
        for (subDivision in tree.all()) {
            if (subDivision.objectTypes == 0) {
                continue
            }
            val objects = RgnSubFile.parseSubDivision(
                reader = rgnStream,
                header = rgnHeader,
                subDivision = subDivision,
                levels = levels,
                lblReader = lblStream,
                lblHeader = lblHeader,
            ).toList()
            for (obj in objects) {
                when (obj) {
                    is MapObject.Point -> pointCount++
                    is MapObject.PolyShape -> polyCount++
                }
                if (!obj.label.isNullOrEmpty()) {
                    withLabel++
                }
            }
        }

        // Monaco has plenty of POIs and roads — expect non-trivial counts.
        assertTrue(
            pointCount > 50,
            "Monaco point count too low: $pointCount (parser likely misaligned)",
        )
        assertTrue(
            polyCount > 50,
            "Monaco poly count too low: $polyCount (parser likely misaligned)",
        )
        assertTrue(
            withLabel > 10,
            "Monaco label-resolved count too low: $withLabel",
        )
    }

    @Test
    fun headerCarriesOpenTopoMapDescription() {
        val bytes = loadFixture("fixtures/otm-monaco/otm-monaco.img")
        val r = ByteArrayImgRandomAccessReader(bytes)
        // Map description starts at offset 0x49 and runs 20 bytes. We check a prefix
        // rather than the full string — OpenTopoMap appends a build counter that may
        // change between regenerations.
        r.seek(0x49L)
        val description = r.readString(20)
        assertEquals("OpenTopoMap Monaco", description.trimEnd(' ', ' ').take(18))
    }

    private fun loadFixture(path: String): ByteArray {
        val stream = OtmMonacoFixtureTest::class.java.classLoader
            ?.getResourceAsStream(path)
        assertNotNull(stream, "fixture not on test classpath: $path")
        return stream.use { it.readBytes() }
    }
}
