/*
 * kmp-garmin-img — Viewer composables.
 */
package com.asamm.garminimg.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Three-row layout: toolbar at top, map area in the middle (filling
 * available space), status bar at the bottom.
 */
@Composable
fun ViewerApp(state: ViewerState) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(state)
            HorizontalDivider()
            MapPanel(state, modifier = Modifier.weight(1f).fillMaxWidth())
            HorizontalDivider()
            StatusBar(state)
        }
    }
}

@Composable
private fun Toolbar(state: ViewerState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { pickAndLoad(state) }) {
            Text("Open IMG…")
        }
        Button(onClick = { state.reload() }, enabled = state.loadedFile != null) {
            Text("Reload")
        }
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.antiAliased,
                onCheckedChange = {
                    state.antiAliased = it
                    state.reload()
                },
            )
            Text("Anti-alias", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun MapPanel(state: ViewerState, modifier: Modifier = Modifier) {
    val img = state.rendered
    Box(
        modifier = modifier.background(ComposeColor(0xFFE0E0E0)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(
                bitmap = img.toComposeImageBitmap(),
                contentDescription = "rendered map",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "No map loaded — click \"Open IMG…\" above.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBar(state: ViewerState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Opens the platform native file dialog, restricted to *.img files.
 * Loads the chosen file via [ViewerState.loadAndRender].
 */
private fun pickAndLoad(state: ViewerState) {
    // AWT FileDialog is the simplest cross-platform native picker; Compose
    // Desktop has no built-in file picker yet. Filter is best-effort —
    // on Windows it's a hint, on macOS it's enforced.
    val dialog = FileDialog(null as Frame?, "Open Garmin IMG", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".img", ignoreCase = true) }
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    if (dir != null && file != null) {
        state.loadAndRender(File(dir, file))
    }
}
