/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

/**
 * Decodes a byte sequence taken from an IMG label section into a Kotlin
 * [String], using the codepage recorded in the LBL header.
 *
 * Garmin IMGs store labels in single- or multi-byte encodings keyed by a
 * codepage number from a fixed list (most commonly 0/850 = "Western European",
 * 65001 = UTF-8, 932 = Shift-JIS, 950 = Big5). We implement the two everyday
 * cases (Latin-1 / cp1252-ish, and UTF-8) directly here. Anything else is decoded
 * as raw Latin-1 — the bytes round-trip but the visible glyph may be wrong.
 *
 * `java.nio.charset` could add the exotic codepages; they're rare in mkgmap-built
 * and OpenTopoMap fixtures (both default to cp1252).
 */
class LabelCharset internal constructor(private val codepage: Int) {

    /**
     * Decodes `bytes[0..size)` to a String.
     */
    fun decode(bytes: ByteArray, size: Int = bytes.size): String {
        return when (codepage) {
            CODEPAGE_UTF8 -> decodeUtf8(bytes, size)
            CODEPAGE_CP1252 -> decodeCp1252(bytes, size)
            else -> decodeLatin1(bytes, size)
        }
    }

    /**
     * Windows-1252 / "Western European". Same as Latin-1 except for the
     * 32 bytes 0x80..0x9F, which Latin-1 leaves as control codes but
     * cp1252 maps to printable glyphs (€ ‚ ƒ „ … † ‡ ˆ ‰ Š ‹ Œ Ž ' ' "
     * " • – — ˜ ™ š › œ ž Ÿ). mkgmap-built IMGs commonly use cp1252 for
     * Western European place names — without this table, en-dashes and
     * Œ/œ ligatures decoded silently to nothing.
     */
    private fun decodeCp1252(bytes: ByteArray, size: Int): String {
        val sb = StringBuilder(size)
        for (i in 0 until size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x80..0x9F) {
                val mapped = CP1252_HIGH_BYTES[b - 0x80]
                if (mapped != 0) {
                    sb.append(mapped.toChar())
                }
                // Bytes that aren't defined in cp1252 (0x81, 0x8D, 0x8F,
                // 0x90, 0x9D) are silently dropped — same as the JVM's
                // built-in cp1252 decoder.
            } else {
                sb.append(Char(b))
            }
        }
        return sb.toString()
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
         * Codepage numbers we recognise explicitly. Anything else
         * falls back to Latin-1 decoding (see [decode]); the
         * codepages listed below are the only ones where [decode]
         * produces a glyph-correct result.
         *
         * SHIFT_JIS (932) and BIG5 (950) would belong here, through
         * `java.nio.charset`. IMGs that ship those codepages
         * decode as Latin-1 today — names will be mojibake. Add them
         * when a real-world fixture appears.
         */
        const val CODEPAGE_LATIN1: Int = 0
        const val CODEPAGE_CP850: Int = 850
        const val CODEPAGE_CP1252: Int = 1252
        const val CODEPAGE_UTF8: Int = 65001

        /**
         * Returns a [LabelCharset] for the given codepage. Caches one
         * instance per codepage: `LabelCharset.of(1252)` is a single
         * allocation reused for the lifetime of the process, instead
         * of one allocation per [LblSubFile.parseHeader] call.
         */
        fun of(codepage: Int): LabelCharset {
            return cache.getOrPut(codepage) { LabelCharset(codepage) }
        }

        private val cache: MutableMap<Int, LabelCharset> = HashMap()

        /**
         * cp1252 mapping for the 0x80..0x9F byte range. 0 means "no
         * defined character" (cp1252 leaves five slots in this range
         * undefined: 0x81, 0x8D, 0x8F, 0x90, 0x9D).
         */
        private val CP1252_HIGH_BYTES: IntArray = intArrayOf(
            0x20AC, 0x0000, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
            0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x0000, 0x017D, 0x0000,
            0x0000, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
            0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x0000, 0x017E, 0x0178,
        )
    }
}
