/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import java.io.File

/**
 * Starts the viewer where the last run ended; the first run opens the map and style named by the
 * `viewer.mapFile` and `viewer.themeFile` system properties, which the Gradle run task sets.
 */
fun main() {
    val sessionFile = Session.userFile
    val session = Session.read(sessionFile)
    val formats = MapFormats(listOf(MapsforgeDriver(AwtGraphicFactory.INSTANCE)))
    val state = ViewerState(
        formats = formats,
        session = session,
        capturesDir = File(System.getProperty("viewer.capturesDir") ?: "captures"),
    )
    state.openMap(fileOr(session.mapPath, "viewer.mapFile"))
    state.openStyle(fileOr(session.stylePath, "viewer.themeFile"))
    application {
        val saved = session.window
        val window = rememberWindowState(
            size = if (saved == null) DpSize(1280.dp, 900.dp) else DpSize(saved.width.dp, saved.height.dp),
            position = if (saved == null) WindowPosition.PlatformDefault else WindowPosition(saved.x.dp, saved.y.dp),
        )
        Window(
            title = "kmp-vector-maps viewer",
            state = window,
            onCloseRequest = {
                val position = window.position
                val bounds = if (position.isSpecified) {
                    Session.Window(
                        x = position.x.value.toInt(),
                        y = position.y.value.toInt(),
                        width = window.size.width.value.toInt(),
                        height = window.size.height.value.toInt(),
                    )
                } else {
                    null
                }
                try {
                    state.session().copy(window = bounds).write(sessionFile)
                } catch (e: Exception) {
                    System.err.println("Session not saved: $e")
                }
                try {
                    state.close()
                } finally {
                    exitApplication()
                }
            },
        ) {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                ViewerApp(state)
            }
        }
    }
}

/**
 * The file at `path` when it still exists, else the one the system property `property` names.
 */
private fun fileOr(path: String?, property: String): File {
    val previous = path?.let(::File)
    if (previous != null
        && previous.isFile
    ) {
        return previous
    }
    return File(System.getProperty(property))
}
