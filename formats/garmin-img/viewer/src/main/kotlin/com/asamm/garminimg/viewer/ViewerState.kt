/*
 * kmp-garmin-img — Viewer state.
 */
package com.asamm.garminimg.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.coord.CoordUtils
import com.asamm.garminimg.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.render.AwtRenderTarget
import com.asamm.garminimg.render.ImgRenderer
import com.asamm.garminimg.render.Viewport
import com.asamm.garminimg.style.Color
import java.awt.image.BufferedImage
import java.io.File

/**
 * Mutable state holder for the viewer. One instance per Window.
 *
 * The state is intentionally minimal for the Phase F MVP — it's enough to
 * load an IMG, see a render of its TRE bbox, and re-render when the user
 * presses **Reload** (which re-opens the file from disk; useful when the
 * IMG itself changes).
 *
 * Pan / zoom / viewport editing are deliberately deferred — each adds
 * gesture wiring that's unrelated to TYP-styling iteration. Once
 * Phase C.7 is in flight and a wider viewport is needed, we add them
 * incrementally.
 */
class ViewerState {

    /** Currently loaded IMG file path, or null if nothing is open. */
    var loadedFile: File? by mutableStateOf(null)
        private set

    /** Last rendered image (Compose-displayable via .toComposeImageBitmap()). */
    var rendered: BufferedImage? by mutableStateOf(null)
        private set

    /** Status / error text shown in the bottom bar. */
    var status: String by mutableStateOf("Open an IMG file to begin.")
        private set

    /** Anti-aliasing on/off — useful when comparing against the golden PNG. */
    var antiAliased: Boolean by mutableStateOf(true)

    /**
     * Width / height of the rendered image in pixels. Bound to the window's
     * map-panel size; the renderer always renders at this exact resolution.
     */
    var widthPx: Int by mutableStateOf(1024)
    var heightPx: Int by mutableStateOf(1024)

    /**
     * Reads `file` into memory, parses every sub-file, renders the TRE bbox
     * to a BufferedImage. Stores the result in [rendered].
     *
     * Synchronous for the MVP — Monaco-scale fixtures parse in <100 ms.
     * Larger maps will need a coroutine wrapper later.
     */
    fun loadAndRender(file: File) {
        loadedFile = file
        status = "Loading ${file.name}…"
        try {
            val bytes = file.readBytes()
            val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
            val viewport = Viewport.ofTreBounds(
                widthPx = widthPx,
                heightPx = heightPx,
                northBoundary = img.tre.header.northBoundary,
                eastBoundary = img.tre.header.eastBoundary,
                southBoundary = img.tre.header.southBoundary,
                westBoundary = img.tre.header.westBoundary,
            )
            val target = AwtRenderTarget(widthPx, heightPx, antiAliased = antiAliased)
            ImgRenderer(img).render(viewport, target, backgroundColor = Color.rgb(0xF8, 0xF6, 0xF0))
            rendered = target.image
            val nDeg = CoordUtils.toWGS84(img.tre.header.northBoundary)
            val sDeg = CoordUtils.toWGS84(img.tre.header.southBoundary)
            val eDeg = CoordUtils.toWGS84(img.tre.header.eastBoundary)
            val wDeg = CoordUtils.toWGS84(img.tre.header.westBoundary)
            status = "${file.name} — '${img.archive.description}' — " +
                "bbox %.4f..%.4fN / %.4f..%.4fE — TYP: %s".format(
                    sDeg, nDeg, wDeg, eDeg,
                    if (img.typ != null) "yes (codepage ${img.typ?.codepage})" else "no",
                )
        } catch (e: Exception) {
            rendered = null
            status = "Error loading ${file.name}: ${e.message}"
        }
    }

    /**
     * Re-renders the currently loaded file. Useful after toggling AA or
     * after the user has rebuilt the parser/renderer code (next gradle
     * run replaces classes; press Reload to re-render against the new
     * code without restarting the viewer).
     */
    fun reload() {
        loadedFile?.let { loadAndRender(it) }
    }
}
