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
package org.mapsforge.map.reader

import org.mapsforge.map.reader.header.SubFileParameter

/**
 * The key of an index block in [IndexCache].
 */
internal class IndexCacheEntryKey(
    private val subFileParameter: SubFileParameter,
    private val indexBlockNumber: Long,
) {

    private val hashCodeValue = calculateHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IndexCacheEntryKey) {
            return false
        }
        return subFileParameter == other.subFileParameter
            && indexBlockNumber == other.indexBlockNumber
    }

    override fun hashCode(): Int {
        return hashCodeValue
    }

    private fun calculateHashCode(): Int {
        var result = 7
        result = 31 * result + subFileParameter.hashCode()
        result = 31 * result + (indexBlockNumber xor (indexBlockNumber ushr 32)).toInt()
        return result
    }
}
