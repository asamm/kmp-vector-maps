/*
 * Copyright 2017-2022 devemux86
 * Copyright 2019 Matthew Egeler
 * Copyright 2020 Lukas Bai
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.asamm.vectormaps.mapsforge.core.util

import com.asamm.vectormaps.mapsforge.core.model.Tag

/**
 * Process-wide rendering switches; set them before creating renderers and parsing themes, as Paints
 * and symbols read some of them once. `ANTI_ALIASING`, `NUMBER_OF_THREADS`, the polygon settings
 * and `SYMBOL_SCALING` seed new `DisplayModel`s, which the renderer reads; `ANTI_ALIASING` stays the
 * default of Android Paints and AWT canvases created outside a theme.
 */
object Parameters {

    enum class SymbolScaling { ALL, POI }

    /**
     * Use `Config.ARGB_8888` instead of `Config.RGB_565` in non-transparent Android bitmaps.
     */
    var ANDROID_32BIT_COLOR = false

    /**
     * The anti-aliasing new `DisplayModel`s start with. Changing it leaves existing models alone.
     */
    var ANTI_ALIASING = true

    /**
     * Maximum buffer size for map files, in bytes.
     */
    var MAXIMUM_BUFFER_SIZE = 10000000

    /**
     * The thread count new `DisplayModel`s start with; one more than the processors, as one thread
     * is likely blocked reading map data. Changing it leaves existing models alone.
     */
    var NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors() + 1

    /**
     * The polygon exceptions new `DisplayModel`s start with: tags whose closed ways still render
     * below the polygon zoom. Changing it leaves existing models alone.
     */
    val POLYGON_EXCEPTIONS: MutableSet<Tag> = HashSet()

    /**
     * The lowest polygon zoom new `DisplayModel`s start with, e.g. 10 for speed; -1 for all.
     * Changing it leaves existing models alone.
     */
    var POLYGON_ZOOM_MIN = -1

    /**
     * The symbol scaling new `DisplayModel`s start with. Changing it leaves existing models alone.
     */
    var SYMBOL_SCALING = SymbolScaling.ALL

    var VALIDATE_COORDINATES = true

    init {
        POLYGON_EXCEPTIONS.add(Tag("natural", "sea"))
        POLYGON_EXCEPTIONS.add(Tag("natural", "nosea"))

        POLYGON_EXCEPTIONS.add(Tag("freizeitkarte", "meer"))
        POLYGON_EXCEPTIONS.add(Tag("freizeitkarte", "land"))
    }
}
