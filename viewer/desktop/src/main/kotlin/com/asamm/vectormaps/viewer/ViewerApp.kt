/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.floor
import kotlin.math.hypot

// slower releases stop the map where it is, in px per second
private const val MIN_FLING_SPEED = 400f

/**
 * The viewer window: controls on top, the map, and a status line with the view and timings.
 */
@Composable
fun ViewerApp(state: ViewerState) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            Controls(state)
            MapArea(state, Modifier.weight(1f).fillMaxWidth())
            StatusLine(state)
        }
    }
}

@Composable
private fun Controls(state: ViewerState) {
    val hasMap = state.mapFile != null
    val scope = rememberCoroutineScope()
    val styleExtensions = state.driver?.styleExtensions.orEmpty()
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { chooseFile("Map", state.mapExtensions)?.let(state::openMap) }) {
                Text(state.mapFile?.name ?: "Open map")
            }
            GroupDivider()
            val style = state.styleFile
            if (style != null) {
                Picker(
                    caption = "Style",
                    current = style,
                    choices = siblingStyles(style, styleExtensions),
                    name = { it.nameWithoutExtension },
                    onPick = state::openStyle,
                )
            }
            TextButton(
                onClick = { chooseFile("Style", styleExtensions)?.let(state::openStyle) },
                enabled = styleExtensions.isNotEmpty(),
            ) {
                Text("Style file…")
            }
            val menu = state.style?.menu
            if (menu != null) {
                Picker(
                    caption = "Layer",
                    current = state.layerId ?: menu.defaultLayerId,
                    choices = menu.layers.filter { it.isVisible }.map { it.id },
                    name = { id -> menu.layer(id)?.titles?.get(menu.defaultLanguage) ?: id },
                    onPick = state::selectLayer,
                )
            }
            GroupDivider()
            Picker("Text", state.textScale, listOf(1.0f, 1.5f, 2.0f), { "×$it" }) { state.changeSettings(textScale = it) }
            Picker("Tile", state.tileSize, Session.TILE_SIZES, { "$it px" }) { state.changeSettings(tileSize = it) }
            Picker("Threads", state.threads, listOf(1, 2, 4, 8), { "$it" }) { state.changeSettings(threads = it) }
            GroupDivider()
            TextButton(onClick = state::home, enabled = hasMap) {
                Text("Home")
            }
            TextButton(onClick = state::reload, enabled = hasMap) {
                Text("Reload")
            }
            TextButton(onClick = state::rerender, enabled = hasMap) {
                Text("Re-render")
            }
            TextButton(onClick = { scope.launch { state.capture() } }, enabled = hasMap) {
                Text("Capture")
            }
        }
    }
}

@Composable
private fun GroupDivider() {
    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
}

/**
 * A button showing the current value that opens a menu of the others.
 */
@Composable
private fun <T> Picker(
    caption: String,
    current: T,
    choices: List<T>,
    name: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("$caption ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${name(current)} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(name(choice)) },
                    onClick = {
                        open = false
                        if (choice != current) {
                            onPick(choice)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapArea(state: ViewerState, modifier: Modifier) {
    val engine = state.engine
    val viewport = state.viewport
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val missingTile = MaterialTheme.colorScheme.surfaceContainerHighest
    val scope = rememberCoroutineScope()
    val fling = remember(state) { Fling(scope, state::drag) }
    val velocity = remember { VelocityTracker() }
    val wheel = remember { WheelSteps() }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.resize(it.width, it.height) }
            .onPointerEvent(PointerEventType.Press) { fling.stop() }
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { velocity.resetTracking() },
                    onDragEnd = { fling.start(velocity.calculateVelocity()) },
                ) { change, amount ->
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    state.drag(amount.x, amount.y)
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                // wheel away from the user zooms in
                val steps = -wheel.add(change.scrollDelta.y)
                if (steps != 0) {
                    fling.stop()
                    state.zoom(steps = steps, x = change.position.x, y = change.position.y)
                }
            },
    ) {
        // read while drawing, so a new tile redraws; the lambda itself stays the same
        state.frame
        drawRect(background)
        if (engine == null
            || viewport == null
            || viewport.tileSize != engine.tileSizePx
        ) {
            return@Canvas
        }
        val width = size.width.toInt()
        val height = size.height.toInt()
        val tileSize = viewport.tileSize.toFloat()
        for (tile in viewport.tiles(width, height)) {
            val left = viewport.screenX(tile.x, width)
            val top = viewport.screenY(tile.y, height)
            val image = engine[tile]
            if (image != null) {
                // floor, not truncate, so tiles either side of the screen edge stay one grid
                drawImage(image = image, dstOffset = IntOffset(floor(left).toInt(), floor(top).toInt()))
            } else {
                // a gap between missing tiles shows the grid
                drawRect(missingTile, topLeft = Offset(left + 1f, top + 1f), size = Size(tileSize - 2f, tileSize - 2f))
            }
        }
    }
}

@Composable
private fun StatusLine(state: ViewerState) {
    state.frame
    val viewport = state.viewport
    val engine = state.engine
    val view = listOfNotNull(
        state.driver?.id,
        viewport?.let { "z${it.zoom}  %.5f %.5f".format(it.lat, it.lon) },
        state.setupMs?.let { "setup $it ms" },
    )
    val problem = state.error ?: engine?.error
    val text = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(view.joinToString("   "), style = text)
                engine?.stats?.summary()?.let { Text(it, style = text) }
                state.notice?.let { Text(it, style = text, color = MaterialTheme.colorScheme.primary) }
            }
            if (problem != null) {
                Text(problem, style = text, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Adds up wheel deltas into whole zoom steps: a mouse wheel notch is one, a touchpad sends
 * fractions.
 */
private class WheelSteps {

    private var pending = 0f

    fun add(delta: Float): Int {
        pending += delta
        val steps = pending.toInt()
        pending -= steps
        return steps
    }
}

/**
 * Keeps the map gliding after a drag and slows it down, as a thrown map would.
 */
private class Fling(
    private val scope: CoroutineScope,
    private val move: (dx: Float, dy: Float) -> Unit,
) {

    private var job: Job? = null

    fun start(velocity: Velocity) {
        stop()
        val speed = hypot(velocity.x, velocity.y)
        if (speed < MIN_FLING_SPEED) {
            return
        }
        job = scope.launch {
            var moved = 0f
            AnimationState(initialValue = 0f, initialVelocity = speed).animateDecay(exponentialDecay()) {
                val step = value - moved
                moved = value
                move(step * velocity.x / speed, step * velocity.y / speed)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Other style files of the map's format in the folder of `style`; `.poi.xml` files are LoMaps POI
 * themes, not map styles.
 */
private fun siblingStyles(style: File, extensions: Set<String>): List<File> {
    val folder = style.parentFile ?: return listOf(style)
    return folder.listFiles().orEmpty()
        .filter { it.isFile && it.extension.lowercase() in extensions && !it.name.endsWith(".poi.xml") }
        .sortedBy { it.name }
}

private fun chooseFile(kind: String, extensions: Set<String>): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Open $kind"
    chooser.fileFilter = FileNameExtensionFilter("$kind files", *extensions.toTypedArray())
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
        return null
    }
    return chooser.selectedFile
}
