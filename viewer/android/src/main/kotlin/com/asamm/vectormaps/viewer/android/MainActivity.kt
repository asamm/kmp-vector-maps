/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer.android

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView
import com.asamm.vectormaps.core.MapFormats
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.mapsforge.MapsforgeDriver
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.model.DisplayModel
import java.io.File

/**
 * Shows the bundled Monaco map with the LoMaps theme. Other files, readable by the app:
 * `adb shell am start -n com.asamm.vectormaps.viewer.android/.MainActivity --es map <path>
 * --es theme <path>`.
 */
class MainActivity : Activity() {

    private var mapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // edge to edge: the status bar lies over the light panel, so its icons go dark
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            // through the decor view, which `window.insetsController` does not create yet
            window.decorView.windowInsetsController?.setSystemBarsAppearance(light, light)
        }
        AndroidGraphicFactory.createInstance(application)
        // createInstance sets Mapsforge's global scale; the driver takes it per renderer
        val density = DisplayModel.getDeviceScaleFactor()
        DisplayModel.setDeviceScaleFactor(1.0f)
        val view = try {
            val fixtures = unpackFixtures()
            val map = intent.getStringExtra("map")?.let(::File) ?: File(fixtures, "maps/monaco.osm.map")
            val theme = intent.getStringExtra("theme")?.let(::File) ?: File(fixtures, "themes/lomaps/theme.xml")
            MapView(
                context = this,
                formats = MapFormats(listOf(MapsforgeDriver(AndroidGraphicFactory.INSTANCE))),
                mapFile = map,
                styleFile = theme,
                // 512 px tiles as everywhere in this repo (D20)
                config = RenderConfig(tileSizePx = 512, deviceScale = density),
            )
        } catch (e: Exception) {
            showError(e)
            return
        }
        mapView = view
        setContentView(view)
    }

    override fun onDestroy() {
        mapView?.close()
        super.onDestroy()
    }

    // a dev tool, English only
    @SuppressLint("SetTextI18n")
    private fun showError(e: Exception) {
        val text = TextView(this).apply {
            text = "Cannot open the map\n\n${e.javaClass.simpleName}: ${e.message}"
            setTextIsSelectable(true)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        // the frame takes the system bar insets as its padding, which would replace the text's own
        val frame = FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(text)
        }
        setContentView(frame)
    }

    /**
     * Copies the `maps` and `themes` asset folders to app storage, since the readers need real
     * files; again after each install, so changed fixtures arrive. Returns the copy's root.
     */
    private fun unpackFixtures(): File {
        val root = File(filesDir, "fixtures")
        val stamp = File(root, ".installed")
        val installed = File(applicationInfo.sourceDir).lastModified().toString()
        if (stamp.isFile
            && stamp.readText() == installed
        ) {
            return root
        }
        val pending = ArrayDeque(listOf("maps", "themes"))
        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            val entries = assets.list(path).orEmpty()
            if (entries.isNotEmpty()) {
                entries.mapTo(pending) { "$path/$it" }
                continue
            }
            val copy = File(root, path)
            copy.parentFile?.mkdirs()
            // a copy cut short never takes the real name
            val partial = File(copy.path + ".part")
            assets.open(path).use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.renameTo(copy)) {
                "unpackFixtures(), cannot rename $partial"
            }
        }
        stamp.writeText(installed)
        return root
    }
}
