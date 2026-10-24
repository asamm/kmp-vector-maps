/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapInfo
import com.asamm.vectormaps.core.StyleLayer
import com.asamm.vectormaps.core.StyleMenu
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler
import java.io.File

/**
 * Mapsforge `.map` v5 files with XML render themes, drawn by the vendored Mapsforge renderer.
 *
 * @param graphicFactory the host's graphics backend, such as `AwtGraphicFactory.INSTANCE`; its
 * renderers fill [TileBitmapTarget]s with this backend's bitmaps
 */
class MapsforgeDriver(
    val graphicFactory: GraphicFactory,
) : MapFormatDriver {

    override val id: String = "mapsforge"

    override val extensions: Set<String> = setOf("map")

    override fun probe(file: File): MapInfo? {
        val mapFile = MapFile(file)
        try {
            val box = mapFile.boundingBox()
            return MapInfo(
                bounds = GeoBounds(
                    minLat = box.minLatitude,
                    minLon = box.minLongitude,
                    maxLat = box.maxLatitude,
                    maxLon = box.maxLongitude,
                ),
                languages = mapFile.mapLanguages?.toList() ?: emptyList(),
            )
        } finally {
            mapFile.close()
        }
    }

    override fun open(files: List<File>): MapsforgeDataset {
        return MapsforgeDataset(driver = this, files = files)
    }

    /**
     * Reads the theme up to its style menu, or up to its first image when it has none, so no
     * symbol is built at the wrong scale; rules are compiled per renderer.
     */
    override fun loadStyle(file: File): MapsforgeStyle {
        val lastModified = file.lastModified()
        val reader = MenuReader()
        val xmlTheme = ExternalRenderTheme(file, reader)
        xmlTheme.resourceProvider = XmlThemeResourceProvider { _, _ ->
            throw MenuRead()
        }
        try {
            RenderThemeHandler.getRenderTheme(graphicFactory, DisplayModel(), xmlTheme)
        } catch (e: MenuRead) {
            // expected: stops the parse after the menu or at the first image
        }
        return MapsforgeStyle(
            file = file,
            lastModified = lastModified,
            id = "${file.absolutePath}@$lastModified",
            menu = reader.menu?.let { toStyleMenu(it) },
        )
    }

    /**
     * Null for a menu missing a required attribute or naming an absent default layer; the style
     * then renders every category.
     */
    private fun toStyleMenu(menu: XmlRenderThemeStyleMenu): StyleMenu? {
        val id = menu.id
        val defaultLayerId = menu.defaultValue
        val defaultLanguage = menu.defaultLanguage
        if (id == null
            || defaultLayerId == null
            || defaultLanguage == null
            || menu.getLayer(defaultLayerId) == null
        ) {
            return null
        }
        return StyleMenu(
            id = id,
            defaultLayerId = defaultLayerId,
            defaultLanguage = defaultLanguage,
            layers = menu.layers.values.filter { it.id != null }.map { layer ->
                StyleLayer(
                    id = layer.id,
                    titles = layer.titles.toMap(),
                    isVisible = layer.isVisible,
                    isEnabled = layer.isEnabled,
                    overlayIds = layer.overlays.mapNotNull { it.id },
                )
            },
        )
    }

    private class MenuReader : XmlRenderThemeMenuCallback {

        var menu: XmlRenderThemeStyleMenu? = null

        override fun getCategories(style: XmlRenderThemeStyleMenu): Set<String> {
            menu = style
            throw MenuRead()
        }
    }

    private class MenuRead : RuntimeException() {

        override fun fillInStackTrace(): Throwable {
            return this
        }
    }
}
