/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File
import java.util.Locale
import kotlin.math.ceil

/**
 * Judges tiles drawn without labels against upstream (ROADMAP D37): a tile passes when at most
 * [MAX_DIFFERING_SHARE] of its pixels differ, or up to the share its entry in the accepted
 * differences allows. An entry no tile needs any more is stale and fails, so the list stays
 * exactly the confirmed differences.
 *
 * Accepted file, one entry per line, a line starting with `#` is a comment:
 * `<backend> <tile file> <max share>% <upstream.md C-row> <reason>`.
 *
 * @param upstreamDoc `docs/upstream.md`, whose deviation table holds the C-rows an entry may cite
 * @param backend `awt` or `android`; entries of the other backend are validated, then ignored
 */
class DrawingCheck(acceptedFile: File, upstreamDoc: File, private val backend: String) {

    private val cRows: Set<String> = C_ROW.findAll(upstreamDoc.readText()).map { it.groupValues[1] }.toSet()

    private val accepted: Map<String, Entry> = parse(acceptedFile)

    // tiles judged so far, with their differing share
    private val seen = mutableMapOf<String, Double>()

    /**
     * Returns null when the tile passes, else why it fails.
     */
    fun verdict(tile: String, differingPixels: Int, totalPixels: Int): String? {
        val share = differingPixels.toDouble() / totalPixels
        seen[tile] = share
        val entry = accepted[tile]
        if (share <= MAX_DIFFERING_SHARE) {
            return null
        }
        if (entry == null) {
            return "$tile: ${percent(share)} differ, over the ${percent(MAX_DIFFERING_SHARE)} limit and not accepted;" +
                " entry: $backend $tile ${entryShare(share)}% <C-row> <reason>"
        }
        if (share > entry.maxShare) {
            return "$tile: ${percent(share)} differ, over its accepted ${percent(entry.maxShare)} (${entry.deviation})"
        }
        return null
    }

    /**
     * Entries of this backend that no judged tile needed: unknown tiles and tiles within the limit.
     */
    fun staleEntries(): List<String> {
        return accepted.values.mapNotNull { entry ->
            val share = seen[entry.tile]
            when {
                share == null -> "${entry.tile}: accepted, but no such drawing tile"
                share <= MAX_DIFFERING_SHARE -> "${entry.tile}: accepted, but within the limit (${percent(share)})"
                else -> null
            }
        }
    }

    private fun parse(file: File): Map<String, Entry> {
        val result = mutableMapOf<String, Entry>()
        file.readLines().forEachIndexed { index, raw ->
            // an editor may save a byte-order mark, which trim() keeps
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty()
                || line.startsWith("#")
            ) {
                return@forEachIndexed
            }
            val where = "parse(${file.name}), line ${index + 1}"
            val parts = line.split(WHITESPACE, limit = 5)
            require(parts.size == 5) {
                "$where: expected <backend> <tile> <share>% <C-row> <reason>"
            }
            require(parts[0] in BACKENDS) {
                "$where: backend '${parts[0]}', expected one of $BACKENDS"
            }
            val percent = parts[2].removeSuffix("%").toDoubleOrNull()
            require(parts[2].endsWith("%")
                && percent != null
                && percent > MAX_DIFFERING_SHARE * 100.0
                && percent <= 100.0
            ) {
                "$where: share '${parts[2]}', expected a percentage above the limit, up to 100%"
            }
            require(parts[3] in cRows) {
                "$where: '${parts[3]}' is no C-row of upstream.md"
            }
            if (parts[0] != backend) {
                return@forEachIndexed
            }
            val entry = Entry(
                tile = parts[1],
                maxShare = percent / 100.0,
                deviation = parts[3],
            )
            require(result.put(entry.tile, entry) == null) {
                "$where: ${entry.tile} listed twice"
            }
        }
        return result
    }

    private fun percent(share: Double): String {
        return "%.3f %%".format(Locale.ROOT, share * 100.0)
    }

    // rounded up, so the printed share accepts the tile it was measured on
    private fun entryShare(share: Double): String {
        return "%.3f".format(Locale.ROOT, ceil(share * 100_000.0) / 1_000.0)
    }

    private data class Entry(
        val tile: String,
        val maxShare: Double,
        val deviation: String,
    )

    companion object {

        // 1,310 of a 512 px tile's 262,144 pixels
        const val MAX_DIFFERING_SHARE = 0.005

        private val BACKENDS = setOf("awt", "android")

        private val WHITESPACE = Regex("\\s+")

        // a row of the deviation table: `| C7 | ...`
        private val C_ROW = Regex("^\\| (C\\d+) \\|", RegexOption.MULTILINE)

        /**
         * True for tiles drawn without labels, which [DrawingCheck] judges; the others stay exact.
         */
        fun isDrawingTile(fileName: String): Boolean {
            return "_separate_" in fileName
        }
    }
}
