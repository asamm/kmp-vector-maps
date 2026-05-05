/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.io.ImgRandomAccessReader
import com.asamm.garminimg.io.LabelCharset

/**
 * Parser for an `.LBL` sub-file. The LBL holds the textual labels (POI names,
 * road names, place names) referenced from RGN by 24-bit logical offsets. It
 * also holds POI metadata (city/zip/phone), country and region tables — those
 * extra sections are deferred to B.2.4.b.
 *
 * # Phase B.2.4 status
 *
 * - **B.2.4.a (this commit):** common header + labels section descriptor +
 *   codepage + label decoders for the three label codings: 6 (6-bit packed),
 *   9 (8-bit codepage), 10 (10-bit / codepage). Covers what TRE/RGN need to
 *   resolve sub-division labels.
 * - **B.2.4.b (later):** POI metadata (street / city / zip / phone), city
 *   table, ZIP table, country/region tables, base-11 number decoding.
 *
 * # On-disk header layout
 *
 * Offsets are relative to the start of the LBL sub-file:
 * - `0x00..0x01` — sub-file header length (UInt16). Common to every sub-file.
 * - `0x0D` — lock flag (must be 0).
 * - `0x15..0x18` — labels-section offset (UInt32).
 * - `0x19..0x1C` — labels-section length (UInt32).
 * - `0x1D` — `dataOffsetMultiplier` exponent (multiplier = `1 shl byte`).
 * - `0x1E` — label coding (6, 9, or 10; see [LabelCoding]).
 * - `0xAA..0xAB` — codepage (UInt16).
 *
 * Other section descriptors (country at 0x1F, region at 0x2A, city at 0x35,
 * POI at 0x57, ZIP at 0x72) are read but discarded by the B.2.4.a header
 * parse — the data classes don't surface them yet.
 */
object LblSubFile {

    private const val OFFSET_LABELS_DESCRIPTOR: Long = 0x15L
    private const val OFFSET_CODEPAGE: Long = 0xAAL

    /**
     * Reads the LBL header. Leaves the reader positioned arbitrarily; callers
     * seek explicitly for label retrieval.
     */
    fun parseHeader(reader: ImgRandomAccessReader): LblHeader {
        val common = reader.readSubFileCommonHeader()
        require(common.headerLength >= 0x1F) {
            "LBL header length 0x${common.headerLength.toString(16)} is too small for " +
                "the labels-section descriptor (need >= 0x1F)."
        }

        reader.seek(OFFSET_LABELS_DESCRIPTOR)
        val labelsOffset = reader.readUInt32()
        val labelsLength = reader.readUInt32()
        val multiplierExp = reader.readByte()
        val codingByte = reader.readByte()
        val coding = LabelCoding.fromOnDiskValue(codingByte)

        val codepage = if (common.headerLength >= OFFSET_CODEPAGE.toInt() + 2) {
            reader.seek(OFFSET_CODEPAGE)
            reader.readUInt16()
        } else {
            LabelCharset.CODEPAGE_LATIN1
        }

        return LblHeader(
            headerLength = common.headerLength,
            labelsOffset = labelsOffset,
            labelsLength = labelsLength,
            dataOffsetMultiplier = 1 shl multiplierExp,
            coding = coding,
            codepage = codepage,
        )
    }

    /**
     * Resolves a logical label `offset` (typically a 22-bit value carried by a
     * sub-division or POI record) to its decoded string. Returns "???" for
     * unsupported codings — matches the Java predecessor's `NOCHAR` sentinel.
     */
    fun readLabel(
        reader: ImgRandomAccessReader,
        header: LblHeader,
        offset: Long,
    ): String {
        if (offset < 0 || offset * header.dataOffsetMultiplier >= header.labelsLength) {
            throw IllegalArgumentException(
                "Invalid label offset: 0x${offset.toString(16)} " +
                    "(labelsLength=${header.labelsLength}, " +
                    "multiplier=${header.dataOffsetMultiplier})",
            )
        }
        val absoluteOffset = header.labelsOffset + offset * header.dataOffsetMultiplier
        return when (header.coding) {
            LabelCoding.SIX_BIT -> readLabel6Bit(reader, absoluteOffset)
            // mkgmap-built maps with non-multibyte codepages typically use coding
            // 9; the Java predecessor delegates to the 10-bit path for both
            // because byte-level decoding is identical.
            LabelCoding.EIGHT_BIT,
            LabelCoding.TEN_BIT -> readLabelByteEncoded(reader, absoluteOffset, header)

            LabelCoding.UNKNOWN -> NOCHAR
        }
    }

    /**
     * Reads a null-terminated byte sequence and decodes via the LBL's codepage.
     * The Garmin "8-bit" and "10-bit" codings share this byte-level format.
     *
     * Bytes 0x01..0x06 at the start are highway-shield prefix codes (mkgmap
     * `Format6Encoder` uses 0x29 + c for the same purpose in 6-bit). They
     * stand for shield icons that go in front of route numbers (Hwy, I-,
     * Rt, etc.) — they are not text. We drop them so a label like
     * `de la République` reads as `de la République` instead of
     * dropping the whole label because its first char isn't a letter.
     */
    private fun readLabelByteEncoded(
        reader: ImgRandomAccessReader,
        offset: Long,
        header: LblHeader,
    ): String {
        reader.seek(offset)
        val out = ArrayList<Byte>()
        var stripping = true
        while (true) {
            val b = reader.readByte()
            if (b == 0) {
                break
            }
            // Skip leading shield bytes; keep everything once we've seen
            // any non-shield byte so embedded shields (rare but valid)
            // survive.
            if (stripping && b in 0x01..0x06) {
                continue
            }
            stripping = false
            out.add(b.toByte())
        }
        val charset = LabelCharset.of(header.codepage)
        return charset.decode(out.toByteArray())
    }

