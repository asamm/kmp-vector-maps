/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

/**
 * A style source of one format with its menu: Mapsforge theme, Garmin TYP, MVT style. Immutable
 * and shared; each renderer compiles it for its own selection and configuration.
 */
interface MapStyle {

    /**
     * Identifies the style source and its version, such as path and modification time; two
     * styles with equal ids render alike.
     */
    val id: String

    /**
     * Layers a user can choose from; null when the style has no usable menu.
     */
    val menu: StyleMenu?
}

/**
 * A style's menu with the format's own ids, which hosts store in their preferences.
 *
 * @param defaultLayerId layer used when a selection names none
 */
data class StyleMenu(
    val id: String,
    val defaultLayerId: String,
    val defaultLanguage: String,
    val layers: List<StyleLayer>,
) {

    fun layer(id: String): StyleLayer? {
        return layers.firstOrNull { it.id == id }
    }
}

/**
 * One menu entry: a base layer or an overlay.
 *
 * @param titles title per language code
 * @param isVisible shown to the user; hidden layers are only included by other layers
 * @param isEnabled an overlay's default state
 * @param overlayIds overlays the user can toggle on top of this layer
 */
data class StyleLayer(
    val id: String,
    val titles: Map<String, String>,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val overlayIds: List<String>,
)

/**
 * The user's choice in a [StyleMenu].
 *
 * @param layerId chosen layer; null for the menu default
 * @param overlayIds enabled overlays; null for each overlay's default state
 */
data class StyleSelection(
    val layerId: String? = null,
    val overlayIds: Set<String>? = null,
) {

    companion object {

        /**
         * The menu's default layer with default overlays, or no selection for a style without a
         * menu.
         */
        val DEFAULT = StyleSelection()
    }
}
