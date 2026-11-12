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
                require(width <= MAX_SPRITE_PX
                    && height <= MAX_SPRITE_PX
                ) {
                    "loadSprite(), sheet of $width x $height px"
                }
                return AwtSprite(index, reader.read(0))
            } finally {
                reader.dispose()
            }
        }
    }

    override fun createTarget(widthPx: Int, heightPx: Int, sprite: Any?): AwtMvtRenderTarget {
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
        return AwtMvtRenderTarget(image, sprite as AwtSprite?, fonts)
    }

    private companion object {

        // far above any real sheet
        const val MAX_SPRITE_PX = 8192
    }
}
