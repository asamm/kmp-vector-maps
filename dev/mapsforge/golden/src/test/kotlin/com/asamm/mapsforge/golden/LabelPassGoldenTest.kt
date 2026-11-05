/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares the label pass render (`renderLabelPass`) with the committed `labels/pass`: dumps exact,
 * images within the label goldens' tolerance ([LabelGoldens]).
 */
class LabelPassGoldenTest {

    @Test
    fun passMatchesItsGoldens() {
        val goldens = files(TestDirs.passLabels)
        assertTrue(goldens.any { it.extension == "png" }, "no label pass goldens in ${TestDirs.passLabels}")
        assertEquals(
            goldens.map { it.name },
            files(TestDirs.passActual).map { it.name },
            "label pass golden sets differ, $UPDATE_TASK",
        )
        val problems = goldens.mapNotNull { golden ->
            val actual = File(TestDirs.passActual, golden.name)
            if (golden.extension == "png") {
                imageVerdict(golden = golden, actual = actual)
            } else {
                dumpVerdict(golden = golden, actual = actual)
            }
        }
        if (problems.isNotEmpty()) {
            fail(problems.joinToString(separator = "\n"))
        }
    }

    @Test
    fun turningTheScreenMovesTheLabels() {
        val dumps = files(TestDirs.passActual).filter { it.extension == "txt" }
        for (straight in dumps.filter { it.name.contains("_r0") }) {
            val turned = File(straight.parentFile, straight.name.replace("_r0", "_r30"))
            val straightLines = straight.readLines().drop(1)
            assertTrue(straightLines.size > 10, "${straight.name} places ${straightLines.size} labels")
            assertTrue(
                straightLines != turned.readLines().drop(1),
                "${turned.name} places the same labels as ${straight.name}",
            )
        }
    }

    private fun imageVerdict(golden: File, actual: File): String? {
        val expected = ImageIO.read(golden)
        val differing = LabelGoldens.differingPixels(
            expectedFile = golden,
            actualFile = actual,
            channelTolerance = LabelGoldens.CHANNEL_TOLERANCE,
            diffFile = File(TestDirs.diff, "pass/${golden.name}"),
        )
        return LabelGoldens.verdict(
            tile = golden.name,
            differingPixels = differing,
            totalPixels = expected.width * expected.height,
            updateTask = UPDATE_TASK,
        )
    }

    private fun dumpVerdict(golden: File, actual: File): String? {
        // guard against an empty dump passing trivially
        if (golden.readLines().none { it.startsWith("  ") }) {
            return "${golden.name} holds no entries"
        }
        val difference = LabelGoldens.firstDifference(expected = golden, actual = actual) ?: return null
        return "$difference; an intended change: $UPDATE_TASK"
    }

    private fun files(dir: File): List<File> {
        return dir.listFiles().orEmpty().sortedBy { it.name }
    }

    private companion object {

        const val UPDATE_TASK = "./gradlew :dev:mapsforge:golden:updateLabelPassGoldens"
    }
}
