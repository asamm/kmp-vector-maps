/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.FontFamily
import org.mapsforge.core.graphics.FontStyle
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

/**
 * Native and Java heap per Mapsforge Paint, by how the theme builds it (locus-core F2), each Paint
 * drawn once so lazy native parts exist. Logs `PaintCost` lines, asserts nothing; skipped without
 * `-Pandroid.testInstrumentationRunnerArguments.bench.paintCost=true`.
 */
@RunWith(AndroidJUnit4::class)
class PaintCostTest {

    @Test
    fun bytesPerPaint() {
        val enabled = InstrumentationRegistry.getArguments().getString("bench.paintCost")
        assumeTrue("bench.paintCost not set", enabled == "true")
        // the context-less instance: createInstance would reset statics other bench tests set
        val factory = AndroidGraphicFactory.INSTANCE
        val base = factory.createPaint().apply {
            setStyle(Style.STROKE)
            setStrokeCap(Cap.BUTT)
            strokeWidth = 2f
        }
        val shader = factory.createBitmap(16, 16, true)
        val variants = linkedMapOf<String, () -> Paint>(
            "createPaint()" to { factory.createPaint() },
            "createPaint(copy)" to { factory.createPaint(base) },
            "copy + dash" to {
                factory.createPaint(base).apply {
                    setDashPathEffect(floatArrayOf(4f, 2f))
                }
            },
            "copy + typeface" to {
                factory.createPaint(base).apply {
                    setTypeface(FontFamily.SANS_SERIF, FontStyle.BOLD)
                }
            },
            "copy + text size" to {
                factory.createPaint(base).apply {
                    setTextSize(14f)
                }
            },
            "copy + bitmap shader" to {
                factory.createPaint(base).apply {
                    setBitmapShader(shader)
                }
            },
        )
        val target = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(target)
        for ((name, create) in variants) {
            // twice: the first run warms caches shared by all paints
            measure(name = name, canvas = canvas, create = create)
            val (nativeBytes, javaBytes) = measure(name = name, canvas = canvas, create = create)
            Log.i(TAG, "PaintCost: $name native ${"%.0f".format(nativeBytes)} B, java ${"%.0f".format(javaBytes)} B")
        }
    }

    /**
     * Native and Java heap bytes per Paint for [COUNT] Paints kept alive.
     */
    private fun measure(name: String, canvas: android.graphics.Canvas, create: () -> Paint): Pair<Double, Double> {
        val keep = ArrayList<Paint>(COUNT)
        settle()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val javaBefore = javaHeapUsed()
        repeat(COUNT) {
            val paint = create()
            // drawing creates the lazy native parts, e.g. a shader's
            canvas.drawLine(0f, 0f, 1f, 1f, AndroidGraphicFactory.getPaint(paint))
            keep.add(paint)
        }
        settle()
        val nativeAfter = Debug.getNativeHeapAllocatedSize()
        val javaAfter = javaHeapUsed()
        check(keep.size == COUNT) {
            "measure(), $name kept ${keep.size}"
        }
        return Pair(
            (nativeAfter - nativeBefore).toDouble() / COUNT,
            (javaAfter - javaBefore).toDouble() / COUNT,
        )
    }

    private fun javaHeapUsed(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun settle() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(200)
        }
    }

    private companion object {

        private const val TAG = "PaintCostTest"

        private const val COUNT = 20_000
    }
}
