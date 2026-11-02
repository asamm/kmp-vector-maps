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
import com.asamm.vectormaps.garminimg.parser.TreLevel
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
     * Reads the IMG directory (TRE, RGN and LBL required) and the first TRE header.
     */
    override fun probe(file: File): MapInfo {
        return MapInfo(bounds = readSummary(file = file, readTyp = false).bounds, languages = emptyList())
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
     * With `readTyp`, also decodes the TYP to learn whether it is usable; probes skip that cost.
     */
    internal fun readSummary(file: File, readTyp: Boolean): ImgSummary {
        return FileImgRandomAccessReader(file).use { reader ->
            val archive = ImgArchive.open(reader)
            // a map without RGN or LBL would only fail at its first renderer
            for (type in listOf("TRE", "RGN", "LBL")) {
                requireNotNull(archive.findFirst(type)) {
                    "readSummary(), no $type in $file"
                }
            }
            val tre = archive.openSubFile(archive.findFirst("TRE")!!, reader)
            val typ = archive.findFirst("TYP")
            ImgSummary(
                bounds = toBounds(TreSubFile.parseHeader(tre)),
                levels = TreSubFile.parseLevels(tre).levels.filterNot { it.inherited },
                // as `ImgFile`, which drops a TYP whose header or sections do not parse
                hasTyp = readTyp
                    && typ != null
                    && runCatching { readTyp(archive.openSubFile(typ, reader)) }.isSuccess,
            )
        }
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
    val levels: List<TreLevel>,
    val hasTyp: Boolean,
)
