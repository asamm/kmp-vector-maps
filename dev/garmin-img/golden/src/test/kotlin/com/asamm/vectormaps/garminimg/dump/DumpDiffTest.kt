/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
/*
 * Reader regression rig.
 *
 * Runs the legacy Java parser (libGarminImg, sitting in this same
 * test source set under `org.free.garminimg.*`) and the Kotlin
 * rewrite against the same IMG fixture, dumps every parsed map object
 * to a sorted text file, and diffs the two outputs.
 *
 * The test fails on regressions (more diffs than the recorded
 * baseline). Use [BASELINE_TOTAL_DIFFS] = -1 while iterating to just
 * print the report; flip to a number to lock the current state and
 * gate further changes.
 */
package com.asamm.vectormaps.garminimg.dump

import org.free.garminimg.dump.ImgContentDumper as JavaImgContentDumper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DumpDiffTest {

    @Test
    fun parsersAgreeOnOtmMonaco() {
        runDumpDiff(
            fixture = "maps/otm-monaco/otm-monaco.img",
            tag = "otm-monaco",
            // OpenTopoMap-built IMG with full POI metadata, NET roads,
            // multi-byte cp1252 labels. The 8 remaining diffs are all
            // in our favour — Java's NET resolution returns empty for
            // a handful of road records where we successfully resolve
            // the underlying LBL label.
            baselineTotalDiffs = 8,
        )
    }

    @Test
    fun parsersAgreeOnMkgmapMonaco() {
        runDumpDiff(
            fixture = "maps/mkgmap-monaco/monaco.img",
            tag = "mkgmap-monaco",
            // Plain mkgmap-built IMG with no TYP. Smaller fixture
            // exercises the no-NET / minimal-POI-metadata path. The 2
            // diffs are the same flavour as on otm-monaco: a single
            // POI where Java's getNameNoException swallows an
            // exception (returning "") while we successfully resolve
            // the underlying LBL label.
            baselineTotalDiffs = 2,
        )
    }

    /**
     * Runs the legacy Java parser and our Kotlin parser against the
     * same fixture, dumps each to disk, and reports the diff. Fails
     * if [baselineTotalDiffs] is non-negative and the diff count
     * exceeds it.
     */
    private fun runDumpDiff(fixture: String, tag: String, baselineTotalDiffs: Int) {
        val img = materialiseFixture(fixture)
        val outDir = File("build/test-output/dump").apply { mkdirs() }
        val javaDump = File(outDir, "$tag.java.dump.txt")
        val kotlinDump = File(outDir, "$tag.kotlin.dump.txt")

        JavaImgContentDumper.dump(img.absolutePath, javaDump.absolutePath)
        ImgContentDumper.dump(img.absolutePath, kotlinDump.absolutePath)

        val javaLines = readBody(javaDump).map(::stripShieldPrefix).toSortedSet()
        val kotlinLines = readBody(kotlinDump).toSortedSet()
        val matched = javaLines.intersect(kotlinLines).size
        val javaOnly = (javaLines - kotlinLines).toList()
        val kotlinOnly = (kotlinLines - javaLines).toList()
        val total = javaOnly.size + kotlinOnly.size
        val javaTotal = javaLines.size
        val kotlinTotal = kotlinLines.size
        val matchPct = if (javaTotal > 0) 100.0 * matched / javaTotal else 0.0
        println(
            buildString {
                appendLine("=== dump-diff report [$tag] ===")
                appendLine("java records:    $javaTotal")
                appendLine("kotlin records:  $kotlinTotal")
                appendLine("matched:         $matched (${"%.2f".format(matchPct)}%)")
                appendLine("java-only:       ${javaOnly.size}")
                appendLine("kotlin-only:     ${kotlinOnly.size}")
                appendLine("total diffs:     $total")
                appendLine()
                appendLine("--- first 10 java-only ---")
                javaOnly.take(10).forEach { appendLine("  $it") }
                appendLine("--- first 10 kotlin-only ---")
                kotlinOnly.take(10).forEach { appendLine("  $it") }
            }
        )
        File(outDir, "$tag.diff.java-only.txt").writeText(javaOnly.joinToString("\n"))
        File(outDir, "$tag.diff.kotlin-only.txt").writeText(kotlinOnly.joinToString("\n"))

        if (baselineTotalDiffs >= 0) {
            assertTrue(
                total <= baselineTotalDiffs,
                "[$tag] diff count $total exceeds baseline $baselineTotalDiffs — parser regression?",
            )
        }
    }

    /**
     * Drops the 4-line file header and returns the rest of the dump.
     */
    private fun readBody(file: File): List<String> {
        return file.readLines(Charsets.UTF_8).drop(4)
    }

    /**
     * Java preserves the highway-shield prefix bytes (0x01..0x07) at
     * the start of road labels; our Kotlin parser strips them as part
     * of label decoding. Strip them on the Java side before diffing so
     * we compare actual content, not the shield-byte preservation
     * choice.
     */
    private val SHIELD_REGEX = Regex("""label="\\u000[1-7]""")
    private fun stripShieldPrefix(line: String): String {
        return SHIELD_REGEX.replace(line, "label=\"")
    }

    /**
     * Materialises a classpath resource into a real on-disk file so
     * the dumpers — which both take a [java.io.File] path — can read
     * it via random-access. Returns the temp file (auto-deleted on
     * JVM exit).
     */
    private fun materialiseFixture(resource: String): File {
        val stream = javaClass.classLoader?.getResourceAsStream(resource)
            ?: error("missing fixture: $resource")
        val tmp = File.createTempFile("dump-diff-", ".img")
        tmp.deleteOnExit()
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp
    }
}
