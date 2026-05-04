package com.asamm.garminimg.parser

import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.io.LabelCharset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for [LblSubFile.parseHeader] + [LblSubFile.readLabel] using
 * synthetic LBL byte payloads driven by [LblBuilder].
 */
class LblSubFileTest {

    @Test
    fun parsesHeaderWithLabelsDescriptor() {
        val lbl = LblBuilder()
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .coding(LabelCoding.EIGHT_BIT)
            .label(offset = 0, "Hello")
            .label(offset = 6, "World")
            .build()

        val header = openLblHeader(lbl)

        assertEquals(LabelCharset.CODEPAGE_CP1252, header.codepage)
        assertEquals(LabelCoding.EIGHT_BIT, header.coding)
        assertEquals(1, header.dataOffsetMultiplier)
    }

    @Test
    fun decodesEightBitAsciiLabel() {
        val lbl = LblBuilder()
            .coding(LabelCoding.EIGHT_BIT)
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .label(offset = 0, "Hello")
            .label(offset = 6, "World")
            .build()

        val (header, stream) = openLbl(lbl)
        assertEquals("Hello", LblSubFile.readLabel(stream, header, 0L))
        assertEquals("World", LblSubFile.readLabel(stream, header, 6L))
    }

    @Test
    fun decodesUtf8Label() {
        val lbl = LblBuilder()
            .coding(LabelCoding.TEN_BIT)
            .codepage(LabelCharset.CODEPAGE_UTF8)
            .label(offset = 0, "Café Müller", utf8 = true)
            .build()

        val (header, stream) = openLbl(lbl)
        assertEquals("Café Müller", LblSubFile.readLabel(stream, header, 0L))
    }

    @Test
    fun decodesLatin1ByteAtOrAbove80() {
        // A label with bytes beyond ASCII-7 in cp1252 encoding. We treat the
        // byte stream as Latin-1 in commonMain; "café" in cp1252 = 63 61 66 E9.
        val raw = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte(), 0x00)
        val lbl = LblBuilder()
            .coding(LabelCoding.EIGHT_BIT)
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .rawLabel(offset = 0, bytes = raw)
            .build()

        val (header, stream) = openLbl(lbl)
        // Latin-1 decode: 0xE9 -> U+00E9 'é'
        assertEquals("café", LblSubFile.readLabel(stream, header, 0L))
    }

    @Test
    fun rejectsLabelOffsetPastSectionEnd() {
        val lbl = LblBuilder()
            .coding(LabelCoding.EIGHT_BIT)
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .label(offset = 0, "X")
            .build()

        val (header, stream) = openLbl(lbl)
        assertFailsWith<IllegalArgumentException> {
            LblSubFile.readLabel(stream, header, 9999L)
        }
    }

    @Test
    fun unknownCodingReturnsNochar() {
        val lbl = LblBuilder()
            .coding(LabelCoding.UNKNOWN)
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .label(offset = 0, "X")
            .build()

        val (header, stream) = openLbl(lbl)
        assertEquals(LblSubFile.NOCHAR, LblSubFile.readLabel(stream, header, 0L))
    }

    @Test
    fun rejectsLockedSubFile() {
        val lbl = LblBuilder()
            .coding(LabelCoding.EIGHT_BIT)
            .codepage(LabelCharset.CODEPAGE_CP1252)
            .label(offset = 0, "X")
            .locked(true)
            .build()

        assertFailsWith<IllegalArgumentException> { openLblHeader(lbl) }
    }

    private fun openLblHeader(lblBytes: ByteArray): LblHeader {
        return openLbl(lblBytes).first
    }

    private fun openLbl(lblBytes: ByteArray): Pair<LblHeader, com.asamm.garminimg.io.ImgRandomAccessReader> {
        val img = SyntheticImg.builder()
            .blockSizeExponent(9, 0)
            .addFatEntry(
                fileName = "00000001",
                fileType = "LBL",
                fileSize = lblBytes.size,
                firstBlock = 4,
            )
            .payloadAtBlock(blockIndex = 4, bytes = lblBytes)
            .build()

        val container = ByteArrayImgRandomAccessReader(img)
        val archive = ImgArchive.open(container)
        val lblEntry = archive.findFirst("LBL") ?: error("missing LBL")
        val stream = archive.openSubFile(lblEntry, container)
        val header = LblSubFile.parseHeader(stream)
        return header to stream
    }
}
