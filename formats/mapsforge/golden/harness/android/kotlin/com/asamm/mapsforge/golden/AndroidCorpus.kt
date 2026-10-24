/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import android.app.Application
import android.graphics.Bitmap
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.model.DisplayModel
import java.io.File

/**
 * The golden corpus rendered through Mapsforge's Android graphics, as PNG files; label and text
 * dumps stay with the AWT corpus, where they do not depend on the backend.
 *
 * Compiled twice — against upstream `mapsforge-map-android` 0.30.0
 * (`:formats:mapsforge:golden-android-upstream`) and against the vendored module
 * (`:formats:mapsforge:golden-android`) — and run under Robolectric's native graphics.
 */
object AndroidCorpus {

    /**
     * Renders the corpus twice and keeps the second pass, which runs once the SVG file-cache writer
     * threads of the first are done; they draw identity hashes at varying moments (ROADMAP M2.4).
     */
    fun renderAll(application: Application, fixturesDir: File, outDir: File) {
        outDir.deleteRecursively()
        outDir.mkdirs()
        AndroidGraphicFactory.createInstance(application)
        // the device scale is a JVM-wide static in Mapsforge; leave it as found
        val originalDeviceScale = DisplayModel.getDeviceScaleFactor()
        try {
            val threadsBefore = Thread.getAllStackTraces().keys
            renderSets(fixturesDir = fixturesDir) { _, _, _ -> }
            joinSvgStorers(before = threadsBefore)
            renderSets(fixturesDir = fixturesDir) { set, tile, bitmap ->
                val file = File(outDir, Corpus.tileFileName(set = set, tile = tile, extension = "png"))
                file.outputStream().use { out ->
                    AndroidGraphicFactory.getBitmap(bitmap).compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            IdentityHashes.align("end")
            // not compared; tells which slot drew a different count when a tie flips
            File(outDir, "identity_hashes.log").writeText(IdentityHashes.usage().joinToString(separator = "\n"))
        } finally {
            DisplayModel.setDeviceScaleFactor(originalDeviceScale)
        }
    }

    private fun renderSets(fixturesDir: File, onTile: (Corpus.RenderSet, Tile, TileBitmap) -> Unit) {
        for (set in Corpus.SETS) {
            Corpus.renderSet(
                set = set,
                fixturesDir = fixturesDir,
                graphicFactory = AndroidGraphicFactory.INSTANCE,
            ) { tile, bitmap ->
                onTile(set, tile, bitmap)
            }
        }
    }

    /**
     * Waits for the unnamed threads `AndroidSvgBitmapStore.put` started since [before].
     */
    private fun joinSvgStorers(before: Set<Thread>) {
        for (thread in Thread.getAllStackTraces().keys - before) {
            // Mapsforge's file-cache writers are the only unnamed threads the render starts
            if (thread.name.startsWith("Thread-")) {
                thread.join(30_000)
                check(!thread.isAlive) {
                    "joinSvgStorers(), $thread still running"
                }
            }
        }
    }
}
