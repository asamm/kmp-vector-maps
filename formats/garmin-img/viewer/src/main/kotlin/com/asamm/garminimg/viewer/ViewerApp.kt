/*
 * kmp-garmin-img — Viewer composables.
 */
package com.asamm.garminimg.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.pow

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
        Button(onClick = { state.homeView() }, enabled = state.loadedFile != null) {
            Text("Home")
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MapPanel(state: ViewerState, modifier: Modifier = Modifier) {
    val img = state.rendered
    // Live drag offset — translates the displayed image during a drag for
    // instant feedback. On drag end we hand the accumulated delta to
    // ViewerState.panBy, which re-renders at the new viewport; the offset
    // resets back to zero. Sub-render-cycle interactivity without paying
    // the render cost on every mouse move.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .background(ComposeColor(0xFFE0E0E0))
            .onSizeChanged { state.setPanelSize(it.width, it.height) }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val scrollY = change.scrollDelta.y
                if (scrollY == 0f) {
                    return@onPointerEvent
                }
                // Wheel up (negative scrollY on most platforms) = zoom in.
                val factor = ZOOM_PER_TICK.pow(-scrollY.toDouble())
                state.zoomAt(factor, change.position.x, change.position.y)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragOffset = Offset.Zero },
                    onDrag = { change, delta ->
                        change.consume()
                        dragOffset += delta
                    },
                    onDragEnd = {
                        if (dragOffset != Offset.Zero) {
                            state.panBy(dragOffset.x, dragOffset.y)
                        }
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = { dragOffset = Offset.Zero },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(
                bitmap = img.toComposeImageBitmap(),
                contentDescription = "rendered map",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        translationX = dragOffset.x,
                        translationY = dragOffset.y,
                    ),
            )
        } else {
            Text(
                "No map loaded — click \"Open IMG…\" above.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Zoom factor per scroll-wheel tick. 1.2 ≈ 20% zoom per tick — feels
 * responsive without being jumpy.
 */
private const val ZOOM_PER_TICK: Double = 1.2

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
