/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.symbol.TextMetrics
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * [MvtGraphics] on AWT: `BufferedImage` targets, sprites as [AwtSprite], text in `fonts`.
 */
class AwtMvtGraphics(
    private val fonts: AwtFonts,
) : MvtGraphics {

    override val metrics: TextMetrics = fonts

    override fun loadSprite(index: SpriteIndex, png: ByteArray): Any {
        val input = requireNotNull(ImageIO.createImageInputStream(ByteArrayInputStream(png))) {
            "loadSprite(), no image input"
        }
        input.use {
            val reader = requireNotNull(ImageIO.getImageReaders(input).asSequence().firstOrNull()) {
                "loadSprite(), not a readable image"
            }
            try {
                reader.input = input
                // the size first, so a crafted header cannot exhaust the heap
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0
                    && height > 0
                    && width.toLong() * height <= MAX_SPRITE_PIXELS
                ) {
                    "loadSprite(), sheet of $width x $height px"
                }
                val sheet = try {
                    reader.read(0)
                } catch (e: OutOfMemoryError) {
                    // an Error passes the caller's catch; the style goes without the sheet
                    throw IllegalArgumentException("loadSprite(), no memory for the sheet", e)
                }
                return AwtSprite(index, sheet)
            } finally {
                reader.dispose()
            }
        }
    }

    override fun createTarget(widthPx: Int, heightPx: Int, sprite: Any?): AwtMvtRenderTarget {
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
        return AwtMvtRenderTarget(image, sprite as AwtSprite?, fonts)
    }

    override fun discard(target: MvtRenderTarget) {
        (target as AwtMvtRenderTarget).dispose()
    }

    private companion object {

        // 64 MB of pixels, far above any real sheet, as the Android graphics
        const val MAX_SPRITE_PIXELS = 4096L * 4096
    }
}
