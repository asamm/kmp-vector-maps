/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.style.Bitmap
import java.awt.image.BufferedImage
import java.util.IdentityHashMap

/**
 * TYP patterns and icons converted for AWT, shared by the [AwtRenderTarget]s of one host, such as
 * every canvas of a `GarminCanvasFactory`, so each bitmap converts once rather than once per tile;
 * safe from several render threads. Keeps every bitmap it converted until it is dropped: a TYP
 * parsed again, as on every open of a map, adds new entries, so a host keeps one per opened map or
 * style.
 */
class AwtBitmapCache {

    // TYP bitmaps are interned per style table: identity keys skip the content hash
    private val images = IdentityHashMap<Bitmap, BufferedImage>()

    internal fun image(bitmap: Bitmap, convert: (Bitmap) -> BufferedImage): BufferedImage {
        synchronized(images) {
            return images.getOrPut(bitmap) { convert(bitmap) }
        }
    }
}
