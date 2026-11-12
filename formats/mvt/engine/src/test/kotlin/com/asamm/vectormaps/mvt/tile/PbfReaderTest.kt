/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.tile

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PbfReaderTest {

    @Test
    fun rejectsATruncatedPackedField() {
        // length 2, then a varint whose continuation runs past the field
        val bytes = byteArrayOf(2, 0x81.toByte(), 0x81.toByte(), 0x01)

        assertFailsWith<IllegalArgumentException> {
            PbfReader(bytes, 0, bytes.size).readPackedVarints()
        }
    }

    @Test
    fun rejectsALengthBeyondTheBuffer() {
        // length 0x7fffffff
        val bytes = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x07, 0)

        assertFailsWith<IllegalArgumentException> {
            PbfReader(bytes, 0, bytes.size).readString()
        }
    }
}
