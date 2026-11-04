/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingCheckTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun tileWithinLimitPasses() {
        val check = check("")
        assertNull(check.verdict(tile = TILE, differingPixels = 1_310, totalPixels = TOTAL))
        assertTrue(check.staleEntries().isEmpty())
    }

    @Test
    fun tileOverLimitFailsUnlessAccepted() {
        assertNotNull(check("").verdict(tile = TILE, differingPixels = 1_311, totalPixels = TOTAL))
        val check = check("awt $TILE 2% C8 wider casing")
        assertNull(check.verdict(tile = TILE, differingPixels = 5_000, totalPixels = TOTAL))
        assertNotNull(check.verdict(tile = TILE, differingPixels = 6_000, totalPixels = TOTAL))
        assertTrue(check.staleEntries().isEmpty())
    }

    @Test
    fun entryNoTileNeedsIsStale() {
        val check = check(
            """
            # comment
            awt $TILE 2% C8 wider casing
            awt other.png 2% C8 wider casing
            """,
        )
        check.verdict(tile = TILE, differingPixels = 0, totalPixels = TOTAL)
        assertEquals(2, check.staleEntries().size)
    }

    @Test
    fun otherBackendIgnored() {
        val check = check("android $TILE 2% C8 wider casing")
        assertNotNull(check.verdict(tile = TILE, differingPixels = 5_000, totalPixels = TOTAL))
        assertTrue(check.staleEntries().isEmpty())
    }

    @Test
    fun malformedEntryFails() {
        assertFailsWith<IllegalArgumentException> {
            check("awt $TILE 2 C8 no percent sign")
        }
        assertFailsWith<IllegalArgumentException> {
            check("awt $TILE 2% C8 twice\nawt $TILE 3% C8 twice")
        }
        val invalid = listOf(
            "AWT $TILE 2% C8 backend case",
            "awt $TILE NaN% C8 not a number",
            "awt $TILE 0.4% C8 within the limit",
            "awt $TILE 2% TODO no C-row",
            "awt $TILE 2% C9 C-row not in upstream.md",
        )
        for (entry in invalid) {
            assertFailsWith<IllegalArgumentException>(entry) {
                check(entry)
            }
        }
    }

    @Test
    fun hashInReasonAndByteOrderMarkRead() {
        val check = check("\uFEFF# header\nawt $TILE 2% C8 see #412")
        assertNull(check.verdict(tile = TILE, differingPixels = 5_000, totalPixels = TOTAL))
    }

    @Test
    fun printedEntryAcceptsItsTile() {
        for (differingPixels in 1_311..TOTAL step 7) {
            val verdict = check("").verdict(tile = TILE, differingPixels = differingPixels, totalPixels = TOTAL)
            val entry = assertNotNull(verdict).substringAfter("entry: ").replace("<C-row> <reason>", "C8 measured")
            val accepted = check(entry)
            assertNull(accepted.verdict(tile = TILE, differingPixels = differingPixels, totalPixels = TOTAL), entry)
        }
    }

    @Test
    fun onlyLabelFreeTilesAreDrawingTiles() {
        assertTrue(DrawingCheck.isDrawingTile(TILE))
        assertFalse(DrawingCheck.isDrawingTile("theme_car_ds2.625_ts1.0_z15_17052_11960.png"))
    }

    private fun check(content: String): DrawingCheck {
        val file = File(dir, "accepted.txt")
        file.writeText(content.trimIndent())
        val upstreamDoc = File(dir, "upstream.md")
        upstreamDoc.writeText("| ID | Where |\n|---|---|\n| C8 | theme `Line` |\n")
        return DrawingCheck(acceptedFile = file, upstreamDoc = upstreamDoc, backend = "awt")
    }

    private companion object {

        private const val TILE = "theme_car_ds2.625_ts1.0_separate_z15_17052_11960.png"

        private const val TOTAL = 512 * 512
    }
}
