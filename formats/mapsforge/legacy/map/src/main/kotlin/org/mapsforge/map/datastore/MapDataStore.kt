/*
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2015 devemux86
 * Copyright 2015 lincomatic
 * Copyright 2024-2025 Sublimis
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
package org.mapsforge.map.datastore

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import java.util.Locale

/**
 * Base class for map data stores.
 *
 * @param language the preferred language for localized names, null for the default
 */
abstract class MapDataStore @JvmOverloads constructor(language: String? = null) {

    @JvmField
    protected var priority = 0

    @JvmField
    protected var preferredLanguage: String? = language

    open var mapCallback: MapCallback? = null

    /**
     * The area of the map data, null when there is none.
     */
    abstract fun boundingBox(): BoundingBox?

    /**
     * Closes the map database.
     */
    abstract fun close()

    /**
     * `s` in the preferred language, see [extract].
     */
    protected open fun extractLocalized(s: String?): String? {
        return extract(s, preferredLanguage)
    }

    /**
     * The timestamp of the data used to render `tile`.
     */
    abstract fun getDataTimestamp(tile: Tile): Long

    /**
     * Reads only labels for a tile; by default all data, as [readMapData].
     */
    open fun readNamedItems(tile: Tile): MapReadResult? {
        return readMapData(tile)
    }

    /**
     * Reads labels for the area from `upperLeft` to `lowerRight`, tile by tile.
     */
    open fun readNamedItems(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        val result = MapReadResult()
        for (x in upperLeft.tileX..lowerRight.tileX) {
            for (y in upperLeft.tileY..lowerRight.tileY) {
                val current = Tile(x, y, upperLeft.zoomLevel, upperLeft.tileSize)
                result.add(readNamedItems(current)!!)
            }
        }
        return result
    }

    /**
     * Reads all map data of the tile at its zoom level.
     */
    abstract fun readMapData(tile: Tile): MapReadResult?

    /**
     * Reads all map data for the area from `upperLeft` to `lowerRight`, tile by tile.
     */
    open fun readMapData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        val result = MapReadResult()
        for (x in upperLeft.tileX..lowerRight.tileX) {
            for (y in upperLeft.tileY..lowerRight.tileY) {
                val current = Tile(x, y, upperLeft.zoomLevel, upperLeft.tileSize)
                result.add(readMapData(current)!!)
            }
        }
        return result
    }

    /**
     * Reads only POI data for the tile.
     */
    abstract fun readPoiData(tile: Tile): MapReadResult?

    /**
     * Reads POI data for the area from `upperLeft` to `lowerRight`, tile by tile.
     */
    open fun readPoiData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        val result = MapReadResult()
        for (x in upperLeft.tileX..lowerRight.tileX) {
            for (y in upperLeft.tileY..lowerRight.tileY) {
                val current = Tile(x, y, upperLeft.zoomLevel, upperLeft.tileSize)
                result.add(readPoiData(current)!!)
            }
        }
        return result
    }

    /**
     * The priority in a multi-map store; higher reads first, below 0 only fills gaps.
     */
    open fun getPriority(): Int {
        return priority
    }

    /**
     * Sets [getPriority]; returns this store.
     */
    open fun setPriority(priority: Int): MapDataStore {
        this.priority = priority
        return this
    }

    /**
     * The initial position, null when the store has none.
     */
    abstract fun startPosition(): LatLong?

    /**
     * The initial zoom level, null when the store has none.
     */
    abstract fun startZoomLevel(): Byte?

    abstract fun supportsTile(tile: Tile): Boolean

    abstract fun supportsFullTile(tile: Tile): Boolean

    abstract fun supportsArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean

    abstract fun supportsFullArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean

    /**
     * True to keep an unnamed way with `tags` in label reads; false by default.
     */
    open fun wayAsLabelTagFilter(tags: List<Tag>): Boolean {
        return false
    }

    companion object {

        /**
         * The name for `language` from a multilingual name, "default\rlang\bname\r…": an exact
         * language match, else a base-language fallback (zh-min-lan to zh), else the default;
         * null for a blank `s`.
         */
        @JvmStatic
        fun extract(s: String?, language: String?): String? {
            if (s == null
                || s.trim { it <= ' ' }.isEmpty()
            ) {
                return null
            }

            if (language == null
                || language.trim { it <= ' ' }.isEmpty()
            ) {
                // the default name without splitting, the common case
                val end = s.indexOf('\r')
                return if (end < 0) s else s.substring(0, end)
            }

            val langNames = splitJava(s, '\r')

            var fallback: String? = null
            for (i in 1 until langNames.size) {
                val langName = splitJava(langNames[i], '\b')
                if (langName.size != 2) {
                    continue
                }

                // Perfect match
                if (langName[0].equals(language, ignoreCase = true)) {
                    return langName[1]
                }

                // Fall back to base, e.g. zh-min-lan -> zh
                if (fallback == null
                    && !langName[0].contains("-")
                    && (language.contains("-") || language.contains("_"))
                    && language.lowercase(Locale.ENGLISH).startsWith(langName[0].lowercase(Locale.ENGLISH))
                ) {
                    fallback = langName[1]
                }
            }
            return fallback ?: langNames[0]
        }

        // String.split semantics without a regex: trailing empty parts dropped
        private fun splitJava(s: String, separator: Char): List<String> {
            return s.split(separator).dropLastWhile { it.isEmpty() }
        }
    }
}
