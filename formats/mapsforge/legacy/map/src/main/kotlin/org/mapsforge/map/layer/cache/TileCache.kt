/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
package org.mapsforge.map.layer.cache

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.ObservableInterface

/**
 * Interface for tile image caches.
 */
interface TileCache : ObservableInterface {

    /**
     * True if this cache holds an image for `key`.
     */
    fun containsKey(key: Job): Boolean

    /**
     * Destroys this cache; call it when the cache is no longer needed. To empty a cache that stays
     * in use, call `purge` instead.
     */
    fun destroy()

    /**
     * The image for `key`, null if this cache holds none.
     */
    fun get(key: Job): TileBitmap?

    fun getCapacity(): Int

    /**
     * The capacity of the first level of a multi-level cache.
     */
    fun getCapacityFirstLevel(): Int

    /**
     * The image for `key` only if the fastest level of a multi-level cache holds it, else null.
     */
    fun getImmediately(key: Job): TileBitmap?

    /**
     * Drops every tile added before; purge when map model parameters change, such as the render
     * style for locally rendered tiles or the source for downloaded ones.
     */
    fun purge()

    fun put(key: Job, bitmap: TileBitmap)

    /**
     * Reserves a working set; a multi-level cache brings its elements into the fastest level.
     */
    fun setWorkingSet(workingSet: Set<@JvmSuppressWildcards Job>)
}
