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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
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
        capturesDir = System.getProperty("viewer.capturesDir")?.let(::File)
            ?: File(sessionFile.parentFile, "captures"),
    )
    fileOr(session.mapPath, "viewer.mapFile")?.let(state::openMap)
    fileOr(session.stylePath, "viewer.themeFile")?.let(state::openStyle)
    application {
        val saved = session.window
        val window = rememberWindowState(
            size = if (saved == null) DpSize(1280.dp, 900.dp) else DpSize(saved.width.dp, saved.height.dp),
            position = if (saved != null && onScreen(saved)) {
                WindowPosition(saved.x.dp, saved.y.dp)
            } else {
                WindowPosition.PlatformDefault
            },
        )
        Window(
            title = "kmp-vector-maps viewer",
            state = window,
            onCloseRequest = {
                try {
                    state.session().copy(window = floatingBounds(window) ?: saved).write(sessionFile)
                } catch (e: Exception) {
                    System.err.println("main(), session not saved: $e")
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
 * The window's bounds when it floats; a maximized, full-screen or minimized window reports bounds
 * that would not restore it.
 */
private fun floatingBounds(window: WindowState): Session.Window? {
    val position = window.position
    if (window.placement != WindowPlacement.Floating
        || window.isMinimized
        || !position.isSpecified
    ) {
        return null
    }
    return Session.Window(
        x = position.x.value.toInt(),
        y = position.y.value.toInt(),
        width = window.size.width.value.toInt(),
        height = window.size.height.value.toInt(),
    )
}

// a monitor the window was on may be gone
private fun onScreen(window: Session.Window): Boolean {
    val bounds = Rectangle(window.x, window.y, window.width, window.height)
    return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
        it.defaultConfiguration.bounds.intersects(bounds)
    }
}

/**
 * The file at `path` when it still exists, else the one the system property `property` names, if
 * set; outside the Gradle run task neither may exist.
 */
private fun fileOr(path: String?, property: String): File? {
    val previous = path?.let(::File)
    if (previous != null
        && previous.isFile
    ) {
        return previous
    }
    return System.getProperty(property)?.let(::File)
}
