/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * What the viewer restores on the next start, kept in a properties file.
 *
 * @param place the last view, valid for `mapPath` only
 * @param window window bounds in dp; null lets the platform place the window
 */
data class Session(
    val mapPath: String? = null,
    val stylePath: String? = null,
    val layerId: String? = null,
    val textScale: Float = 1.0f,
    val tileSize: Int = 512,
    val threads: Int = 4,
    val place: Place? = null,
    val window: Window? = null,
) {

    data class Place(
        val lat: Double,
        val lon: Double,
        val zoom: Int,
    )

    data class Window(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Writes `file` through a temporary file, so an interrupted write never leaves half a session.
     */
    fun write(file: File) {
        val props = Properties()
        mapPath?.let { props["map"] = it }
        stylePath?.let { props["style"] = it }
        layerId?.let { props["layer"] = it }
        props["textScale"] = textScale.toString()
        props["tileSize"] = tileSize.toString()
        props["threads"] = threads.toString()
        place?.let {
            props["place"] = "${it.lat},${it.lon},${it.zoom}"
        }
        window?.let {
            props["window"] = "${it.x},${it.y},${it.width},${it.height}"
        }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        try {
            temp.outputStream().use { props.store(it, "kmp-vector-maps viewer") }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.delete()
        }
    }

    companion object {

        /**
         * `~/.kmp-vector-maps/viewer.properties`, shared by every checkout.
         */
        val userFile: File
            get() = File(System.getProperty("user.home"), ".kmp-vector-maps/viewer.properties")

        /**
         * Reads `file`; any missing or unreadable value keeps its default, an unreadable file gives
         * the defaults.
         */
        fun read(file: File): Session {
            if (!file.isFile) {
                return Session()
            }
            val props = Properties()
            try {
                file.inputStream().use { props.load(it) }
            } catch (e: Exception) {
                return Session()
            }
            val defaults = Session()
            val place = props.getProperty("place")?.split(',')?.takeIf { it.size == 3 }?.let { parts ->
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                val zoom = parts[2].toIntOrNull()
                if (lat == null
                    || lon == null
                    || zoom == null
                ) {
                    null
                } else {
                    Place(lat = lat, lon = lon, zoom = zoom)
                }
            }
            val window = props.getProperty("window")?.split(',')?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 4 && it[2] > 0 && it[3] > 0 }
                ?.let { Window(x = it[0], y = it[1], width = it[2], height = it[3]) }
            return Session(
                mapPath = props.getProperty("map"),
                stylePath = props.getProperty("style"),
                layerId = props.getProperty("layer"),
                textScale = props.getProperty("textScale")?.toFloatOrNull()?.takeIf { it > 0f } ?: defaults.textScale,
                tileSize = props.getProperty("tileSize")?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.tileSize,
                threads = props.getProperty("threads")?.toIntOrNull() ?: defaults.threads,
                place = place,
                window = window,
            )
        }
    }
}
