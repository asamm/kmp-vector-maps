/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.MapFormatDriver
import com.asamm.vectormaps.core.MapInfo
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.parser.TreHeader
import com.asamm.vectormaps.garminimg.parser.TreSubFile
import com.asamm.vectormaps.garminimg.parser.TypBodyParser
import com.asamm.vectormaps.garminimg.parser.TypSubFile
import com.asamm.vectormaps.garminimg.utils.coord.CoordUtils
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader
import java.io.File

/**
 * Garmin `.img` maps, styled by their embedded TYP, an external `.typ` file or the built-in
 * styles, drawn by the Garmin engine.
 *
 * @param canvasFactory the host's graphics backend, such as `AwtRenderTarget`; its renderers fill
 * [GarminTileTarget]s with this backend's canvases
 */
class GarminDriver(
    val canvasFactory: GarminCanvasFactory,
) : MapFormatDriver {

    override val id: String = "garmin-img"

    override val extensions: Set<String> = setOf("img")

    override val styleExtensions: Set<String> = setOf("typ")

    /**
     * Reads the IMG directory (a map with TRE, RGN and LBL required) and every map's TRE header.
     */
    override fun probe(file: File): MapInfo {
        return MapInfo(bounds = readSummary(file = file, full = false).bounds, languages = emptyList())
    }

    override fun open(files: List<File>): GarminDataset {
        return GarminDataset(driver = this, files = files)
    }

    /**
     * Reads a standalone `.typ` file; throws for a file without the TYP signature or whose
     * sections cannot be read, while single bad records are dropped.
     */
    override fun loadStyle(file: File): GarminStyle {
        val lastModified = file.lastModified()
        val table = FileImgRandomAccessReader(file).use { reader ->
            val header = TypSubFile.parseHeader(reader)
            require(header.signature.startsWith("GARMIN")) {
                "loadStyle(), not a TYP file: $file"
            }
            TypBodyParser.parse(reader, header)
        }
        return GarminStyle.External(file = file, lastModified = lastModified, table = table)
    }

    /**
     * Bounds of all the file's maps; with `full`, also their TRE levels and whether its TYP decodes,
     * which probes skip.
     */
    internal fun readSummary(file: File, full: Boolean): ImgSummary {
        return FileImgRandomAccessReader(file).use { reader ->
            val archive = ImgArchive.open(reader)
            // a map without RGN or LBL would only fail at its first renderer
            val names = archive.mapNames()
            require(names.isNotEmpty()) {
                "readSummary(), no map with TRE, RGN and LBL in $file"
            }
            val tres = names.map { archive.openSubFile(checkNotNull(archive.find(it, "TRE")), reader) }
            val typ = archive.findFirst("TYP")
            val mapBounds = tres.map { toBounds(TreSubFile.parseHeader(it)) }
            ImgSummary(
                bounds = mapBounds.reduce(GeoBounds::union),
                mapBounds = mapBounds,
                levels = if (full) tres.flatMap(::readLevels).distinctBy { it.level } else emptyList(),
                // as `ImgFile`, which drops a TYP whose header or sections do not parse
                hasTyp = full
                    && typ != null
                    && runCatching { readTyp(archive.openSubFile(typ, reader)) }.isSuccess,
            )
        }
    }

    private fun readLevels(tre: ImgRandomAccessReader): List<GarminLevel> {
        return TreSubFile.parseLevels(tre).levels
            .filterNot { it.inherited }
            .map { GarminLevel(level = it.level, bitsPerCoord = it.bitsPerCoord) }
    }

    private fun readTyp(reader: ImgRandomAccessReader) {
        TypBodyParser.parse(reader, TypSubFile.parseHeader(reader))
    }

    private fun toBounds(header: TreHeader): GeoBounds {
        return GeoBounds(
            minLat = CoordUtils.toWGS84(header.southBoundary),
            minLon = CoordUtils.toWGS84(header.westBoundary),
            maxLat = CoordUtils.toWGS84(header.northBoundary),
            maxLon = eastOf(header.eastBoundary),
        )
    }
}

/**
 * The east boundary in degrees; 180° is stored as the 24-bit value 0x800000, which reads as -180°.
 */
private fun eastOf(eastBoundary: Int): Double {
    if (eastBoundary == -0x800000) {
        return 180.0
    }
    return CoordUtils.toWGS84(eastBoundary)
}

internal class ImgSummary(
    val bounds: GeoBounds,
    // per map, in archive order
    val mapBounds: List<GeoBounds>,
    val levels: List<GarminLevel>,
    val hasTyp: Boolean,
)
