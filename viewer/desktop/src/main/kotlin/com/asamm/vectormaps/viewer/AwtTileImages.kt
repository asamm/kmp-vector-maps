/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.mapsforge.TileBitmapTarget
import org.mapsforge.map.awt.graphics.AwtGraphicFactory

/**
 * Mapsforge tiles as Compose images; the Mapsforge bitmap is released once copied.
 */
object AwtTileImages : TileImages<ImageBitmap> {

    override fun newTarget(sizePx: Int): RenderTarget {
        return TileBitmapTarget(sizePx)
    }

    override fun take(target: RenderTarget): ImageBitmap {
        val bitmap = checkNotNull((target as TileBitmapTarget).bitmap)
        try {
            return AwtGraphicFactory.getBitmap(bitmap).toComposeImageBitmap()
        } finally {
            bitmap.decrementRefCount()
        }
    }

    override fun release(image: ImageBitmap) {
        // the native Skia pixels go with the image's cleaner; freeing them here could pull them
        // from under a frame still drawing
    }
}
