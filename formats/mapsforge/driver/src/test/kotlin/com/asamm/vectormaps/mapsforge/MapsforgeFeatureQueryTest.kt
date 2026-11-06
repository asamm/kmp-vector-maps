/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.FeatureStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.map.awt.graphics.AwtGraphicFactory
import com.asamm.vectormaps.mapsforge.map.layer.renderer.RendererJob
import com.asamm.vectormaps.mapsforge.map.reader.MapFile
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapsforgeFeatureQueryTest {

    private val fixtures = File(System.getProperty("fixturesDir"))
    private val monaco = File(fixtures, "maps/monaco.osm.map")
    private val theme = File(fixtures, "themes/lomaps/theme.xml")
    private val driver = MapsforgeDriver(AwtGraphicFactory.INSTANCE)

    @Test
    fun mapPoisResolveAsTheRenderMatchesThem() {
        var withSymbol = 0
        var withCaption = 0
        val reader = MapFile(monaco)
        try {
            driver.open(listOf(monaco)).use { dataset ->
                val config = RenderConfig(tileSizePx = 256)
                dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                    val query = renderer.featureQuery
                    val renderTheme = renderer.themeFuture.get()
                    // every tile of the map file at two zooms
                    val box = reader.boundingBox()
                    for (zoom in listOf(14, 16)) {
                        val z = zoom.toByte()
                        val xs = MercatorProjection.longitudeToTileX(box.minLongitude, z)..
                            MercatorProjection.longitudeToTileX(box.maxLongitude, z)
                        val ys = MercatorProjection.latitudeToTileY(box.maxLatitude, z)..
                            MercatorProjection.latitudeToTileY(box.minLatitude, z)
                        for (y in ys) {
                            for (x in xs) {
                                val tile = Tile(x, y, zoom.toByte(), 256)
                                val pois = reader.readPoiData(tile)?.pois ?: continue
                                // the context a render of the tile matches its POIs in
                                val job = RendererJob(
                                    tile,
                                    reader,
                                    renderer.themeFuture,
                                    renderer.displayModel,
                                    1f,
                                    false,
                                    false,
                                )
                                renderTheme.acquireZoom(tile.zoomLevel)
                                val context = RenderContext(job, AwtGraphicFactory.INSTANCE)
                                try {
                                    for (poi in pois) {
                                        val rendered = query.match(context = context, poi = poi)
                                        val queried = query.point(
                                            tags = poi.tags.associate { it.key!! to it.value!! },
                                            zoom = zoom,
                                        )
                                        assertEquals(rendered, queried, "${poi.tags} at z$zoom")
                                        assertEquals(
                                            rendered.symbols.map { it.png.size },
                                            queried.symbols.map { it.png.size },
                                        )
                                        if (rendered.symbols.isNotEmpty()) {
                                            withSymbol++
                                        }
                                        if (rendered.captions.isNotEmpty()) {
                                            withCaption++
                                        }
                                    }
                                } finally {
                                    context.destroy()
                                    renderTheme.releaseZoom(tile.zoomLevel)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        assertTrue(withSymbol > 50, "$withSymbol POIs with a symbol")
        assertTrue(withCaption > 50, "$withCaption POIs with a caption")
    }

    @Test
    fun blankNameDrawsNoCaption() {
        val reader = MapFile(monaco)
        try {
            driver.open(listOf(monaco)).use { dataset ->
                val config = RenderConfig(tileSizePx = 256)
                dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                    val query = renderer.featureQuery
                    // a POI of the map the theme labels with its name at z17
                    val box = reader.boundingBox()
                    val pois = reader.readPoiData(
                        Tile(
                            MercatorProjection.longitudeToTileX(box.minLongitude, 17),
                            MercatorProjection.latitudeToTileY(box.maxLatitude, 17),
                            17,
                            256,
                        ),
                        Tile(
                            MercatorProjection.longitudeToTileX(box.maxLongitude, 17),
                            MercatorProjection.latitudeToTileY(box.minLatitude, 17),
                            17,
                            256,
                        ),
                    )!!.pois
                    val tags = pois
                        .map { poi -> poi.tags.associate { it.key!! to it.value!! } }
                        .first { tags ->
                            val name = tags["name"]
                            name != null
                                && query.point(tags = tags, zoom = 17).captions.any { it.text.value == name }
                        }
                    val named = query.point(tags = tags, zoom = 17)
                    val blank = query.point(tags = tags + ("name" to " "), zoom = 17)

                    assertEquals(named.symbols, blank.symbols)
                    assertTrue(blank.captions.none { it.text.value.isBlank() }, "$blank")
                }
            }
        } finally {
            reader.close()
        }
    }

    @Test
    fun queryFailsOnceTheRendererCloses() {
        driver.open(listOf(monaco)).use { dataset ->
            val config = RenderConfig(tileSizePx = 256)
            val renderer = dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config)
            val query = renderer.featureQuery
            renderer.close()

            assertFailsWith<IllegalStateException> {
                query.point(mapOf("amenity" to "cafe"), zoom = 18)
            }
        }
    }

    @Test
    fun unknownTagsResolveToNothing() {
        driver.open(listOf(monaco)).use { dataset ->
            val config = RenderConfig(tileSizePx = 256)
            dataset.createRenderer(driver.loadStyle(theme), StyleSelection.DEFAULT, config).use { renderer ->
                assertSame(FeatureStyle.EMPTY, renderer.featureQuery.point(mapOf("no" to "such"), zoom = 16))
            }
        }
    }
}
