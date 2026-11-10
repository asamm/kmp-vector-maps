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
package com.asamm.vectormaps.mapsforge.map.rendertheme

import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.mapelements.MapElementContainer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.CanvasRasterer
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.layer.renderer.ShapePaintContainer
import com.asamm.vectormaps.mapsforge.map.rendertheme.rule.RenderTheme
import java.util.LinkedList
import java.util.concurrent.ExecutionException
import kotlin.math.max
import kotlin.math.pow

/**
 * A RenderContext contains all the information and data to render a map area, it is passed between
 * calls in order to avoid local data stored in the DatabaseRenderer. Creating it waits for the
 * job's theme and scales it to the job's zoom level.
 */
open class RenderContext @Throws(InterruptedException::class, ExecutionException::class) constructor(
    val rendererJob: RendererJob,
    graphicFactory: GraphicFactory,
) {

    val renderTheme: RenderTheme

    // Configuration that drives the rendering
    val canvasRasterer: CanvasRasterer

    // Data generated for the rendering process
    private var drawingLayer = 0

    private val labels: MutableList<MapElementContainer>

    // per layer the lists of its levels, each created on first use (C15)
    private val ways = arrayOfNulls<Array<MutableList<ShapePaintContainer>?>>(LAYERS.toInt())

    init {
        this.labels = LinkedList()
        this.canvasRasterer = CanvasRasterer(this@RenderContext, graphicFactory)
        this.renderTheme = rendererJob.renderThemeFuture!!.get()
        this.renderTheme.scaleTextSize(rendererJob.textScale, rendererJob.tile.zoomLevel)
        setScaleStrokeWidth(this.rendererJob.tile.zoomLevel)
        // after scaling, so a zoom evicted meanwhile re-enters the window with its Paints
        this.renderTheme.useZoom(rendererJob.tile.zoomLevel, 0)
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
        this.drawingLayer = index.toInt()
    }

    fun addToCurrentDrawingLayer(level: Int, element: ShapePaintContainer) {
        addToDrawingLayer(this.drawingLayer, level, element)
    }

    fun addToDrawingLayer(layer: Int, level: Int, element: ShapePaintContainer) {
        var levels = this.ways[layer]
        if (levels == null) {
            levels = arrayOfNulls(this.renderTheme.levels)
            this.ways[layer] = levels
        }
        var wayList = levels[level]
        if (wayList == null) {
            wayList = ArrayList()
            levels[level] = wayList
        }
        wayList.add(element)
    }

    fun drawWays() {
        for (layer in this.ways.indices) {
            val shapePaintContainers = this.ways[layer] ?: continue

            for (level in shapePaintContainers.indices) {
                val wayList = shapePaintContainers[level] ?: continue

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
     * Sets the scale stroke factor for the given zoom level.
     */
    private fun setScaleStrokeWidth(zoomLevel: Byte) {
        val zoomLevelDiff = max(zoomLevel - STROKE_MIN_ZOOM_LEVEL, 0)
        this.renderTheme.scaleStrokeWidth(
            STROKE_INCREASE.pow(zoomLevelDiff.toDouble()).toFloat(),
            this.rendererJob.tile.zoomLevel,
        )
    }

    private companion object {

        private const val LAYERS: Byte = 11

        private const val STROKE_INCREASE = 1.5
        private const val STROKE_MIN_ZOOM_LEVEL: Byte = 12
    }
}
