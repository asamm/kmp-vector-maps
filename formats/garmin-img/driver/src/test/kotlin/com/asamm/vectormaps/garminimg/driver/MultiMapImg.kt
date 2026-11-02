/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Writes a multi-map IMG, as a gmapsupp holds one map per tile: the maps of `sources` under the
 * names `00000001`, `00000002`, … and the first source's TYP for all of them.
 */
internal fun writeMultiMapImg(target: File, sources: List<File>) {
    val subFiles = ArrayList<Triple<String, String, ByteArray>>()
    for ((index, source) in sources.withIndex()) {
        val name = "%08d".format(Locale.ROOT, index + 1)
        for (type in if (index == 0) MAP_TYPES + "TYP" else MAP_TYPES) {
            val bytes = readSubFile(source, type) ?: continue
            subFiles += Triple(if (type == "TYP") "STYLES" else name, type, bytes)
        }
    }
    val fatStart = HEADER_SIZE.toLong()
    // the FAT header block, then one 512-byte entry per sub-file
    val fatEnd = fatStart + FAT_BLOCK * (1 + subFiles.size)
    var block = ((fatEnd + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
    val out = ByteArray((block.toLong() * BLOCK_SIZE).toInt())
    val header = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    "DSKIMG\u0000".toByteArray().copyInto(out, 0x10)
    "MULTI-MAP TEST".padEnd(20).toByteArray().copyInto(out, 0x49)
    // block size 2^(9 + 2) = 2048
    out[0x61] = 9
    out[0x62] = 2
    header.putShort(0x1FE, 0xAA55.toShort())
    out[fatStart.toInt()] = 1
    header.putInt(fatStart.toInt() + 0x0C, fatEnd.toInt())
    val data = ByteArrayOutputStream()
    for ((i, subFile) in subFiles.withIndex()) {
        val (name, type, bytes) = subFile
        val entry = (fatStart + FAT_BLOCK * (i + 1)).toInt()
        out[entry] = 1
        name.padEnd(8).toByteArray().copyInto(out, entry + 1)
        type.toByteArray().copyInto(out, entry + 9)
        header.putInt(entry + 12, bytes.size)
        header.putShort(entry + 16, 0)
        header.putShort(entry + 0x20, block.toShort())
        val blocks = (bytes.size + BLOCK_SIZE - 1) / BLOCK_SIZE
        data.write(bytes)
        data.write(ByteArray(blocks * BLOCK_SIZE - bytes.size))
        block += blocks
    }
    target.outputStream().use { stream ->
        stream.write(out)
        data.writeTo(stream)
    }
}

private fun readSubFile(img: File, type: String): ByteArray? {
    FileImgRandomAccessReader(img).use { reader ->
        val archive = ImgArchive.open(reader)
        val entry = archive.findFirst(type) ?: return null
        val stream = archive.openSubFile(entry, reader)
        val bytes = ByteArray(stream.size.toInt())
        stream.seek(0L)
        stream.readBlock(bytes)
        return bytes
    }
}

private val MAP_TYPES = listOf("TRE", "RGN", "LBL", "NET")

private const val HEADER_SIZE = 0x200

private const val FAT_BLOCK = 512

private const val BLOCK_SIZE = 2048
