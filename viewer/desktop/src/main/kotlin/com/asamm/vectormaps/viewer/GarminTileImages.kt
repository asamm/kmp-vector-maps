/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.garminimg.driver.GarminTileTarget
import com.asamm.vectormaps.garminimg.render.AwtRenderTarget

/**
 * Garmin tiles as Compose images, copied from the AWT canvas the renderer drew.
 */
object GarminTileImages : TileImages<ImageBitmap> {

    override fun newTarget(sizePx: Int): RenderTarget {
        return GarminTileTarget(sizePx)
    }

    override fun take(target: RenderTarget): ImageBitmap {
        val canvas = checkNotNull((target as GarminTileTarget).canvas) as AwtRenderTarget
        return canvas.image.toComposeImageBitmap()
    }

    override fun release(image: ImageBitmap) {
        // as AwtTileImages: the image's cleaner frees the pixels
    }
}
