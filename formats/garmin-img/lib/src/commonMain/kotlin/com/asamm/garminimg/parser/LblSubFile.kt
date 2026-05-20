/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.utils.io.ImgRandomAccessReader
import com.asamm.garminimg.utils.io.LabelCharset
import com.asamm.garminimg.model.PoiMetadata

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
    // Each LBL reference-table descriptor is 14 bytes: offset(4) +
    // length(4) + recordSize(2) + reserved(4). Country starts right
    // after the labels descriptor (which itself runs 0x15..0x1E, 10
    // bytes). Region/city follow at +14 / +28.
    private const val OFFSET_COUNTRY_DESCRIPTOR: Long = 0x1FL
    private const val OFFSET_REGION_DESCRIPTOR: Long = 0x2DL
    private const val OFFSET_CITY_DESCRIPTOR: Long = 0x3BL
    private const val OFFSET_POI_DESCRIPTOR: Long = 0x57L
    private const val OFFSET_ZIP_DESCRIPTOR: Long = 0x72L
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
        // Kotlin's `Int.shl` masks the shift count mod 32. A corrupt
        // header with exp >= 32 would silently yield multiplier=1 and
        // we'd then index labels at random positions. Real IMGs use
        // 0..7; cap at 15 (== 32-byte alignment) — anything past that
        // is a parse error.
        require(multiplierExp in 0..15) {
            "LBL label multiplier exponent $multiplierExp out of range [0, 15]"
        }
        val codingByte = reader.readByte()
        val coding = LabelCoding.fromOnDiskValue(codingByte)

        // Country definitions (0x1F): name + region range, indexed.
        val country = readSection(reader, common.headerLength, OFFSET_COUNTRY_DESCRIPTOR)
        // Region definitions (0x2A): name + country index.
        val region = readSection(reader, common.headerLength, OFFSET_REGION_DESCRIPTOR)
        // City definitions (0x35): name + region/country.
        val city = readSection(reader, common.headerLength, OFFSET_CITY_DESCRIPTOR)
        // POI properties section (0x57): per-POI metadata (street/city/zip/phone).
        val poiOffset: Long
        val poiLength: Long
        val poiMultiplier: Int
        val poiGlobalMask: Int
        if (common.headerLength >= OFFSET_POI_DESCRIPTOR.toInt() + 9) {
            reader.seek(OFFSET_POI_DESCRIPTOR)
            poiOffset = reader.readUInt32()
            poiLength = reader.readUInt32()
            val poiMultiplierExp = reader.readByte()
            require(poiMultiplierExp in 0..15) {
                "LBL POI multiplier exponent $poiMultiplierExp out of range [0, 15]"
            }
            poiMultiplier = 1 shl poiMultiplierExp
            poiGlobalMask = reader.readByte()
        } else {
            poiOffset = 0L
            poiLength = 0L
            poiMultiplier = 1
            poiGlobalMask = 0
        }
        // ZIP table (0x72): zip-code strings indexed by city.
        val zip = readSection(reader, common.headerLength, OFFSET_ZIP_DESCRIPTOR)

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
            poiOffset = poiOffset,
            poiLength = poiLength,
            poiMultiplier = poiMultiplier,
            poiGlobalMask = poiGlobalMask,
            country = country,
            region = region,
            city = city,
            zip = zip,
        )
    }

    /**
     * Reads a fixed-record-size section descriptor (offset, length,
     * record size) from the LBL header. The country / region / city /
     * zip blocks all share this layout. Returns a zero-length section
     * if the IMG's header is too short to carry the descriptor.
     */
    private fun readSection(
        reader: ImgRandomAccessReader,
        headerLength: Int,
        offset: Long,
    ): LblTableSection {
        // Each descriptor is 11 bytes: offset(4) + length(4) + recordSize(2)
        // + reserved(1).
        if (headerLength < offset.toInt() + 11) {
            return LblTableSection.EMPTY
        }
        reader.seek(offset)
        val o = reader.readUInt32()
        val l = reader.readUInt32()
        val recordSize = reader.readUInt16()
        return LblTableSection(o, l, recordSize)
    }

    /**
     * Resolves a POI label offset. POIs in Garmin IMGs don't store their
     * label in LBL directly — they use the POI index table at
     * [LblHeader.poiOffset]. Each POI table entry is a 24-bit value
     * whose lower 22 bits are the actual LBL label offset, plus an
     * optional property mask + zero or more attribute fields (street,
     * city, zip, phone) we currently ignore. Returns null if the POI
     * section isn't populated or the offset is out of range.
     */
    fun readPoiName(
        reader: ImgRandomAccessReader,
        header: LblHeader,
        offset: Long,
    ): String? {
        if (header.poiLength <= 0L) {
            return null
        }
        val multOffset = offset * header.poiMultiplier
        // Reject negative offset (a corrupt RGN record can hand us one
        // — `Int` arithmetic wraps silently) and any offset that would
        // either land at section EOF or read past it. The Java
        // predecessor used `>=` here; we keep that semantics.
        if (multOffset < 0L || multOffset >= header.poiLength) {
            return null
        }
        reader.seek(header.poiOffset + multOffset)
        val labelOffset = (reader.readUInt24() and 0x3FFFFF).toLong()
        return try {
            readLabel(reader, header, labelOffset)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Reads the full POI metadata block at the given offset — name plus
     * any of the optional attributes (street number, street, city, ZIP,
     * phone) the IMG carries for this POI. Returns null if the POI
     * section isn't populated or the offset is invalid.
     *
     * The block layout, per mkgmap / Garmin docs:
     * 1. UInt24: low 22 bits = label offset; bit 23 (0x800000) toggles
     *    a per-POI mask byte.
     * 2. (optional) byte localMask — XORs the per-IMG `poiGlobalMask`
     *    bits via [maskPointAttributes].
     * 3. (mask & 0x01) → street-number string (base-11 or LBL pointer)
     * 4. (mask & 0x02) → street-name LBL pointer
     * 5. (mask & 0x04) → city index (1- or 2-byte depending on city table size)
     * 6. (mask & 0x08) → ZIP index
     * 7. (mask & 0x10) → phone string
     */
    /**
     * Callback that knows how to resolve a city-by-point reference.
     * The legacy parser supports two encodings of a POI's city: a
     * direct label offset, or a `(subDivisionIndex, indexedPointIndex)`
     * pair pointing into RGN's indexed-points segment. Cross-file
     * access is messy to thread through LBL; the resolver is supplied
     * by [ImgFile] so it has TRE+RGN handles in scope.
     */
    fun interface IndexedPointLabelResolver {
        fun resolve(subDivisionIndex: Int, pointIndex: Int): String?
    }

    fun readPoi(
        reader: ImgRandomAccessReader,
        header: LblHeader,
        offset: Long,
        indexedPointLabelResolver: IndexedPointLabelResolver? = null,
    ): PoiMetadata? {
        if (header.poiLength <= 0L) {
            return null
        }
        val multOffset = offset * header.poiMultiplier
        // Reject negative offset (a corrupt RGN record can hand us one
        // — `Int` arithmetic wraps silently) and any offset that would
        // either land at section EOF or read past it. The Java
        // predecessor used `>=` here; we keep that semantics.
        if (multOffset < 0L || multOffset >= header.poiLength) {
            return null
        }
        reader.seek(header.poiOffset + multOffset)
        val local = reader.readUInt24()
        val labelOffset = (local and 0x3FFFFF).toLong()
        // Read label first so readLabel's seek doesn't disturb our
        // position past the POI header. Save & restore around it.
        val afterHeader = reader.position()
        val name = try {
            readLabel(reader, header, labelOffset)
        } catch (_: IllegalArgumentException) {
            null
        }
        reader.seek(afterHeader)

        var mask = header.poiGlobalMask
        if (local and 0x800000 != 0) {
            mask = maskPointAttributes(mask, reader.readByte())
        }
        // Each metadata field reader may seek into LBL for label
        // resolution; we restore back to the cursor after each so
        // subsequent fields read from the right POI-block position.
        val streetNumber = if (mask and 0x01 != 0) {
            val before = reader.position()
            val v = readPoiNumber(reader, header)
            // readPoiNumber consumed a known number of bytes regardless
            // of the label-pointer / base-11 branch; nothing to restore.
            // (label resolution inside happens after position is past
            // the encoded bytes — that's safe.)
            v
        } else null
        // After streetNumber, position is past the encoded bytes; same
        // for the rest. We just need to make sure intermediate label
        // resolutions don't leave the cursor astray.
        val street = if (mask and 0x02 != 0) {
            val raw = reader.readUInt24()
            if (raw and 0x800000 != 0) {
                null
            } else {
                val savedPos = reader.position()
                val s = try {
                    readLabel(reader, header, (raw and 0x3FFFFF).toLong())
                } catch (_: IllegalArgumentException) { null }
                reader.seek(savedPos)
                s
            }
        } else null
        val cityIndex = if (mask and 0x04 != 0) {
            if (header.city.recordCount > 255) reader.readUInt16() else reader.readByte()
        } else 0
        val zipIndex = if (mask and 0x08 != 0) {
            if (header.zip.recordCount > 255) reader.readUInt16() else reader.readByte()
        } else 0
        val phone = if (mask and 0x10 != 0) readPoiNumber(reader, header) else null
        // Resolve indexed lookups.
        val cityName = if (cityIndex > 0) {
            readCityName(reader, header, cityIndex, indexedPointLabelResolver)
        } else null
        val zipCode = if (zipIndex > 0) readZipCode(reader, header, zipIndex) else null
        return PoiMetadata(
            name = name,
            streetNumber = streetNumber,
            street = street,
            cityIndex = cityIndex,
            cityName = cityName,
            zipIndex = zipIndex,
            zipCode = zipCode,
            phone = phone,
        )
    }

    /**
     * mkgmap "base-11 / label-pointer" hybrid for POI street numbers
     * and phone fields. If the first byte's high bit is clear, the
     * remaining 23 bits form an LBL label offset. Otherwise the bytes
     * encode digits in base-11 (0..9 + '-') — two digits per byte.
     *
     * Important: this function snapshots the reader's position before
     * any [readLabel] call and restores it afterwards, so the cursor
     * stays at the byte right after the encoded number — every
     * subsequent POI metadata field starts where it should.
     */
    private fun readPoiNumber(reader: ImgRandomAccessReader, header: LblHeader): String? {
        val first = reader.readByte()
        if (first and 0x80 == 0) {
            // High bit clear → 24-bit (3-byte) label offset.
            val offset = (first.toLong() shl 16) or reader.readUInt16().toLong()
            val savedPos = reader.position()
            val result = try {
                readLabel(reader, header, offset)
            } catch (_: IllegalArgumentException) {
                null
            }
            reader.seek(savedPos)
            return result
        }
        // Base-11 path. Java's loop is do-while: it always reads at
        // least one continuation byte even if the first byte's high
        // bit is set (because the high bit on the first byte is the
        // sentinel that selected this path, not a "last byte" marker).
        val sb = StringBuilder()
        decodeBase11Pair(first and 0x7F, last = true, out = sb)
        var b: Int
        do {
            b = reader.readByte()
            decodeBase11Pair(b and 0x7F, last = b and 0x80 != 0, out = sb)
        } while (b and 0x80 == 0)
        return sb.toString()
    }

    private fun readCityName(
        reader: ImgRandomAccessReader,
        header: LblHeader,
        index: Int,
        indexedPointLabelResolver: IndexedPointLabelResolver?,
    ): String? {
        val table = header.city
        if (table.isEmpty || table.recordSize != 5) {
            return null
        }
        val recordOffset = (index - 1).toLong() * table.recordSize
        if (recordOffset >= table.length) {
            return null
        }
        val saved = reader.position()
        reader.seek(table.offset + recordOffset)
        val data = reader.readUInt24()
        val info = reader.readUInt16()
        val name = if (info and 0x8000 != 0) {
            // Point reference: data's high 16 bits are the
            // SubDivision index, low 8 bits are the indexed-point
            // index within that sub-division. Cross-references RGN.
            indexedPointLabelResolver?.resolve(
                subDivisionIndex = data ushr 8,
                pointIndex = data and 0xFF,
            )
        } else {
            try {
                readLabel(reader, header, (data and 0x3FFFFF).toLong())
            } catch (_: IllegalArgumentException) { null }
        }
        reader.seek(saved)
        return name
    }

    private fun readZipCode(
        reader: ImgRandomAccessReader,
        header: LblHeader,
        index: Int,
    ): String? {
        val table = header.zip
        if (table.isEmpty) {
            return null
        }
        val recordOffset = (index - 1).toLong() * table.recordSize
        if (recordOffset >= table.length) {
            return null
        }
        val saved = reader.position()
        reader.seek(table.offset + recordOffset)
        val raw = reader.readUInt24()
        val zip = try {
            readLabel(reader, header, (raw and 0x3FFFFF).toLong())
        } catch (_: IllegalArgumentException) { null }
        reader.seek(saved)
        return zip
    }

    /**
     * XORs `localMask` bits against `globalMask`, but only for the
     * *set* bits in `globalMask` — the other bits stay clear. Mirrors
     * the mkgmap convention: each POI may flip on/off any of the
     * globally-expected attribute fields.
     */
    private fun maskPointAttributes(globalMask: Int, localMask: Int): Int {
        var result = globalMask
        var globalBit = 0x01
        var localBit = 0x01
        for (i in 0 until 8) {
            if (globalMask and globalBit != 0) {
                if (localMask and localBit == 0) {
                    result = result xor globalBit
                }
                localBit = localBit shl 1
            }
            globalBit = globalBit shl 1
        }
        return result
    }

    private fun decodeBase11Pair(byte: Int, last: Boolean, out: StringBuilder) {
        val a = byte / 11
        val b = byte % 11
        appendBase11Digit(a, last, out)
        appendBase11Digit(b, last, out)
    }

    private fun appendBase11Digit(digit: Int, last: Boolean, out: StringBuilder) {
        if (digit == 10) {
            if (!last) out.append('-')
        } else {
            out.append(digit)
        }
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
 *
 * @param poiOffset logical byte offset of the POI index section. POIs (cities, towns,
 *   restaurants, etc.) reference a row in this table by `(rgnLabelOffset * poiMultiplier)`; the
 *   row's first 24 bits hold the actual LBL label offset, plus optional metadata fields
 *   (street/city/zip/phone). 0 when the IMG ships no POI metadata.
 * @param poiGlobalMask default property mask applied to every POI in this LBL. A POI may
 *   override individual bits via its own local mask (see [LblSubFile.readPoi]); this is the
 *   per-IMG starting set of "expected attributes" (street, city, zip, phone…).
 */
data class LblHeader(
    val headerLength: Int,
    val labelsOffset: Long,
    val labelsLength: Long,
    val dataOffsetMultiplier: Int,
    val coding: LabelCoding,
    val codepage: Int,
    val poiOffset: Long = 0L,
    val poiLength: Long = 0L,
    val poiMultiplier: Int = 1,
    val poiGlobalMask: Int = 0,
    val country: LblTableSection = LblTableSection.EMPTY,
    val region: LblTableSection = LblTableSection.EMPTY,
    val city: LblTableSection = LblTableSection.EMPTY,
    val zip: LblTableSection = LblTableSection.EMPTY,
)

/**
 * Common shape for the LBL reference tables (country, region, city,
 * zip). Each row is [recordSize] bytes long; total `length / recordSize`
 * records sit at [offset]. `recordSize == 0` means "no such section".
 */
data class LblTableSection(
    val offset: Long,
    val length: Long,
    val recordSize: Int,
) {
    val isEmpty: Boolean
        get() = recordSize == 0 || length == 0L

    val recordCount: Int
        get() = if (recordSize <= 0) 0 else (length / recordSize).toInt()

    companion object {
        val EMPTY: LblTableSection = LblTableSection(0L, 0L, 0)
    }
}

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
