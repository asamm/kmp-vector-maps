/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelPlacerTest {

    @Test
    fun higherPriorityWinsAndIsDrawnLast() {
        val low = point(id = 1, x = 0.0, y = 0.0, priority = 1)
        val high = point(id = 2, x = 5.0, y = 0.0, priority = 9)
        val apart = point(id = 3, x = 500.0, y = 0.0, priority = 5)

        val placed = ids(LabelPlacer.place(listOf(low, apart, high), rotationDeg = 0.0))

        assertEquals(listOf(3L, 2L), placed)
    }

    @Test
    fun equalPrioritiesBrokenByIdInAnyInputOrder() {
        val candidates = (1L..20L).map { id ->
            point(id = id, x = (id % 5) * 15.0, y = (id / 5) * 15.0, priority = 3)
        }
        val expected = ids(LabelPlacer.place(candidates, rotationDeg = 0.0))

        val random = Random(7)
        repeat(10) {
            assertEquals(expected, ids(LabelPlacer.place(candidates.shuffled(random), 0.0)))
        }
        assertTrue(1L in expected)
    }

    @Test
    fun sameIdIsPlacedOnce() {
        val fromTileA = point(id = 4, x = 0.0, y = 0.0, priority = 2)
        val fromTileB = point(id = 4, x = 0.0, y = 0.0, priority = 2)

        assertEquals(listOf(4L), ids(LabelPlacer.place(listOf(fromTileA, fromTileB), 0.0)))
    }

    @Test
    fun touchingBoxesClash() {
        // half width 10: centres 20 apart touch, 20.5 apart do not
        val first = point(id = 1, x = 0.0, y = 0.0, priority = 1)

        val touching = LabelPlacer.place(listOf(first, point(2, 20.0, 0.0, 1)), 0.0)
        val apart = LabelPlacer.place(listOf(first, point(2, 20.5, 0.0, 1)), 0.0)

        assertEquals(1, touching.size)
        assertEquals(2, apart.size)
    }

    @Test
    fun uprightBoxesClashOnceTheMapTurns() {
        // wide upright labels stacked 30 px apart; turned 90 degrees, they stand side by side
        val upper = point(id = 1, x = 0.0, y = 0.0, priority = 1, halfWidth = 50.0)
        val lower = point(id = 2, x = 0.0, y = 30.0, priority = 1, halfWidth = 50.0)

        assertEquals(2, LabelPlacer.place(listOf(upper, lower), rotationDeg = 0.0).size)
        assertEquals(listOf(1L), ids(LabelPlacer.place(listOf(upper, lower), rotationDeg = 90.0)))
    }

    @Test
    fun mapAlignedBoxTurnsWithTheMap() {
        // a line symbol along x, a point 20 px off the line: apart at every rotation
        val along = lineSymbol(id = 1, alignment = BoxAlignment.MAP)
        val beside = point(
            id = 2,
            x = 0.0,
            y = -20.0,
            priority = 1,
            halfWidth = 3.0,
            halfHeight = 3.0,
        )

        for (rotation in listOf(0.0, 45.0, 90.0, 210.0)) {
            assertEquals(2, LabelPlacer.place(listOf(along, beside), rotation).size, "$rotation")
        }

        // the same box kept upright lies across the point once the map turns 90 degrees
        val upright = lineSymbol(id = 1, alignment = BoxAlignment.VIEWPORT)
        assertEquals(listOf(1L), ids(LabelPlacer.place(listOf(upright, beside), 90.0)))
    }

    @Test
    fun turnedBoxesOverlapOnlyWhenTheirShapesDo() {
        // two 45-degree bars side by side: their bounds overlap, the bars do not
        val first = rotatedBar(id = 1, x = 0.0)
        val second = rotatedBar(id = 2, x = 30.0)
        val crossing = rotatedBar(id = 3, x = 30.0, angleDeg = -45.0)

        assertEquals(2, LabelPlacer.place(listOf(first, second), 0.0).size)
        assertEquals(1, LabelPlacer.place(listOf(first, crossing), 0.0).size)
    }

    @Test
    fun displayGroups() {
        val ifSpace = point(id = 1, x = 0.0, y = 0.0, priority = 5)
        val always = point(id = 2, x = 0.0, y = 0.0, priority = 1, display = LabelDisplay.ALWAYS)
        val order = point(id = 3, x = 0.0, y = 0.0, priority = 4, display = LabelDisplay.ORDER)
        val orderLow = point(id = 4, x = 0.0, y = 0.0, priority = 3, display = LabelDisplay.ORDER)
        val ifSpaceLow = point(id = 5, x = 0.0, y = 0.0, priority = 2)

        val placed = LabelPlacer.place(listOf(ifSpace, always, order, orderLow, ifSpaceLow), 0.0)

        assertEquals(setOf(1L, 2L, 3L), ids(placed).toSet())
    }

    @Test
    fun alwaysNeverBlocksAndDrawsOnTopAtEqualPriority() {
        // apart, so all are kept: at equal priority ALWAYS draws above ORDER above IF_SPACE
        val ifSpace = point(id = 1, x = 0.0, y = 0.0, priority = 3)
        val always = point(id = 2, x = 100.0, y = 0.0, priority = 3, display = LabelDisplay.ALWAYS)
        val order = point(id = 3, x = 200.0, y = 0.0, priority = 3, display = LabelDisplay.ORDER)

        assertEquals(
            listOf(1L, 3L, 2L),
            ids(LabelPlacer.place(listOf(always, order, ifSpace), rotationDeg = 0.0)),
        )

        // a top-priority ALWAYS label over a lower IF_SPACE one blocks nothing
        val alwaysTop = point(id = 4, x = 0.0, y = 0.0, priority = 9, display = LabelDisplay.ALWAYS)
        assertEquals(2, LabelPlacer.place(listOf(alwaysTop, ifSpace), rotationDeg = 0.0).size)
    }

    @Test
    fun equalPointTextsCloseTogetherClash() {
        val town = pointText(id = 1, x = 0.0, text = "Brno")
        val near = pointText(id = 2, x = 150.0, text = "Brno")
        val far = pointText(id = 2, x = 250.0, text = "Brno")
        val other = pointText(id = 2, x = 150.0, text = "Praha")

        assertEquals(listOf(1L), ids(LabelPlacer.place(listOf(town, near), rotationDeg = 0.0)))
        assertEquals(2, LabelPlacer.place(listOf(town, far), rotationDeg = 0.0).size)
        assertEquals(2, LabelPlacer.place(listOf(town, other), rotationDeg = 0.0).size)

        // without a radius, equal texts only clash by their boxes
        val street = pathText(id = 3, path = doubleArrayOf(0.0, 0.0, 100.0, 0.0))
        val sameStreet = pathText(id = 4, path = doubleArrayOf(150.0, 0.0, 250.0, 0.0))
        assertEquals(2, LabelPlacer.place(listOf(street, sameStreet), rotationDeg = 0.0).size)
    }

    @Test
    fun candidateWithoutBoxesNeverClashes() {
        val solid = point(id = 1, x = 0.0, y = 0.0, priority = 5)
        val empty = LabelCandidate(
            id = 2,
            kind = LabelKind.POINT,
            x = 0.0,
            y = 0.0,
            priority = 1,
            boxes = emptyList(),
        )

        assertEquals(2, LabelPlacer.place(listOf(solid, empty), 0.0).size)
    }

    @Test
    fun pathTextReadsUpright() {
        val leftwards = pathText(id = 1, path = doubleArrayOf(100.0, 0.0, 0.0, 0.0))
        val rightwards = pathText(id = 1, path = doubleArrayOf(0.0, 0.0, 100.0, 0.0))

        assertTrue(LabelPlacer.place(listOf(leftwards), 0.0).single().isReversed)
        assertFalse(LabelPlacer.place(listOf(leftwards), 180.0).single().isReversed)
        assertFalse(LabelPlacer.place(listOf(rightwards), 0.0).single().isReversed)
        assertTrue(LabelPlacer.place(listOf(rightwards), 180.0).single().isReversed)
        assertFalse(LabelPlacer.place(listOf(point(2, 0.0, 0.0, 1)), 180.0).single().isReversed)

        // a vertical path reverses, as Mapsforge's `auto`
        val vertical = pathText(id = 3, path = doubleArrayOf(0.0, 0.0, 0.0, 100.0))
        assertTrue(LabelPlacer.place(listOf(vertical), 0.0).single().isReversed)
    }

    @Test
    fun fixedDirectionIgnoresRotation() {
        val path = doubleArrayOf(100.0, 0.0, 0.0, 0.0)
        val forward = pathText(id = 1, path = path, direction = PathTextDirection.FORWARD)
        val reverse = pathText(id = 1, path = path, direction = PathTextDirection.REVERSE)

        for (rotation in listOf(0.0, 180.0)) {
            assertFalse(LabelPlacer.place(listOf(forward), rotation).single().isReversed)
            assertTrue(LabelPlacer.place(listOf(reverse), rotation).single().isReversed)
        }

        // upside down: the leftwards path is drawn as given at 0 degrees, flipped at 180
        val down = pathText(id = 1, path = path, direction = PathTextDirection.UPSIDE_DOWN)
        assertFalse(LabelPlacer.place(listOf(down), 0.0).single().isReversed)
        assertTrue(LabelPlacer.place(listOf(down), 180.0).single().isReversed)
    }

    @Test
    fun pathIsCopied() {
        val path = doubleArrayOf(0.0, 0.0, 100.0, 0.0)
        val candidate = pathText(id = 1, path = path)

        path[2] = -100.0

        assertEquals(100.0, candidate.path!![2])
        assertFalse(LabelPlacer.place(listOf(candidate), 0.0).single().isReversed)
    }

    @Test
    fun absurdGeometryDoesNotBreakTheGrid() {
        val huge = LabelCandidate(
            id = 1,
            kind = LabelKind.POINT,
            x = 0.0,
            y = 0.0,
            priority = 9,
            boxes = listOf(
                CollisionBox(centerX = 0.0, centerY = -1e38, halfWidth = 1e300, halfHeight = 1.0),
            ),
        )
        val far = point(id = 2, x = 1e12, y = 0.0, priority = 1)

        // whether the two clash is beside the point at this precision; placing them must not throw
        val placed = LabelPlacer.place(listOf(huge, far), rotationDeg = 30.0)

        assertTrue(1L in ids(placed))
    }

    @Test
    fun gridMatchesCheckingEveryPair() {
        val random = Random(42)
        repeat(20) { round ->
            val candidates = List(400) { i ->
                val alignment = BoxAlignment.entries[random.nextInt(BoxAlignment.entries.size)]
                LabelCandidate(
                    // many repeated ids, each a different candidate, so deduplication runs too
                    id = random.nextLong(0, 380),
                    kind = LabelKind.POINT,
                    x = 100_000.0 + random.nextDouble(0.0, 3000.0),
                    y = 50_000.0 + random.nextDouble(0.0, 3000.0),
                    priority = random.nextInt(0, 5),
                    boxes = List(1 + random.nextInt(3)) {
                        CollisionBox(
                            centerX = random.nextDouble(-40.0, 40.0),
                            centerY = random.nextDouble(-40.0, 40.0),
                            halfWidth = random.nextDouble(1.0, 300.0),
                            halfHeight = random.nextDouble(1.0, 20.0),
                            angleDeg = random.nextDouble(0.0, 360.0),
                            alignment = alignment,
                        )
                    },
                    display = LabelDisplay.entries[random.nextInt(LabelDisplay.entries.size)],
                )
            }
            val rotation = random.nextDouble(0.0, 360.0)

            assertEquals(
                everyPair(candidates, rotation),
                ids(LabelPlacer.place(candidates, rotation)),
                "round $round",
            )
        }
    }

    @Test
    fun rejectsInconsistentCandidates() {
        assertFailsWith<IllegalArgumentException> {
            LabelCandidate(
                id = 1,
                kind = LabelKind.PATH_TEXT,
                x = 0.0,
                y = 0.0,
                priority = 1,
                boxes = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            pathText(id = 1, path = doubleArrayOf(0.0, 0.0, 1.0))
        }
        assertFailsWith<IllegalArgumentException> {
            point(id = 1, x = Double.NaN, y = 0.0, priority = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            CollisionBox(centerX = 0.0, centerY = 0.0, halfWidth = -1.0, halfHeight = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            LabelPlacer.place(listOf(point(id = 1, x = 0.0, y = 0.0, priority = 1)), Double.NaN)
        }
    }

    // reference placement: every candidate checked against every kept one
    private fun everyPair(candidates: List<LabelCandidate>, rotationDeg: Double): List<Long> {
        val order = candidates
            .sortedWith(LabelPlacer.PLACEMENT_ORDER)
            .distinctBy { it.id }
        val radians = Math.toRadians(rotationDeg)
        val shapes = order.map {
            ScreenShape.of(candidate = it, cos = cos(radians), sin = sin(radians))
        }
        val kept = ArrayList<Int>()
        for (i in order.indices) {
            val clashes = order[i].display != LabelDisplay.ALWAYS
                && kept.any { j ->
                    order[j].display == order[i].display
                        && shapes[i] != null
                        && shapes[j] != null
                        && shapes[i]!!.overlaps(shapes[j]!!)
                }
            if (!clashes) {
                kept.add(i)
            }
        }
        return kept.map { order[it].id }.reversed()
    }

    private fun ids(placed: List<PlacedLabel>): List<Long> {
        return placed.map { it.candidate.id }
    }

    private fun point(
        id: Long,
        x: Double,
        y: Double,
        priority: Int,
        halfWidth: Double = 10.0,
        halfHeight: Double = 5.0,
        display: LabelDisplay = LabelDisplay.IF_SPACE,
    ): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.POINT,
            x = x,
            y = y,
            priority = priority,
            boxes = listOf(
                CollisionBox(
                    centerX = 0.0,
                    centerY = 0.0,
                    halfWidth = halfWidth,
                    halfHeight = halfHeight,
                ),
            ),
            display = display,
        )
    }

    private fun lineSymbol(id: Long, alignment: BoxAlignment): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.LINE_SYMBOL,
            x = 0.0,
            y = 0.0,
            priority = 2,
            boxes = listOf(
                CollisionBox(
                    centerX = 0.0,
                    centerY = 0.0,
                    halfWidth = 50.0,
                    halfHeight = 2.0,
                    alignment = alignment,
                ),
            ),
        )
    }

    private fun rotatedBar(id: Long, x: Double, angleDeg: Double = 45.0): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.LINE_SYMBOL,
            x = x,
            y = 0.0,
            priority = 1,
            boxes = listOf(
                CollisionBox(
                    centerX = 0.0,
                    centerY = 0.0,
                    halfWidth = 40.0,
                    halfHeight = 3.0,
                    angleDeg = angleDeg,
                ),
            ),
        )
    }

    private fun pointText(id: Long, x: Double, text: String): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.POINT,
            x = x,
            y = 0.0,
            priority = 1,
            // small boxes: they never overlap at these distances
            boxes = listOf(
                CollisionBox(centerX = 0.0, centerY = 0.0, halfWidth = 5.0, halfHeight = 5.0),
            ),
            text = LabelText(value = text, sizePx = 12.0f, fillArgb = 0xFF000000.toInt()),
            sameTextRadiusPx = 200.0,
        )
    }

    private fun pathText(
        id: Long,
        path: DoubleArray,
        direction: PathTextDirection = PathTextDirection.UPRIGHT,
    ): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.PATH_TEXT,
            x = path[0],
            y = path[1],
            priority = 1,
            boxes = listOf(
                CollisionBox(centerX = 50.0, centerY = 0.0, halfWidth = 50.0, halfHeight = 6.0),
            ),
            text = LabelText(value = "Main Street", sizePx = 12.0f, fillArgb = 0xFF000000.toInt()),
            path = path,
            direction = direction,
        )
    }
}
