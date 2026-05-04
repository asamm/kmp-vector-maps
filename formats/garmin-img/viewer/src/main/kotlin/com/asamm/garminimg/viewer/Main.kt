/*
 * kmp-garmin-img — Desktop viewer entry point.
 */
package com.asamm.garminimg.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

/**
 * Compose Multiplatform Desktop entry. Run with `./gradlew :viewer:run`.
 *
 * Restores window dimensions, anti-aliasing toggle, and last-opened IMG
 * file from persistent preferences (see [ViewerPrefs]). Saves changes
 * back as they happen so the viewer comes back exactly where the user
 * left it on next launch.
 */
fun main() {
    val restored = ViewerPrefs.restore()
    application {
        val windowState = WindowState(
            width = restored.windowWidthDp.dp,
            height = restored.windowHeightDp.dp,
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "kmp-garmin-img viewer",
            state = windowState,
        ) {
            val state = remember { ViewerState(initialAntiAliased = restored.antiAliased) }

            // Auto-load the last-opened file once on launch (if it still exists).
            LaunchedEffect(Unit) {
                restored.lastFilePath?.let { path ->
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        state.loadAndRender(file)
                    }
                }
            }

            // Persist window size as the user resizes. distinctUntilChanged
            // collapses the flood of intermediate sizes during a drag.
            LaunchedEffect(windowState) {
                snapshotFlow { windowState.size }
                    .distinctUntilChanged()
                    .collect { size ->
                        ViewerPrefs.saveWindowSize(
                            size.width.value.toInt(),
                            size.height.value.toInt(),
                        )
                    }
            }

            MaterialTheme {
                ViewerApp(state)
            }
        }
    }
}
