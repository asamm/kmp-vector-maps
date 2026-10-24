/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import kotlin.math.max
import kotlin.math.min

/**
 * A WGS84 bounding box in degrees; does not cross the antimeridian.
 */
data class GeoBounds(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {

    init {
        require(minLat >= -90.0
            && maxLat <= 90.0
            && minLat <= maxLat
        ) {
            "GeoBounds(), latitudes $minLat..$maxLat"
        }
        require(minLon >= -180.0
            && maxLon <= 180.0
            && minLon <= maxLon
        ) {
            "GeoBounds(), longitudes $minLon..$maxLon"
        }
    }

    fun union(other: GeoBounds): GeoBounds {
        return GeoBounds(
            minLat = min(minLat, other.minLat),
            minLon = min(minLon, other.minLon),
            maxLat = max(maxLat, other.maxLat),
            maxLon = max(maxLon, other.maxLon),
        )
    }
}
