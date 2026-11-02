/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg

import java.io.File

/**
 * A file under `dev/fixtures`, whose path the build passes as the `fixturesDir` system property.
 */
internal fun fixtureFile(path: String): File {
    val file = File(System.getProperty("fixturesDir"), path)
    check(file.isFile) {
        "fixtureFile(), missing $file"
    }
    return file
}

internal fun fixtureBytes(path: String): ByteArray {
    return fixtureFile(path).readBytes()
}
