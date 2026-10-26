/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015-2020 devemux86
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
package org.mapsforge.map.model

import org.mapsforge.map.model.common.Observable
import org.mapsforge.map.rendertheme.ThemeCallback

/**
 * Display settings: tile size from the device and user scale factors, background color, label
 * width. The scale statics are process-wide.
 */
open class DisplayModel : Observable() {

    private var backgroundColorValue = DEFAULT_BACKGROUND_COLOR
    private var fixedTileSize = 0
    private var maxTextWidthValue = DEFAULT_MAX_TEXT_WIDTH
    private var maxTextWidthFactor = DEFAULT_MAX_TEXT_WIDTH_FACTOR
    private var themeCallbackValue: ThemeCallback? = null
    private var tileSizeValue = DEFAULT_TILE_SIZE
    private var tileSizeMultipleValue = 64
    private var userScaleFactorValue = defaultUserScaleFactor

    init {
        setTileSize()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DisplayModel) {
            return false
        }
        return backgroundColorValue == other.backgroundColorValue
            && fixedTileSize == other.fixedTileSize
            && maxTextWidthValue == other.maxTextWidthValue
            && java.lang.Float.floatToIntBits(maxTextWidthFactor) == java.lang.Float.floatToIntBits(other.maxTextWidthFactor)
            && tileSizeValue == other.tileSizeValue
            && tileSizeMultipleValue == other.tileSizeMultipleValue
            && java.lang.Float.floatToIntBits(userScaleFactorValue) == java.lang.Float.floatToIntBits(other.userScaleFactorValue)
    }

    /**
     * The background color, ARGB.
     */
    var backgroundColor: Int
        @Synchronized
        get() {
            return backgroundColorValue
        }
        @Synchronized
        set(color) {
            backgroundColorValue = color
        }

    /**
     * The maximum width of a text label in pixels.
     */
    val maxTextWidth: Int
        get() {
            return maxTextWidthValue
        }

    /**
     * The device scale factor times the user scale factor.
     */
    val scaleFactor: Float
        @Synchronized
        get() {
            return deviceScaleFactor * userScaleFactorValue
        }

    var themeCallback: ThemeCallback?
        @Synchronized
        get() {
            return themeCallbackValue
        }
        @Synchronized
        set(themeCallback) {
            themeCallbackValue = themeCallback
        }

    /**
     * The tile size in pixels: the fixed one, else 256 scaled and rounded to [tileSizeMultiple].
     */
    val tileSize: Int
        @Synchronized
        get() {
            return tileSizeValue
        }

    /**
     * The tile size is rounded to a multiple of this, 64 by default.
     */
    var tileSizeMultiple: Int
        @Synchronized
        get() {
            return tileSizeMultipleValue
        }
        @Synchronized
        set(multiple) {
            tileSizeMultipleValue = multiple
            setTileSize()
        }

    /**
     * The user's scale factor, which also scales the tile size.
     */
    var userScaleFactor: Float
        @Synchronized
        get() {
            return userScaleFactorValue
        }
        @Synchronized
        set(scaleFactor) {
            userScaleFactorValue = scaleFactor
            setTileSize()
        }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + backgroundColorValue
        result = prime * result + fixedTileSize
        result = prime * result + maxTextWidthValue
        result = prime * result + java.lang.Float.floatToIntBits(maxTextWidthFactor)
        result = prime * result + tileSizeValue
        result = prime * result + tileSizeMultipleValue
        result = prime * result + java.lang.Float.floatToIntBits(userScaleFactorValue)
        return result
    }

    /**
     * A tile size independent of the scale factors; 0 to scale again.
     */
    fun setFixedTileSize(tileSize: Int) {
        fixedTileSize = tileSize
        setTileSize()
    }

    /**
     * The maximum label width as a fraction of the tile size, 0.7 by default.
     */
    fun setMaxTextWidthFactor(maxTextWidthFactor: Float) {
        this.maxTextWidthFactor = maxTextWidthFactor
        setMaxTextWidth()
    }

    private fun setMaxTextWidth() {
        maxTextWidthValue = (tileSizeValue * maxTextWidthFactor).toInt()
    }

    private fun setTileSize() {
        if (fixedTileSize == 0) {
            val temp = DEFAULT_TILE_SIZE * deviceScaleFactor * userScaleFactorValue
            // clamps to the nearest multiple of the tileSizeMultiple, and never to 0
            tileSizeValue = Math.max(
                tileSizeMultipleValue,
                Math.round(temp / tileSizeMultipleValue) * tileSizeMultipleValue,
            )
        } else {
            tileSizeValue = fixedTileSize
        }
        setMaxTextWidth()
    }

    companion object {

        // format AARRGGBB
        private const val DEFAULT_BACKGROUND_COLOR = -0x111112

        private const val DEFAULT_TILE_SIZE = 256

        private const val DEFAULT_MAX_TEXT_WIDTH_FACTOR = 0.7f

        private val DEFAULT_MAX_TEXT_WIDTH = (DEFAULT_TILE_SIZE * DEFAULT_MAX_TEXT_WIDTH_FACTOR).toInt()

        @JvmField
        var lineScale = 1f

        @JvmField
        var symbolScale = 1f

        @JvmField
        var textScale = 1f

        private var defaultUserScaleFactor = 1f

        private var deviceScaleFactor = 1f

        /**
         * The user scale factor new models start with.
         */
        @JvmStatic
        @Synchronized
        fun getDefaultUserScaleFactor(): Float {
            return defaultUserScaleFactor
        }

        /**
         * The device scale factor, e.g. the screen density; process-wide.
         */
        @JvmStatic
        @Synchronized
        fun getDeviceScaleFactor(): Float {
            return deviceScaleFactor
        }

        @JvmStatic
        @Synchronized
        fun setDefaultUserScaleFactor(scaleFactor: Float) {
            defaultUserScaleFactor = scaleFactor
        }

        @JvmStatic
        @Synchronized
        fun setDeviceScaleFactor(scaleFactor: Float) {
            deviceScaleFactor = scaleFactor
        }
    }
}
