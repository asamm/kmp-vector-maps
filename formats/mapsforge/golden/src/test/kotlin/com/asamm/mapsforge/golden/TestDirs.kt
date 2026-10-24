/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File

/**
 * Directories the golden test task passes in as system properties.
 */
object TestDirs {

    val fixtures: File by lazy {
        dirProperty("golden.fixturesDir")
    }

    val expected: File by lazy {
        dirProperty("golden.expectedDir")
    }

    val actual: File by lazy {
        dirProperty("golden.actualDir")
    }

    val diff: File by lazy {
        dirProperty("golden.diffDir")
    }

    private fun dirProperty(key: String): File {
        val path = requireNotNull(System.getProperty(key)) {
            "dirProperty($key), not set; run through Gradle: ./gradlew :formats:mapsforge:golden:test"
        }
        return File(path)
    }
}
