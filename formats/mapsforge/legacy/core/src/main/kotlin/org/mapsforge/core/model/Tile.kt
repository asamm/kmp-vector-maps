/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2016 Ludwig M Brinckmann
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
package org.mapsforge.core.model

import org.mapsforge.core.util.MercatorProjection
import java.io.Serializable

/**
 * A tile of the Mercator grid at a zoom level and tile size; out of range numbers throw
 * [IllegalArgumentException]. Equal by number, zoom level and tile size.
 */
open class Tile(
    @JvmField val tileX: Int,
    @JvmField val tileY: Int,
    @JvmField val zoomLevel: Byte,
    @JvmField val tileSize: Int,
) : Serializable {

    /**
     * The map size at this zoom level in pixels.
     */
    @JvmField
    val mapSize: Long

    @Volatile
    private var cachedBoundingBox: BoundingBox? = null

    @Volatile
    private var cachedOrigin: Point? = null

    @Transient
    private val mySync = Any()

    init {
        if (tileX < 0) {
            throw IllegalArgumentException("tileX must not be negative: $tileX")
        } else if (tileY < 0) {
            throw IllegalArgumentException("tileY must not be negative: $tileY")
        } else if (zoomLevel < 0) {
            throw IllegalArgumentException("zoomLevel must not be negative: $zoomLevel")
        }

        val maxTileNumber = getMaxTileNumber(zoomLevel).toLong()
        if (tileX > maxTileNumber) {
            throw IllegalArgumentException("invalid tileX number on zoom level $zoomLevel: $tileX")
        } else if (tileY > maxTileNumber) {
            throw IllegalArgumentException("invalid tileY number on zoom level $zoomLevel: $tileY")
        }

        mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Tile) {
            return false
        }
        return tileX == other.tileX
            && tileY == other.tileY
            && zoomLevel == other.zoomLevel
            && tileSize == other.tileSize
    }

    /**
     * The geographic box of this tile, computed once.
     */
    val boundingBox: BoundingBox
        get() {
            var myBoundingBox = cachedBoundingBox
            if (myBoundingBox == null) {
                synchronized(mySync) {
                    myBoundingBox = cachedBoundingBox
                    if (myBoundingBox == null) {
                        val minLatitude = Math.max(
                            MercatorProjection.LATITUDE_MIN,
                            MercatorProjection.tileYToLatitude((tileY + 1).toLong(), zoomLevel),
                        )
                        val minLongitude = Math.max(-180.0, MercatorProjection.tileXToLongitude(tileX.toLong(), zoomLevel))
                        val maxLatitude = Math.min(
                            MercatorProjection.LATITUDE_MAX,
                            MercatorProjection.tileYToLatitude(tileY.toLong(), zoomLevel),
                        )
                        var maxLongitude = Math.min(180.0, MercatorProjection.tileXToLongitude((tileX + 1).toLong(), zoomLevel))
                        if (maxLongitude == -180.0) {
                            // dateline crossing: the right tile starts at -180, an invalid box
                            maxLongitude = 180.0
                        }
                        val created = BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude)
                        cachedBoundingBox = created
                        myBoundingBox = created
                    }
                }
            }
            return myBoundingBox!!
        }

    /**
     * The eight surrounding tiles, wrapping at the map's edges.
     */
    val neighbours: Set<Tile>
        get() {
            val neighbours = HashSet<Tile>(8)
            neighbours.add(left)
            neighbours.add(aboveLeft)
            neighbours.add(above)
            neighbours.add(aboveRight)
            neighbours.add(right)
            neighbours.add(belowRight)
            neighbours.add(below)
            neighbours.add(belowLeft)
            return neighbours
        }

    /**
     * This tile's pixel rectangle on the whole map.
     */
    val boundaryAbsolute: Rectangle
        get() {
            return Rectangle(origin.x, origin.y, origin.x + tileSize, origin.y + tileSize)
        }

    /**
     * This tile's pixel rectangle in its own coordinates.
     */
    val boundaryRelative: Rectangle
        get() {
            return Rectangle(0.0, 0.0, tileSize.toDouble(), tileSize.toDouble())
        }

    /**
     * The upper left corner in pixels on the whole map, computed once.
     */
    val origin: Point
        get() {
            var myOrigin = cachedOrigin
            if (myOrigin == null) {
                synchronized(mySync) {
                    myOrigin = cachedOrigin
                    if (myOrigin == null) {
                        val x = MercatorProjection.tileToPixel(tileX.toLong(), tileSize).toDouble()
                        val y = MercatorProjection.tileToPixel(tileY.toLong(), tileSize).toDouble()
                        val created = Point(x, y)
                        cachedOrigin = created
                        myOrigin = created
                    }
                }
            }
            return myOrigin!!
        }

    /**
     * The tile to the left, wrapping at the map's edge.
     */
    val left: Tile
        get() {
            var x = tileX - 1
            if (x < 0) {
                x = getMaxTileNumber(zoomLevel)
            }
            return Tile(x, tileY, zoomLevel, tileSize)
        }

    /**
     * The tile to the right, wrapping at the map's edge.
     */
    val right: Tile
        get() {
            var x = tileX + 1
            if (x > getMaxTileNumber(zoomLevel)) {
                x = 0
            }
            return Tile(x, tileY, zoomLevel, tileSize)
        }

    /**
     * The tile above, wrapping at the map's edge.
     */
    val above: Tile
        get() {
            var y = tileY - 1
            if (y < 0) {
                y = getMaxTileNumber(zoomLevel)
            }
            return Tile(tileX, y, zoomLevel, tileSize)
        }

    /**
     * The tile below, wrapping at the map's edge.
     */
    val below: Tile
        get() {
            var y = tileY + 1
            if (y > getMaxTileNumber(zoomLevel)) {
                y = 0
            }
            return Tile(tileX, y, zoomLevel, tileSize)
        }

    val aboveLeft: Tile
        get() {
            var y = tileY - 1
            var x = tileX - 1
            if (y < 0) {
                y = getMaxTileNumber(zoomLevel)
            }
            if (x < 0) {
                x = getMaxTileNumber(zoomLevel)
            }
            return Tile(x, y, zoomLevel, tileSize)
        }

    val aboveRight: Tile
        get() {
            var y = tileY - 1
            var x = tileX + 1
            if (y < 0) {
                y = getMaxTileNumber(zoomLevel)
            }
            if (x > getMaxTileNumber(zoomLevel)) {
                x = 0
            }
            return Tile(x, y, zoomLevel, tileSize)
        }

    val belowLeft: Tile
        get() {
            var y = tileY + 1
            var x = tileX - 1
            if (y > getMaxTileNumber(zoomLevel)) {
                y = 0
            }
            if (x < 0) {
                x = getMaxTileNumber(zoomLevel)
            }
            return Tile(x, y, zoomLevel, tileSize)
        }

    val belowRight: Tile
        get() {
            var y = tileY + 1
            var x = tileX + 1
            if (y > getMaxTileNumber(zoomLevel)) {
                y = 0
            }
            if (x > getMaxTileNumber(zoomLevel)) {
                x = 0
            }
            return Tile(x, y, zoomLevel, tileSize)
        }

    /**
     * The tile one zoom level up, null at zoom level 0.
     */
    val parent: Tile?
        get() {
            if (zoomLevel.toInt() == 0) {
                return null
            }
            return Tile(tileX / 2, tileY / 2, (zoomLevel - 1).toByte(), tileSize)
        }

    /**
     * The horizontal offset of this tile within `otherTile`, an ancestor, in tiles.
     */
    fun getShiftX(otherTile: Tile): Int {
        if (this == otherTile) {
            return 0
        }
        return tileX % 2 + 2 * parent!!.getShiftX(otherTile)
    }

    /**
     * The vertical offset of this tile within `otherTile`, an ancestor, in tiles.
     */
    fun getShiftY(otherTile: Tile): Int {
        if (this == otherTile) {
            return 0
        }
        return tileY % 2 + 2 * parent!!.getShiftY(otherTile)
    }

    override fun hashCode(): Int {
        var result = 7
        result = 31 * result + (tileX xor (tileX ushr 16))
        result = 31 * result + (tileY xor (tileY ushr 16))
        result = 31 * result + zoomLevel
        result = 31 * result + tileSize
        return result
    }

    override fun toString(): String {
        return "x=$tileX, y=$tileY, z=$zoomLevel"
    }

    companion object {

        private const val serialVersionUID = 1L

        /**
         * The geographic box from `upperLeft` to `lowerRight`.
         */
        @JvmStatic
        fun getBoundingBox(upperLeft: Tile, lowerRight: Tile): BoundingBox {
            val ul = upperLeft.boundingBox
            val lr = lowerRight.boundingBox
            return ul.extendBoundingBox(lr)
        }

        /**
         * The pixel rectangle from `upperLeft` to `lowerRight` on the whole map.
         */
        @JvmStatic
        fun getBoundaryAbsolute(upperLeft: Tile, lowerRight: Tile): Rectangle {
            return Rectangle(
                upperLeft.origin.x,
                upperLeft.origin.y,
                lowerRight.origin.x + upperLeft.tileSize,
                lowerRight.origin.y + upperLeft.tileSize,
            )
        }

        /**
         * True if the two tile areas overlap; false across zoom levels.
         */
        @JvmStatic
        fun tileAreasOverlap(upperLeft: Tile, lowerRight: Tile, upperLeftOther: Tile, lowerRightOther: Tile): Boolean {
            if (upperLeft.zoomLevel != upperLeftOther.zoomLevel) {
                return false
            }
            if (upperLeft == upperLeftOther
                && lowerRight == lowerRightOther
            ) {
                return true
            }
            return getBoundaryAbsolute(upperLeft, lowerRight).intersects(getBoundaryAbsolute(upperLeftOther, lowerRightOther))
        }

        /**
         * The highest tile number at `zoomLevel`; negative throws [IllegalArgumentException].
         */
        @JvmStatic
        fun getMaxTileNumber(zoomLevel: Byte): Int {
            if (zoomLevel < 0) {
                throw IllegalArgumentException("zoomLevel must not be negative: $zoomLevel")
            } else if (zoomLevel.toInt() == 0) {
                return 0
            }
            return (2 shl zoomLevel - 1) - 1
        }
    }
}
