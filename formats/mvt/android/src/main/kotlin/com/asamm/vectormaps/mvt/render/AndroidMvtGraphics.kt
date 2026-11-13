/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.symbol.TextMetrics

/**
 * [MvtGraphics] on Android: `Bitmap` targets drawn through `Canvas`, sprites as [AndroidSprite],
 * text in `fonts`.
 */
class AndroidMvtGraphics(
    private val fonts: AndroidFonts = AndroidFonts(),
) : MvtGraphics {

    override val metrics: TextMetrics = fonts

    override fun loadSprite(index: SpriteIndex, png: ByteArray): Any {
        // the size first, so a crafted header cannot ask for more than MAX_SPRITE_PIXELS
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        require(bounds.outWidth > 0
            && bounds.outHeight > 0
        ) {
            "loadSprite(), not a readable image"
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SPRITE_PIXELS) {
            "loadSprite(), sheet of ${bounds.outWidth} x ${bounds.outHeight} px"
        }
        val sheet = try {
            BitmapFactory.decodeByteArray(png, 0, png.size)
        } catch (e: OutOfMemoryError) {
            // the driver then loads the style without icons
            throw IllegalArgumentException("loadSprite(), no memory for the sheet", e)
        }
        requireNotNull(sheet) {
            "loadSprite(), not a readable image"
        }
        return AndroidSprite(index, sheet)
    }

    override fun createTarget(widthPx: Int, heightPx: Int, sprite: Any?): AndroidMvtRenderTarget {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        return AndroidMvtRenderTarget(bitmap, sprite as AndroidSprite?, fonts)
    }

    private companion object {

        // 64 MB of pixels, far above any real sheet (@2x ones reach about 2048 px a side)
        const val MAX_SPRITE_PIXELS = 4096L * 4096
    }
}
