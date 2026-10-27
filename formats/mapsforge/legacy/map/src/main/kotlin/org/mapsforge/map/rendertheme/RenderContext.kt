/*
 * Copyright 2015 Ludwig M Brinckmann
 * Copyright 2024 Sublimis
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
package org.mapsforge.map.rendertheme

import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Tile
import org.mapsforge.map.layer.renderer.CanvasRasterer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.layer.renderer.ShapePaintContainer
import org.mapsforge.map.rendertheme.rule.RenderTheme
import java.util.LinkedList
import java.util.concurrent.ExecutionException

/**
 * A RenderContext contains all the information and data to render a map area, it is passed between
 * calls in order to avoid local data stored in the DatabaseRenderer. Creating it waits for the
 * job's theme and scales it to the job's zoom level.
 */
open class RenderContext @Throws(InterruptedException::class, ExecutionException::class) constructor(
    rendererJob: RendererJob,
    graphicFactory: GraphicFactory,
) {

    @JvmField
    val rendererJob: RendererJob

    @JvmField
    val renderTheme: RenderTheme

    // Configuration that drives the rendering
    @JvmField
    val canvasRasterer: CanvasRasterer

    // Data generated for the rendering process
    private var drawingLayer: List<MutableList<ShapePaintContainer>>? = null

    @JvmField
    protected val labels: MutableList<MapElementContainer>

    @JvmField
    protected val ways: List<List<MutableList<ShapePaintContainer>>>

    init {
        this.rendererJob = rendererJob
        this.labels = LinkedList()
        this.canvasRasterer = CanvasRasterer(this@RenderContext, graphicFactory)
        this.renderTheme = rendererJob.renderThemeFuture!!.get()
        this.renderTheme.scaleTextSize(rendererJob.textScale, rendererJob.tile.zoomLevel)
        this.ways = createWayLists()
        setScaleStrokeWidth(this.rendererJob.tile.zoomLevel)
    }

    fun destroy() {
        this.canvasRasterer.destroy()
    }

    fun setDrawingLayer(layer: Byte) {
        var index = layer
        if (index < 0) {
            index = 0
        } else if (index >= LAYERS) {
            index = (LAYERS - 1).toByte()
        }
        this.drawingLayer = this.ways[index.toInt()]
    }

    fun addToCurrentDrawingLayer(level: Int, element: ShapePaintContainer) {
        this.drawingLayer!![level].add(element)
    }

    fun addToDrawingLayer(layer: Int, level: Int, element: ShapePaintContainer) {
        this.ways[layer][level].add(element)
    }

    fun drawWays() {
        val levelsPerLayer = this.ways[0].size

        for (layer in 0 until this.ways.size) {
            val shapePaintContainers = this.ways[layer]

            for (level in 0 until levelsPerLayer) {
                val wayList = shapePaintContainers[level]

                for (index in wayList.size - 1 downTo 0) {
                    canvasRasterer.drawShapePaintContainer(wayList[index])
                }
            }
        }
    }

    fun addLabel(element: MapElementContainer) {
        this.labels.add(element)
    }

    fun getLabels(): MutableList<MapElementContainer> {
        return this.labels
    }

    fun clearLabels(labelsToClear: Set<MapElementContainer>) {
        this.labels.removeAll(labelsToClear)
    }

    /**
     * Just a way of generating a hash key for a tile if only the RendererJob is known.
     *
     * @return a RendererJob based on the current one, only tile changes
     */
    fun otherTile(tile: Tile): RendererJob {
        return this.rendererJob.otherTile(tile)
    }

    private fun createWayLists(): List<List<MutableList<ShapePaintContainer>>> {
        val result: MutableList<List<MutableList<ShapePaintContainer>>> = ArrayList(LAYERS.toInt())
        val levels = this.renderTheme.levels

        repeat(LAYERS.toInt()) {
            val innerWayList: MutableList<MutableList<ShapePaintContainer>> = ArrayList(levels)
            repeat(levels) {
                innerWayList.add(ArrayList(0))
            }
            result.add(innerWayList)
        }
        return result
    }

    /**
     * Sets the scale stroke factor for the given zoom level.
     */
    private fun setScaleStrokeWidth(zoomLevel: Byte) {
        val zoomLevelDiff = Math.max(zoomLevel - STROKE_MIN_ZOOM_LEVEL, 0)
        this.renderTheme.scaleStrokeWidth(
            Math.pow(STROKE_INCREASE, zoomLevelDiff.toDouble()).toFloat(),
            this.rendererJob.tile.zoomLevel,
        )
    }

    private companion object {

        private const val LAYERS: Byte = 11

        private const val STROKE_INCREASE = 1.5
        private const val STROKE_MIN_ZOOM_LEVEL: Byte = 12
    }
}
