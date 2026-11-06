/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

/**
 * Per-renderer settings that formats would otherwise keep in globals, such as Mapsforge's device
 * scale and tile size.
 *
 * @param deviceScale size of a style pixel in screen pixels
 * @param textScale extra factor on label text, on top of `deviceScale`
 * @param language preferred label language; null for the map's default
 */
data class RenderConfig(
    val tileSizePx: Int,
    val deviceScale: Float = 1.0f,
    val textScale: Float = 1.0f,
    val language: String? = null,
) {

    init {
        require(tileSizePx > 0) {
            "RenderConfig(), tileSizePx $tileSizePx"
        }
        require(deviceScale > 0.0f
            && deviceScale.isFinite()
            && textScale > 0.0f
            && textScale.isFinite()
        ) {
            "RenderConfig(), deviceScale $deviceScale, textScale $textScale"
        }
    }
}

/**
 * Everything besides the dataset, the tile, driver-specific renderer options (such as
 * `GarminOptions`) and [RenderHooks] that decides a renderer's output: on one dataset, equal keys give equal tiles.
 * Hosts keep one tile cache per dataset, keyed by it; after [MapDataset.addMap] they clear that
 * cache and recreate their renderers.
 */
data class RenderKey(
    val styleId: String,
    val selection: StyleSelection,
    val config: RenderConfig,
)
