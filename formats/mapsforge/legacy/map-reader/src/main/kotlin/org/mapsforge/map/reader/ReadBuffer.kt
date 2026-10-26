/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015-2020 devemux86
 * Copyright 2016 bvgastel
 * Copyright 2017 linuskr
 * Copyright 2017 Gustl22
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.reader

import org.mapsforge.core.model.Tag
import org.mapsforge.core.util.Parameters
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads from a file channel into a buffer and decodes the data; all numbers are big-endian.
 */
class ReadBuffer internal constructor(private val inputChannel: FileChannel) {

    private var bufferData: ByteArray? = null

    internal var bufferPosition = 0

    private var bufferWrapper: ByteBuffer? = null

    private val tagIds = ArrayList<Int>()

    internal val bufferSize: Int
        get() = data().size

    fun readByte(): Byte {
        return data()[bufferPosition++]
    }

    fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readInt())
    }

    /**
     * Reads `length` bytes from the channel's current position into the buffer, which grows when
     * needed, and resets the buffer position; false when the read is short or too large.
     */
    @Throws(IOException::class)
    fun readFromFile(length: Int): Boolean {
        if (!ensureCapacity(length)) {
            return false
        }
        bufferPosition = 0
        val wrapper = checkNotNull(bufferWrapper)
        wrapper.clear()
        return inputChannel.read(wrapper) == length
    }

    /**
     * Reads `length` bytes from `offset` in the file into the buffer, as [readFromFile].
     */
    @Throws(IOException::class)
    fun readFromFile(offset: Long, length: Int): Boolean {
        if (!ensureCapacity(length)) {
            return false
        }
        bufferPosition = 0
        val wrapper = checkNotNull(bufferWrapper)
        wrapper.clear()
        synchronized(inputChannel) {
            inputChannel.position(offset)
            return inputChannel.read(wrapper) == length
        }
    }

    fun readInt(): Int {
        bufferPosition += 4
        return Deserializer.getInt(data(), bufferPosition - 4)
    }

    fun readLong(): Long {
        bufferPosition += 8
        return Deserializer.getLong(data(), bufferPosition - 8)
    }

    /**
     * Two bytes as a signed int.
     */
    fun readShort(): Int {
        bufferPosition += 2
        return Deserializer.getShort(data(), bufferPosition - 2)
    }

    /**
     * A variable-byte signed int: each byte's first bit is the continuation flag, the last byte's
     * second bit the sign; six (last byte) or seven (other bytes) data bits.
     */
    fun readSignedInt(): Int {
        val data = data()
        var variableByteDecode = 0
        var variableByteShift = 0

        // check if the continuation bit is set
        while (data[bufferPosition].toInt() and 0x80 != 0) {
            variableByteDecode = variableByteDecode or ((data[bufferPosition++].toInt() and 0x7f) shl variableByteShift)
            variableByteShift += 7
        }

        // read the six data bits from the last byte
        if (data[bufferPosition].toInt() and 0x40 != 0) {
            // negative
            return -(variableByteDecode or ((data[bufferPosition++].toInt() and 0x3f) shl variableByteShift))
        }
        // positive
        return variableByteDecode or ((data[bufferPosition++].toInt() and 0x3f) shl variableByteShift)
    }

    /**
     * The tags of `numberOfTags` tag IDs, decoding `%b`, `%i`, `%f`, `%h` and `%s` values from the
     * buffer; null on an invalid ID.
     */
    internal fun readTags(tagsArray: Array<Tag>, numberOfTags: Byte): MutableList<Tag>? {
        tagIds.clear()

        val maxTag = tagsArray.size

        // as the Java byte loop: a negative count wraps to 1..255
        repeat(numberOfTags.toInt() and 0xff) {
            val tagId = readUnsignedInt()
            if (tagId < 0
                || tagId >= maxTag
            ) {
                LOGGER.warning("invalid tag ID: $tagId")
                return null
            }
            tagIds.add(tagId)
        }

        val tags = ArrayList<Tag>()
        for (tagId in tagIds) {
            var tag = tagsArray[tagId]
            // Decode variable values of tags
            if (tag.value.length == 2
                && tag.value[0] == '%'
            ) {
                var value: String? = tag.value
                when (tag.value[1]) {
                    'b' -> value = readByte().toString()
                    'i' -> value = if (tag.key.contains(":colour")) {
                        "#" + Integer.toHexString(readInt())
                    } else {
                        readInt().toString()
                    }
                    'f' -> value = readFloat().toString()
                    'h' -> value = readShort().toString()
                    's' -> value = readUTF8EncodedString()
                }
                tag = Tag(tag.key, value)
            }
            tags.add(tag)
        }

        return tags
    }

    /**
     * A variable-byte unsigned int: each byte's first bit is the continuation flag, the other seven
     * are data.
     */
    fun readUnsignedInt(): Int {
        val data = data()
        var variableByteDecode = 0
        var variableByteShift = 0

        // check if the continuation bit is set
        while (data[bufferPosition].toInt() and 0x80 != 0) {
            variableByteDecode = variableByteDecode or ((data[bufferPosition++].toInt() and 0x7f) shl variableByteShift)
            variableByteShift += 7
        }

        // read the seven data bits from the last byte
        return variableByteDecode or (data[bufferPosition++].toInt() shl variableByteShift)
    }

    /**
     * A string whose byte length precedes it as a variable-byte unsigned int; null when invalid.
     */
    fun readUTF8EncodedString(): String? {
        return readUTF8EncodedString(readUnsignedInt())
    }

    /**
     * The next `stringLength` bytes decoded as UTF-8; null when the length is invalid.
     */
    fun readUTF8EncodedString(stringLength: Int): String? {
        val data = data()
        if (stringLength > 0
            && bufferPosition + stringLength <= data.size
        ) {
            bufferPosition += stringLength
            return String(data, bufferPosition - stringLength, stringLength, Charsets.UTF_8)
        }
        LOGGER.warning("invalid string length: $stringLength")
        return null
    }

    /**
     * Skips `bytes` bytes in the buffer.
     */
    internal fun skipBytes(bytes: Int) {
        bufferPosition += bytes
    }

    // null until the first read; reading before it is a caller bug
    private fun data(): ByteArray {
        return bufferData ?: throw NullPointerException("ReadBuffer, nothing read yet")
    }

    // a larger buffer when needed; false when the length is too large or the allocation fails
    private fun ensureCapacity(length: Int): Boolean {
        val data = bufferData
        if (data != null
            && data.size >= length
        ) {
            return true
        }
        // ensure that the read buffer is not too large
        if (length > Parameters.MAXIMUM_BUFFER_SIZE) {
            LOGGER.warning("invalid read length: $length")
            return false
        }
        try {
            val grown = ByteArray(length)
            bufferData = grown
            bufferWrapper = ByteBuffer.wrap(grown, 0, length)
        } catch (t: Throwable) {
            LOGGER.log(Level.SEVERE, t.toString(), t)
            return false
        }
        return true
    }

    private companion object {

        val LOGGER: Logger = Logger.getLogger(ReadBuffer::class.java.name)
    }
}
