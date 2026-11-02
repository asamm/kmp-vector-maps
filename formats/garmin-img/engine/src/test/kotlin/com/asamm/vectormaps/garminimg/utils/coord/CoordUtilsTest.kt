/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.coord

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Smoke tests for [CoordUtils] coordinate helpers.
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

    @Test
    fun radianRoundTripIsIdentity() {
        val angles = doubleArrayOf(0.0, PI / 4, -PI / 4, PI - 0.01, -PI + 0.01)
        for (rad in angles) {
            val garmin = CoordUtils.fromWGS84Rad(rad)
            val back = CoordUtils.toWGS84Rad(garmin)
            assertEquals(rad, back, 1e-6, "rad round-trip failed for $rad")
        }
    }

    @Test
    fun overlappingBoundingBoxesMatch() {
        // box A: [0,10] x [0,10]; box B: [5,15] x [5,15] — overlap
        assertTrue(CoordUtils.matchesCoordinates(0, 10, 0, 10, 5, 15, 5, 15))
    }

    @Test
    fun disjointBoundingBoxesDoNotMatch() {
        assertFalse(CoordUtils.matchesCoordinates(0, 10, 0, 10, 20, 30, 20, 30))
    }

    @Test
    fun touchingBoundingBoxesMatch() {
        // edge-touching is treated as a match by matchesCoordinates
        assertTrue(CoordUtils.matchesCoordinates(0, 10, 0, 10, 10, 20, 0, 10))
    }

    @Test
    fun includedInCoordinatesDetectsContainment() {
        assertTrue(CoordUtils.includedInCoordinates(2, 8, 2, 8, 0, 10, 0, 10))
        assertFalse(CoordUtils.includedInCoordinates(0, 12, 2, 8, 0, 10, 0, 10))
    }

    @Test
    fun pointInBoxOverload() {
        assertTrue(CoordUtils.includedInCoordinates(5, 5, 0, 10, 0, 10))
        assertFalse(CoordUtils.includedInCoordinates(11, 5, 0, 10, 0, 10))
    }

    @Test
    fun dmsFormattingForKnownValues() {
        // 0 → 0d00'00.00 (canonical zero)
        val zero = CoordUtils.toDMS(0)
        assertTrue(zero.contains("0d"), "expected zero DMS, got: $zero")
    }

    private companion object {
        private const val WGS84_TOLERANCE: Double = 1e-4
    }
}
