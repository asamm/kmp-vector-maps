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
 * its sprite decoded by the driver's graphics; it has no layer menu.
 *
 * @param sprite the platform's decoded sprite sheet, or null without one
 */
class MvtStyle internal constructor(
    override val id: String,
    val style: Style,
    internal val spriteIndex: SpriteIndex?,
    internal val sprite: Any?,
) : MapStyle {

    override val menu: StyleMenu? = null
}
