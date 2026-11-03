/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.style.Bitmap
import java.util.IdentityHashMap
import android.graphics.Bitmap as AndroidBitmap

/**
 * TYP patterns and icons converted for Android, shared by the [AndroidRenderTarget]s of one host,
 * such as every canvas of a `GarminCanvasFactory`, so each bitmap converts once rather than once
 * per tile; safe from several render threads, which only draw from the converted bitmaps. Keeps
 * every bitmap it converted until it is dropped: a TYP parsed again, as on every open of a map,
 * adds new entries, so a host keeps one per opened map or style.
 */
class AndroidBitmapCache {

    // TYP bitmaps are interned per style table: identity keys skip the content hash
    private val bitmaps = IdentityHashMap<Bitmap, AndroidBitmap>()

    internal fun bitmap(bitmap: Bitmap, convert: (Bitmap) -> AndroidBitmap): AndroidBitmap {
        synchronized(bitmaps) {
            return bitmaps.getOrPut(bitmap) { convert(bitmap) }
        }
    }
}
