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
package org.mapsforge.core.util

/**
 * A map of at most [capacity] entries that drops the least recently accessed one when full.
 * Not thread safe.
 *
 * @param capacity negative throws [IllegalArgumentException]
 */
open class LRUCache<K, V>(
    @JvmField val capacity: Int,
) : LinkedHashMap<K, V>(calculateInitialCapacity(capacity), LOAD_FACTOR, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > capacity
    }

    private companion object {

        private const val LOAD_FACTOR = 0.6f

        private const val serialVersionUID = 1L

        fun calculateInitialCapacity(capacity: Int): Int {
            if (capacity < 0) {
                throw IllegalArgumentException("capacity must not be negative: $capacity")
            }
            return (capacity / LOAD_FACTOR).toInt() + 2
        }
    }
}
