/*
 * Copyright 2016 devemux86
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
package com.asamm.vectormaps.mapsforge.core.util

object Utils {

    fun hashTagParameter(keyValue: String): Int {
        return keyValue.hashCode()
    }

    fun convertListString(list: List<String>): IntArray {
        return IntArray(list.size) { hashTagParameter(list[it]) }
    }

    fun contains(data: IntArray, item: Int): Boolean {
        for (value in data) {
            if (value == item) {
                return true
            }
        }
        return false
    }

    /**
     * Removes duplicates from the sorted `sortedCollection` in place, keeping the order of the rest;
     * iterates it once, so it suits collections with fast removal.
     */
    fun <T : Comparable<T>?> deduplicateSorted(sortedCollection: MutableCollection<T>): MutableCollection<T> {
        if (sortedCollection.isEmpty()) {
            return sortedCollection
        }
        val sortedIterator = sortedCollection.iterator()
        var pivot = sortedIterator.next()
        while (sortedIterator.hasNext()) {
            val item = sortedIterator.next()
            if (pivot === item
                || (pivot != null && pivot.compareTo(item) == 0)
            ) {
                // removing duplicates is cheaper than building a new list when they are few
                sortedIterator.remove()
                continue
            }
            pivot = item
        }
        return sortedCollection
    }
}
