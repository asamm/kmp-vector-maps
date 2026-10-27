/*
 * Copyright 2014 Ludwig M Brinckmann
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
package org.mapsforge.map.layer.labels

import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.WorkingSetCache
import org.mapsforge.map.util.LayerUtil

/**
 * A LabelStore where the data is stored per tile.
 */
open class TileBasedLabelStore(
    capacity: Int,
) : WorkingSetCache<Tile, List<MapElementContainer>>(capacity), LabelStore {

    private var lastVisibleTileSet: Set<Tile> = HashSet()

    private var version: Int = 0

    fun destroy() {
        this.clear()
    }

    /**
     * Stores the map elements residing on `tile`.
     */
    @Synchronized
    fun storeMapItems(tile: Tile, mapItems: MutableList<MapElementContainer>) {
        this.put(tile, LayerUtil.collisionFreeOrdered(mapItems, Rotation.NULL_ROTATION, false))
        this.version += 1
    }

    final override fun getVersion(): Int {
        return this.version
    }

    @Synchronized
    final override fun getVisibleItems(
        upperLeft: Tile?,
        lowerRight: Tile?,
    ): MutableList<MapElementContainer> {
        return getVisibleItems(LayerUtil.getTiles(upperLeft!!, lowerRight!!))
    }

    @Synchronized
    private fun getVisibleItems(tiles: Set<Tile>): MutableList<MapElementContainer> {
        lastVisibleTileSet = tiles

        val visibleItems: MutableList<MapElementContainer> = ArrayList()
        for (tile in lastVisibleTileSet) {
            if (containsKey(tile)) {
                visibleItems.addAll(get(tile)!!)
            }
        }
        return visibleItems
    }

    /**
     * True if `tile` is in the current tile set and no data is stored for it.
     */
    @Synchronized
    fun requiresTile(tile: Tile): Boolean {
        return this.lastVisibleTileSet.contains(tile)
            && !this.containsKey(tile)
    }

    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<Tile, List<MapElementContainer>>?,
    ): Boolean {
        if (size > this.capacity) {
            return true
        }
        return false
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
