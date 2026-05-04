/*
 * kmp-garmin-img — Desktop viewer entry point.
 */
package com.asamm.garminimg.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

/**
 * Compose Multiplatform Desktop entry. Run with `./gradlew :viewer:run`.
 */
fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "kmp-garmin-img viewer",
            state = WindowState(width = 1280.dp, height = 800.dp),
        ) {
            val state = remember { ViewerState() }
            MaterialTheme {
                ViewerApp(state)
            }
        }
    }
}
