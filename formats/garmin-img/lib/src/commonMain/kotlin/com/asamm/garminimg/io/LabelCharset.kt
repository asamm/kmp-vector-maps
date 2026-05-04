/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.io

/**
 * Decodes a byte sequence taken from an IMG label section into a Kotlin
 * [String], using the codepage recorded in the LBL header.
 *
 * Garmin IMGs store labels in single- or multi-byte encodings keyed by a
 * codepage number from a fixed list (most commonly 0/850 = "Western European",
 * 65001 = UTF-8, 932 = Shift-JIS, 950 = Big5). commonMain has no charset
 * support beyond UTF-8 and ASCII, so we implement the two everyday cases
 * (Latin-1 / cp1252-ish, and UTF-8) directly here. Anything else is decoded as
 * raw Latin-1 — the bytes round-trip but the visible glyph may be wrong.
 *
 * Future B.2.4.b work will introduce a platform-specific charset bridge for
 * exotic codepages; right now they're rare in mkgmap-built and OpenTopoMap
 * fixtures (both default to cp1252).
 */
class LabelCharset internal constructor(private val codepage: Int) {

    /**
     * Decodes `bytes[0..size)` to a String.
     */
    fun decode(bytes: ByteArray, size: Int = bytes.size): String {
        return when (codepage) {
            CODEPAGE_UTF8 -> decodeUtf8(bytes, size)
            else -> decodeLatin1(bytes, size)
        }
    }

    private fun decodeUtf8(bytes: ByteArray, size: Int): String {
        return bytes.decodeToString(endIndex = size)
    }

    /**
     * Decodes as Latin-1: each byte 0..255 maps directly to its Char with the
     * same code point. Works for cp1252 too at the cost of 32 mojibake glyphs
     * in the 0x80..0x9F range — acceptable for Phase B; revisit when
     * platform-specific charset bridge lands.
     */
    private fun decodeLatin1(bytes: ByteArray, size: Int): String {
        val sb = StringBuilder(size)
        for (i in 0 until size) {
            sb.append(Char(bytes[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    companion object {

        /**
         * Codepage numbers we recognise explicitly. Anything else falls back to
         * Latin-1 decoding (see [decode]).
         */
        const val CODEPAGE_LATIN1: Int = 0
        const val CODEPAGE_CP850: Int = 850
        const val CODEPAGE_CP1252: Int = 1252
        const val CODEPAGE_UTF8: Int = 65001
        const val CODEPAGE_SHIFT_JIS: Int = 932
        const val CODEPAGE_BIG5: Int = 950

        /**
         * Returns a [LabelCharset] that handles the given codepage. The result
         * never throws — exotic codepages get a Latin-1 fallback decoder.
         */
        fun of(codepage: Int): LabelCharset {
            return LabelCharset(codepage)
        }
    }
}
