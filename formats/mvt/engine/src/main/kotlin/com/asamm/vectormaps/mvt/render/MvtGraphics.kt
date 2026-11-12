/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.symbol.TextMetrics

/**
 * A platform's drawing for the MVT driver: its targets, its decoded sprites and the text measuring
 * that matches its drawing, such as `AwtMvtGraphics`. Called from several render threads at once.
 */
interface MvtGraphics {

    val metrics: TextMetrics

    /**
     * The sprite sheet `png` described by `index`, decoded for this platform's targets.
     */
    fun loadSprite(index: SpriteIndex, png: ByteArray): Any

    /**
     * A transparent target of `widthPx` by `heightPx` drawing icons and patterns from `sprite`,
     * one that [loadSprite] returned, or none.
     */
    fun createTarget(widthPx: Int, heightPx: Int, sprite: Any?): MvtRenderTarget
}
