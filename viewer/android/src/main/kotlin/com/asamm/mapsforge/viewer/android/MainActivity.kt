/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.viewer.android

import android.app.Activity
import android.content.res.AssetManager
import android.os.Bundle
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.File

/**
 * Single-screen viewer. Defaults to the bundled Monaco map and LoMaps theme; override with
 * `adb shell am start -n com.asamm.mapsforge.viewer.android/.MainActivity --es map <path>
 * --es theme <path>`, paths readable by the app (e.g. its external files directory).
 */
class MainActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(application)
        val fixtures = File(filesDir, "fixtures")
        copyAssets(assets, path = "", target = fixtures)
        val mapFile = intent.getStringExtra(EXTRA_MAP)?.let(::File)
            ?: File(fixtures, "maps/monaco.osm.map")
        val themeFile = intent.getStringExtra(EXTRA_THEME)?.let(::File)
            ?: File(fixtures, "themes/lomaps/theme.xml")
        mapView = MapView(context = this, mapFile = mapFile, themeFile = themeFile)
        setContentView(mapView)
    }

    override fun onDestroy() {
        mapView.close()
        super.onDestroy()
    }

    /**
     * Copies the bundled fixtures once per install; an existing file is kept.
     */
    private fun copyAssets(assets: AssetManager, path: String, target: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            if (target.exists()) {
                return
            }
            target.parentFile?.mkdirs()
            assets.open(path).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            return
        }
        for (child in children) {
            // skip the platform's own asset folders
            if (path.isEmpty() && child !in FIXTURE_ROOTS) {
                continue
            }
            val childPath = if (path.isEmpty()) child else "$path/$child"
            copyAssets(assets, path = childPath, target = File(target, child))
        }
    }

    private companion object {
        const val EXTRA_MAP = "map"
        const val EXTRA_THEME = "theme"
        val FIXTURE_ROOTS = setOf("maps", "themes")
    }
}
