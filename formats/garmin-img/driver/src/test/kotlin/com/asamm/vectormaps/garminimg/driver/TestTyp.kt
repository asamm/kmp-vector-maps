/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a TYP file of solid records as mkgmap lays them out (`TypPolygon`, `TypLine`,
 * `TypPoint`, `DrawOrder`), each with its day colours and optional night colours, label font
 * style and font colours. Colours are `0xRRGGBB`.
 */
internal class TestTyp {

    private val polygons = ArrayList<Pair<Int, ByteArray>>()
    private val lines = ArrayList<Pair<Int, ByteArray>>()
    private val points = ArrayList<Pair<Int, ByteArray>>()
    private val drawOrder = ByteArrayOutputStream()

    fun polygon(
        type: Int,
        subType: Int = 0,
        day: Pair<Int, Int>,
        night: Pair<Int, Int>? = null,
        font: Font? = null,
    ) = apply {
        val record = ByteArrayOutputStream()
        // scheme: 0x01 night colours, 0x20 extended font; two solid colours each
        record.write((if (night != null) 0x01 else 0) or (if (font != null) 0x20 else 0))
        record.bgr(day.first)
        record.bgr(day.second)
        night?.let {
            record.bgr(it.first)
            record.bgr(it.second)
        }
        font?.write(record)
        polygons += key(type, subType) to record.toByteArray()
    }

    fun line(
        type: Int,
        subType: Int = 0,
        day: Pair<Int, Int>,
        night: Pair<Int, Int>? = null,
        font: Font? = null,
    ) = apply {
        val record = ByteArrayOutputStream()
        // scheme in the low 3 bits, no bitmap; flags 0x04 extended font
        record.write(if (night != null) 0x01 else 0)
        record.write(if (font != null) 0x04 else 0)
        record.bgr(day.first)
        record.bgr(day.second)
        night?.let {
            record.bgr(it.first)
            record.bgr(it.second)
        }
        // line width, then width with the border
        record.write(2)
        record.write(4)
        font?.write(record)
        lines += key(type, subType) to record.toByteArray()
    }

    /**
     * A 1×1 icon of `day` colour, and of `night` colour in the night image.
     */
    fun point(type: Int, subType: Int = 0, day: Int, night: Int? = null, font: Font? = null) = apply {
        val record = ByteArrayOutputStream()
        // flags: 0x01 bitmap, 0x02 night image, 0x08 extended font
        record.write(0x01 or (if (night != null) 0x02 else 0) or (if (font != null) 0x08 else 0))
        record.write(1)
        record.write(1)
        record.icon(day)
        night?.let { record.icon(it) }
        font?.write(record)
        points += key(type, subType) to record.toByteArray()
    }

    /**
     * Appends a DRAWORDER entry: a plain type without a mask, an extended one with its subtypes.
     */
    fun drawOrder(type: Int, subTypes: List<Int> = emptyList()) = apply {
        drawOrder.write(type and 0xFF)
        drawOrder.le(subTypes.fold(0) { mask, subType -> mask or (1 shl subType) }, 4)
    }

    fun build(): ByteArray {
        val body = ByteArrayOutputStream()
        fun section(records: List<Pair<Int, ByteArray>>): Pair<Section, Section> {
            val dataStart = HEADER_LENGTH + body.size()
            val offsets = ArrayList<Int>()
            for ((_, bytes) in records) {
                offsets += body.size() + HEADER_LENGTH - dataStart
                body.write(bytes)
            }
            val data = Section(dataStart, body.size() + HEADER_LENGTH - dataStart)
            val indexStart = HEADER_LENGTH + body.size()
            for ((i, record) in records.withIndex()) {
                body.le(record.first, 2)
                body.le(offsets[i], 2)
            }
            return data to Section(indexStart, records.size * INDEX_ITEM)
        }
        val (pointData, pointIndex) = section(points)
        val (lineData, lineIndex) = section(lines)
        val (polygonData, polygonIndex) = section(polygons)
        val stacking = Section(HEADER_LENGTH + body.size(), drawOrder.size())
        body.write(drawOrder.toByteArray())

        val header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        header.put(0, HEADER_LENGTH.toByte())
        "GARMIN TYP".toByteArray().copyInto(header.array(), 2)
        header.putShort(0x15, 1252.toShort())
        for ((i, data) in listOf(pointData, lineData, polygonData).withIndex()) {
            header.putInt(0x17 + i * 8, data.offset)
            header.putInt(0x17 + i * 8 + 4, data.length)
        }
        for ((i, index) in listOf(pointIndex, lineIndex, polygonIndex).withIndex()) {
            header.putInt(0x33 + i * 10, index.offset)
            header.putShort(0x33 + i * 10 + 4, INDEX_ITEM.toShort())
            header.putInt(0x33 + i * 10 + 6, index.length)
        }
        header.putInt(0x51, stacking.offset)
        header.putShort(0x55, DRAW_ORDER_ITEM.toShort())
        header.putInt(0x57, stacking.length)
        return header.array() + body.toByteArray()
    }

    /**
     * mkgmap's `FontStyle` (1 no label, 2 small, 3 normal, 4 large) and font colours.
     */
    class Font(val style: Int = 0, val day: Int? = null, val night: Int? = null) {

        fun write(out: ByteArrayOutputStream) {
            out.write(style or (if (day != null) 0x08 else 0) or (if (night != null) 0x10 else 0))
            day?.let { out.bgr(it) }
            night?.let { out.bgr(it) }
        }
    }

    private class Section(val offset: Int, val length: Int)

    private companion object {

        const val HEADER_LENGTH = 0x5B
        const val INDEX_ITEM = 4
        const val DRAW_ORDER_ITEM = 5

        fun key(type: Int, subType: Int): Int {
            return (type shl 5) or (subType and 0x1F)
        }

        fun ByteArrayOutputStream.bgr(rgb: Int) {
            write(rgb and 0xFF)
            write((rgb shr 8) and 0xFF)
            write((rgb shr 16) and 0xFF)
        }

        fun ByteArrayOutputStream.le(value: Int, bytes: Int) {
            for (i in 0 until bytes) {
                write((value shr (8 * i)) and 0xFF)
            }
        }

        // one palette colour plus the implicit transparent one: 1 bit per pixel, a 1-byte row
        fun ByteArrayOutputStream.icon(rgb: Int) {
            write(1)
            write(0x10)
            bgr(rgb)
            write(0)
        }
    }
}
