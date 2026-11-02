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
 * One Garmin `.img` file; rendering several together comes with M4.3, so [addMap] adds nothing.
 * Each renderer opens its own readers.
 */
class GarminDataset internal constructor(
    override val driver: GarminDriver,
    files: List<File>,
) : MapDataset {

    val file: File

    override val bounds: GeoBounds

    /**
     * The embedded TYP when the map has one, then the built-in styles.
     */
    override val styles: List<GarminStyle>

    init {
        require(files.size == 1) {
            "GarminDataset(), one file expected, got ${files.size}"
        }
        file = files[0]
        val summary = driver.readSummary(file)
        bounds = summary.bounds
        styles = if (summary.hasTyp) {
            listOf(GarminStyle.Embedded(file = file, lastModified = file.lastModified()), GarminStyle.BuiltIn)
        } else {
            listOf(GarminStyle.BuiltIn)
        }
    }

    override fun addMap(file: File): Boolean {
        return false
    }

    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
    ): GarminRenderer {
        return createRenderer(style = style, selection = selection, config = config, options = GarminOptions())
    }

    /**
     * Like [createRenderer] with the Garmin [options], which [RenderKey] does not cover: a host
     * that varies them keys its tile cache itself. Labels outside the tiles come with M6.
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
        val img = ImgFile.open { FileImgRandomAccessReader(file) }
        try {
            return GarminRenderer(
                key = RenderKey(styleId = style.id, selection = selection, config = config),
                img = img,
                styleResolver = style.resolver(img),
                canvasFactory = driver.canvasFactory,
                options = options,
                bounds = bounds,
            )
        } catch (e: Exception) {
            img.close()
            throw e
        }
    }

    override fun close() {
        // nothing open: renderers own their readers
    }
}

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
