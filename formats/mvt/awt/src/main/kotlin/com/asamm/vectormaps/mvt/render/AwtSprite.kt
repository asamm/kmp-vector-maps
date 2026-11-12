/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.SpriteIndex
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * A sprite sheet decoded for AWT; [image] cuts each image once.
 */
class AwtSprite(
    private val index: SpriteIndex,
    private val sheet: BufferedImage,
) {

    private val images = ConcurrentHashMap<String, BufferedImage>()

    fun image(name: String): BufferedImage? {
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
        val copy = BufferedImage(i.width, i.height, BufferedImage.TYPE_INT_ARGB)
        copy.createGraphics().apply {
            drawImage(sheet.getSubimage(i.x, i.y, i.width, i.height), 0, 0, null)
            dispose()
        }
        images[name] = copy
        return copy
    }
}
