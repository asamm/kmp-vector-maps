/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import java.io.File

/**
 * Registry of the drivers a host supports; picks the driver for a file. No global instance: the
 * host builds one with the drivers it ships.
 */
class MapFormats(val drivers: List<MapFormatDriver>) {

    init {
        val duplicates = drivers.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "MapFormats(), duplicate driver ids $duplicates"
        }
    }

    fun driver(id: String): MapFormatDriver? {
        return drivers.firstOrNull { it.id == id }
    }

    /**
     * Returns the first driver, in registration order, that claims the file's extension and whose
     * probe succeeds, or null when none does. A probe that throws counts as failed, so one damaged
     * file never stops a folder scan.
     */
    fun probe(file: File): Probed? {
        val outcome = outcomes(file).firstOrNull { it.info != null } ?: return null
        return Probed(driver = outcome.driver, info = checkNotNull(outcome.info))
    }

    /**
     * Why [probe] returns null for `file`, one line per driver that claims its extension, or that
     * none claims it; for hosts that show the user more than "no driver reads it". It probes
     * again, so a file that changed in between may read now.
     */
    fun unreadableReason(file: File): String {
        val outcomes = outcomes(file).toList()
        if (outcomes.isEmpty()) {
            return "no driver reads .${file.extension.lowercase()} files"
        }
        return outcomes.joinToString("\n") { outcome ->
            val reason = when {
                outcome.failure != null -> outcome.failure.message ?: outcome.failure.javaClass.simpleName
                outcome.info == null -> "not a file of this format"
                else -> "readable now"
            }
            "${outcome.driver.id}: $reason"
        }
    }

    // each driver that claims the file's extension, in registration order, probed on demand
    private fun outcomes(file: File): Sequence<Outcome> {
        val extension = file.extension.lowercase()
        return drivers.asSequence()
            .filter { extension in it.extensions }
            .map { driver ->
                try {
                    Outcome(driver = driver, info = driver.probe(file), failure = null)
                } catch (e: Exception) {
                    Outcome(driver = driver, info = null, failure = e)
                }
            }
    }

    private class Outcome(
        val driver: MapFormatDriver,
        val info: MapInfo?,
        val failure: Exception?,
    )

    data class Probed(
        val driver: MapFormatDriver,
        val info: MapInfo,
    )
}
