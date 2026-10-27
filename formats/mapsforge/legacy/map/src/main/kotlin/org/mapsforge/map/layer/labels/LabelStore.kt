/*
 * Copyright 2014-2015 Ludwig M Brinckmann
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
import org.mapsforge.core.model.Tile

/**
 * The LabelStore is an abstract store for labels from which it is possible to retrieve a
 * priority-ordered queue of items that are visible within a given bounding box for a zoom level.
 */
interface LabelStore {

    /**
     * Clears the data.
     */
    fun clear()

    /**
     * A version number, which changes every time an update is made to the LabelStore.
     */
    fun getVersion(): Int

    /**
     * The map elements visible on the tiles from `upperLeft` to `lowerRight`, the corners of the
     * visible area.
     */
    fun getVisibleItems(upperLeft: Tile?, lowerRight: Tile?): MutableList<MapElementContainer>
}
