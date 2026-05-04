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
import com.asamm.garminimg.render.StyleResolver
import com.asamm.garminimg.render.Viewport
import com.asamm.garminimg.style.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

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
class ViewerState(
    initialAntiAliased: Boolean = true,
) {

    /** Currently loaded IMG file path, or null if nothing is open. */
    var loadedFile: File? by mutableStateOf(null)
        private set

    /** Last rendered image (Compose-displayable via .toComposeImageBitmap()). */
    var rendered: BufferedImage? by mutableStateOf(null)
        private set

    /** Status / error text shown in the bottom bar. */
    var status: String by mutableStateOf("Open an IMG file to begin.")
        private set

    /**
     * Anti-aliasing on/off — useful when comparing against the golden PNG.
     * Setter writes through to [ViewerPrefs] so the choice survives restarts.
     */
    private var _antiAliased: Boolean by mutableStateOf(initialAntiAliased)
    var antiAliased: Boolean
        get() = _antiAliased
        set(value) {
            if (_antiAliased != value) {
                _antiAliased = value
                ViewerPrefs.saveAntiAliased(value)
            }
        }

    /**
     * Width of the rendered image in pixels. Height is computed from the
     * loaded IMG's TRE bbox aspect (Web Mercator) so the render comes out
     * at the same shape a desktop tool like QMapShack would show — same
     * lat/lon range, no horizontal squish.
     */
    var widthPx: Int by mutableStateOf(1600)
    var heightPx: Int by mutableStateOf(800)
        private set

    /**
     * Reads `file` into memory, parses every sub-file, renders the TRE bbox
     * to a BufferedImage. Stores the result in [rendered].
     *
     * Synchronous for the MVP — Monaco-scale fixtures parse in <100 ms.
     * Larger maps will need a coroutine wrapper later.
     */
    fun loadAndRender(file: File) {
        loadedFile = file
        ViewerPrefs.saveLastFile(file.absolutePath)
        status = "Loading ${file.name}…"
        try {
            val bytes = file.readBytes()
            val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }

            // Adapt heightPx to the TRE bbox so the rendered image mirrors
            // the Mercator projection's true aspect — no horizontal squish.
            // Also clamp to a sane range so a degenerate fixture doesn't
            // produce a 1-pixel-tall render.
            val nDeg = CoordUtils.toWGS84(img.tre.header.northBoundary)
            val sDeg = CoordUtils.toWGS84(img.tre.header.southBoundary)
            val eDeg = CoordUtils.toWGS84(img.tre.header.eastBoundary)
            val wDeg = CoordUtils.toWGS84(img.tre.header.westBoundary)
            val mercAspect = mercatorAspectRatio(nDeg, sDeg, eDeg, wDeg)
            heightPx = (widthPx / mercAspect).toInt().coerceIn(200, 4096)

            val viewport = Viewport.ofTreBounds(
                widthPx = widthPx,
                heightPx = heightPx,
                northBoundary = img.tre.header.northBoundary,
                eastBoundary = img.tre.header.eastBoundary,
                southBoundary = img.tre.header.southBoundary,
                westBoundary = img.tre.header.westBoundary,
            )
            val target = AwtRenderTarget(widthPx, heightPx, antiAliased = antiAliased)
            val resolver = img.typStyles?.let { StyleResolver.typAware(it) }
                ?: StyleResolver.internalDefaults()
            ImgRenderer(img, resolver).render(
                viewport,
                target,
                backgroundColor = Color.rgb(0xF8, 0xF6, 0xF0),
            )
            rendered = target.image
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

    /**
     * Web Mercator aspect ratio for a lat/lon bbox: how many longitude
     * units fit per latitude unit when projected. Larger result → wider.
     * Mirrors the math in `Projection.mercatorY` so the viewer's heightPx
     * matches the renderer's projection precisely (no horizontal squish).
     */
    private fun mercatorAspectRatio(nDeg: Double, sDeg: Double, eDeg: Double, wDeg: Double): Double {
        // Mercator x is just longitude; mercator y is ln(tan(π/4 + lat/2)).
        val sRad = sDeg * PI / 180.0
        val nRad = nDeg * PI / 180.0
        val mercY1 = ln(tan(PI / 4.0 + sRad / 2.0))
        val mercY2 = ln(tan(PI / 4.0 + nRad / 2.0))
        val mercX1 = wDeg * PI / 180.0
        val mercX2 = eDeg * PI / 180.0
        val dx = mercX2 - mercX1
        val dy = mercY2 - mercY1
        return if (dy != 0.0) dx / dy else 2.0
    }
}
