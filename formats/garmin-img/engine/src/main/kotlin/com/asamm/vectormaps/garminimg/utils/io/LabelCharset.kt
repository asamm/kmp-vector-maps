/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes a byte sequence taken from an IMG label section into a Kotlin [String], using the
 * codepage recorded in the LBL header.
 *
 * Codepages map to `java.nio` charsets as in the legacy parser: 0 and 850 decode as cp1252,
 * 65001 as UTF-8, 932 as MS932, 950 as Big5, anything else as `cp<n>`; an unsupported codepage
 * falls back to Latin-1.
 */
class LabelCharset internal constructor(private val charset: Charset) {

    /**
     * Decodes `bytes[0..size)` to a String.
     */
    fun decode(bytes: ByteArray, size: Int = bytes.size): String {
        return String(bytes, 0, size, charset)
    }

    companion object {

        const val CODEPAGE_LATIN1: Int = 0
        const val CODEPAGE_CP850: Int = 850
        const val CODEPAGE_CP1252: Int = 1252
        const val CODEPAGE_UTF8: Int = 65001

        private val cache = ConcurrentHashMap<Int, LabelCharset>()

        /**
         * Returns the shared [LabelCharset] for the given codepage.
         */
        fun of(codepage: Int): LabelCharset {
            return cache.getOrPut(codepage) { LabelCharset(charsetOf(codepage)) }
        }

        private fun charsetOf(codepage: Int): Charset {
            val name = when (codepage) {
                CODEPAGE_LATIN1, CODEPAGE_CP850 -> "cp1252"
                CODEPAGE_UTF8 -> return Charsets.UTF_8
                932 -> "MS932"
                950 -> "Big5"
                else -> "cp$codepage"
            }
            return try {
                Charset.forName(name)
            } catch (_: Exception) {
                Charsets.ISO_8859_1
            }
        }
    }
}
