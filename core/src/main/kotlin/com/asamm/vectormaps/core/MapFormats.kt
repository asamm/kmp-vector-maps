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
        val extension = file.extension.lowercase()
        for (driver in drivers) {
            if (extension !in driver.extensions) {
                continue
            }
            val info = try {
                driver.probe(file)
            } catch (e: Exception) {
                null
            } ?: continue
            return Probed(driver = driver, info = info)
        }
        return null
    }

    data class Probed(
        val driver: MapFormatDriver,
        val info: MapInfo,
    )
}
