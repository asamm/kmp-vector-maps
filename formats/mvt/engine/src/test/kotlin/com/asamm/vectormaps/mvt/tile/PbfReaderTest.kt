/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.tile

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun skipsALengthDelimitedField() {
        // field 1 of 2 bytes, then field 2 = varint 7
        val bytes = byteArrayOf(0x0a, 2, 0x55, 0x55, 0x10, 7)
        val reader = PbfReader(bytes, 0, bytes.size)

        reader.skip(reader.readTag())

        assertEquals(0x10, reader.readTag())
        assertEquals(7L, reader.readVarint())
    }
}
