/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.io

/**
 * In-memory [ImgRandomAccessReader] backed by a [ByteArray].
 *
 * Suitable for unit tests, small fixtures, or assets pre-loaded into RAM; on-disk IMGs use
 * [FileImgRandomAccessReader].
 */
class ByteArrayImgRandomAccessReader(private val data: ByteArray) : ImgRandomAccessReader {

    private var pos: Int = 0

    override val size: Long
        get() = data.size.toLong()

    override fun position(): Long {
        return pos.toLong()
    }

    override fun seek(pos: Long) {
        require(pos in 0L..data.size.toLong()) {
            "seek($pos) out of bounds for size=${data.size}"
        }
        this.pos = pos.toInt()
    }

    override fun readByte(): Int {
        if (pos >= data.size) {
            throw IndexOutOfBoundsException("readByte() at EOF: pos=$pos, size=${data.size}")
        }
        return data[pos++].toInt() and 0xFF
    }

    override fun readBlock(buffer: ByteArray) {
        if (pos + buffer.size > data.size) {
            throw IndexOutOfBoundsException(
                "readBlock(${buffer.size}) past EOF: pos=$pos, size=${data.size}",
            )
        }
        data.copyInto(buffer, destinationOffset = 0, startIndex = pos, endIndex = pos + buffer.size)
        pos += buffer.size
    }
}
