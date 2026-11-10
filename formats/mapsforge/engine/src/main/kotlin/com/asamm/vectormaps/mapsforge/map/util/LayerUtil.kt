/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2024 Sublimis
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
package com.asamm.vectormaps.mapsforge.map.util

import com.asamm.vectormaps.mapsforge.core.mapelements.MapElementContainer
import com.asamm.vectormaps.mapsforge.core.model.Rectangle
import com.asamm.vectormaps.mapsforge.core.model.Rotation
import com.asamm.vectormaps.mapsforge.core.model.Tile
import java.util.Collections
import java.util.LinkedList

object LayerUtil {

    /**
     * The tiles from `upperLeft` to `lowerRight`.
     */
    fun getTiles(upperLeft: Tile, lowerRight: Tile): Set<Tile> {
        val tiles = HashSet<Tile>()
        for (tileY in upperLeft.tileY..lowerRight.tileY) {
            for (tileX in upperLeft.tileX..lowerRight.tileX) {
                tiles.add(Tile(tileX, tileY, upperLeft.zoomLevel, upperLeft.tileSize))
            }
        }
        return tiles
    }

    /**
     * The items that do not clash with an already kept, higher-priority item, ascending or
     * descending by priority.
     * Sorts `input` in place.
     */
    fun collisionFreeOrdered(
        input: MutableList<MapElementContainer>,
        rotation: Rotation?,
        ascendingOrder: Boolean,
    ): MutableList<MapElementContainer> {
        val output = LinkedList<MapElementContainer>()

        // sort items by priority (highest first)
        Collections.sort(input, Collections.reverseOrder())

        // in order of display and priority, see if an item can be drawn, i.e. none of the items
        // in the currentItemsToDraw list clashes with it.
        for (item in input) {
            var hasSpace = true
            for (outputElement in output) {
                if (outputElement.clashesWith(item, rotation)) {
                    hasSpace = false
                    break
                }
            }
            if (hasSpace) {
                if (ascendingOrder) {
                    output.addFirst(item)
                } else {
                    output.add(item)
                }
            }
        }
        return output
    }

    fun collisionFreeOrdered(
        input: MutableList<MapElementContainer>,
        rotation: Rotation?,
    ): MutableList<MapElementContainer> {
        return collisionFreeOrdered(input, rotation, true)
    }

    /**
     * As [collisionFreeOrdered], first dropping items that span tiles and clash with an equal or
     * higher-priority item.
     */
    fun collisionAndContestingFreeOrdered(
        input: MutableList<MapElementContainer>,
        tile: Tile,
        rotation: Rotation?,
        ascendingOrder: Boolean,
    ): MutableList<MapElementContainer> {
        val output = LinkedList<MapElementContainer>()

        Collections.sort(input, Collections.reverseOrder())

        val inputProcessed = omitContestingMultiTiledItems(input, tile, rotation)

        // in order of display and priority, see if an item can be drawn, i.e. none of the items
        // in the currentItemsToDraw list clashes with it.
        for (item in inputProcessed) {
            var hasSpace = true
            for (outputElement in output) {
                if (item.clashesWith(outputElement, rotation)) {
                    hasSpace = false
                    break
                }
            }
            if (hasSpace) {
                if (ascendingOrder) {
                    output.addFirst(item)
                } else {
                    output.add(item)
                }
            }
        }
        return output
    }

    fun collisionAndContestingFreeOrdered(
        input: MutableList<MapElementContainer>,
        tile: Tile,
        rotation: Rotation?,
    ): MutableList<MapElementContainer> {
        return collisionAndContestingFreeOrdered(input, tile, rotation, true)
    }

    /**
     * `input` without the items that span tiles and clash with an equal or higher-priority item;
     * `input` must be sorted by priority, highest first.
     */
    fun omitContestingMultiTiledItems(
        input: Collection<MapElementContainer>,
        tile: Tile,
        rotation: Rotation?,
    ): List<MapElementContainer> {
        val output = ArrayList<MapElementContainer>()
        val tileRect = tile.boundaryAbsolute

        for (mainItem in input) {
            var toRetain = true

            if (isLabelMultiTiled(mainItem.getClashRect(rotation), tileRect)) {
                for (otherItem in input) {
                    if (otherItem.priority >= mainItem.priority) {
                        if (mainItem != otherItem
                            && mainItem.clashesWith(otherItem, rotation)
                        ) {
                            toRetain = false
                            break
                        }
                    } else {
                        break
                    }
                }
            }

            if (toRetain) {
                output.add(mainItem)
            }
        }

        return output
    }

    /**
     * Removes the items that are not visible from `input`.
     */
    fun removeInvisibleItems(input: MutableCollection<MapElementContainer>) {
        val iterator = input.iterator()
        while (iterator.hasNext()) {
            val label = iterator.next()
            if (label.isNotVisible) {
                iterator.remove()
            }
        }
    }

    /**
     * True if `labelRect` reaches beyond `tileRect` but still overlaps it.
     */
    fun isLabelMultiTiled(labelRect: Rectangle?, tileRect: Rectangle): Boolean {
        return !tileRect.contains(labelRect)
            && tileRect.intersects(labelRect)
    }
}
