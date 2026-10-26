/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observer

/**
 * The tile cache `DatabaseRenderer` asks whether a neighbour was drawn, keeping only the keys: the
 * host caches the bitmaps. Evicts like Mapsforge's `InMemoryTileCache` of the same capacity.
 */
internal class DrawnTiles(private val capacity: Int) : TileCache {

    private val keys = object : LinkedHashMap<Job, Unit>(capacity, 0.75f, true) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Job, Unit>): Boolean {
            return size > capacity
        }
    }

    @Synchronized
    override fun containsKey(key: Job): Boolean {
        return keys.containsKey(key)
    }

    @Synchronized
    override fun put(key: Job, bitmap: TileBitmap) {
        keys[key] = Unit
    }

    @Synchronized
    override fun purge() {
        keys.clear()
    }

    override fun destroy() {
        purge()
    }

    override fun get(key: Job): TileBitmap? {
        return null
    }

    override fun getImmediately(key: Job): TileBitmap? {
        return null
    }

    override fun getCapacity(): Int {
        return capacity
    }

    override fun getCapacityFirstLevel(): Int {
        return capacity
    }

    override fun setWorkingSet(workingSet: Set<Job>) {
        // no working set: keys only
    }

    override fun addObserver(observer: Observer?) {
        // nothing observes a key cache
    }

    override fun removeObserver(observer: Observer?) {
        // nothing observes a key cache
    }
}
