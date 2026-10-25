/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class MapFormatsTest {

    private val monaco = MapInfo(
        bounds = GeoBounds(minLat = 43.72, minLon = 7.40, maxLat = 43.76, maxLon = 7.44),
        languages = listOf("en"),
    )

    @Test
    fun probePicksFirstDriverWhoseProbeSucceeds() {
        val rejecting = FakeDriver(id = "a", extensions = setOf("map"), info = null)
        val accepting = FakeDriver(id = "b", extensions = setOf("map"), info = monaco)
        val formats = MapFormats(listOf(rejecting, accepting))

        val probed = formats.probe(File("monaco.map"))

        assertSame(accepting, probed?.driver)
        assertEquals(monaco, probed?.info)
        assertEquals(listOf("monaco.map"), rejecting.probed)
    }

    @Test
    fun probeTreatsAThrowingProbeAsFailed() {
        val throwing = FakeDriver(
            id = "a",
            extensions = setOf("map"),
            failure = IllegalArgumentException("bad header"),
        )
        val accepting = FakeDriver(id = "b", extensions = setOf("map"), info = monaco)
        val formats = MapFormats(listOf(throwing, accepting))

        assertSame(accepting, formats.probe(File("monaco.map"))?.driver)
        assertNull(MapFormats(listOf(throwing)).probe(File("monaco.map")))
    }

    @Test
    fun probeSkipsDriversOfOtherExtensions() {
        val garmin = FakeDriver(id = "garmin", extensions = setOf("img"), info = monaco)
        val formats = MapFormats(listOf(garmin))

        assertNull(formats.probe(File("monaco.map")))
        assertEquals(emptyList(), garmin.probed)
    }

    @Test
    fun probeMatchesExtensionIgnoringCase() {
        val driver = FakeDriver(id = "mapsforge", extensions = setOf("map"), info = monaco)

        assertSame(driver, MapFormats(listOf(driver)).probe(File("MONACO.MAP"))?.driver)
    }

    @Test
    fun driverIdsMustBeUnique() {
        assertFailsWith<IllegalArgumentException> {
            MapFormats(listOf(FakeDriver(id = "a"), FakeDriver(id = "a")))
        }
    }

    @Test
    fun driverLooksUpById() {
        val driver = FakeDriver(id = "mapsforge")
        val formats = MapFormats(listOf(FakeDriver(id = "garmin"), driver))

        assertSame(driver, formats.driver("mapsforge"))
        assertNull(formats.driver("mvt"))
    }

    private class FakeDriver(
        override val id: String,
        override val extensions: Set<String> = emptySet(),
        override val styleExtensions: Set<String> = emptySet(),
        private val info: MapInfo? = null,
        private val failure: Exception? = null,
    ) : MapFormatDriver {

        val probed = mutableListOf<String>()

        override fun probe(file: File): MapInfo? {
            probed += file.name
            if (failure != null) {
                throw failure
            }
            return info
        }

        override fun open(files: List<File>): MapDataset {
            throw UnsupportedOperationException()
        }

        override fun loadStyle(file: File): MapStyle {
            throw UnsupportedOperationException()
        }
    }
}
