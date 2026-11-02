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
package com.asamm.vectormaps.mapsforge.map.rendertheme

import java.io.Serializable

/**
 * An individual layer in the rendertheme V4+ menu system: its translated titles, the categories
 * it enables and its optional overlays. Its fields keep the upstream names, for the serialized
 * form.
 */
open class XmlRenderThemeStyleLayer internal constructor(
    val id: String?,
    private val visible: Boolean,
    private val enabled: Boolean,
    private val defaultLanguage: String?,
) : Serializable {

    val categories: MutableSet<String> = LinkedHashSet()
    val overlays: MutableList<XmlRenderThemeStyleLayer> = ArrayList()
    val titles: MutableMap<String, String> = HashMap()

    val isEnabled: Boolean
        get() = this.enabled

    val isVisible: Boolean
        get() = this.visible

    fun addCategory(category: String) {
        this.categories.add(category)
    }

    fun addOverlay(overlay: XmlRenderThemeStyleLayer) {
        this.overlays.add(overlay)
    }

    fun addTranslation(language: String, name: String) {
        this.titles[language] = name
    }

    /**
     * The title in [language], else in the menu's default language.
     */
    fun getTitle(language: String?): String? {
        return this.titles[language] ?: this.titles[this.defaultLanguage]
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
