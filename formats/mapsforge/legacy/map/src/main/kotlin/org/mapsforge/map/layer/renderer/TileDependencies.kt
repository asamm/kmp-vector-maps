/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Tile

/**
 * Tracks which labels overlap from a drawn tile onto its neighbours. A label drawn per tile can
 * reach into the neighbouring tiles (labels reaching further are ignored); a neighbour drawn later
 * must draw those labels regardless of priority, as part of them is already drawn.
 */
open class TileDependencies internal constructor() {

    internal val overlapData: MutableMap<Tile, MutableMap<Tile, MutableSet<MapElementContainer>>> =
        HashMap()

    // for the multithreaded renderer we also need to keep track of tiles that are in progress
    // and not yet in the TileCache to avoid truncated labels.
    internal val tilesInProgress: MutableSet<Tile> = HashSet()

    /**
     * Stores `element` drawn on `from` as overlapping onto `to`, which must not be drawn yet.
     */
    internal fun addOverlappingElement(from: Tile, to: Tile, element: MapElementContainer) {
        if (!overlapData.containsKey(from)) {
            overlapData[from] = HashMap()
        }
        if (!overlapData[from]!!.containsKey(to)) {
            overlapData[from]!![to] = HashSet()
        }
        overlapData[from]!![to]!!.add(element)
    }

    /**
     * The elements of `from` that overlap onto `to`.
     */
    internal fun getOverlappingElements(from: Tile, to: Tile): Set<MapElementContainer> {
        if (overlapData.containsKey(from)
            && overlapData[from]!!.containsKey(to)
        ) {
            return overlapData[from]!![to]!!
        }
        return HashSet(0)
    }

    /**
     * Drops the data of `from`; call it when the tile leaves the TileCache to be drawn again.
     */
    internal fun removeTileData(from: Tile) {
        overlapData.remove(from)
    }

    /**
     * Drops what `from` overlaps onto `to`; call it when the tile leaves the TileCache to be drawn
     * again.
     */
    internal fun removeTileData(from: Tile, to: Tile) {
        if (overlapData.containsKey(from)) {
            overlapData[from]!!.remove(to)
        }
    }

    @Synchronized
    internal fun isTileInProgress(tile: Tile): Boolean {
        return tilesInProgress.contains(tile)
    }

    @Synchronized
    internal fun addTileInProgress(tileInProgress: Tile) {
        tilesInProgress.add(tileInProgress)
    }

    @Synchronized
    internal fun removeTileInProgress(tileFinished: Tile) {
        tilesInProgress.remove(tileFinished)
    }
}
