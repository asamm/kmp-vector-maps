/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench

import com.asamm.vectormaps.core.TileRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Tile helpers the JVM benches share.
 */
internal object BenchTiles {

    /**
     * The `lat,lon` (or `lat:lon`) of system property `name`; null when unset.
     */
    fun center(name: String): Pair<Double, Double>? {
        return System.getProperty(name).orEmpty()
            .takeIf { it.isNotBlank() }
            ?.split(Regex("[,:]"))
            ?.let { it[0].trim().toDouble() to it[1].trim().toDouble() }
    }

    /**
     * The Web Mercator tile at `zoom` holding (`lat`, `lon`).
     */
    fun tileOf(lat: Double, lon: Double, zoom: Int): TileRequest {
        val n = (1 shl zoom).toDouble()
        val latRad = Math.toRadians(lat)
        return TileRequest(
            x = floor((lon + 180.0) / 360.0 * n).toInt(),
            y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt(),
            zoom = zoom,
        )
    }
}
