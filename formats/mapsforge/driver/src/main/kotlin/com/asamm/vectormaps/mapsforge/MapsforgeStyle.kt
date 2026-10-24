/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.StyleMenu
import com.asamm.vectormaps.core.StyleSelection
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
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

    /**
     * Categories for `selection`: the chosen layer's own plus its enabled overlays'. A layer the
     * theme does not have, such as a stale preference, or an unusable menu renders every category,
     * as in Locus.
     */
    internal fun menuCallback(selection: StyleSelection): XmlRenderThemeMenuCallback {
        return SelectionCallback(selection = selection, hasMenu = menu != null)
    }

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
}
