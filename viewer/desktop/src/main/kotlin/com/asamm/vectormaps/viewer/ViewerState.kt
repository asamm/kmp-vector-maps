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
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.label.LabelPass
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.core.label.TiledLabelPass
import com.asamm.vectormaps.garminimg.driver.GarminDataset
import com.asamm.vectormaps.garminimg.style.Color as GarminColor
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget
import com.asamm.vectormaps.garminimg.driver.GarminLabelPass
import com.asamm.vectormaps.garminimg.driver.GarminDriver
import com.asamm.vectormaps.garminimg.driver.GarminOptions
import com.asamm.vectormaps.garminimg.driver.GarminLevel
import com.asamm.vectormaps.mapsforge.MapsforgeDataset
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import com.asamm.vectormaps.mapsforge.MapsforgeLabelPass
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mvt.driver.MvtDataset
import com.asamm.vectormaps.mvt.driver.MvtDriver
import com.asamm.vectormaps.mvt.driver.MvtLabelPass
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * One viewer window: the open map and style, render settings and the view. Changing the map, the
 * style or a setting builds a new renderer; panning and zooming only ask for tiles.
 *
 * @param session the previous run's state; its map and style are opened by the caller
 * @param onOpen called before a map or style file is read, such as to drop converted TYP bitmaps
 */
