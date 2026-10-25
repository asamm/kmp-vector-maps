/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewportTest {

    private val monaco = GeoBounds(minLat = 43.72, minLon = 7.40, maxLat = 43.76, maxLon = 7.44)

    @Test
    fun latLonRoundTrips() {
        val viewport = Viewport.at(lat = 43.735, lon = 7.423, zoom = 14, tileSize = 512)

        assertEquals(43.735, viewport.lat, 1e-9)
        assertEquals(7.423, viewport.lon, 1e-9)
    }

    @Test
    fun zoomKeepsTheFocusUnderTheSamePixel() {
        val viewport = Viewport.at(lat = 43.735, lon = 7.423, zoom = 14, tileSize = 256)
        val tile = viewport.tiles(width = 800, height = 600).first()
        // a pixel 100 px right of and 40 px above the centre, inside `tile` at zoom 15
        val before = viewport.screenX(tile.x, 800)

        val zoomed = viewport.zoomed(steps = 1, focusX = 100.0, focusY = -40.0)

        assertEquals(15, zoomed.zoom)
        assertEquals((before - 500.0) * 2.0 + 500.0, zoomed.screenX(tile.x * 2, 800).toDouble(), 1e-3)
        assertEquals(viewport.x, zoomed.zoomed(steps = -1, focusX = 100.0, focusY = -40.0).x, 1e-12)
    }

    @Test
    fun dragMovesTheContent() {
        val viewport = Viewport.at(lat = 43.735, lon = 7.423, zoom = 14, tileSize = 256)

        val dragged = viewport.dragged(dx = 30.0, dy = -12.0)

        assertEquals(viewport.screenX(8529, 800) + 30f, dragged.screenX(8529, 800), 1e-3f)
        assertEquals(viewport.screenY(5974, 600) - 12f, dragged.screenY(5974, 600), 1e-3f)
    }

    @Test
    fun dragAndZoomKeepTheCentreOnTheWorld() {
        val viewport = Viewport.at(lat = 0.0, lon = 0.0, zoom = 2, tileSize = 512)

        val flung = viewport.dragged(dx = 1e6, dy = -1e6)
        val zoomed = flung.zoomed(steps = 1, focusX = -5000.0, focusY = 5000.0)

        assertEquals(0.0, flung.x)
        assertEquals(1.0, flung.y)
        assertTrue(flung.tiles(width = 800, height = 600).isNotEmpty())
        assertTrue(zoomed.x in 0.0..1.0 && zoomed.y in 0.0..1.0)
    }

    @Test
    fun atClampsZoomAndLongitude() {
        val far = Viewport.at(lat = 0.0, lon = 500.0, zoom = 64, tileSize = 512)
        val near = Viewport.at(lat = 0.0, lon = 0.0, zoom = -3, tileSize = 512)

        assertEquals(Viewport.MAX_ZOOM, far.zoom)
        assertEquals(1.0, far.x)
        assertEquals(Viewport.MIN_ZOOM, near.zoom)
        assertTrue(far.tiles(width = 800, height = 600).isNotEmpty())
    }

    @Test
    fun tilesStopAtTheScreenEdge() {
        // the screen spans exactly tiles 3..4 on both axes
        val viewport = Viewport(zoom = 3, x = 0.5, y = 0.5, tileSize = 256)

        val tiles = viewport.tiles(width = 512, height = 512)

        assertEquals(setOf(3, 4), tiles.map { it.x }.toSet())
        assertEquals(setOf(3, 4), tiles.map { it.y }.toSet())
        assertEquals(4, tiles.size)
        assertTrue(viewport.tiles(width = 300, height = 300).first() in tiles)
        assertEquals(TileRequest(x = 0, y = 0, zoom = 3), Viewport(3, 0.0625, 0.0625, 256).tiles(256, 256).single())
    }

    @Test
    fun fittingShowsTheWholeBoundsAtTheHighestZoom() {
        val viewport = Viewport.fitting(bounds = monaco, width = 800, height = 600, tileSize = 256)

        assertEquals(43.74, viewport.lat, 1e-3)
        assertTrue(shows(viewport, monaco, 800, 600))
        assertFalse(shows(viewport.zoomed(steps = 1, focusX = 0.0, focusY = 0.0), monaco, 800, 600))
    }

    private fun shows(viewport: Viewport, bounds: GeoBounds, width: Int, height: Int): Boolean {
        val northWest = Viewport.at(bounds.maxLat, bounds.minLon, viewport.zoom, viewport.tileSize)
        val southEast = Viewport.at(bounds.minLat, bounds.maxLon, viewport.zoom, viewport.tileSize)
        val worldPx = viewport.tileSize.toDouble() * (1L shl viewport.zoom)
        return (southEast.x - northWest.x) * worldPx <= width
            && (southEast.y - northWest.y) * worldPx <= height
    }
}
