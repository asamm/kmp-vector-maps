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
import kotlin.math.max
import kotlin.math.tan

/**
 * Mutable state holder for the viewer. One instance per Window.
 *
 * Holds the currently-loaded [ImgFile] plus a live [Viewport] (in Garmin
 * fixed-point coords). Pan / zoom gestures from [ViewerApp] mutate the
 * viewport and trigger a re-render against the cached IMG — no re-parse
 * needed, which keeps interactions snappy.
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
                rerender()
            }
        }

    /**
     * Render dimensions in pixels — track the displayed map panel size so
     * mouse coords map 1:1 to image pixels (no letterboxing math). Updated
     * by [ViewerApp] via [setPanelSize] on resize.
     */
    var widthPx: Int by mutableStateOf(1600)
        private set
    var heightPx: Int by mutableStateOf(800)
        private set

    /**
     * Cached parsed IMG. Holds onto the file bytes too so we can pass a
     * fresh reader factory to [ImgFile.open] when needed.
     */
    private var imgFile: ImgFile? = null
    private var imgBytes: ByteArray? = null

    /**
     * Current viewport in Garmin fixed-point coords. Pan / zoom gestures
     * mutate this and call [rerender]. Null when nothing is loaded.
     */
    private var viewport: Viewport? = null

    /**
     * Reads `file` into memory, parses every sub-file, fits the viewport
     * to the TRE bbox, and renders the first frame.
     */
    fun loadAndRender(file: File) {
        loadedFile = file
        ViewerPrefs.saveLastFile(file.absolutePath)
        status = "Loading ${file.name}…"
        try {
            val bytes = file.readBytes()
            imgBytes = bytes
            val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }
            imgFile = img
            viewport = fitToTreBounds(img)
            rerender()
        } catch (e: Exception) {
            imgFile = null
            imgBytes = null
            viewport = null
            rendered = null
            status = "Error loading ${file.name}: ${e.message}"
        }
    }

    /** Re-renders the currently loaded file from disk (re-parse + reset view). */
    fun reload() {
        loadedFile?.let { loadAndRender(it) }
    }

    /** Resets the viewport to the loaded IMG's full TRE bbox. */
    fun homeView() {
        val img = imgFile ?: return
        viewport = fitToTreBounds(img)
        rerender()
    }

    /**
     * Updates the panel size (called by [ViewerApp] via `onSizeChanged`).
     * Triggers a re-render at the new dimensions.
     */
    fun setPanelSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) {
            return
        }
        if (widthPx == this.widthPx && heightPx == this.heightPx) {
            return
        }
        this.widthPx = widthPx
        this.heightPx = heightPx
        // Resize the viewport's pixel dimensions while keeping the centre
        // and the longitude-coverage stable; latitude span is recomputed
        // from the new aspect so Web Mercator stays correct.
        val vp = viewport ?: return
        val centreLon = (vp.minLon.toLong() + vp.maxLon) / 2L
        val centreLat = (vp.minLat.toLong() + vp.maxLat) / 2L
        val lonHalf = (vp.maxLon - vp.minLon) / 2L
        val latHalf = mercatorLatHalf(lonHalf, centreLat.toInt(), widthPx, heightPx)
        viewport = Viewport(
            widthPx = widthPx,
            heightPx = heightPx,
            minLon = (centreLon - lonHalf).toInt(),
            maxLon = (centreLon + lonHalf).toInt(),
            minLat = (centreLat - latHalf).toInt(),
            maxLat = (centreLat + latHalf).toInt(),
        )
        rerender()
    }

    /**
     * Translates the viewport by `dxPx` / `dyPx` screen pixels (positive
     * dx pans right). Re-renders.
     */
    fun panBy(dxPx: Float, dyPx: Float) {
        val vp = viewport ?: return
        val lonRange = (vp.maxLon - vp.minLon).toLong()
        val latRange = (vp.maxLat - vp.minLat).toLong()
        // Negative because dragging the *image* right means the viewport
        // moves *left* in Garmin coords.
        val dLon = -(dxPx.toDouble() / vp.widthPx * lonRange).toLong()
        // Y axis: screen y grows down; latitude grows north (up). Drag
        // image down → viewport pans *up* in lat.
        val dLat = (dyPx.toDouble() / vp.heightPx * latRange).toLong()
        viewport = Viewport(
            widthPx = vp.widthPx,
            heightPx = vp.heightPx,
            minLon = (vp.minLon + dLon).toInt(),
            maxLon = (vp.maxLon + dLon).toInt(),
            minLat = (vp.minLat + dLat).toInt(),
            maxLat = (vp.maxLat + dLat).toInt(),
        )
        rerender()
    }

    /**
     * Zooms by [factor] (>1 = zoom in, <1 = zoom out) anchored at the
     * given screen-pixel coordinates so the world point under the cursor
     * stays put.
     */
    fun zoomAt(factor: Double, anchorXPx: Float, anchorYPx: Float) {
        val vp = viewport ?: return
        if (factor <= 0.0) {
            return
        }
        val anchorLon = vp.minLon + ((anchorXPx / vp.widthPx) * (vp.maxLon - vp.minLon)).toLong()
        // Screen Y → latitude inverted (top of screen = max lat).
        val anchorLat = vp.maxLat - ((anchorYPx / vp.heightPx) * (vp.maxLat - vp.minLat)).toLong()
        val newLonRange = ((vp.maxLon - vp.minLon).toDouble() / factor).toLong().coerceAtLeast(2L)
        // Re-derive lat range from the lon range and panel aspect so
        // every zoom keeps the Web Mercator pixel-square invariant —
        // otherwise small lat-cos drift accumulates across many zooms.
        val newLonHalf = newLonRange / 2L
        val newLatHalf = mercatorLatHalf(newLonHalf, anchorLat.toInt(), vp.widthPx, vp.heightPx)
        val newLatRange = (newLatHalf * 2L).coerceAtLeast(2L)
        // Place the anchor at the same fractional position in the new
        // viewport so the world point under the cursor doesn't move.
        val anchorFracX = anchorXPx / vp.widthPx
        val anchorFracY = anchorYPx / vp.heightPx
        val newMinLon = anchorLon - (newLonRange * anchorFracX).toLong()
        val newMaxLat = anchorLat + (newLatRange * anchorFracY).toLong()
        viewport = Viewport(
            widthPx = vp.widthPx,
            heightPx = vp.heightPx,
            minLon = newMinLon.toInt(),
            maxLon = (newMinLon + newLonRange).toInt(),
            minLat = (newMaxLat - newLatRange).toInt(),
            maxLat = newMaxLat.toInt(),
        )
        rerender()
    }

    /**
     * Renders the currently-loaded IMG at the current [viewport]. Cheap —
     * just walks RGN / draws — so safe to call on every gesture event for
     * Monaco-sized fixtures. Continent-scale IMGs will eventually need
     * a coroutine wrapper.
     */
    private fun rerender() {
        val img = imgFile ?: return
        val vp = viewport ?: return
        val target = AwtRenderTarget(vp.widthPx, vp.heightPx, antiAliased = antiAliased)
        val resolver = img.typStyles?.let { StyleResolver.typAware(it) }
            ?: StyleResolver.internalDefaults()
        ImgRenderer(img, resolver).render(
            viewport = vp,
            target = target,
            backgroundColor = Color.rgb(0xF8, 0xF6, 0xF0),
        )
        rendered = target.image
        val nDeg = CoordUtils.toWGS84(img.tre.header.northBoundary)
        val sDeg = CoordUtils.toWGS84(img.tre.header.southBoundary)
        val eDeg = CoordUtils.toWGS84(img.tre.header.eastBoundary)
        val wDeg = CoordUtils.toWGS84(img.tre.header.westBoundary)
        val viewS = CoordUtils.toWGS84(vp.minLat)
        val viewN = CoordUtils.toWGS84(vp.maxLat)
        val viewW = CoordUtils.toWGS84(vp.minLon)
        val viewE = CoordUtils.toWGS84(vp.maxLon)
        status = "%s — view %.4f..%.4fN / %.4f..%.4fE (TRE bbox %.4f..%.4fN / %.4f..%.4fE)".format(
            loadedFile?.name ?: "?",
            viewS, viewN, viewW, viewE, sDeg, nDeg, wDeg, eDeg,
        )
    }

    /**
     * Builds a [Viewport] whose Garmin bbox = TRE bbox and pixel size =
     * current widthPx with heightPx adjusted to match Mercator aspect at
     * the bbox's centre latitude.
     */
    private fun fitToTreBounds(img: ImgFile): Viewport {
        val nDeg = CoordUtils.toWGS84(img.tre.header.northBoundary)
        val sDeg = CoordUtils.toWGS84(img.tre.header.southBoundary)
        val eDeg = CoordUtils.toWGS84(img.tre.header.eastBoundary)
        val wDeg = CoordUtils.toWGS84(img.tre.header.westBoundary)
        val mercAspect = mercatorAspectRatio(nDeg, sDeg, eDeg, wDeg)
        // Adjust heightPx so the rendered image matches the Mercator
        // projection's true aspect (no horizontal squish), then keep
        // future panel resizes in sync via setPanelSize.
        heightPx = (widthPx / mercAspect).toInt().coerceIn(200, 4096)
        return Viewport.ofTreBounds(
            widthPx = widthPx,
            heightPx = heightPx,
            northBoundary = img.tre.header.northBoundary,
            eastBoundary = img.tre.header.eastBoundary,
            southBoundary = img.tre.header.southBoundary,
            westBoundary = img.tre.header.westBoundary,
        )
    }

    /**
     * Web Mercator aspect ratio for a lat/lon bbox. Larger = wider.
     */
    private fun mercatorAspectRatio(nDeg: Double, sDeg: Double, eDeg: Double, wDeg: Double): Double {
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

    /**
     * Half-extent in latitude (Garmin coords) that, when paired with
     * `lonHalf` on a `widthPx × heightPx` panel centred at
     * [centreLatGarmin], produces a Web Mercator-correct viewport (no
     * horizontal squish).
     *
     * Derived from the small-range Mercator approximation:
     *   1° lon spans `cos(L) × 111km` while 1° lat spans `111km`. To
     *   put the same metres-per-pixel on both axes we need
     *   `Δlat = Δlon × cos(L) × heightPx / widthPx`.
     */
    private fun mercatorLatHalf(
        lonHalf: Long,
        centreLatGarmin: Int,
        widthPx: Int,
        heightPx: Int,
    ): Long {
        if (widthPx <= 0) {
            return lonHalf
        }
        val centreDeg = CoordUtils.toWGS84(centreLatGarmin)
        val cosLat = max(0.01, kotlin.math.cos(centreDeg * PI / 180.0))
        return (lonHalf.toDouble() * cosLat * heightPx / widthPx).toLong()
    }
}
