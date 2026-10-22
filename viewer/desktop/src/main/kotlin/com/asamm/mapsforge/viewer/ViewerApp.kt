/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.asamm.mapsforge.viewer.common.Viewport
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val TEXT_SCALES = listOf(1.0f, 1.5f, 2.0f)
private val TILE_SIZES = listOf(256, 512)
private val THREAD_COUNTS = listOf(1, 2, 4, 8)

/**
 * Viewer screen: toolbar, map canvas and status bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewerApp(initialMap: File, initialTheme: File) {
    var mapFile by remember { mutableStateOf(initialMap) }
    var themeFile by remember { mutableStateOf(initialTheme) }
    var textScale by remember { mutableStateOf(1.0f) }
    var tileSize by remember { mutableStateOf(256) }
    var threads by remember { mutableStateOf(4) }

    val engine = remember(mapFile, themeFile, textScale, tileSize, threads) {
        TileEngine(
            mapFile = mapFile,
            themeFile = themeFile,
            textScale = textScale,
            tileSize = tileSize,
            threads = threads,
        )
    }
    DisposableEffect(engine) {
        onDispose {
            engine.close()
        }
    }

    var viewport by remember { mutableStateOf<Viewport?>(null) }
    var viewportMap by remember { mutableStateOf<File?>(null) }
    // keep the position across engine changes, reset it only for a new map
    LaunchedEffect(engine) {
        val current = viewport
        viewport = if (current == null || engine.mapFile != viewportMap) {
            viewportMap = engine.mapFile
            Viewport.of(center = startPosition(engine), zoom = startZoom(engine), tileSize = tileSize)
        } else {
            Viewport.of(center = current.center, zoom = current.zoom, tileSize = tileSize)
        }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(engine, viewport, canvasSize) {
        val vp = viewport ?: return@LaunchedEffect
        if (canvasSize.width > 0 && vp.tileSize == engine.tileSize) {
            engine.request(vp.visibleTiles(canvasSize.width, canvasSize.height))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Toolbar(
            mapFile = mapFile,
            themeFile = themeFile,
            textScale = textScale,
            tileSize = tileSize,
            threads = threads,
            onMapFile = { mapFile = it },
            onThemeFile = { themeFile = it },
            onTextScale = { textScale = it },
            onTileSize = { tileSize = it },
            onThreads = { threads = it },
            onRerender = { engine.clear() },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color(0xFFE0E0E0))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        viewport = viewport?.panBy(drag.x.toDouble(), drag.y.toDouble())
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.first()
                    val delta = if (change.scrollDelta.y < 0) 1 else -1
                    viewport = viewport?.zoomBy(
                        delta = delta,
                        anchorX = change.position.x - canvasSize.width / 2.0,
                        anchorY = change.position.y - canvasSize.height / 2.0,
                    )
                },
        ) {
            MapCanvas(engine = engine, viewport = viewport, size = canvasSize)
        }
        StatusBar(engine = engine, viewport = viewport)
    }
}

@Composable
private fun MapCanvas(engine: TileEngine, viewport: Viewport?, size: IntSize) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (viewport == null || viewport.tileSize != engine.tileSize) {
            return@Canvas
        }
        val originX = viewport.centerX - size.width / 2.0
        val originY = viewport.centerY - size.height / 2.0
        for (tile in viewport.visibleTiles(size.width, size.height)) {
            val left = (tile.tileX.toLong() * viewport.tileSize - originX).toFloat()
            val top = (tile.tileY.toLong() * viewport.tileSize - originY).toFloat()
            val image = engine.tiles[tile]
            if (image == null) {
                drawRect(
                    color = Color(0xFFCCCCCC),
                    topLeft = Offset(left + 1, top + 1),
                    size = Size(viewport.tileSize - 2f, viewport.tileSize - 2f),
                )
            } else {
                drawImage(image = image, dstOffset = IntOffset(left.toInt(), top.toInt()))
            }
        }
    }
}

@Composable
private fun Toolbar(
    mapFile: File,
    themeFile: File,
    textScale: Float,
    tileSize: Int,
    threads: Int,
    onMapFile: (File) -> Unit,
    onThemeFile: (File) -> Unit,
    onTextScale: (Float) -> Unit,
    onTileSize: (Int) -> Unit,
    onThreads: (Int) -> Unit,
    onRerender: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { pickFile("Open map", "*.map")?.let(onMapFile) }) {
            Text(mapFile.name)
        }
        Choice(
            label = "theme",
            value = themeFile,
            options = themesNextTo(themeFile),
            format = { it.nameWithoutExtension },
            onSelect = onThemeFile,
        )
        OutlinedButton(onClick = { pickFile("Open theme", "*.xml")?.let(onThemeFile) }) {
            Text("Other theme…")
        }
        Choice(label = "text", value = textScale, options = TEXT_SCALES, format = { "×$it" }, onSelect = onTextScale)
        Choice(label = "tile", value = tileSize, options = TILE_SIZES, format = { "$it px" }, onSelect = onTileSize)
        Choice(label = "threads", value = threads, options = THREAD_COUNTS, format = { "$it" }, onSelect = onThreads)
        Button(onClick = onRerender) {
            Text("Re-render")
        }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    value: T,
    options: List<T>,
    format: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: ${format(value)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(format(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusBar(engine: TileEngine, viewport: Viewport?) {
    val center = viewport?.center
    val position = if (viewport == null || center == null) {
        ""
    } else {
        "z%d · %.5f, %.5f".format(viewport.zoom, center.latitude, center.longitude)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("$position · ${engine.stats.summary.value}")
        engine.error.value?.let {
            Text(it, color = Color(0xFFB00020))
        }
    }
}

private fun startPosition(engine: TileEngine): org.mapsforge.core.model.LatLong {
    return engine.mapDataStore.startPosition() ?: engine.mapDataStore.boundingBox().centerPoint
}

private fun startZoom(engine: TileEngine): Int {
    return engine.mapDataStore.startZoomLevel()?.toInt() ?: 14
}

private fun themesNextTo(themeFile: File): List<File> {
    return themeFile.parentFile
        ?.listFiles { f -> f.extension == "xml" && !f.name.endsWith(".poi.xml") }
        ?.sortedBy { it.name }
        ?: listOf(themeFile)
}

private fun pickFile(title: String, pattern: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.file = pattern
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
