/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.rendertheme

import java.io.Serializable

/**
 * Entry class for automatically building menus from rendertheme V4+ files: holds the layers of
 * the theme's style menu in document order. Serializable to pass through an Android Intent; its
 * fields keep the upstream names. A theme may omit the attributes, so they are nullable.
 */
open class XmlRenderThemeStyleMenu(
    val id: String?,
    val defaultLanguage: String?,
    val defaultValue: String?,
) : Serializable {

    val layers: MutableMap<String?, XmlRenderThemeStyleLayer> = LinkedHashMap()

    fun createLayer(id: String?, visible: Boolean, enabled: Boolean): XmlRenderThemeStyleLayer {
        val style = XmlRenderThemeStyleLayer(id, visible, enabled, this.defaultLanguage)
        this.layers[id] = style
        return style
    }

    fun getLayer(id: String?): XmlRenderThemeStyleLayer? {
        return this.layers[id]
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
