/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
package com.asamm.vectormaps.mapsforge.map.layer.renderer

import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.datastore.MapDataStore
import com.asamm.vectormaps.mapsforge.map.layer.queue.Job
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderThemeFuture

/**
 * A tile to render from a map data store with a theme; the hash code leaves out alpha, the display
 * model and `labelsOnly`, equality does not. A null `mapFile` or a non-positive or NaN `textScale`
 * throws [IllegalArgumentException].
 */
open class RendererJob(
    tile: Tile?,
    mapFile: MapDataStore?,
    val renderThemeFuture: RenderThemeFuture?,
    val displayModel: DisplayModel,
    val textScale: Float,
    isTransparent: Boolean,
    val labelsOnly: Boolean,
) : Job(tile, isTransparent) {

    val mapDataStore: MapDataStore

    private val hashCodeValue: Int

    init {
        if (mapFile == null) {
            throw IllegalArgumentException("mapFile must not be null")
        } else if (textScale <= 0f
            || textScale.isNaN()
        ) {
            throw IllegalArgumentException("invalid textScale: $textScale")
        }

        this.mapDataStore = mapFile

        this.hashCodeValue = calculateHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (!super.equals(other)) {
            return false
        } else if (other !is RendererJob) {
            return false
        }
        if (mapDataStore != other.mapDataStore) {
            return false
        } else if (textScale.toBits()
            != other.textScale.toBits()
        ) {
            return false
        } else if (renderThemeFuture == null
            && other.renderThemeFuture != null
        ) {
            return false
        } else if (renderThemeFuture != null
            && renderThemeFuture != other.renderThemeFuture
        ) {
            return false
        } else if (labelsOnly != other.labelsOnly) {
            return false
        } else if (displayModel != other.displayModel) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return hashCodeValue
    }

    /**
     * This job for another tile, a way to get the cache key of a tile when only the job is known.
     */
    fun otherTile(tile: Tile): RendererJob {
        return RendererJob(
            tile,
            mapDataStore,
            renderThemeFuture,
            displayModel,
            textScale,
            hasAlpha,
            labelsOnly,
        )
    }

    private fun calculateHashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + mapDataStore.hashCode()
        result = prime * result + textScale.toBits()
        if (renderThemeFuture != null) {
            result = prime * result + renderThemeFuture.hashCode()
        }
        return result
    }
}
