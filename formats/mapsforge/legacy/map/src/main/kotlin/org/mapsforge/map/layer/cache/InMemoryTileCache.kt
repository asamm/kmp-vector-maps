/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 devemux86
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
import org.mapsforge.core.util.WorkingSetCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observable
import org.mapsforge.map.model.common.Observer
import java.util.logging.Logger

/**
 * A thread-safe cache for tile images with a variable size and LRU policy.
 *
 * @param capacity the maximum number of entries; negative throws [IllegalArgumentException]
 */
open class InMemoryTileCache(capacity: Int) : TileCache {

    private var lruCache: BitmapLRUCache = BitmapLRUCache(capacity)

    private val observable: Observable = Observable()

    @Synchronized
    final override fun containsKey(key: Job): Boolean {
        return lruCache.containsKey(key)
    }

    @Synchronized
    final override fun destroy() {
        purge()
    }

    @Synchronized
    final override fun get(key: Job): TileBitmap? {
        val bitmap = lruCache[key]
        if (bitmap != null) {
            bitmap.incrementRefCount()
        }
        return bitmap
    }

    @Synchronized
    final override fun getCapacity(): Int {
        return lruCache.capacity
    }

    final override fun getCapacityFirstLevel(): Int {
        return getCapacity()
    }

    final override fun getImmediately(key: Job): TileBitmap? {
        return get(key)
    }

    @Synchronized
    final override fun purge() {
        for (bitmap in lruCache.values) {
            bitmap.decrementRefCount()
        }
        lruCache.clear()
    }

    @Synchronized
    final override fun put(key: Job, bitmap: TileBitmap) {
        val old = lruCache[key]
        if (old != null) {
            old.decrementRefCount()
        }

        if (lruCache.put(key, bitmap) != null) {
            LOGGER.warning("overwriting cached entry: $key")
        }
        bitmap.incrementRefCount()
        observable.notifyObservers()
    }

    /**
     * Sets the new size of this cache, dropping entries by the cache policy if it holds more;
     * negative throws [IllegalArgumentException].
     */
    @Synchronized
    fun setCapacity(capacity: Int) {
        val lruCacheNew = BitmapLRUCache(capacity)
        lruCacheNew.putAll(lruCache)
        lruCache = lruCacheNew
    }

    @Synchronized
    final override fun setWorkingSet(workingSet: Set<@JvmSuppressWildcards Job>) {
        lruCache.setWorkingSet(workingSet)
    }

    final override fun addObserver(observer: Observer?) {
        observable.addObserver(observer)
    }

    final override fun removeObserver(observer: Observer?) {
        observable.removeObserver(observer)
    }

    private companion object {

        private val LOGGER: Logger = Logger.getLogger(InMemoryTileCache::class.java.name)
    }
}

/**
 * The LRU map behind [InMemoryTileCache]; releases the bitmap it evicts.
 */
internal class BitmapLRUCache(capacity: Int) : WorkingSetCache<Job, TileBitmap>(capacity) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Job, TileBitmap>?): Boolean {
        if (size > capacity) {
            val bitmap: TileBitmap? = eldest!!.value
            if (bitmap != null) {
                bitmap.decrementRefCount()
            }
            return true
        }
        return false
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
