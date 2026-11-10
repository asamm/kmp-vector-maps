/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2025 Sublimis
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
package com.asamm.vectormaps.mapsforge.map.datastore

import com.asamm.vectormaps.mapsforge.core.model.LatLong
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Tag
import java.util.Objects

/**
 * An immutable way or area (a closed way); ordered by layer, sizes, label position, tags, then
 * nodes, null last.
 *
 * @param layer the layer plus 5, so it is not negative
 * @param coordinates the nodes of each ring as latitude, longitude pairs
 * @param labelPosition the position of the area label, null when there is none
 */
open class Way(
    val layer: Byte,
    val tags: List<Tag>,
    val coordinates: Array<DoubleArray>,
    val labelPosition: LatLong?,
) : Comparable<Way?> {

    /**
     * The nodes as new [LatLong]s on every call; the renderer reads [coordinates].
     */
    val latLongs: Array<Array<LatLong>>
        get() {
            return Array(coordinates.size) { i ->
                val ring = coordinates[i]
                Array(ring.size / 2) { j ->
                    LatLong(ring[2 * j], ring[2 * j + 1])
                }
            }
        }

    override fun compareTo(other: Way?): Int {
        if (this === other) {
            return 0
        }
        if (null == other) {
            return -1
        }

        var retVal = layer.compareTo(other.layer)
        if (retVal != 0) {
            return retVal
        }

        retVal = coordinates.size.compareTo(other.coordinates.size)
        if (retVal != 0) {
            return retVal
        }

        retVal = tags.size.compareTo(other.tags.size)
        if (retVal != 0) {
            return retVal
        }

        if (labelPosition != null
            && other.labelPosition != null
        ) {
            retVal = labelPosition.compareTo(other.labelPosition)
            if (retVal != 0) {
                return retVal
            }
        } else if (labelPosition != null) {
            return -1
        } else if (other.labelPosition != null) {
            return 1
        }

        for (i in tags.indices) {
            retVal = tags[i].compareTo(other.tags[i])
            if (retVal != 0) {
                return retVal
            }
        }

        for (i in coordinates.indices) {
            val ring = coordinates[i]
            val otherRing = other.coordinates[i]
            retVal = ring.size.compareTo(otherRing.size)
            if (retVal != 0) {
                return retVal
            }
            // as LatLong.compareTo: longitude, then latitude
            for (j in ring.indices step 2) {
                retVal = Point.compareCoord(ring[j + 1], ring[j], otherRing[j + 1], otherRing[j])
                if (retVal != 0) {
                    return retVal
                }
            }
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Way) {
            return false
        }
        return layer == other.layer
            && Objects.equals(labelPosition, other.labelPosition)
            && Objects.deepEquals(coordinates, other.coordinates)
            && Objects.equals(tags, other.tags)
    }

    override fun hashCode(): Int {
        return Objects.hash(labelPosition, coordinates.contentDeepHashCode(), layer, tags)
    }
}