class ViewerState(
    private val formats: MapFormats,
    session: Session,
    private val capturesDir: File,
    private val onOpen: () -> Unit = {},
) : Closeable {

    var mapFile: File? by mutableStateOf(null)
        private set

    /**
     * Format of the open map; its style files are the only ones that render it.
     */
    var driver: MapFormatDriver? by mutableStateOf(null)
        private set

    /**
     * The style file in use; null when the style is one the map brings itself, see [mapStyles].
     */
    var styleFile: File? by mutableStateOf(null)
        private set

    /**
     * Styles the open map brings itself, such as a Garmin map's TYP and built-in styles.
     */
    var mapStyles: List<MapStyle> by mutableStateOf(emptyList())
        private set

    /**
     * Garmin render settings; reset when another map opens.
     */
    var garmin: GarminOptions by mutableStateOf(GarminOptions())
        private set

    /**
     * TRE levels of an open Garmin map, for its level choice.
     */
    var garminLevels: List<GarminLevel> by mutableStateOf(emptyList())
        private set

    var style: MapStyle? by mutableStateOf(null)
        private set

    // the style last read from a file, closed when another replaces it
    private var loadedStyle: MapStyle? = null

    var layerId: String? by mutableStateOf(session.layerId)
        private set

    var textScale: Float by mutableStateOf(session.textScale)
        private set

    var tileSize: Int by mutableStateOf(session.tileSize)
        private set

    var threads: Int by mutableStateOf(session.threads)
        private set

    /**
     * Clockwise map rotation in degrees.
     */
    var rotationDeg: Float by mutableStateOf(0.0f)
        private set

    /**
     * The renderer's label pass.
     */
    var labelPass: LabelPass? by mutableStateOf(null)
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
     * The full text behind [error], such as each driver's reason or the cause chain.
     */
    var errorDetails: String? by mutableStateOf(null)
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
     * The maps of the open file that do not parse and are skipped, for as long as it is open.
     */
    var skippedMaps: String? by mutableStateOf(null)
        private set

    /**
     * Each skipped map and its reason.
     */
    var skippedMapsDetails: String? by mutableStateOf(null)
        private set

    /**
     * Time the last renderer took to build, mostly compiling the style.
     */
    var setupMs: Long? by mutableStateOf(null)
        private set

    private val frames = AtomicInteger()

    private var dataset: MapDataset? = null

    // the pass of the engine createEngine built last, until install shows it
    private var builtLabelPass: LabelPass? = null

    // the label store size of the last renderer built
    private var builtLabelStoreTiles = 0

    // the device scale of the last renderer built, which its Garmin tiles measured labels at
    private var builtDeviceScale = 1.0f

    private val labelsRequested = AtomicBoolean()

    // the view last asked of the label pass; UI thread only
    private var labelsAskedFor: LabelViewport? = null

    // a newer view waits for the placement in flight
    @Volatile
    private var labelsPending = false

    // the last label image and the placement and view it shows; UI thread only
    private var labelImageCache: Pair<Pair<PreparedLabels, LabelViewport>, ImageBitmap>? = null

    // the last style file and layer per driver id, so switching formats keeps each one's style
    private val lastStyleFiles = HashMap<String, Pair<File, String?>>().apply {
        for ((driverId, style) in session.formatStyles) {
            put(driverId, File(style.path) to style.layerId)
        }
    }

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
            formatStyles = lastStyleFiles.mapValues { (_, style) ->
                Session.FormatStyle(path = style.first.absolutePath, layerId = style.second)
            },
            textScale = textScale,
            tileSize = tileSize,
            threads = threads,
            place = vp?.let { Session.Place(lat = it.lat, lon = it.lon, zoom = it.zoom) },
        )
    }

    /**
     * Opens `file` with the first driver that reads it; keeps the current style file when that
     * driver reads it too. Else a map without styles of its own (Mapsforge) takes the driver's last
     * style file, any other its own first style. Opening the same map again keeps its own style and
     * the Garmin settings; a style file that fails leaves the error, not another style.
     */
    fun openMap(file: File) {
        val probed = formats.probe(file)
        if (probed == null) {
            fail("openMap(), no driver reads ${file.name}", formats.unreadableReason(file))
            return
        }
        onOpen()
        val opened = try {
            probed.driver.open(listOf(file))
        } catch (e: Exception) {
            fail("openMap(${file.name}), ${e.message}", causesOf(e))
            return
        }
        dropEngine()
        replaceLoadedStyle(null)
        dataset?.close()
        dataset = opened
        val sameMap = mapFile?.absolutePath == file.absolutePath
        if (!sameMap) {
            viewport = null
        }
        val previousStyle = style
        val previousIndex = mapStyles.indexOf(previousStyle)
        // a style file the new map cannot read stays the one for its own format
        styleFile?.takeUnless(probed.driver::readsStyle)?.let { rememberStyle(file = it, layerId = layerId) }
        // a map with its own styles starts with them, not with a file remembered from another map
        val remembered = lastStyleFiles[probed.driver.id]?.takeIf { opened.styles.isEmpty() }
        if (remembered != null
            && styleFile?.let(probed.driver::readsStyle) != true
        ) {
            layerId = remembered.second
        }
        val keptStyle = styleFile?.takeIf(probed.driver::readsStyle) ?: remembered?.first
        style = null
        styleFile = keptStyle
        mapStyles = opened.styles
        val unreadable = (opened as? GarminDataset)?.unreadableMaps.orEmpty()
        // the previous map's notice does not carry over
        notice = null
        skippedMaps = unreadable.takeIf { it.isNotEmpty() }?.let {
            "${if (it.size == 1) "1 map" else "${it.size} maps"} of ${file.name} unreadable, skipped"
        }
        skippedMapsDetails = unreadable.takeIf { it.isNotEmpty() }?.joinToString("\n") { "map ${it.mapName}: ${it.reason}" }
        garminLevels = (opened as? GarminDataset)?.levels.orEmpty()
        garmin = if (sameMap) {
            garmin.copy(level = garmin.level?.takeIf { level -> garminLevels.any { it.level == level } })
        } else {
            GarminOptions()
        }
        mapFile = file
        driver = probed.driver
        clearError()
        if (viewport == null) {
            startView()
        }
        // a new dataset has new style instances; the same kind at the same place is the same choice,
        // as the day and night TYP styles share a kind, else the first of that kind
        val ownStyle = opened.styles.getOrNull(previousIndex)
            ?.takeIf { sameMap && previousStyle != null && it::class == previousStyle::class }
            ?: opened.styles.firstOrNull { sameMap && previousStyle != null && it::class == previousStyle::class }
            ?: opened.styles.firstOrNull()
        val built = keptStyle != null
            && openStyle(keptStyle)
        when {
            // a failed style file keeps its error; the map's own styles stay in the picker
            built || keptStyle != null -> Unit
            ownStyle != null -> useMapStyle(ownStyle)
            else -> notice = "Choose a style"
        }
    }

    /**
     * Keeps `file` as the style for the next map of the format that reads it, without opening it.
     */
    fun rememberStyle(file: File, layerId: String? = null) {
        val driver = formats.drivers.firstOrNull { it.readsStyle(file) } ?: return
        lastStyleFiles[driver.id] = file to layerId
    }

    /**
     * Renders with one of [mapStyles]; returns false when the renderer cannot be built, and the
     * current style stays.
     */
    fun useMapStyle(style: MapStyle): Boolean {
        val dataset = dataset ?: return false
        val layer = layerId?.takeIf { style.menu?.layer(it) != null }
        val built = createEngine(dataset = dataset, style = style, layerId = layer) ?: return false
        replaceLoadedStyle(null)
        styleFile = null
        this.style = style
        layerId = layer
        clearError()
        install(built)
        return true
    }

    fun changeGarmin(options: GarminOptions) {
        garmin = options
        rebuild()
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
            rememberStyle(file = file, layerId = layerId)
            return false
        }
        onOpen()
        val loaded = try {
            dataset.driver.loadStyle(file)
        } catch (e: Exception) {
            fail("openStyle(${file.name}), ${e.message}", causesOf(e))
            return false
        }
        val layer = layerId?.takeIf { loaded.menu?.layer(it) != null }
        // the rules compile only in createRenderer, so nothing is committed before it succeeded
        val built = createEngine(dataset = dataset, style = loaded, layerId = layer)
        if (built == null) {
            loaded.close()
            return false
        }
        replaceLoadedStyle(loaded)
        styleFile = file
        style = loaded
        layerId = layer
        lastStyleFiles[dataset.driver.id] = file to layer
        clearError()
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

    fun rotate(degrees: Float) {
        rotationDeg = degrees
        showTiles()
    }

    /**
     * The placed labels drawn on a transparent screen-sized image; null without a pass or a
     * placement for this zoom.
     */
    fun labelImage(width: Int, height: Int): ImageBitmap? {
        val pass = labelPass ?: return null
        val vp = viewport ?: return null
        val prepared = pass.prepared
        if (width <= 0
            || height <= 0
            || prepared?.view?.zoom != vp.zoom
        ) {
            return null
        }
        val view = vp.labelViewport(width, height, rotationDeg.toDouble())
        // frames that change neither the placement nor the view reuse the image
        val key = Pair(prepared, view)
        labelImageCache?.let { (cachedKey, image) ->
            if (cachedKey == key) {
                return image
            }
        }
        val image = when (pass) {
            is MapsforgeLabelPass -> {
                mapsforgeLabels(pass = pass, view = view, prepared = prepared)
            }
            is GarminLabelPass -> {
                val target = AwtRenderTarget(
                    widthPx = width,
                    heightPx = height,
                    antiAliased = garmin.antiAliasing,
                    renderScale = builtDeviceScale,
                )
                target.beginFrame(GarminColor.Transparent)
                pass.draw(target = target, view = view, prepared = prepared)
                target.endFrame()
                target.image.toComposeImageBitmap()
            }
            is MvtLabelPass -> {
                val target = pass.newTarget(width, height) as AwtMvtRenderTarget
                pass.draw(target = target, view = view, prepared = prepared)
                target.dispose()
                target.image.toComposeImageBitmap()
            }
            else -> {
                return null
            }
        }
        labelImageCache = Pair(key, image)
        return image
    }

    private fun mapsforgeLabels(pass: MapsforgeLabelPass, view: LabelViewport, prepared: PreparedLabels): ImageBitmap {
        val bitmap = AwtGraphicFactory.INSTANCE.createBitmap(view.widthPx, view.heightPx, true)
        val canvas = AwtGraphicFactory.INSTANCE.createCanvas()
        try {
            canvas.setBitmap(bitmap)
            pass.draw(canvas = canvas, view = view, prepared = prepared)
            return AwtGraphicFactory.getBitmap(bitmap).toComposeImageBitmap()
        } finally {
            canvas.destroy()
            bitmap.decrementRefCount()
        }
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
        if (labelPass != null
            && builtLabelStoreTiles < labelStoreTiles()
        ) {
            // a larger screen needs a larger label store
            rebuild()
            return
        }
        showTiles()
    }

    fun drag(dx: Float, dy: Float) {
        val (mapDx, mapDy) = unrotated(dx.toDouble(), dy.toDouble())
        viewport?.let { moveTo(it.dragged(mapDx, mapDy)) }
    }

    /**
     * Zooms around the screen point `x`, `y`.
     */
    fun zoom(steps: Int, x: Float, y: Float) {
        val (focusX, focusY) = unrotated(x - screenWidth / 2.0, y - screenHeight / 2.0)
        viewport?.let {
            moveTo(it.zoomed(steps = steps, focusX = focusX, focusY = focusY))
        }
    }

    // a screen vector in the map's frame
    private fun unrotated(dx: Double, dy: Double): Pair<Double, Double> {
        val radians = Math.toRadians(-rotationDeg.toDouble())
        return Pair(dx * cos(radians) - dy * sin(radians), dx * sin(radians) + dy * cos(radians))
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
        val rotation = rotationDeg
        // as MapArea draws: the tiles of the covered square turned, the labels upright on top
        val (coverWidth, coverHeight) = coveredSide()
        val shown = vp.tiles(coverWidth, coverHeight).mapNotNull { tile ->
            engine[tile]?.let { image ->
                ShownTile(
                    image = image,
                    x = floor(vp.screenX(tile.x, coverWidth)).toInt() + (width - coverWidth) / 2,
                    y = floor(vp.screenY(tile.y, coverHeight)).toInt() + (height - coverHeight) / 2,
                )
            }
        }
        val labels = labelImage(width, height)?.toAwtImage()
        val name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        val png = File(capturesDir, "$name.png")
        val view = Properties()
        view["map"] = map.absolutePath
        view["style"] = styleFile?.absolutePath ?: style?.id ?: ""
        view["layer"] = layerId ?: ""
        view["zoom"] = vp.zoom.toString()
        view["lat"] = String.format(Locale.ROOT, "%.6f", vp.lat)
        view["lon"] = String.format(Locale.ROOT, "%.6f", vp.lon)
        view["screen"] = "${width}x$height"
        view["tileSize"] = vp.tileSize.toString()
        view["textScale"] = textScale.toString()
        view["labels"] = if (labelPass != null) "pass" else "none"
        view["rotation"] = rotation.toString()
        if (dataset is GarminDataset) {
            view["garmin.antiAliasing"] = garmin.antiAliasing.toString()
            view["garmin.objectKinds"] = "0x" + garmin.objectKinds.toString(16)
            view["garmin.level"] = garmin.level?.toString() ?: "auto"
        }
        try {
            withContext(Dispatchers.IO) {
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    val upright = g.transform
                    g.rotate(Math.toRadians(rotation.toDouble()), width / 2.0, height / 2.0)
                    for (tile in shown) {
                        g.drawImage(tile.image.toAwtImage(), tile.x, tile.y, null)
                    }
                    g.transform = upright
                    labels?.let { g.drawImage(it, 0, 0, null) }
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
            fail("capture(), ${e.javaClass.simpleName}: ${e.message}", causesOf(e))
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
        replaceLoadedStyle(null)
        dataset?.close()
        dataset = null
    }

    // the replaced style's theme lives on until the renderers still using it close
    private fun replaceLoadedStyle(style: MapStyle?) {
        loadedStyle?.close()
        loadedStyle = style
    }

    // reads a style file again, so an edit on disk applies; a changed map file needs Reload
    private fun rebuild() {
        val file = styleFile
        val mapStyle = style.takeIf { file == null }
        val built = when {
            file != null -> openStyle(file)
            mapStyle != null -> useMapStyle(mapStyle)
            else -> false
        }
        // the old engine renders the old settings; the controls already show the new ones
        if (!built) {
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
            val selection = StyleSelection(layerId = layerId)
            val config = RenderConfig(tileSizePx = tileSize, textScale = textScale)
            builtLabelStoreTiles = labelStoreTiles()
            builtDeviceScale = config.deviceScale
            val renderer = if (dataset is GarminDataset) {
                dataset.createRenderer(
                    style = style,
                    selection = selection,
                    config = config,
                    options = garmin,
                    labelStoreTiles = builtLabelStoreTiles,
                )
            } else if (dataset is MapsforgeDataset) {
                dataset.createRenderer(
                    style = style,
                    selection = selection,
                    config = config,
                    labelStoreTiles = builtLabelStoreTiles,
                )
            } else if (dataset is MvtDataset) {
                dataset.createRenderer(
                    style = style,
                    selection = selection,
                    config = config,
                    labelStoreTiles = builtLabelStoreTiles,
                )
            } else {
                dataset.createRenderer(style = style, selection = selection, config = config)
            }
            setupMs = (System.nanoTime() - started) / 1_000_000
            renderer.labelPass?.onPrepared = {
                frame = frames.incrementAndGet()
                if (labelsPending) {
                    requestLabels()
                }
            }
            return TileEngine(
                renderer = renderer,
                images = images,
                threads = threads,
                capacity = engineCapacity(renderer),
            ) {
                frame = frames.incrementAndGet()
                // a new tile stored its labels
                requestLabels()
            }.also {
                builtLabelPass = renderer.labelPass
            }
        } catch (e: Exception) {
            fail("createEngine(), ${e.message}", causesOf(e))
            return null
        }
    }

    // a separate label pass keeps the labels of its store's tiles only, so no more tiles than that
    private fun engineCapacity(renderer: MapRenderer): Int {
        val byMemory = (CACHE_BYTES / (4L * tileSize * tileSize)).toInt().coerceAtLeast(1)
        val labelTiles = (renderer.labelPass as? TiledLabelPass<*>)?.storeCapacity ?: return byMemory
        return minOf(byMemory, labelTiles)
    }

    private fun fail(message: String, details: String) {
        error = message
        errorDetails = details
    }

    private fun clearError() {
        error = null
        errorDetails = null
    }

    // each cause's type and message, for the full error
    private fun causesOf(e: Throwable): String {
        return generateSequence(e) { it.cause }.joinToString("\n") { "${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun install(built: TileEngine<ImageBitmap>) {
        dropEngine()
        engine = built
        labelPass = builtLabelPass
        // a new pass has placed nothing yet
        labelsAskedFor = null
        labelsPending = false
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
        labelPass = null
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
            val side = coveredSide()
            engine.show(vp.tiles(side.first, side.second))
            prepareLabels()
        }
    }

    /**
     * The screen size whose tiles cover the turned screen: the screen itself, or the square around
     * its diagonal.
     */
    fun coveredSide(): Pair<Int, Int> {
        if (rotationDeg == 0.0f) {
            return Pair(screenWidth, screenHeight)
        }
        val diagonal = Viewport.diagonal(screenWidth, screenHeight)
        return Pair(diagonal, diagonal)
    }

    // the tiles under the screen at any rotation and alignment, plus a ring; a store this size
    // keeps every visible tile's labels
    private fun labelStoreTiles(): Int {
        val side = ceil(Viewport.diagonal(screenWidth, screenHeight).toDouble() / tileSize).toInt() + 2
        return maxOf(MIN_LABEL_STORE_TILES, side * side)
    }

    // tile arrivals come on render threads; the view is read on the UI thread, once per burst, so
    // an older view never replaces a newer one
    private fun requestLabels() {
        if (labelsRequested.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                labelsRequested.set(false)
                prepareLabels()
            }
        }
    }

    // one placement at a time, so a drag's moves don't cancel each other and labels keep landing;
    // the placement's arrival asks for the latest view
    private fun prepareLabels() {
        val pass = labelPass ?: return
        val vp = viewport ?: return
        if (screenWidth <= 0) {
            return
        }
        val asked = labelsAskedFor
        if (asked != null
            && pass.prepared?.view != asked
        ) {
            labelsPending = true
            return
        }
        labelsPending = false
        val view = vp.labelViewport(screenWidth, screenHeight, rotationDeg.toDouble())
        labelsAskedFor = view
        pass.prepare(view)
    }

    private fun imagesFor(driver: MapFormatDriver): TileImages<ImageBitmap> {
        return when (driver) {
            is MapsforgeDriver -> AwtTileImages
            is GarminDriver -> GarminTileImages
            is MvtDriver -> MvtTileImages
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

        // the Mapsforge driver's own default
        const val MIN_LABEL_STORE_TILES = 100
    }
}
