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
package org.mapsforge.map.datastore

import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tag
import java.util.Arrays
import java.util.Objects

/**
 * An immutable way or area (a closed way); ordered by layer, sizes, label position, tags, then
 * nodes, null last.
 *
 * @param layer the layer plus 5, so it is not negative
 * @param labelPosition the position of the area label, null when there is none
 */
open class Way(
    @JvmField val layer: Byte,
    @JvmField val tags: List<Tag>,
    @JvmField val latLongs: Array<Array<LatLong>>,
    @JvmField val labelPosition: LatLong?,
) : Comparable<Way?> {

    override fun compareTo(other: Way?): Int {
        if (this === other) {
            return 0
        }
        if (null == other) {
            return -1
        }

        var retVal = java.lang.Byte.compare(layer, other.layer)
        if (retVal != 0) {
            return retVal
        }

        retVal = Integer.compare(latLongs.size, other.latLongs.size)
        if (retVal != 0) {
            return retVal
        }

        retVal = Integer.compare(tags.size, other.tags.size)
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

        for (i in latLongs.indices) {
            retVal = Integer.compare(latLongs[i].size, other.latLongs[i].size)
            if (retVal != 0) {
                return retVal
            }
            for (j in latLongs[i].indices) {
                retVal = latLongs[i][j].compareTo(other.latLongs[i][j])
                if (retVal != 0) {
                    return retVal
                }
            }
        }

        return retVal
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
            && Objects.deepEquals(latLongs, other.latLongs)
            && Objects.equals(tags, other.tags)
    }

    override fun hashCode(): Int {
        return Objects.hash(labelPosition, Arrays.deepHashCode(latLongs), layer, tags)
    }
}
