/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.coord

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test for the [CoordUtils] conversions.
 * Ported from the libGarminImg Java tests; behavioural parity, no logic change.
 */
class CoordUtilsTest {

    @Test
    fun wgs84RoundTripIsIdentityForCardinalPoints() {
        for (deg in doubleArrayOf(0.0, 90.0, -90.0, 179.99, -179.99, 49.2, -33.8)) {
            val garmin = CoordUtils.fromWGS84(deg)
            val back = CoordUtils.toWGS84(garmin)
            assertEquals(deg, back, WGS84_TOLERANCE, "WGS84 round-trip failed for $deg")
        }
    }

    private companion object {
        private const val WGS84_TOLERANCE: Double = 1e-4
    }
}
