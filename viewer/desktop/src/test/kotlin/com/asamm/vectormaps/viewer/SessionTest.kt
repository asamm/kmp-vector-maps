/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionTest {

    private val temp = createTempDirectory().toFile()

    private val file = File(temp, "viewer.properties")

    @AfterTest
    fun deleteTemp() {
        temp.deleteRecursively()
    }

    @Test
    fun writtenSessionReadsBack() {
        val session = Session(
            mapPath = """C:\mapy\česko.map""",
            stylePath = "/themes/theme.xml",
            layerId = "car",
            textScale = 1.5f,
            tileSize = 256,
            threads = 2,
            place = Session.Place(lat = 50.087, lon = 14.421, zoom = 13),
            window = Session.Window(x = 10, y = 20, width = 1280, height = 900),
        )

        session.write(file)

        assertEquals(session, Session.read(file))
        assertFalse(File(temp, "viewer.properties.tmp").exists())
    }

    @Test
    fun malformedFileGivesTheDefaults() {
        // a write cut short inside a \uXXXX escape
        file.writeText("""map=C\:\\mapy\\\u00""")

        assertEquals(Session(), Session.read(file))
    }

    @Test
    fun failedWriteLeavesNoTemporaryFile() {
        // a non-empty folder in the file's place makes the final move fail
        File(file, "child").apply { parentFile.mkdirs() }.writeText("x")

        assertFailsWith<IOException> {
            Session(layerId = "car").write(file)
        }
        assertFalse(File(temp, "viewer.properties.tmp").exists())
    }

    @Test
    fun badValuesKeepTheirDefaults() {
        file.writeText("tileSize=0\ntextScale=-1\nplace=50.0,14.0\nwindow=1,2,0,4\nthreads=x\n")

        val session = Session.read(file)

        assertEquals(Session(), session)
        assertNull(session.place)
    }
}
