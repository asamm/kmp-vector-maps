/*
 * Copyright 2014 Ludwig M Brinckmann
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
 * An [LRUCache] that also keeps a working set of keys from eviction in the near future.
 */
open class WorkingSetCache<K, V>(capacity: Int) : LRUCache<K, V>(capacity) {

    /**
     * Touches every key of `workingSet`, so they are evicted last; locks the set while reading it.
     */
    open fun setWorkingSet(workingSet: Set<@JvmSuppressWildcards K>) {
        synchronized(workingSet) {
            for (key in workingSet) {
                this[key]
            }
        }
    }

    private companion object {

        private const val serialVersionUID = 1L
    }
}
