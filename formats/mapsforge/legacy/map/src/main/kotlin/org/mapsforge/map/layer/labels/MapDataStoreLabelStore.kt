/*
 * Copyright 2015 Ludwig M Brinckmann
 * Copyright 2024-2025 Sublimis
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

import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.layer.renderer.StandardRenderer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture

/**
 * A LabelStore that reads the labels out of a MapDataStore.
 */
open class MapDataStoreLabelStore(
    mapDataStore: MapDataStore,
    renderThemeFuture: RenderThemeFuture,
    textScale: Float,
    displayModel: DisplayModel,
    graphicFactory: GraphicFactory,
) : LabelStore {

    internal val textScale: Float = textScale

    internal val renderThemeFuture: RenderThemeFuture = renderThemeFuture

    internal val standardRenderer: StandardRenderer =
        StandardRenderer(mapDataStore, graphicFactory, true)

    internal val displayModel: DisplayModel = displayModel

    final override fun clear() {
    }

    final override fun getVersion(): Int {
        // the mapDataStore cannot change, so version will always be the same.
        return 0
    }

    final override fun getVisibleItems(
        upperLeft: Tile?,
        lowerRight: Tile?,
    ): MutableList<MapElementContainer> {
        try {
            val rendererJob = RendererJob(
                upperLeft,
                this.standardRenderer.mapDataStore,
                this.renderThemeFuture,
                this.displayModel,
                this.textScale,
                true,
                true,
            )
            val renderContext = RenderContext(rendererJob, standardRenderer.graphicFactory)

            val mapReadResult =
                this.standardRenderer.mapDataStore!!.readMapData(upperLeft!!, lowerRight!!)

            if (mapReadResult == null) {
                return ArrayList()
            }

            this.standardRenderer.processReadMapData(renderContext, mapReadResult)

            return renderContext.getLabels()
        } catch (e: Exception) {
            return ArrayList()
        }
    }
}
