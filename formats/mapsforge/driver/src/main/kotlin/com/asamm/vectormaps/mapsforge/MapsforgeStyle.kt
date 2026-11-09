/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.StyleMenu
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.ExternalRenderTheme
import com.asamm.vectormaps.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import com.asamm.vectormaps.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File

/**
 * A Mapsforge XML render theme file with its style menu.
 *
 * @param lastModified file time read with the menu; the id and the menu hold for it only
 */
class MapsforgeStyle internal constructor(
    val file: File,
    internal val lastModified: Long,
    override val id: String,
    override val menu: StyleMenu?,
) : MapStyle {

    private val lock = Any()

    // the theme compiled for the latest renderer's settings, one reference held by the style
    private var active: ActiveTheme? = null

    private var closed = false

    /**
     * The compiled theme and its `DisplayModel` for [selection] and [config], with one reference
     * for the caller to release; parsed only when the settings differ from the active theme's,
     * which a replaced theme's last renderer then destroys.
     */
    internal fun acquireTheme(
        graphicFactory: GraphicFactory,
        selection: StyleSelection,
        config: RenderConfig,
    ): ActiveTheme {
        val key = ThemeKey(
            selection = selection,
            tileSizePx = config.tileSizePx,
            deviceScale = config.deviceScale,
            textScale = config.textScale,
        )
        synchronized(lock) {
            check(!closed) {
                "acquireTheme(), style $id closed"
            }
            val current = active
            if (current != null
                && current.key == key
            ) {
                current.themeFuture.incrementRefCount()
                return current
            }
            val parsed = parseTheme(graphicFactory = graphicFactory, key = key)
            current?.themeFuture?.decrementRefCount()
            active = parsed
            parsed.themeFuture.incrementRefCount()
            return parsed
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            active?.themeFuture?.decrementRefCount()
            active = null
        }
    }

    private fun parseTheme(graphicFactory: GraphicFactory, key: ThemeKey): ActiveTheme {
        // within the theme setup, the order of a plain Mapsforge setup, which the golden test compares
        // against
        val displayModel = DisplayModel()
        // new models copy the Mapsforge statics; AndroidGraphicFactory sets the device scale
        displayModel.deviceScale = 1.0f
        displayModel.lineScaleFactor = 1.0f
        displayModel.symbolScaleFactor = 1.0f
        displayModel.textScaleFactor = 1.0f
        // closed ways at every zoom, the upstream default, whatever the Mapsforge statics hold
        displayModel.polygonZoomMin = -1
        displayModel.antiAliasing = true
        displayModel.userScaleFactor = key.deviceScale
        displayModel.setFixedTileSize(key.tileSizePx)
        // larger text wraps later, as in Locus
        displayModel.setMaxTextWidthFactor(MAX_TEXT_WIDTH_FACTOR * key.textScale)
        val themeFuture = RenderThemeFuture(
            graphicFactory,
            ExternalRenderTheme(file, menuCallback(key.selection)),
            displayModel,
        )
        try {
            themeFuture.run()
            // the renderer only logs a parse failure; fail here with the real cause
            themeFuture.get()
        } catch (e: Exception) {
            themeFuture.decrementRefCount()
            throw e
        }
        return ActiveTheme(key = key, themeFuture = themeFuture, displayModel = displayModel)
    }

    /**
     * Categories for `selection`: the chosen layer's own plus its enabled overlays'. A layer the
     * theme does not have, such as a stale preference, or an unusable menu renders every category,
     * as in Locus.
     */
    internal fun menuCallback(selection: StyleSelection): XmlRenderThemeMenuCallback {
        return SelectionCallback(selection = selection, hasMenu = menu != null)
    }

    /**
     * A compiled theme shared by the renderers whose settings match [key].
     */
    internal class ActiveTheme(
        val key: ThemeKey,
        val themeFuture: RenderThemeFuture,
        val displayModel: DisplayModel,
    )

    /**
     * What the compiled theme depends on: the label language only affects the readers.
     */
    internal data class ThemeKey(
        val selection: StyleSelection,
        val tileSizePx: Int,
        val deviceScale: Float,
        val textScale: Float,
    )

    private class SelectionCallback(
        private val selection: StyleSelection,
        private val hasMenu: Boolean,
    ) : XmlRenderThemeMenuCallback {

        override fun getCategories(style: XmlRenderThemeStyleMenu): Set<String>? {
            if (!hasMenu) {
                return null
            }
            val layerId = selection.layerId ?: style.defaultValue
            // null renders every category
            val layer = style.getLayer(layerId) ?: return null
            val result = HashSet(layer.categories)
            for (overlay in layer.overlays) {
                if (selection.overlayIds?.contains(overlay.id) ?: overlay.isEnabled) {
                    result.addAll(overlay.categories)
                }
            }
            return result
        }
    }

    private companion object {

        // Mapsforge's default
        const val MAX_TEXT_WIDTH_FACTOR = 0.7f
    }
}
