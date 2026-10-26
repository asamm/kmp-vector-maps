/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2016 bvgastel
 * Copyright 2017 linuskr
 * Copyright 2017 devemux86
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

import org.mapsforge.core.util.LRUCache
import org.mapsforge.map.reader.header.SubFileParameter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * An LRU cache of index blocks read from a map file; safe for concurrent reads.
 *
 * @param capacity the maximum number of cached blocks; negative throws
 */
internal class IndexCache(
    private val fileChannel: FileChannel,
    capacity: Int,
) {

    private val map: MutableMap<IndexCacheEntryKey, ByteArray> = LRUCache(capacity)

    /**
     * Empties the cache at the end of its lifetime.
     */
    fun destroy() {
        synchronized(map) {
            map.clear()
        }
    }

    /**
     * The index entry of block `blockNumber` in the sub-file; reads and caches its index block on
     * a miss.
     */
    @Throws(IOException::class)
    fun getIndexEntry(subFileParameter: SubFileParameter, blockNumber: Long): Long {
        // check if the block number is out of bounds
        if (blockNumber >= subFileParameter.numberOfBlocks) {
            throw IOException("invalid block number: $blockNumber")
        }

        // calculate the index block number
        val indexBlockNumber = blockNumber / INDEX_ENTRIES_PER_BLOCK

        val indexCacheEntryKey = IndexCacheEntryKey(subFileParameter, indexBlockNumber)

        // check for cached index block; the access-ordered map changes on every get
        var indexBlock = synchronized(map) { map[indexCacheEntryKey] }
        if (indexBlock == null) {
            // cache miss: read the block outside the lock, a racing reader at worst reads it twice
            val indexBlockPosition = subFileParameter.indexStartAddress + indexBlockNumber * SIZE_OF_INDEX_BLOCK

            // in Long: a crafted block count would wrap an Int to any size, also a too small one
            val remainingIndexSize = subFileParameter.indexEndAddress - indexBlockPosition
            val indexBlockSize = minOf(SIZE_OF_INDEX_BLOCK.toLong(), remainingIndexSize).toInt()
            indexBlock = ByteArray(indexBlockSize)
            if (fileChannel.readFully(ByteBuffer.wrap(indexBlock), indexBlockPosition) != indexBlockSize) {
                throw IOException("could not read index block with size: $indexBlockSize")
            }

            synchronized(map) {
                map[indexCacheEntryKey] = indexBlock
            }
        }

        // calculate the address of the index entry inside the index block
        val indexEntryInBlock = blockNumber % INDEX_ENTRIES_PER_BLOCK
        val addressInIndexBlock = (indexEntryInBlock * SubFileParameter.BYTES_PER_INDEX_ENTRY).toInt()

        return Deserializer.getFiveBytesLong(indexBlock, addressInIndexBlock)
    }

    private companion object {

        // index entries per index block
        const val INDEX_ENTRIES_PER_BLOCK = 128

        // maximum size of one index block in bytes
        const val SIZE_OF_INDEX_BLOCK = INDEX_ENTRIES_PER_BLOCK * SubFileParameter.BYTES_PER_INDEX_ENTRY
    }
}
