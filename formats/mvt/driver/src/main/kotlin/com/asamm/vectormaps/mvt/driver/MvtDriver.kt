/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapInfo
import com.asamm.vectormaps.mvt.render.MvtGraphics
import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.source.PmTilesSource
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The Mapbox Vector Tile driver (M10, docs/mvt.md): offline maps from `.pmtiles` files through
 * [open], online maps from a host's [MvtTileSource] through [openSource], and MapLibre styles from
 * a `style.json` with its sprite beside it. Takes the platform drawing from the host.
 */
class MvtDriver(
    val graphics: MvtGraphics,
) : MapFormatDriver {

    override val id: String = "mvt"

    override val extensions: Set<String> = setOf("pmtiles")

    override val styleExtensions: Set<String> = setOf("json")

    /**
     * Reads the PMTiles header; throws for another file or tile type.
     */
    override fun probe(file: File): MapInfo {
        return PmTilesSource(file).use { MapInfo(bounds = boundsOf(it), languages = emptyList()) }
    }

    /**
     * Opens `files` as one dataset; throws when one is not a PMTiles map, is listed twice or has
     * another max zoom than the first.
     */
    override fun open(files: List<File>): MvtDataset {
        require(files.isNotEmpty()) {
            "open(), no files"
        }
        val dataset = MvtDataset(driver = this, sources = emptyList())
        try {
            for (file in files) {
                require(dataset.add(file)) {
                    "open(), $file listed twice"
                }
            }
        } catch (e: Exception) {
            dataset.close()
            throw e
        }
        return dataset
    }

    /**
     * Opens a dataset over `source`, such as the host's online tiles; the dataset closes it.
     */
    fun openSource(source: MvtTileSource): MvtDataset {
        return MvtDataset(driver = this, sources = listOf(source))
    }

    /**
     * Reads a MapLibre `style.json` and its sprite (`sprite.json` and `sprite.png` beside it, or
     * the files its `sprite` names there); a style without a readable sprite draws no icons or
     * patterns. Its vector sources all read from the dataset it renders. Throws for a file with no
     * style layers, such as a sprite index.
     */
    override fun loadStyle(file: File): MvtStyle {
        val lastModified = file.lastModified()
        val style = Style.parse(file.readText())
        require(style.layers.isNotEmpty()) {
            "loadStyle($file), no style layers"
        }
        // the name only, so the sprite stays beside the style
        val spriteName = style.sprite?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() && it != ".." }
            ?: "sprite"
        val indexFile = File(file.parentFile, "$spriteName.json")
        val pngFile = File(file.parentFile, "$spriteName.png")
        val (index, sprite) = if (indexFile.isFile
            && pngFile.isFile
        ) {
            try {
                val index = SpriteIndex.parse(indexFile.readText())
                index to graphics.loadSprite(index, pngFile.readBytes())
            } catch (e: Exception) {
                LOGGER.log(Level.WARNING, "loadStyle($file), sprite $spriteName", e)
                null to null
            }
        } else {
            null to null
        }
        return MvtStyle(
            id = "${file.absolutePath}@$lastModified",
            style = style,
            spriteIndex = index,
            sprite = sprite,
        )
    }

    internal companion object {

        // the whole Web Mercator world, for a source that names no bounds
        private val WORLD = GeoBounds(minLat = -85.0511, minLon = -180.0, maxLat = 85.0511, maxLon = 180.0)

        private val LOGGER: Logger = Logger.getLogger(MvtDriver::class.java.name)

        /**
         * The source's bounds within the world; an axis that is reversed, such as one crossing the
         * antimeridian, or not a number spans the whole world.
         */
        fun boundsOf(source: MvtTileSource): GeoBounds {
            val b = source.bounds
            if (b == null
                || b.size < 4
            ) {
                return WORLD
            }
            val lon = range(min = b[0], max = b[2], limit = WORLD.maxLon)
            val lat = range(min = b[1], max = b[3], limit = WORLD.maxLat)
            return GeoBounds(
                minLat = lat?.first ?: WORLD.minLat,
                minLon = lon?.first ?: WORLD.minLon,
                maxLat = lat?.second ?: WORLD.maxLat,
                maxLon = lon?.second ?: WORLD.maxLon,
            )
        }

        // min..max within ±limit; null when reversed or not a number
        private fun range(min: Double, max: Double, limit: Double): Pair<Double, Double>? {
            val lo = min.coerceIn(-limit, limit)
            val hi = max.coerceIn(-limit, limit)
            if (!(lo <= hi)) {
                return null
            }
            return lo to hi
        }
    }
}
