/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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

/**
 * Converts big-endian bytes of a byte array to numbers.
 */
internal object Deserializer {

    /**
     * Five bytes as an unsigned long.
     */
    fun getFiveBytesLong(buffer: ByteArray, offset: Int): Long {
        return (buffer[offset].toLong() and 0xffL) shl 32 or
            ((buffer[offset + 1].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 3].toLong() and 0xffL) shl 8) or
            (buffer[offset + 4].toLong() and 0xffL)
    }

    /**
     * Four bytes as a signed int.
     */
    fun getInt(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() shl 24 or
            ((buffer[offset + 1].toInt() and 0xff) shl 16) or
            ((buffer[offset + 2].toInt() and 0xff) shl 8) or
            (buffer[offset + 3].toInt() and 0xff)
    }

    /**
     * Eight bytes as a signed long.
     */
    fun getLong(buffer: ByteArray, offset: Int): Long {
        return (buffer[offset].toLong() and 0xffL) shl 56 or
            ((buffer[offset + 1].toLong() and 0xffL) shl 48) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 40) or
            ((buffer[offset + 3].toLong() and 0xffL) shl 32) or
            ((buffer[offset + 4].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 5].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 6].toLong() and 0xffL) shl 8) or
            (buffer[offset + 7].toLong() and 0xffL)
    }

    /**
     * Two bytes as a signed int.
     */
    fun getShort(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() shl 8 or (buffer[offset + 1].toInt() and 0xff)
    }
}
