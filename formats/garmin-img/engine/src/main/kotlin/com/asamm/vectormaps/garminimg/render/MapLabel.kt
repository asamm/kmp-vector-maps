/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.render

import com.asamm.vectormaps.garminimg.style.Color

/**
 * A label the renderer found, for a label pass that places labels apart from the tiles (the core
 * label model): positions in the render's pixels, sizes as the target measures them.
 *
 * @param x anchor: a point's position, an area's centroid, a line's middle vertex
 * @param offsetYPx screen offset of the text centre below the anchor; point labels sit under their
 *  icon however the map turns
 * @param pathXs [Kind.LINE] only: the line the text follows, centred along it
 */
class MapLabel(
    val kind: Kind,
    val x: Float,
    val y: Float,
    val offsetYPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val priority: Int,
    val text: String,
    val color: Color,
    val outlineColor: Color?,
    val fontSize: Float,
    pathXs: FloatArray? = null,
    pathYs: FloatArray? = null,
) {

    // copied: a caller reusing its arrays must not move a queued label
    val pathXs: FloatArray? = pathXs?.copyOf()

    val pathYs: FloatArray? = pathYs?.copyOf()

    enum class Kind {
        POINT,
        AREA,
        LINE,
    }
}
