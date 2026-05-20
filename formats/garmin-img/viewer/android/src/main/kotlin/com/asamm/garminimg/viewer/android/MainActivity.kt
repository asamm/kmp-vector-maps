/*
 * kmp-garmin-img — Android viewer Main Activity.
 */
package com.asamm.garminimg.viewer.android

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File

/**
 * Single-activity host for [MapView].
 *
 * Reads an IMG from the app's own external-files directory:
 * `/sdcard/Android/data/com.asamm.garminimg.viewer.android/files/<IMG_FILE_NAME>`.
 *
 * That path is freely writable via `adb push` and freely readable by the
 * app without any storage permission (the app owns it). Push the test
 * IMG there before launching:
 *
 * ```
 * adb push otm-monaco.img /sdcard/Android/data/com.asamm.garminimg.viewer.android/files/
 * ```
 *
 * A real product would use the Storage Access Framework. The point of
 * this viewer is iteration speed on real device hardware, not UX polish.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir = getExternalFilesDir(null)
        val file = if (dir != null) File(dir, IMG_FILE_NAME) else null
        if (file == null || !file.exists()) {
            // Show an error message instead of crashing — easier to
            // diagnose on a phone with no logcat to hand.
            val tv = TextView(this).apply {
                text = buildString {
                    append("IMG file not found:\n")
                    append(file?.absolutePath ?: "(no external storage)")
                    append("\n\nPush a Garmin IMG to that path then reopen:\n")
                    append("adb push otm-monaco.img \\\n")
                    append("  ${dir?.absolutePath}/")
                }
                setPadding(48, 96, 48, 48)
            }
            setContentView(tv)
            return
        }
        val container = FrameLayout(this)
        container.addView(MapView(this, file))
        setContentView(container)
    }

    companion object {

        /**
         * Filename inside the app's external-files dir. Adjust before
         * building to point at a different test IMG.
         */
        private const val IMG_FILE_NAME = "otm-monaco.img"
    }
}
