/*
 * kmp-garmin-img — Viewer preferences persistence.
 */
package com.asamm.garminimg.viewer

import java.util.prefs.Preferences

/**
 * Cross-platform persistent settings for the viewer:
 * window dimensions, last-opened IMG file path, AA toggle.
 *
 * Backed by [Preferences] — Windows registry, macOS plist, or
 * Linux XDG config depending on the OS. Survives reinstalls of the
 * viewer and restarts of Compose Desktop.
 *
 * Read once at startup ([restore]); written incrementally as the user
 * changes things (window resize → [saveWindowSize]; file open →
 * [saveLastFile]; AA checkbox → [saveAntiAliased]).
 */
object ViewerPrefs {

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/asamm/garminimg/viewer")
    }

    private const val KEY_WIN_WIDTH = "windowWidthDp"
    private const val KEY_WIN_HEIGHT = "windowHeightDp"
    private const val KEY_LAST_FILE = "lastFilePath"
    private const val KEY_ANTI_ALIASED = "antiAliased"

    private const val DEFAULT_WIDTH_DP = 1280
    private const val DEFAULT_HEIGHT_DP = 800
    private const val DEFAULT_AA = true

    fun restore(): Restored {
        return Restored(
            windowWidthDp = prefs.getInt(KEY_WIN_WIDTH, DEFAULT_WIDTH_DP),
            windowHeightDp = prefs.getInt(KEY_WIN_HEIGHT, DEFAULT_HEIGHT_DP),
            lastFilePath = prefs.get(KEY_LAST_FILE, null),
            antiAliased = prefs.getBoolean(KEY_ANTI_ALIASED, DEFAULT_AA),
        )
    }

    fun saveWindowSize(widthDp: Int, heightDp: Int) {
        if (widthDp in 200..8192 && heightDp in 200..8192) {
            prefs.putInt(KEY_WIN_WIDTH, widthDp)
            prefs.putInt(KEY_WIN_HEIGHT, heightDp)
        }
    }

    fun saveLastFile(path: String?) {
        if (path == null) {
            prefs.remove(KEY_LAST_FILE)
        } else {
            prefs.put(KEY_LAST_FILE, path)
        }
    }

    fun saveAntiAliased(value: Boolean) {
        prefs.putBoolean(KEY_ANTI_ALIASED, value)
    }

    data class Restored(
        val windowWidthDp: Int,
        val windowHeightDp: Int,
        val lastFilePath: String?,
        val antiAliased: Boolean,
    )
}
