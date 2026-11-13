/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import android.graphics.Bitmap
import android.graphics.Rect
import com.asamm.vectormaps.mvt.style.SpriteIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * A style's sprite sheet for [AndroidMvtRenderTarget], shared by the targets of the style: icons
 * draw from their place in [sheet], only fill patterns are cut out, once each.
 */
class AndroidSprite(
    private val index: SpriteIndex,
    val sheet: Bitmap,
) {

    private val places = ConcurrentHashMap<String, Rect>()

    private val patterns = ConcurrentHashMap<String, Bitmap>()

    /**
     * Where image `name` lies in [sheet], or null when the sprite has none or it lies outside the
     * sheet.
     */
    fun place(name: String): Rect? {
        places[name]?.let {
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
        val place = Rect(i.x, i.y, i.x + i.width, i.y + i.height)
        places[name] = place
        return place
    }

    /**
     * Image `name` as a bitmap of its own, for a repeating fill pattern; null as for [place].
     */
    fun pattern(name: String): Bitmap? {
        patterns[name]?.let {
            return it
        }
        val place = place(name) ?: return null
        val image = Bitmap.createBitmap(sheet, place.left, place.top, place.width(), place.height())
        patterns[name] = image
        return image
    }
}
