/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.floor

/**
 * One viewer window: the open map and style, render settings and the view. Changing the map, the
 * style or a setting builds a new renderer; panning and zooming only ask for tiles.
 *
 * @param session the previous run's state; its map and style are opened by the caller
 */
class ViewerState(
    private val formats: MapFormats,
    session: Session,
    private val capturesDir: File,
) : Closeable {

    var mapFile: File? by mutableStateOf(null)
        private set

    /**
     * Format of the open map; its style files are the only ones that render it.
     */
    var driver: MapFormatDriver? by mutableStateOf(null)
        private set

    var styleFile: File? by mutableStateOf(null)
        private set

    var style: MapStyle? by mutableStateOf(null)
        private set

    var layerId: String? by mutableStateOf(session.layerId)
        private set

    var textScale: Float by mutableStateOf(session.textScale)
        private set

    var tileSize: Int by mutableStateOf(session.tileSize)
        private set

    var threads: Int by mutableStateOf(session.threads)
        private set

    var engine: TileEngine<ImageBitmap>? by mutableStateOf(null)
        private set

    var viewport: Viewport? by mutableStateOf(null)
        private set

    /**
     * Last failure to open or build; render failures come from the engine.
     */
    var error: String? by mutableStateOf(null)
        private set

    /**
     * Changes whenever a tile arrives, so composables reading it redraw.
     */
    var frame: Int by mutableIntStateOf(0)
        private set

    /**
     * Outcome of the last action worth confirming, such as where a capture went.
     */
    var notice: String? by mutableStateOf(null)
        private set

    /**
     * Time the last renderer took to build, mostly compiling the style.
     */
    var setupMs: Long? by mutableStateOf(null)
        private set

    private val frames = AtomicInteger()

    private var dataset: MapDataset? = null

    private var screenWidth = 0

    private var screenHeight = 0

    // where to start once the screen size is known; for the session's map only
    private var startPlace: Pair<String, Session.Place>? =
        session.mapPath?.let { path -> session.place?.let { path to it } }

    val mapExtensions: Set<String>
        get() {
            return formats.drivers.flatMapTo(HashSet()) { it.extensions }
        }

    /**
     * The state to restore on the next start; window bounds are the caller's.
     */
    fun session(): Session {
        val vp = viewport
        return Session(
            mapPath = mapFile?.absolutePath,
            stylePath = styleFile?.absolutePath,
            layerId = layerId,
            textScale = textScale,
            tileSize = tileSize,
            threads = threads,
            place = vp?.let { Session.Place(lat = it.lat, lon = it.lon, zoom = it.zoom) },
        )
    }

    /**
     * Opens `file` with the first driver that reads it; keeps the current style when that driver
     * reads it too.
     */
    fun openMap(file: File) {
        val probed = formats.probe(file)
        if (probed == null) {
            error = "openMap(), no driver reads ${file.name}"
            return
        }
        val opened = try {
            probed.driver.open(listOf(file))
        } catch (e: Exception) {
            error = "openMap(${file.name}), ${e.message}"
            return
        }
        dropEngine()
        dataset?.close()
        dataset = opened
        if (mapFile?.absolutePath != file.absolutePath) {
            viewport = null
        }
        val keptStyle = styleFile?.takeIf { it.extension.lowercase() in probed.driver.styleExtensions }
        style = null
        styleFile = keptStyle
        mapFile = file
        driver = probed.driver
        error = null
        if (viewport == null) {
            startView()
        }
        if (keptStyle == null) {
            notice = "Choose a style"
        } else {
            openStyle(keptStyle)
        }
    }

    /**
     * Loads `file` with the map's driver and renders with it; returns false when it cannot be
     * read or compiled, and the current style and map stay. With no map open, `file` waits for
     * the next one.
     */
    fun openStyle(file: File): Boolean {
        val dataset = dataset
        if (dataset == null) {
            styleFile = file
            return false
        }
        val loaded = try {
            dataset.driver.loadStyle(file)
        } catch (e: Exception) {
            error = "openStyle(${file.name}), ${e.message}"
            return false
        }
        val layer = layerId?.takeIf { loaded.menu?.layer(it) != null }
        // the rules compile only in createRenderer, so nothing is committed before it succeeded
        val built = createEngine(dataset = dataset, style = loaded, layerId = layer) ?: return false
        styleFile = file
        style = loaded
        layerId = layer
        error = null
        install(built)
        return true
    }

    fun selectLayer(id: String) {
        layerId = id
        rebuild()
    }

    fun changeSettings(
        textScale: Float = this.textScale,
        tileSize: Int = this.tileSize,
        threads: Int = this.threads,
    ) {
        val vp = viewport
        if (vp != null
            && tileSize != vp.tileSize
        ) {
            viewport = vp.copy(tileSize = tileSize)
        }
        this.textScale = textScale
        this.tileSize = tileSize
        this.threads = threads
        rebuild()
    }

    /**
     * Opens the map and style again from disk, keeping the view.
     */
    fun reload() {
        mapFile?.let(::openMap)
    }

    /**
     * Forgets the drawn tiles, so they render again with cold timings.
     */
    fun rerender() {
        engine?.clear()
        showTiles()
    }

    fun home() {
        val bounds = dataset?.bounds ?: return
        if (screenWidth > 0) {
            moveTo(Viewport.fitting(bounds = bounds, width = screenWidth, height = screenHeight, tileSize = tileSize))
        }
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0
            || height <= 0
        ) {
            return
        }
        screenWidth = width
        screenHeight = height
        if (viewport == null) {
            startView()
        }
        showTiles()
    }

    fun drag(dx: Float, dy: Float) {
        viewport?.let { moveTo(it.dragged(dx.toDouble(), dy.toDouble())) }
    }

    /**
     * Zooms around the screen point `x`, `y`.
     */
    fun zoom(steps: Int, x: Float, y: Float) {
        viewport?.let {
            moveTo(it.zoomed(steps = steps, focusX = x - screenWidth / 2.0, focusY = y - screenHeight / 2.0))
        }
    }

    /**
     * Writes the screen as `<time>.png` with the view in `<time>.properties` to the captures folder;
     * returns the image file, or null when nothing is shown or the write failed. Call from the UI
     * thread; the image is composed and written on the IO dispatcher.
     */
    suspend fun capture(): File? {
        val engine = engine ?: return null
        val vp = viewport ?: return null
        val map = mapFile ?: return null
        val width = screenWidth
        val height = screenHeight
        val shown = vp.tiles(width, height).mapNotNull { tile ->
            engine[tile]?.let { image ->
                ShownTile(
                    image = image,
                    x = floor(vp.screenX(tile.x, width)).toInt(),
                    y = floor(vp.screenY(tile.y, height)).toInt(),
                )
            }
        }
        val name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        val png = File(capturesDir, "$name.png")
        val view = Properties()
        view["map"] = map.absolutePath
        view["style"] = styleFile?.absolutePath ?: ""
        view["layer"] = layerId ?: ""
        view["zoom"] = vp.zoom.toString()
        view["lat"] = String.format(Locale.ROOT, "%.6f", vp.lat)
        view["lon"] = String.format(Locale.ROOT, "%.6f", vp.lon)
        view["screen"] = "${width}x$height"
        view["tileSize"] = vp.tileSize.toString()
        view["textScale"] = textScale.toString()
        try {
            withContext(Dispatchers.IO) {
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    for (tile in shown) {
                        g.drawImage(tile.image.toAwtImage(), tile.x, tile.y, null)
                    }
                } finally {
                    g.dispose()
                }
                capturesDir.mkdirs()
                check(ImageIO.write(image, "PNG", png)) {
                    "capture(), no PNG writer"
                }
                File(capturesDir, "$name.properties").outputStream().use { view.store(it, null) }
            }
        } catch (e: Exception) {
            error = "capture(), ${e.javaClass.simpleName}: ${e.message}"
            notice = null
            return null
        }
        notice = "Captured ${png.name}"
        return png
    }

    /**
     * Closes the engine and dataset and waits for renders in progress; for the end of the session.
     */
    override fun close() {
        engine?.close()
        engine = null
        dataset?.close()
        dataset = null
    }

    // reads the style again, so an edit on disk applies and never fails the renderer's freshness check
    private fun rebuild() {
        val style = styleFile
        // the old engine renders the old settings; the controls already show the new ones
        if (style == null
            || !openStyle(style)
        ) {
            dropEngine()
            setupMs = null
        }
    }

    /**
     * A new engine for the current settings, or null with `error` set when the renderer cannot be
     * built; changes nothing else.
     */
    private fun createEngine(
        dataset: MapDataset,
        style: MapStyle,
        layerId: String?,
    ): TileEngine<ImageBitmap>? {
        try {
            val images = imagesFor(dataset.driver)
            val started = System.nanoTime()
            val renderer = dataset.createRenderer(
                style = style,
                selection = StyleSelection(layerId = layerId),
                config = RenderConfig(tileSizePx = tileSize, textScale = textScale),
            )
            setupMs = (System.nanoTime() - started) / 1_000_000
            return TileEngine(
                renderer = renderer,
                images = images,
                threads = threads,
                capacity = (CACHE_BYTES / (4L * tileSize * tileSize)).toInt().coerceAtLeast(1),
            ) {
                frame = frames.incrementAndGet()
            }
        } catch (e: Exception) {
            error = "createEngine(), ${e.message}"
            return null
        }
    }

    private fun install(built: TileEngine<ImageBitmap>) {
        dropEngine()
        engine = built
        notice = null
        if (viewport == null) {
            startView()
        }
        showTiles()
    }

    // the old renderer may still finish a tile; waiting for it would freeze the window
    private fun dropEngine() {
        engine?.closeLater()
        engine = null
    }

    private fun startView() {
        val start = startPlace
        val map = mapFile ?: return
        if (start != null
            && start.first == map.absolutePath
            && screenWidth > 0
        ) {
            startPlace = null
            moveTo(Viewport.at(lat = start.second.lat, lon = start.second.lon, zoom = start.second.zoom, tileSize = tileSize))
            return
        }
        home()
    }

    private fun moveTo(vp: Viewport) {
        viewport = vp
        showTiles()
    }

    private fun showTiles() {
        val vp = viewport ?: return
        val engine = engine ?: return
        if (screenWidth > 0
            && vp.tileSize == engine.tileSizePx
        ) {
            engine.show(vp.tiles(screenWidth, screenHeight))
        }
    }

    private fun imagesFor(driver: MapFormatDriver): TileImages<ImageBitmap> {
        return when (driver) {
            is MapsforgeDriver -> AwtTileImages
            else -> throw IllegalArgumentException("imagesFor(), no images for driver ${driver.id}")
        }
    }

    private class ShownTile(
        val image: ImageBitmap,
        val x: Int,
        val y: Int,
    )

    private companion object {

        // native pixels the kept tiles may hold: 256 tiles at 512 px
        const val CACHE_BYTES = 256L shl 20
    }
}
