/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

/**
 * Desktop viewer entry; the start map and theme come from the `viewer.*` system properties set by
 * the Gradle run task.
 */
fun main() {
    val mapFile = File(System.getProperty("viewer.mapFile"))
    val themeFile = File(System.getProperty("viewer.themeFile"))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "kmp-vector-maps viewer",
            state = rememberWindowState(width = 1280.dp, height = 900.dp),
        ) {
            MaterialTheme {
                ViewerApp(initialMap = mapFile, initialTheme = themeFile)
            }
        }
    }
}
