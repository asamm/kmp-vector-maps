/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2016 devemux86
 * Copyright 2017 usrusr
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
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.SymbolOrientation
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Tag
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.datastore.MapReadResult
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import java.util.Collections

/**
 * Matches the data read from a [MapDataStore] against the render theme and collects the shapes and
 * labels to draw.
 *
 * @param mapDataStore the store the map data is read from
 * @param hillsRenderConfig ignored, hillshading is not supported; kept for the upstream arity,
 * callers pass null
 */
open class StandardRenderer @JvmOverloads constructor(
    mapDataStore: MapDataStore?,
    graphicFactory: GraphicFactory,
    renderLabels: Boolean,
    @Suppress("UNUSED_PARAMETER") hillsRenderConfig: Any? = null,
) {

    @JvmField
    protected val renderCallback: MyRenderCallback = MyRenderCallback()

    @JvmField
    val mapDataStore: MapDataStore? = mapDataStore

    @JvmField
    val graphicFactory: GraphicFactory = graphicFactory

    @JvmField
    protected val renderLabels: Boolean = renderLabels

    /**
     * The start point of the map, null without a store or one.
     */
    val startPosition: LatLong?
        get() {
            if (mapDataStore != null) {
                return mapDataStore.startPosition()
            }
            return null
        }

    /**
     * The start zoom level of the map, a default without a store or one.
     */
    val startZoomLevel: Byte?
        get() {
            if (mapDataStore != null
                && null != mapDataStore.startZoomLevel()
            ) {
                return mapDataStore.startZoomLevel()
            }
            return DEFAULT_START_ZOOM_LEVEL
        }

    val zoomLevelMax: Byte
        get() {
            return ZOOM_MAX
        }

    internal fun renderBitmap(renderContext: RenderContext): Boolean {
        return !renderContext.renderTheme.hasMapBackgroundOutside()
            || mapDataStore!!.supportsTile(renderContext.rendererJob.tile)
    }

    protected open fun renderPointOfInterest(
        renderContext: RenderContext,
        pointOfInterest: PointOfInterest,
    ) {
        renderContext.setDrawingLayer(pointOfInterest.layer)
        renderContext.renderTheme.matchNode(getRenderCallback(), renderContext, pointOfInterest)
    }

    protected open fun renderWaterBackground(renderContext: RenderContext) {
        renderContext.setDrawingLayer(0)
        val coordinates = getTilePixelCoordinates(renderContext.rendererJob.tile.tileSize)
        val tileOrigin = renderContext.rendererJob.tile.origin
        for (i in coordinates.indices) {
            coordinates[i] = coordinates[i].offset(tileOrigin.x, tileOrigin.y)
        }
        val way = PolylineContainer(
            coordinates,
            renderContext.rendererJob.tile,
            renderContext.rendererJob.tile,
            Collections.singletonList(TAG_NATURAL_WATER),
        )
        renderContext.renderTheme.matchClosedWay(getRenderCallback(), renderContext, way)
    }

    protected open fun renderWay(renderContext: RenderContext, way: PolylineContainer) {
        renderContext.setDrawingLayer(way.layer)

        if (way.isClosedWay) {
            if (renderContext.rendererJob.tile.zoomLevel >= Parameters.POLYGON_ZOOM_MIN) {
                renderContext.renderTheme.matchClosedWay(getRenderCallback(), renderContext, way)
            } else if (!Parameters.POLYGON_EXCEPTIONS.isEmpty()) {
                for (tag in Parameters.POLYGON_EXCEPTIONS) {
                    if (way.tags.contains(tag)) {
                        renderContext.renderTheme.matchClosedWay(getRenderCallback(), renderContext, way)
                        break
                    }
                }
            }
        } else {
            renderContext.renderTheme.matchLinearWay(getRenderCallback(), renderContext, way)
        }
    }

    /**
     * Matches the POIs, then the ways, then the water background of `mapReadResult`; null does
     * nothing.
     */
    fun processReadMapData(renderContext: RenderContext, mapReadResult: MapReadResult?) {
        if (mapReadResult == null) {
            return
        }

        for (pointOfInterest in mapReadResult.pois) {
            renderPointOfInterest(renderContext, pointOfInterest)
        }

        for (way in mapReadResult.ways) {
            renderWay(
                renderContext,
                PolylineContainer(way, renderContext.rendererJob.tile, renderContext.rendererJob.tile),
            )
        }

        if (mapReadResult.isWater) {
            renderWaterBackground(renderContext)
        }
    }

    fun interruptAndDestroy() {
        // nothing to stop without hillshading
    }

    open fun getRenderCallback(): MyRenderCallback {
        return renderCallback
    }

    /**
     * Adds the shapes and labels the theme matches to the render context.
     */
    open inner class MyRenderCallback : RenderCallback {

        override fun renderArea(
            renderContext: RenderContext,
            fill: Paint,
            stroke: Paint,
            level: Int,
            way: PolylineContainer,
        ) {
            renderContext.addToCurrentDrawingLayer(level, ShapePaintContainer(way, stroke))
            renderContext.addToCurrentDrawingLayer(level, ShapePaintContainer(way, fill))
        }

        override fun renderAreaCaption(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            caption: String,
            horizontalOffset: Float,
            verticalOffset: Float,
            fill: Paint,
            stroke: Paint,
            position: Position,
            maxTextWidth: Int,
            way: PolylineContainer,
        ) {
            if (renderLabels) {
                val centerPoint = way.centerAbsolute.offset(
                    horizontalOffset.toDouble(),
                    verticalOffset.toDouble(),
                )
                renderContext.addLabel(
                    this@StandardRenderer.graphicFactory.createPointTextContainer(
                        centerPoint,
                        horizontalOffset.toDouble(),
                        verticalOffset.toDouble(),
                        display,
                        priority,
                        caption,
                        fill,
                        stroke,
                        null,
                        position,
                        maxTextWidth,
                    ),
                )
            }
        }

        override fun renderAreaSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            symbol: Bitmap,
            way: PolylineContainer,
        ) {
            if (renderLabels) {
                val centerPosition = way.centerAbsolute
                renderContext.addLabel(
                    SymbolContainer(centerPosition, display, priority, null, symbol, true),
                )
            }
        }

        override fun renderPointOfInterestCaption(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            caption: String,
            horizontalOffset: Float,
            verticalOffset: Float,
            fill: Paint,
            stroke: Paint,
            position: Position,
            maxTextWidth: Int,
            poi: PointOfInterest,
        ) {
            if (renderLabels) {
                val poiPosition = MercatorProjection.getPixelAbsolute(
                    poi.position,
                    renderContext.rendererJob.tile.mapSize,
                )
                renderContext.addLabel(
                    this@StandardRenderer.graphicFactory.createPointTextContainer(
                        poiPosition,
                        horizontalOffset.toDouble(),
                        verticalOffset.toDouble(),
                        display,
                        priority,
                        caption,
                        fill,
                        stroke,
                        null,
                        position,
                        maxTextWidth,
                    ),
                )
            }
        }

        override fun renderPointOfInterestCircle(
            renderContext: RenderContext,
            radius: Float,
            fill: Paint,
            stroke: Paint,
            level: Int,
            poi: PointOfInterest,
        ) {
            val poiPosition = MercatorProjection.getPixelRelativeToTile(
                poi.position,
                renderContext.rendererJob.tile,
            )
            renderContext.addToCurrentDrawingLayer(
                level,
                ShapePaintContainer(CircleContainer(poiPosition, radius), stroke),
            )
            renderContext.addToCurrentDrawingLayer(
                level,
                ShapePaintContainer(CircleContainer(poiPosition, radius), fill),
            )
        }

        override fun renderPointOfInterestSymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            boundary: Rectangle?,
            symbol: Bitmap,
            poi: PointOfInterest,
        ) {
            if (renderLabels) {
                val poiPosition = MercatorProjection.getPixelAbsolute(
                    poi.position,
                    renderContext.rendererJob.tile.mapSize,
                )
                renderContext.addLabel(
                    SymbolContainer(poiPosition, display, priority, boundary, symbol, true),
                )
            }
        }

        override fun renderWay(
            renderContext: RenderContext,
            stroke: Paint,
            dy: Float,
            curveStyle: Curve,
            level: Int,
            way: PolylineContainer,
        ) {
            renderContext.addToCurrentDrawingLayer(level, ShapePaintContainer(way, stroke, dy, curveStyle))
        }

        override fun renderWaySymbol(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            symbol: Bitmap,
            dy: Float,
            boundary: Rectangle?,
            repeat: Boolean,
            repeatGap: Float,
            repeatStart: Float,
            symbolOrientation: SymbolOrientation,
            way: PolylineContainer,
        ) {
            if (renderLabels) {
                WayDecorator.renderSymbol(
                    symbol,
                    display,
                    priority,
                    dy,
                    boundary,
                    repeat,
                    repeatGap,
                    repeatStart,
                    symbolOrientation,
                    way.coordinatesAbsolute,
                    renderContext,
                )
            }
        }

        override fun renderWayText(
            renderContext: RenderContext,
            display: Display,
            priority: Int,
            text: String,
            dy: Float,
            fill: Paint,
            stroke: Paint,
            repeat: Boolean,
            repeatGap: Float,
            repeatStart: Float,
            textOrientation: TextOrientation,
            way: PolylineContainer,
        ) {
            if (renderLabels) {
                WayDecorator.renderText(
                    graphicFactory,
                    way.upperLeft,
                    way.lowerRight,
                    text,
                    display,
                    priority,
                    dy,
                    fill,
                    stroke,
                    repeat,
                    repeatGap,
                    repeatStart,
                    textOrientation,
                    way.coordinatesAbsolute,
                    renderContext,
                )
            }
        }
    }

    companion object {

        protected const val DEFAULT_START_ZOOM_LEVEL: Byte = 12

        @JvmField
        protected val TAG_NATURAL_WATER: Tag = Tag("natural", "water")

        protected const val ZOOM_MAX: Byte = 22

        // the tile's corners, closed: the last point is the first
        private fun getTilePixelCoordinates(tileSize: Int): Array<Point> {
            val first = Point(0.0, 0.0)
            return arrayOf(
                first,
                Point(tileSize.toDouble(), 0.0),
                Point(tileSize.toDouble(), tileSize.toDouble()),
                Point(0.0, tileSize.toDouble()),
                first,
            )
        }
    }
}
