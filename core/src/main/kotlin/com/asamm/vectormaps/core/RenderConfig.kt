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
    val labelMode: LabelMode = LabelMode.IN_TILE,
) {

    init {
        require(tileSizePx > 0) {
            "RenderConfig(), tileSizePx $tileSizePx"
        }
        require(deviceScale > 0.0f
            && textScale > 0.0f
        ) {
            "RenderConfig(), deviceScale $deviceScale, textScale $textScale"
        }
    }
}

enum class LabelMode {

    /**
     * Labels are drawn into the tiles, placed across tile edges; until M6.1 the result depends on
     * which neighbour tiles were rendered first.
     */
    IN_TILE,

    /**
     * Tiles carry no labels; the host draws them in a separate screen-space pass.
     */
    SEPARATE,
}

/**
 * Everything besides the dataset and the tile that decides a renderer's output: on one dataset,
 * equal keys give equal tiles, for [LabelMode.IN_TILE] only from M6.1. Hosts keep one tile cache
 * per dataset, keyed by it, and invalidate it after [MapDataset.addMap].
 */
data class RenderKey(
    val styleId: String,
    val selection: StyleSelection,
    val config: RenderConfig,
)
