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
package org.mapsforge.core.util

import org.mapsforge.core.model.Tag

/**
 * Process-wide rendering switches; set them before creating renderers and parsing themes, as Paints
 * and symbols read some of them once.
 */
object Parameters {

    enum class ParentTilesRendering { QUALITY, SPEED, OFF }

    enum class SymbolScaling { ALL, POI }

    /**
     * Use `Config.ARGB_8888` instead of `Config.RGB_565` in non-transparent Android bitmaps.
     */
    @JvmField
    var ANDROID_32BIT_COLOR = false

    @JvmField
    var ANTI_ALIASING = true

    @JvmField
    var ELASTIC_ZOOM = false

    @JvmField
    var FRACTIONAL_ZOOM = false

    /**
     * Process layer scroll events.
     */
    @JvmField
    var LAYER_SCROLL_EVENT = false

    /**
     * Maximum buffer size for map files, in bytes.
     */
    @JvmField
    var MAXIMUM_BUFFER_SIZE = 10000000

    /**
     * One more than the processors, as one thread is likely blocked reading map data.
     */
    @JvmField
    var NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors() + 1

    @JvmField
    var PARENT_TILES_RENDERING = ParentTilesRendering.SPEED

    /**
     * Tags whose closed ways still render below [POLYGON_ZOOM_MIN].
     */
    @JvmField
    val POLYGON_EXCEPTIONS: MutableSet<Tag> = HashSet()

    /**
     * Lowest zoom level that renders polygons, e.g. 10 for speed; -1 for all.
     */
    @JvmField
    var POLYGON_ZOOM_MIN = -1

    /**
     * Faster map rotation with a matrix.
     */
    @JvmField
    var ROTATION_MATRIX = true

    /**
     * A frame buffer large enough for either orientation, so a rotation needs no new one.
     */
    @JvmField
    var SQUARE_FRAME_BUFFER = true

    @JvmField
    var SYMBOL_SCALING = SymbolScaling.ALL

    @JvmField
    var VALIDATE_COORDINATES = true

    init {
        POLYGON_EXCEPTIONS.add(Tag("natural", "sea"))
        POLYGON_EXCEPTIONS.add(Tag("natural", "nosea"))

        POLYGON_EXCEPTIONS.add(Tag("freizeitkarte", "meer"))
        POLYGON_EXCEPTIONS.add(Tag("freizeitkarte", "land"))
    }
}
