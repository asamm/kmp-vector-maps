/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import java.io.File

/**
 * One Garmin `.img` file; rendering several together comes with M4.3b, so [addMap] adds nothing.
 * The map is parsed once and shared by all renderers, whose workers read through their own readers.
 */
class GarminDataset internal constructor(
    override val driver: GarminDriver,
    files: List<File>,
) : MapDataset {

    val file: File

    private val lastModified: Long

    private val lock = Any()

    // headers, sub-division tree and TYP; its own readers are closed at once, as workers reopen
    private var parsed: ImgFile? = null

    override val bounds: GeoBounds

    /**
     * TRE levels with their own data, for [GarminOptions.level].
     */
    val levels: List<GarminLevel>

    /**
     * The embedded TYP when the map has one, then the built-in styles.
     */
    override val styles: List<GarminStyle>

    init {
        require(files.size == 1) {
            "GarminDataset(), one file expected, got ${files.size}"
        }
        file = files[0]
        // read before the content, so a later change always shows
        lastModified = file.lastModified()
        val summary = driver.readSummary(file = file, full = true)
        bounds = summary.bounds
        levels = summary.levels
        styles = if (summary.hasTyp) {
            listOf(GarminStyle.Embedded(file = file, lastModified = lastModified), GarminStyle.BuiltIn)
        } else {
            listOf(GarminStyle.BuiltIn)
        }
    }

    /**
     * Returns false: one file per dataset until M4.3b.
     */
    override fun addMap(file: File): Boolean {
        return false
    }

    /**
     * Renders with the default [GarminOptions]; throws for [LabelMode.SEPARATE] until M6.
     */
    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
    ): GarminRenderer {
        return createRenderer(style = style, selection = selection, config = config, options = GarminOptions())
    }

    /**
     * Like [createRenderer] with the Garmin [options], which [RenderKey] does not cover: a host
     * that varies them keys its tile cache itself. Throws when the map file changed since the
     * dataset was opened, for a level the map lacks, and for [LabelMode.SEPARATE] until M6.
     */
    fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        options: GarminOptions,
    ): GarminRenderer {
        require(style is GarminStyle) {
            "createRenderer(), not a Garmin style: ${style.id}"
        }
        require(style !is GarminStyle.Embedded
            || style.file == file
        ) {
            "createRenderer(), ${style.id} belongs to another map"
        }
        require(config.labelMode == LabelMode.IN_TILE) {
            "createRenderer(), label mode ${config.labelMode} not supported"
        }
        // the dataset's bounds and style ids hold for the file it opened: reopen after a map update
        check(file.lastModified() == lastModified) {
            "createRenderer(), $file changed since the dataset was opened"
        }
        val img = parsed()
        val level = options.level
        require(level == null
            || img.tre.levels.levels.any { it.level == level }
        ) {
            "createRenderer(), $file has no level $level"
        }
        return GarminRenderer(
            key = RenderKey(styleId = style.id, selection = selection, config = config),
            parsed = img,
            // workers open the file when they are first needed; it must still be the parsed one
            openReader = {
                check(file.lastModified() == lastModified) {
                    "render(), $file changed since the dataset was opened"
                }
                FileImgRandomAccessReader(file)
            },
            style = style,
            canvasFactory = driver.canvasFactory,
            options = options,
            bounds = bounds,
        )
    }

    override fun close() {
        // nothing open: the parsed map holds no readers, renderers own theirs
    }

    private fun parsed(): ImgFile {
        synchronized(lock) {
            parsed?.let { return it }
            val img = ImgFile.open { FileImgRandomAccessReader(file) }
            img.close()
            parsed = img
            return img
        }
    }
}

/**
 * A TRE level of a map: lower levels are finer, `bitsPerCoord` is its coordinate precision.
 */
data class GarminLevel(
    val level: Int,
    val bitsPerCoord: Int,
)

/**
 * Garmin-only render settings for development tools; Locus' type toggles come with M8.2.
 *
 * @param objectKinds [ObjectKind] bits to draw
 * @param level TRE level to draw; null picks the level matching the zoom
 */
data class GarminOptions(
    val antiAliasing: Boolean = true,
    val objectKinds: Int = ObjectKind.ALL,
    val level: Int? = null,
)
