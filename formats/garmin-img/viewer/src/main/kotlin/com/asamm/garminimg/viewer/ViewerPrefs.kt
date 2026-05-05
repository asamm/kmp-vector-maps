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
    private const val KEY_WIN_X = "windowXDp"
    private const val KEY_WIN_Y = "windowYDp"
    private const val KEY_LAST_FILE = "lastFilePath"
    private const val KEY_ANTI_ALIASED = "antiAliased"

    private const val DEFAULT_WIDTH_DP = 1280
    private const val DEFAULT_HEIGHT_DP = 800
    private const val DEFAULT_AA = true

    /**
     * Sentinel indicating "no saved position" — caller should fall back
     * to platform-default placement (centred / OS-chosen).
     */
    const val POSITION_UNSET: Int = Int.MIN_VALUE

    fun restore(): Restored {
        return Restored(
            windowWidthDp = prefs.getInt(KEY_WIN_WIDTH, DEFAULT_WIDTH_DP),
            windowHeightDp = prefs.getInt(KEY_WIN_HEIGHT, DEFAULT_HEIGHT_DP),
            windowXDp = prefs.getInt(KEY_WIN_X, POSITION_UNSET),
            windowYDp = prefs.getInt(KEY_WIN_Y, POSITION_UNSET),
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

    /**
     * Persists the window's top-left in dp. Negative values are allowed
     * (multi-monitor setups place windows past 0,0); -32768..32767 covers
     * any sane desktop arrangement.
     */
    fun saveWindowPosition(xDp: Int, yDp: Int) {
        if (xDp in -32768..32767 && yDp in -32768..32767) {
            prefs.putInt(KEY_WIN_X, xDp)
            prefs.putInt(KEY_WIN_Y, yDp)
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
        /** Saved window x in dp, or [POSITION_UNSET] if never saved. */
        val windowXDp: Int,
        /** Saved window y in dp, or [POSITION_UNSET] if never saved. */
        val windowYDp: Int,
        val lastFilePath: String?,
        val antiAliased: Boolean,
    )
}
