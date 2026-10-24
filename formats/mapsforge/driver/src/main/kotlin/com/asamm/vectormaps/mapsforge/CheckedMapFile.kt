/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapCallback
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.datastore.MapReadResult
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.IOException

/**
 * A `MapFile` whose reads throw instead of returning null: Mapsforge logs a read error and returns
 * null, which the renderer would draw as a blank tile.
 */
internal class CheckedMapFile(
    private val file: File,
    language: String?,
) : MapDataStore(language) {

    private val inner = MapFile(file, language)

    override fun boundingBox(): BoundingBox {
        return inner.boundingBox()
    }

    override fun close() {
        inner.close()
    }

    override fun getDataTimestamp(tile: Tile): Long {
        return inner.getDataTimestamp(tile)
    }

    override fun getMapCallback(): MapCallback? {
        return inner.mapCallback
    }

    override fun setMapCallback(mapCallback: MapCallback?) {
        inner.mapCallback = mapCallback
    }

    override fun readMapData(tile: Tile): MapReadResult {
        return checked(inner.readMapData(tile), tile)
    }

    override fun readMapData(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return checked(inner.readMapData(upperLeft, lowerRight), upperLeft)
    }

    override fun readNamedItems(tile: Tile): MapReadResult {
        return checked(inner.readNamedItems(tile), tile)
    }

    override fun readNamedItems(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return checked(inner.readNamedItems(upperLeft, lowerRight), upperLeft)
    }

    override fun readPoiData(tile: Tile): MapReadResult {
        return checked(inner.readPoiData(tile), tile)
    }

    override fun readPoiData(upperLeft: Tile, lowerRight: Tile): MapReadResult {
        return checked(inner.readPoiData(upperLeft, lowerRight), upperLeft)
    }

    override fun startPosition(): LatLong? {
        return inner.startPosition()
    }

    override fun startZoomLevel(): Byte? {
        return inner.startZoomLevel()
    }

    override fun supportsTile(tile: Tile): Boolean {
        return inner.supportsTile(tile)
    }

    override fun supportsFullTile(tile: Tile): Boolean {
        return inner.supportsFullTile(tile)
    }

    override fun supportsArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        return inner.supportsArea(boundingBox, zoomLevel)
    }

    override fun supportsFullArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        return inner.supportsFullArea(boundingBox, zoomLevel)
    }

    private fun checked(result: MapReadResult?, tile: Tile): MapReadResult {
        // MapFile returns null only after a read error, which it logs
        return result ?: throw IOException("checked($tile), cannot read $file, see the Mapsforge log")
    }
}
