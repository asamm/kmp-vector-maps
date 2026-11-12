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
        val sheet = requireNotNull(ImageIO.read(ByteArrayInputStream(png))) {
            "loadSprite(), not a readable image"
        }
        return AwtSprite(index, sheet)
    }

    override fun createTarget(widthPx: Int, heightPx: Int, sprite: Any?): AwtMvtRenderTarget {
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
        return AwtMvtRenderTarget(image, sprite as AwtSprite?, fonts)
    }
}
