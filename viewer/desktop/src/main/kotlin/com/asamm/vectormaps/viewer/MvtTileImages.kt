/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.mvt.driver.MvtTileTarget
import com.asamm.vectormaps.mvt.render.AwtMvtRenderTarget

/**
 * MVT tiles as Compose images, copied from the AWT target the renderer drew.
 */
object MvtTileImages : TileImages<ImageBitmap> {

    override fun newTarget(sizePx: Int): RenderTarget {
        return MvtTileTarget(sizePx)
    }

    override fun take(target: RenderTarget): ImageBitmap {
        val canvas = checkNotNull((target as MvtTileTarget).canvas) as AwtMvtRenderTarget
        canvas.dispose()
        return canvas.image.toComposeImageBitmap()
    }

    override fun release(image: ImageBitmap) {
        // as AwtTileImages: the image's cleaner frees the pixels
    }
}
