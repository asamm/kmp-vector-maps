/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import java.io.Closeable

/**
 * Renders tiles of one dataset with one style, selection and configuration. Safe to call from up
 * to [maxParallelism] threads at once; it bounds itself beyond that.
 */
interface MapRenderer : Closeable {

    val key: RenderKey

    val maxParallelism: Int

    /**
     * Draws `tile` into `target`, whose size must match the configured tile size and whose type
     * the driver must support (`IllegalArgumentException` otherwise). Checks `isCancelled` before
     * drawing and, where the driver can, between drawing steps; leaves no label state half-filled.
     * Throws when the data cannot be read or drawn; [RenderResult.EMPTY] only for absent data.
     * Cancel through `isCancelled`: interrupting the thread may break the renderer for good.
     */
    fun render(tile: TileRequest, target: RenderTarget, isCancelled: () -> Boolean): RenderResult
}

/**
 * A tile in the Web Mercator tile grid.
 */
data class TileRequest(
    val x: Int,
    val y: Int,
    val zoom: Int,
) {

    init {
        require(zoom in 0..MAX_ZOOM) {
            "TileRequest(), zoom $zoom"
        }
        val max = (1 shl zoom) - 1
        require(x in 0..max
            && y in 0..max
        ) {
            "TileRequest(), tile $x/$y outside zoom $zoom"
        }
    }

    companion object {

        // keeps `1 shl zoom` a positive Int
        const val MAX_ZOOM = 30
    }
}

/**
 * Where a tile is drawn. Each driver defines its target types; a type's KDoc states who owns and
 * releases the output.
 */
interface RenderTarget {

    val widthPx: Int

    val heightPx: Int
}

enum class RenderResult {

    /**
     * The target holds the tile.
     */
    DRAWN,

    /**
     * The dataset has no data for the tile; the target is untouched.
     */
    EMPTY,

    /**
     * Stopped by the cancel check; the target content is undefined.
     */
    CANCELLED,
}
