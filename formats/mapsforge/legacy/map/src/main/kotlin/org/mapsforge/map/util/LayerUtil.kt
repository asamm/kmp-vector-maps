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
package org.mapsforge.map.util

import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.layer.TilePosition
import java.util.Collections
import java.util.LinkedList

object LayerUtil {

    /**
     * The tiles covering `boundingBox` with their pixel positions relative to `topLeftPoint`.
     */
    @JvmStatic
    fun getTilePositions(boundingBox: BoundingBox, zoomLevel: Byte, topLeftPoint: Point, tileSize: Int): List<TilePosition> {
        return getTilePositions(boundingBox, zoomLevel, topLeftPoint, tileSize, 0)
    }

    /**
     * The tiles covering `boundingBox` plus `margin` tiles around it, clipped to the map, with their
     * pixel positions relative to `topLeftPoint`.
     */
    @JvmStatic
    fun getTilePositions(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        topLeftPoint: Point,
        tileSize: Int,
        margin: Int,
    ): List<TilePosition> {
        val maxTileNumber = Tile.getMaxTileNumber(zoomLevel)

        val tileLeft = Math.max(0, MercatorProjection.longitudeToTileX(boundingBox.minLongitude, zoomLevel) - margin)
        val tileTop = Math.max(0, MercatorProjection.latitudeToTileY(boundingBox.maxLatitude, zoomLevel) - margin)
        val tileRight = Math.min(
            MercatorProjection.longitudeToTileX(boundingBox.maxLongitude, zoomLevel) + margin,
            maxTileNumber,
        )
        val tileBottom = Math.min(
            MercatorProjection.latitudeToTileY(boundingBox.minLatitude, zoomLevel) + margin,
            maxTileNumber,
        )

        val initialCapacity = (tileRight - tileLeft + 1) * (tileBottom - tileTop + 1)
        val tilePositions = ArrayList<TilePosition>(initialCapacity)

        for (tileY in tileTop..tileBottom) {
            for (tileX in tileLeft..tileRight) {
                val pixelX = MercatorProjection.tileToPixel(tileX.toLong(), tileSize) - topLeftPoint.x
                val pixelY = MercatorProjection.tileToPixel(tileY.toLong(), tileSize) - topLeftPoint.y

                tilePositions.add(TilePosition(Tile(tileX, tileY, zoomLevel, tileSize), Point(pixelX, pixelY)))
            }
        }

        return tilePositions
    }

    @JvmStatic
    fun getUpperLeft(boundingBox: BoundingBox, zoomLevel: Byte, tileSize: Int): Tile {
        val tileLeft = MercatorProjection.longitudeToTileX(boundingBox.minLongitude, zoomLevel)
        val tileTop = MercatorProjection.latitudeToTileY(boundingBox.maxLatitude, zoomLevel)
        return Tile(tileLeft, tileTop, zoomLevel, tileSize)
    }

    @JvmStatic
    fun getLowerRight(boundingBox: BoundingBox, zoomLevel: Byte, tileSize: Int): Tile {
        val tileRight = MercatorProjection.longitudeToTileX(boundingBox.maxLongitude, zoomLevel)
        val tileBottom = MercatorProjection.latitudeToTileY(boundingBox.minLatitude, zoomLevel)
        return Tile(tileRight, tileBottom, zoomLevel, tileSize)
    }

    /**
     * The tiles from `upperLeft` to `lowerRight`.
     */
    @JvmStatic
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
     * The tiles covering `boundingBox`.
     */
    @JvmStatic
    fun getTiles(boundingBox: BoundingBox, zoomLevel: Byte, tileSize: Int): Set<Tile> {
        val tileLeft = MercatorProjection.longitudeToTileX(boundingBox.minLongitude, zoomLevel)
        val tileTop = MercatorProjection.latitudeToTileY(boundingBox.maxLatitude, zoomLevel)
        val tileRight = MercatorProjection.longitudeToTileX(boundingBox.maxLongitude, zoomLevel)
        val tileBottom = MercatorProjection.latitudeToTileY(boundingBox.minLatitude, zoomLevel)

        val tiles = HashSet<Tile>()

        for (tileY in tileTop..tileBottom) {
            for (tileX in tileLeft..tileRight) {
                tiles.add(Tile(tileX, tileY, zoomLevel, tileSize))
            }
        }
        return tiles
    }

    /**
     * The items that do not clash with an already kept, higher-priority item, ascending or
     * descending by priority.
     * Sorts `input` in place.
     */
    @JvmStatic
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

    @JvmStatic
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
    @JvmStatic
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

    @JvmStatic
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
    @JvmStatic
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
    @JvmStatic
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
    @JvmStatic
    fun isLabelMultiTiled(labelRect: Rectangle?, tileRect: Rectangle): Boolean {
        return !tileRect.contains(labelRect)
            && tileRect.intersects(labelRect)
    }
}
