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

import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.labels.LabelStore
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.util.LayerUtil
import java.util.logging.Logger

/**
 * Renders map tiles by reading from a [MapDataStore]. Three configurations:
 * - render labels directly onto tiles: `renderLabels == true && tileCache != null`
 * - do not render labels but cache them: `renderLabels == false && labelStore != null`
 * - do not render or cache labels: `renderLabels == false && labelStore == null`
 *
 * @param tileCache where tiles are cached, needed if labels are drawn directly onto tiles
 * @param hillsRenderConfig ignored, hillshading is not supported; kept for the upstream arity,
 * callers pass null
 */
open class DatabaseRenderer(
    mapDataStore: MapDataStore?,
    graphicFactory: GraphicFactory,
    tileCache: TileCache?,
    labelStore: LabelStore?,
    renderLabels: Boolean,
    cacheLabels: Boolean,
    hillsRenderConfig: Any?,
) : StandardRenderer(mapDataStore, graphicFactory, renderLabels || cacheLabels, hillsRenderConfig) {

    @JvmField
    protected val tileCache: TileCache? = tileCache

    @JvmField
    protected val labelStore: LabelStore? = labelStore

    /**
     * True to draw the labels onto the tiles; the inherited `renderLabels` also covers caching them.
     */
    @JvmField
    protected val drawLabels: Boolean = renderLabels

    @JvmField
    protected val tileDependencies: TileDependencies = TileDependencies()

    val mapDatabase: MapDataStore?
        get() {
            return mapDataStore
        }

    /**
     * Renders `rendererJob`; null for a labels-only job or if rendering failed (the cause is logged).
     */
    fun executeJob(rendererJob: RendererJob?): TileBitmap? {
        var output: TileBitmap? = null
        var renderContext: RenderContext? = null
        try {
            renderContext = RenderContext(rendererJob, graphicFactory)
            // the context dereferences the job, so it is not null from here
            val job = rendererJob!!

            if (renderBitmap(renderContext)) {
                var bitmap: TileBitmap? = null

                if (mapDataStore != null) {
                    val mapReadResult = mapDataStore.readMapData(job.tile)
                    processReadMapData(renderContext, mapReadResult)
                }

                if (!job.labelsOnly) {
                    bitmap = graphicFactory.createTileBitmap(job.tile.tileSize, job.hasAlpha)
                    bitmap.timestamp = job.mapDataStore.getDataTimestamp(job.tile)
                    renderContext.canvasRasterer.setCanvasBitmap(bitmap)
                    if (!job.hasAlpha
                        && job.displayModel.backgroundColor != renderContext.renderTheme.mapBackground
                    ) {
                        renderContext.canvasRasterer.fill(renderContext.renderTheme.mapBackground)
                    }

                    renderContext.drawWays()
                }

                if (drawLabels) {
                    processLabels(renderContext)
                }
                if (labelStore is TileBasedLabelStore) {
                    // store elements for this tile in the label cache
                    labelStore.storeMapItems(job.tile, renderContext.getLabels())
                }

                if (!job.labelsOnly
                    && renderContext.renderTheme.hasMapBackgroundOutside()
                    && mapDataStore != null
                ) {
                    // blank out all areas outside of map
                    val insideArea = mapDataStore.boundingBox()!!.getPositionRelativeToTile(job.tile)
                    if (!job.hasAlpha) {
                        renderContext.canvasRasterer.fillOutsideAreas(
                            renderContext.renderTheme.mapBackgroundOutside,
                            insideArea,
                        )
                    } else {
                        renderContext.canvasRasterer.fillOutsideAreas(Color.TRANSPARENT, insideArea)
                    }
                }

                output = bitmap
            } else {
                // outside of map area with background defined:
                output = createBackgroundBitmap(renderContext)
            }
        } catch (e: Exception) {
            LOGGER.warning(e.toString())
        } finally {
            if (renderContext != null) {
                renderContext.destroy()
            }
            if (!rendererJob!!.labelsOnly
                && output != null
            ) {
                if (tileCache != null) {
                    tileCache.put(rendererJob, output)
                }
                removeTileInProgress(rendererJob.tile)
            }
        }

        return output
    }

    protected open fun removeTileInProgress(tile: Tile) {
        tileDependencies.removeTileInProgress(tile)
    }

    /**
     * A tile of the outside color only, for tiles outside the map area.
     */
    protected open fun createBackgroundBitmap(renderContext: RenderContext): TileBitmap {
        val bitmap = graphicFactory.createTileBitmap(
            renderContext.rendererJob.tile.tileSize,
            renderContext.rendererJob.hasAlpha,
        )
        renderContext.canvasRasterer.setCanvasBitmap(bitmap)
        if (!renderContext.rendererJob.hasAlpha) {
            renderContext.canvasRasterer.fill(renderContext.renderTheme.mapBackgroundOutside)
        }
        return bitmap
    }

    protected open fun processLabels(renderContext: RenderContext) {
        // if we are drawing the labels per tile, we need to establish which tile-overlapping
        // elements need to be drawn.

        val labelsToDraw: List<MapElementContainer>
        val activeLabels: MutableSet<MapElementContainer> = HashSet()

        if (labelStore is MapDataStoreLabelStore) {
            // Surrounding tiles are required to prevent non-deterministic labels and label tearing
            // when drawing labels on tiles.
            activeLabels.addAll(
                labelStore.getVisibleItems(
                    renderContext.rendererJob.tile.aboveLeft,
                    renderContext.rendererJob.tile.belowRight,
                ),
            )

            LayerUtil.removeInvisibleItems(activeLabels)

            labelsToDraw = LayerUtil.collisionAndContestingFreeOrdered(
                ArrayList(activeLabels),
                renderContext.rendererJob.tile,
                Rotation.NULL_ROTATION,
            )
        } else {
            synchronized(tileDependencies) {
                // first we need to get the labels from the adjacent tiles if they have already been
                // drawn as those overlapping items must also be drawn on the current tile. They must
                // be drawn regardless of priority clashes as a part of them has alread been drawn.
                // Tile.neighbours builds a new HashSet, so its iterator can remove
                val neighbours = renderContext.rendererJob.tile.neighbours as MutableSet<Tile>
                val tileIterator = neighbours.iterator()
                val undrawableElements: MutableSet<MapElementContainer> = HashSet()

                tileDependencies.addTileInProgress(renderContext.rendererJob.tile)
                while (tileIterator.hasNext()) {
                    val neighbour = tileIterator.next()

                    if (tileDependencies.isTileInProgress(neighbour)
                        || tileCache!!.containsKey(renderContext.rendererJob.otherTile(neighbour))
                    ) {
                        // if a neighbour tile has already been drawn, the elements drawn that overlap
                        // onto the current tile should be in the tile dependencies, we add them to the
                        // labels that need to be drawn onto this tile. For the multi-threaded renderer
                        // we also need to take those tiles into account that are not yet in the
                        // TileCache: this is taken care of by the set of tilesInProgress inside the
                        // TileDependencies.
                        activeLabels.addAll(
                            tileDependencies.getOverlappingElements(
                                neighbour,
                                renderContext.rendererJob.tile,
                            ),
                        )

                        // but we need to remove the labels for this tile that overlap onto a tile that
                        // has been drawn
                        for (current in renderContext.getLabels()) {
                            if (current.intersects(neighbour.boundaryAbsolute, Rotation.NULL_ROTATION)) {
                                undrawableElements.add(current)
                            }
                        }
                        // since we already have the data from that tile, we do not need to get the
                        // data for it, so remove it from the neighbours list.
                        tileIterator.remove()
                    } else {
                        tileDependencies.removeTileData(neighbour)
                    }
                }

                // now we remove the elements that overlap onto a drawn tile from the list of labels
                // for this tile
                renderContext.clearLabels(undrawableElements)

                // at this point we have two lists: one is the list of labels that must be drawn
                // because they already overlap from other tiles. The second one is currentLabels that
                // contains the elements on this tile that do not overlap onto a drawn tile. Now we
                // sort this list and remove those elements that clash in this list already.
                val currentElementsOrdered = LayerUtil.collisionFreeOrdered(
                    renderContext.getLabels(),
                    Rotation.NULL_ROTATION,
                    false,
                )

                // now we go through this list, ordered by priority, to see which can be drawn
                // without clashing.
                val currentMapElementsIterator = currentElementsOrdered.iterator()
                while (currentMapElementsIterator.hasNext()) {
                    val current = currentMapElementsIterator.next()
                    for (label in activeLabels) {
                        if (label.clashesWith(current, Rotation.NULL_ROTATION)) {
                            currentMapElementsIterator.remove()
                            break
                        }
                    }
                }

                activeLabels.addAll(currentElementsOrdered)

                // update dependencies, add to the dependencies list all the elements that overlap to
                // the neighbouring tiles, first clearing out the cache for this relation.
                for (tile in neighbours) {
                    tileDependencies.removeTileData(renderContext.rendererJob.tile, tile)
                    for (element in activeLabels) {
                        if (element.intersects(tile.boundaryAbsolute, Rotation.NULL_ROTATION)) {
                            tileDependencies.addOverlappingElement(
                                renderContext.rendererJob.tile,
                                tile,
                                element,
                            )
                        }
                    }
                }
            }

            LayerUtil.removeInvisibleItems(activeLabels)

            labelsToDraw = LayerUtil.collisionFreeOrdered(
                ArrayList(activeLabels),
                Rotation.NULL_ROTATION,
            )
        }

        // We need to draw elements in order of ascending priority: lower priority first, so more
        // important elements will be drawn on top (in case of display=true) items.
        renderContext.canvasRasterer.drawMapElements(labelsToDraw, renderContext.rendererJob.tile)
    }

    companion object {

        @JvmField
        protected val LOGGER: Logger = Logger.getLogger(DatabaseRenderer::class.java.name)
    }
}
