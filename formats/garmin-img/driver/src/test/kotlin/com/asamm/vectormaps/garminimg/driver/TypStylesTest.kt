/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.garminimg.model.MapObject
import com.asamm.vectormaps.garminimg.parser.TypBodyParser
import com.asamm.vectormaps.garminimg.parser.TypStyleTable
import com.asamm.vectormaps.garminimg.parser.TypSubFile
import com.asamm.vectormaps.garminimg.render.StyleResolver
import com.asamm.vectormaps.garminimg.style.Color
import com.asamm.vectormaps.garminimg.style.Style
import com.asamm.vectormaps.garminimg.utils.io.ByteArrayImgRandomAccessReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * TYP records parsed from a [TestTyp]: night colours, DRAWORDER priorities and label fonts.
 */
class TypStylesTest {

    @Test
    fun nightColoursReplaceTheDayOnesRecordByRecord() {
        val table = parse(
            TestTyp()
                .polygon(0x03, day = 0x112233 to 0x445566, night = 0x778899 to 0xAABBCC)
                .polygon(0x04, day = 0x101010 to 0x202020)
                .line(0x01, day = 0x010203 to 0x040506, night = 0x0A0B0C to 0x0D0E0F)
                .point(0x2C, day = 0xFF0000, night = 0x0000FF),
        )

        assertTrue(table.hasNight)
        assertEquals(colour(0x112233), table.lookupPolygon(0x03, 0)?.fillColor)
        assertEquals(colour(0x778899), table.night.lookupPolygon(0x03, 0)?.fillColor)
        assertEquals(colour(0xAABBCC), table.night.lookupPolygon(0x03, 0)?.strokeColor)
        // no night colours: the day record at night too
        assertSame(table.lookupPolygon(0x04, 0), table.night.lookupPolygon(0x04, 0))
        assertEquals(colour(0x0A0B0C), table.night.lookupLine(0x01, 0)?.strokeColor)
        assertEquals(colour(0x0D0E0F), table.night.lookupLine(0x01, 0)?.borderColor)
        assertEquals(0xFF.toByte(), table.lookupPoint(0x2C, 0)?.icon?.rgba?.get(0))
        assertEquals(0xFF.toByte(), table.night.lookupPoint(0x2C, 0)?.icon?.rgba?.get(2))
    }

    @Test
    fun nightLabelColourAloneMakesANightStyle() {
        val table = parse(TestTyp().line(0x01, day = 0x010101 to 0x020202, font = TestTyp.Font(day = 0x111111, night = 0xEEEEEE)))

        assertTrue(table.hasNight)
        assertEquals(colour(0x111111), table.lookupLine(0x01, 0)?.font?.dayColour)
        assertEquals(colour(0xEEEEEE), table.night.lookupLine(0x01, 0)?.font?.dayColour)
    }

    @Test
    fun typWithoutNightColoursIsItsOwnNight() {
        val table = parse(TestTyp().polygon(0x03, day = 0x112233 to 0x445566).point(0x2C, day = 0xFF0000))

        assertFalse(table.hasNight)
        assertSame(table, table.night)
    }

    @Test
    fun drawOrderKeepsPlainAndExtendedTypesApart() {
        val table = parse(
            TestTyp()
                .polygon(0x10, day = 0x111111 to 0x222222)
                .polygon(0x110, subType = 3, day = 0x333333 to 0x444444)
                .drawOrder(0x10)
                .drawOrder(0x110, subTypes = listOf(3)),
        )

        assertEquals(0, table.lookupPolygon(0x10, 0)?.drawPriority)
        // a plain entry must not match an extended type of the same low byte
        assertEquals(1, table.lookupPolygon(0x110, 3)?.drawPriority)
    }

    @Test
    fun fontStylesSizeAndHideLabels() {
        val resolver = StyleResolver.typAware(
            parse(
                TestTyp()
                    .line(0x01, day = 0x010101 to 0x020202, font = TestTyp.Font(style = 1))
                    .polygon(0x03, day = 0x112233 to 0x445566, font = TestTyp.Font(style = 2))
                    .point(0x2C, day = 0xFF0000, font = TestTyp.Font(style = 4)),
            ),
        )

        val line = assertIs<Style.LineStyle>(resolver.resolve(shape(type = 0x01, closed = false)))
        assertNull(line.labelColor)
        val polygon = assertIs<Style.PolygonStyle>(resolver.resolve(shape(type = 0x03, closed = true)))
        assertTrue(polygon.fontScale < 1f)
        val point = assertIs<Style.PointStyle>(
            resolver.resolve(MapObject.Point(type = 0x2C, subType = 0, longitude = 0, latitude = 0, label = "x", indexed = false)),
        )
        assertTrue(point.fontScale > 1f)
    }

    private fun parse(typ: TestTyp): TypStyleTable {
        val reader = ByteArrayImgRandomAccessReader(typ.build())
        return TypBodyParser.parse(reader, TypSubFile.parseHeader(reader))
    }

    private fun shape(type: Int, closed: Boolean): MapObject.PolyShape {
        return MapObject.PolyShape(
            type = type,
            subType = 0,
            longitudes = intArrayOf(0, 1),
            latitudes = intArrayOf(0, 1),
            label = "x",
            closed = closed,
        )
    }

    private fun colour(rgb: Int): Color {
        return Color.rgb(rgb shr 16, rgb shr 8, rgb)
    }
}
