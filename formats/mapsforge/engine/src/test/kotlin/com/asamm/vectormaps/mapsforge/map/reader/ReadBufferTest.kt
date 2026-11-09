/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge.map.reader

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadBufferTest {

    private val temp = Files.createTempDirectory("read-buffer-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun positionalReadLeavesTheChannelPosition() {
        val bytes = ByteArray(64) { it.toByte() }
        val file = File(temp, "data.bin").apply { writeBytes(bytes) }
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            channel.position(5)
            val buffer = ReadBuffer(channel)
            assertTrue(buffer.readFromFile(offset = 16, length = 8))
            assertEquals(5, channel.position())
            val read = ByteArray(8) { buffer.readByte() }
            assertContentEquals(bytes.copyOfRange(16, 24), read)
        }
    }

    @Test
    fun overflowingStringLengthIsInvalid() {
        val file = File(temp, "data.bin").apply { writeBytes(ByteArray(16)) }
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ReadBuffer(raf.channel)
            assertTrue(buffer.readFromFile(offset = 0, length = 16))
            buffer.bufferPosition = 8
            // position plus length wraps below the buffer size
            assertNull(buffer.readUTF8EncodedString(Int.MAX_VALUE - 4))
        }
    }

    @Test
    fun smallerReadIsBoundedByItsOwnLength() {
        val file = File(temp, "data.bin").apply { writeBytes(ByteArray(64) { 'a'.code.toByte() }) }
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ReadBuffer(raf.channel)
            assertTrue(buffer.readFromFile(offset = 0, length = 64))
            assertTrue(buffer.readFromFile(offset = 0, length = 16))
            assertEquals(16, buffer.bufferSize)
            // the reused array still holds 64 bytes; a string past the 16 read is invalid
            buffer.bufferPosition = 8
            assertNull(buffer.readUTF8EncodedString(16))
        }
    }
}