    /**
     * Decodes a Garmin 6-bit packed label.
     *
     * Each 3-byte group encodes 4 6-bit code points. Code points 0x00..0x1A
     * map to characters in [NORMAL_CHARS]; 0x1B switches to [SPECIAL_CHARS]
     * for the next code point only; 0x1C switches to [SYMBOL_CHARS] for the
     * next code point only; 0x1D emits `'|'`; 0x1E and 0x1F emit space; any
     * code point ≥ 0x30 ends the label.
     */
    private fun readLabel6Bit(reader: ImgRandomAccessReader, offset: Long): String {
        reader.seek(offset)
        val sb = StringBuilder()
        var charSet = SixBitCharSet.NORMAL
        while (true) {
            val b1 = reader.readByte()
            val b2 = reader.readByte()
            val b3 = reader.readByte()
            val codes = intArrayOf(
                b1 ushr 2,
                ((b1 and 0x3) shl 4) or (b2 ushr 4),
                ((b2 and 0xF) shl 2) or (b3 ushr 6),
                b3 and 0x3F,
            )
            for (c in codes) {
                if (c > 0x2F) {
                    return sb.toString()
                }
                charSet = appendSixBitChar(c, charSet, sb)
            }
        }
    }

    private fun appendSixBitChar(
        code: Int,
        currentSet: SixBitCharSet,
        sb: StringBuilder,
    ): SixBitCharSet {
        return when (currentSet) {
            SixBitCharSet.NORMAL -> when (code) {
                0x1C -> SixBitCharSet.SYMBOL
                0x1B -> SixBitCharSet.SPECIAL
                0x1D -> {
                    sb.append('|')
                    SixBitCharSet.NORMAL
                }
                0x1E, 0x1F -> {
                    sb.append(' ')
                    SixBitCharSet.NORMAL
                }
                else -> {
                    sb.append(NORMAL_CHARS[code])
                    SixBitCharSet.NORMAL
                }
            }
            SixBitCharSet.SYMBOL -> {
                sb.append(SYMBOL_CHARS[code])
                SixBitCharSet.NORMAL
            }
            SixBitCharSet.SPECIAL -> {
                sb.append(SPECIAL_CHARS[code])
                SixBitCharSet.NORMAL
            }
        }
    }

    private enum class SixBitCharSet { NORMAL, SYMBOL, SPECIAL }

    /**
     * Sentinel returned for labels we can't decode (coding=UNKNOWN). Matches
     * the Java predecessor's `NOCHAR`.
     */
    const val NOCHAR: String = "???"

    private val NORMAL_CHARS: CharArray = charArrayOf(
        ' ', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        '~', '~', '~', '~', '~',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '~', '~', '~', '~', '~', '~',
    )

    private val SYMBOL_CHARS: CharArray = charArrayOf(
        '@', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',',
        '-', '.', '/',
        '~', '~', '~', '~', '~', '~', '~', '~', '~', '~',
        ':', ';', '<', '=', '>', '?',
        '~', '~', '~', '~', '~', '~', '~', '~', '~', '~', '~',
        '[', '\\', ']', '^', '_',
    )

    private val SPECIAL_CHARS: CharArray = charArrayOf(
        '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '~', '~', '~', '~', '~',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '~', '~', '~', '~', '~', '~',
    )
}

/**
 * Parsed LBL header (B.2.4.a fields only). Most other section descriptors are
 * skipped until B.2.4.b lands.
 *
 * @property labelsOffset Logical byte offset of the labels-section start.
 * @property labelsLength Length of the labels section in bytes.
 * @property dataOffsetMultiplier Multiplier applied to a 22-bit offset to
 *   convert it to a byte offset within the labels section. Always a power of
 *   two; typically 1 for compact maps, 2 or 4 for larger ones.
 * @property coding Which decoder applies to label byte-streams.
 * @property codepage Codepage number (e.g. 1252, 65001) for byte-encoded
 *   labels. Ignored when [coding] is [LabelCoding.SIX_BIT].
 */
data class LblHeader(
    val headerLength: Int,
    val labelsOffset: Long,
    val labelsLength: Long,
    val dataOffsetMultiplier: Int,
    val coding: LabelCoding,
    val codepage: Int,
)

/**
 * Per-LBL label coding.
 *
 * On-disk this is a single byte at LBL offset 0x1E. The Java predecessor
 * recognises three values; everything else maps to [UNKNOWN] and labels are
 * returned as [LblSubFile.NOCHAR] sentinels.
 */
enum class LabelCoding(val onDiskValue: Int) {

    /** 6-bit packed encoding (uppercase ASCII + digits + symbols). */
    SIX_BIT(6),

    /** 8-bit codepage encoding (single-byte, null-terminated). */
    EIGHT_BIT(9),

    /**
     * Multibyte codepage encoding; in practice handled identically to 8-bit at
     * the byte level — the codepage decides how runs of bytes become Strings.
     */
    TEN_BIT(10),

    /** Unrecognised coding — returns [LblSubFile.NOCHAR]. */
    UNKNOWN(-1),
    ;

    companion object {

        /**
         * Returns the matching [LabelCoding] for a byte read from the LBL header,
         * or [UNKNOWN] if no enum value matches.
         */
        fun fromOnDiskValue(value: Int): LabelCoding {
            return entries.firstOrNull { it.onDiskValue == value } ?: UNKNOWN
        }
    }
}
