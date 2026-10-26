/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2015-2022 devemux86
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

import org.mapsforge.core.util.Utils
import java.util.Collections
import java.util.LinkedList

/**
 * The ways and POIs a [MapDataStore] read.
 */
open class MapReadResult {

    /**
     * True if the read area is completely covered by water.
     */
    @JvmField
    var isWater = false

    // linked lists: deduplicate removes from them in place
    @JvmField
    val ways: MutableList<Way> = LinkedList()

    @JvmField
    val pois: MutableList<PointOfInterest> = LinkedList()

    fun add(poiWayBundle: PoiWayBundle) {
        ways.addAll(poiWayBundle.ways)
        pois.addAll(poiWayBundle.pois)
    }

    /**
     * Adds the ways and POIs of `other`.
     */
    fun add(other: MapReadResult) {
        ways.addAll(other.ways)
        pois.addAll(other.pois)
    }

    /**
     * Sorts the ways and POIs and drops duplicates; returns this result.
     */
    fun deduplicate(): MapReadResult {
        Collections.sort(ways)
        Utils.deduplicateSorted(ways)
        Collections.sort(pois)
        Utils.deduplicateSorted(pois)
        return this
    }
}
