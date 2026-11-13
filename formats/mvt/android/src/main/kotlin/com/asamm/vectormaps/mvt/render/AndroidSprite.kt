/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import android.graphics.Bitmap
import com.asamm.vectormaps.mvt.style.SpriteIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * A style's sprite sheet for [AndroidMvtRenderTarget]: each image cut out once and shared by the
 * targets of the style.
 */
class AndroidSprite(
    private val index: SpriteIndex,
    private val sheet: Bitmap,
) {

    private val images = ConcurrentHashMap<String, Bitmap>()

    /**
     * Image `name`, or null when the sprite has none or it lies outside the sheet.
     */
    fun image(name: String): Bitmap? {
        images[name]?.let {
            return it
        }
        val i = index.images[name] ?: return null
        if (i.width <= 0
            || i.height <= 0
            || i.x < 0
            || i.y < 0
            || i.x > sheet.width - i.width
            || i.y > sheet.height - i.height
        ) {
            return null
        }
        val image = Bitmap.createBitmap(sheet, i.x, i.y, i.width, i.height)
        images[name] = image
        return image
    }
}
