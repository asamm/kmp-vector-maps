/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.StyleMenu
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style

/**
 * A MapLibre style read by [MvtDriver.loadStyle]: the parsed style, shared by its renderers, and
 * its sprite sheets decoded by the driver's graphics on first use, the `@2x` one only for
 * renderers drawing above a style pixel per screen pixel; it has no layer menu.
 *
 * @param loadSprite the sheet's index and the platform's decoded sheet, `@2x` or not; null without
 *  a readable one
 */
class MvtStyle internal constructor(
    override val id: String,
    val style: Style,
    loadSprite: (hiDpi: Boolean) -> SpriteSheet?,
) : MapStyle {

    override val menu: StyleMenu? = null

    private val normal by lazy { loadSprite(false) }

    private val hiDpi by lazy { loadSprite(true) ?: normal }

    /**
     * The sheet for `pixelScale` screen pixels per style pixel: `@2x` above 1, as MapLibre does
     * above a device pixel ratio of 1, where the style has one.
     */
    internal fun spriteFor(pixelScale: Float): SpriteSheet? {
        return if (pixelScale > 1f) hiDpi else normal
    }
}

/**
 * A sprite sheet: its index and the platform's decoded image.
 */
internal class SpriteSheet(val index: SpriteIndex, val sheet: Any)
