/*
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2015-2022 devemux86
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
import org.mapsforge.core.model.Tile
import java.util.Collections
import java.util.Comparator

/**
 * Reads several map stores as one, highest priority first; [DataPolicy] decides whether the first
 * covering store answers or all of them, with or without deduplication. A store with a negative
 * priority only fills tiles no other store fully covers.
 */
open class MultiMapDataStore @JvmOverloads constructor(
    private val dataPolicy: DataPolicy = DataPolicy.DEDUPLICATE,
) : MapDataStore() {

    enum class DataPolicy {

        /**
         * The data of the first store that covers the request.
         */
        RETURN_FIRST,

        /**
         * The data of all stores.
         */
        RETURN_ALL,

        /**
         * The data of all stores without duplicates.
         */
        DEDUPLICATE,
    }

    private var boundingBox: BoundingBox? = null
    private val mapDatabases: MutableList<MapDataStore> = ArrayList()
    private var startPosition: LatLong? = null
    private var startZoomLevel: Byte = 0

    /**
     * Adds a store; the same store twice throws [IllegalArgumentException].
     *
     * @param useStartZoomLevel take this store's start zoom level
     * @param useStartPosition take this store's start position
     */
    fun addMapDataStore(mapDataStore: MapDataStore, useStartZoomLevel: Boolean, useStartPosition: Boolean) {
        if (mapDatabases.contains(mapDataStore)) {
            throw IllegalArgumentException("Duplicate map database")
        }
        mapDatabases.add(mapDataStore)
        if (useStartZoomLevel) {
            startZoomLevel = mapDataStore.startZoomLevel()!!
        }
        if (useStartPosition) {
            startPosition = mapDataStore.startPosition()
        }
        val current = boundingBox
        boundingBox = if (null == current) {
            mapDataStore.boundingBox()
        } else {
            current.extendBoundingBox(mapDataStore.boundingBox()!!)
        }
        // reverse order of priority
        Collections.sort(mapDatabases, Comparator { mds1, mds2 -> -Integer.compare(mds1.getPriority(), mds2.getPriority()) })
    }

    override fun boundingBox(): BoundingBox? {
        return boundingBox
    }

    override fun close() {
        for (mdb in mapDatabases) {
            mdb.close()
        }
    }

    /**
     * The first covering store's timestamp, or the newest of all covering stores.
     */
    override fun getDataTimestamp(tile: Tile): Long {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (mdb.supportsTile(tile)) {
                        return mdb.getDataTimestamp(tile)
                    }
                }
                return 0
            }
            DataPolicy.RETURN_ALL, DataPolicy.DEDUPLICATE -> {
                var result = 0L
                for (mdb in mapDatabases) {
                    if (mdb.supportsTile(tile)) {
                        result = Math.max(result, mdb.getDataTimestamp(tile))
                    }
                }
                return result
            }
        }
    }

    override fun readNamedItems(tile: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (mdb.supportsTile(tile)) {
                        return mdb.readNamedItems(tile)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllNamedItems(tile)
            DataPolicy.DEDUPLICATE -> return readAllNamedItems(tile).deduplicate()
        }
    }

    private fun readAllNamedItems(tile: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> mdb.supportsTile(tile) },
            supportsFully = { mdb -> mdb.supportsFullTile(tile) },
            read = { mdb -> mdb.readNamedItems(tile) },
        )
    }

    override fun readNamedItems(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (coversArea(mdb, upperLeft, lowerRight)) {
                        return mdb.readNamedItems(upperLeft, lowerRight)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllNamedItems(upperLeft, lowerRight)
            DataPolicy.DEDUPLICATE -> return readAllNamedItems(upperLeft, lowerRight).deduplicate()
        }
    }

    private fun readAllNamedItems(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> coversArea(mdb, upperLeft, lowerRight) },
            supportsFully = { mdb -> coversFullArea(mdb, upperLeft, lowerRight) },
            read = { mdb -> mdb.readNamedItems(upperLeft, lowerRight) },
        )
    }

    override fun readMapData(tile: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (mdb.supportsTile(tile)) {
                        return mdb.readMapData(tile)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllMapData(tile)
            DataPolicy.DEDUPLICATE -> return readAllMapData(tile).deduplicate()
        }
    }

    private fun readAllMapData(tile: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> mdb.supportsTile(tile) },
            supportsFully = { mdb -> mdb.supportsFullTile(tile) },
            read = { mdb -> mdb.readMapData(tile) },
        )
    }

    override fun readMapData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (coversArea(mdb, upperLeft, lowerRight)) {
                        return mdb.readMapData(upperLeft, lowerRight)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllMapData(upperLeft, lowerRight)
            DataPolicy.DEDUPLICATE -> return readAllMapData(upperLeft, lowerRight).deduplicate()
        }
    }

    private fun readAllMapData(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> coversArea(mdb, upperLeft, lowerRight) },
            supportsFully = { mdb -> coversFullArea(mdb, upperLeft, lowerRight) },
            read = { mdb -> mdb.readMapData(upperLeft, lowerRight) },
        )
    }

    override fun readPoiData(tile: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (mdb.supportsTile(tile)) {
                        return mdb.readPoiData(tile)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllPoiData(tile)
            DataPolicy.DEDUPLICATE -> return readAllPoiData(tile).deduplicate()
        }
    }

    private fun readAllPoiData(tile: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> mdb.supportsTile(tile) },
            supportsFully = { mdb -> mdb.supportsFullTile(tile) },
            read = { mdb -> mdb.readPoiData(tile) },
        )
    }

    override fun readPoiData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        when (dataPolicy) {
            DataPolicy.RETURN_FIRST -> {
                for (mdb in mapDatabases) {
                    if (coversArea(mdb, upperLeft, lowerRight)) {
                        return mdb.readPoiData(upperLeft, lowerRight)
                    }
                }
                return null
            }
            DataPolicy.RETURN_ALL -> return readAllPoiData(upperLeft, lowerRight)
            DataPolicy.DEDUPLICATE -> return readAllPoiData(upperLeft, lowerRight).deduplicate()
        }
    }

    private fun readAllPoiData(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return readAll(
            supports = { mdb -> coversArea(mdb, upperLeft, lowerRight) },
            supportsFully = { mdb -> coversFullArea(mdb, upperLeft, lowerRight) },
            read = { mdb -> mdb.readPoiData(upperLeft, lowerRight) },
        )
    }

    // every store in priority order; the negative-priority ones only until a store fills the tile
    private inline fun readAll(
        supports: (MapDataStore) -> Boolean,
        supportsFully: (MapDataStore) -> Boolean,
        read: (MapDataStore) -> MapReadResult?,
    ): MapReadResult {
        val mapReadResult = MapReadResult()
        var isTileFilled = false
        for (mdb in mapDatabases) {
            if (isTileFilled
                && mdb.getPriority() < 0
            ) {
                break
            }
            if (supports(mdb)) {
                val result = read(mdb) ?: continue
                val isWater = mapReadResult.isWater and result.isWater
                mapReadResult.isWater = isWater
                mapReadResult.add(result)
            }
            if (supportsFully(mdb)) {
                isTileFilled = true
            }
        }
        return mapReadResult
    }

    private fun coversArea(mdb: MapDataStore, upperLeft: Tile, lowerRight: Tile): Boolean {
        return mdb.supportsArea(Tile.getBoundingBox(upperLeft, lowerRight), upperLeft.zoomLevel)
    }

    private fun coversFullArea(mdb: MapDataStore, upperLeft: Tile, lowerRight: Tile): Boolean {
        return mdb.supportsFullArea(Tile.getBoundingBox(upperLeft, lowerRight), upperLeft.zoomLevel)
    }

    fun setStartPosition(startPosition: LatLong?) {
        this.startPosition = startPosition
    }

    fun setStartZoomLevel(startZoomLevel: Byte) {
        this.startZoomLevel = startZoomLevel
    }

    /**
     * The set start position, else the center of all stores, else null.
     */
    override fun startPosition(): LatLong? {
        startPosition?.let {
            return it
        }
        return boundingBox?.centerPoint
    }

    override fun startZoomLevel(): Byte? {
        return startZoomLevel
    }

    override fun supportsTile(tile: Tile): Boolean {
        for (mdb in mapDatabases) {
            if (mdb.supportsTile(tile)) {
                return true
            }
        }
        return false
    }

    override fun supportsFullTile(tile: Tile): Boolean {
        for (mdb in mapDatabases) {
            if (mdb.supportsFullTile(tile)) {
                return true
            }
        }
        return false
    }

    override fun supportsArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        for (mdb in mapDatabases) {
            if (mdb.supportsArea(boundingBox, zoomLevel)) {
                return true
            }
        }
        return false
    }

    override fun supportsFullArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        for (mdb in mapDatabases) {
            if (mdb.supportsFullArea(boundingBox, zoomLevel)) {
                return true
            }
        }
        return false
    }
}
