/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Color
import com.asamm.vectormaps.mvt.style.EvalContext
import com.asamm.vectormaps.mvt.style.Expression
import com.asamm.vectormaps.mvt.style.ExpressionCompiler
import com.asamm.vectormaps.mvt.tile.Geometry

/**
 * Where tile (`zoom`, `x`, `y`) of `widthPx` by `heightPx` sits in the data tile it is drawn from:
 * itself up to `maxDataZoom`, its ancestor at that zoom above it (overzoom).
 *
 * @param scale screen pixels per style pixel on a 512 px tile, the host's device scale
 */
internal class DataTileFrame(
    zoom: Int,
    x: Int,
    y: Int,
    private val widthPx: Int,
    private val heightPx: Int,
    maxDataZoom: Int,
    scale: Float = 1f,
) {

    val dataZoom = minOf(zoom, maxDataZoom)

    private val dz = zoom - dataZoom

    val dataX = x shr dz

    val dataY = y shr dz

    // the requested tile's offset within its data tile, in pixels
    private val offsetX = (x - (dataX shl dz)).toFloat() * widthPx

    private val offsetY = (y - (dataY shl dz)).toFloat() * heightPx

    /**
     * Screen pixels per style pixel: 1:1 at 512 px tiles, as MapLibre's, times the device scale.
     */
    val pixelScale = widthPx / 512f * scale

    /**
     * True when `geometry`, widened by `marginPx` on every side, meets the requested tile; above
     * the data zoom most of the data tile's features lie outside it.
     */
    fun meets(geometry: Geometry, extent: Int, marginPx: Float): Boolean {
        val sx = widthPx * (1 shl dz).toFloat() / extent
        val sy = heightPx * (1 shl dz).toFloat() / extent
        return geometry.maxX * sx - offsetX >= -marginPx
            && geometry.minX * sx - offsetX <= widthPx + marginPx
            && geometry.maxY * sy - offsetY >= -marginPx
            && geometry.minY * sy - offsetY <= heightPx + marginPx
    }

    /**
     * `geometry` in the requested tile's pixels, for a layer of `extent`.
     */
    fun project(geometry: Geometry, extent: Int): FloatArray {
        val sx = widthPx * (1 shl dz).toFloat() / extent
        val sy = heightPx * (1 shl dz).toFloat() / extent
        val coords = geometry.coords
        val out = FloatArray(coords.size)
        for (i in coords.indices step 2) {
            out[i] = coords[i] * sx - offsetX
            out[i + 1] = coords[i + 1] * sy - offsetY
        }
        return out
    }
}

/**
 * A layer property's number, or `default` when it is absent or not a number.
 */
internal fun number(e: Expression?, ctx: EvalContext, default: Double): Double {
    return e?.eval(ctx) as? Double ?: default
}

/**
 * A layer property's colour, or null.
 */
internal fun color(e: Expression?, ctx: EvalContext): Color? {
    return ExpressionCompiler.toColor(e?.eval(ctx))
}
