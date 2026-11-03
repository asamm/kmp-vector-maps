/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.StyleMenu
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.parser.TypStyleTable
import com.asamm.vectormaps.garminimg.render.StyleResolver
import java.io.File

/**
 * A Garmin style: the built-in styles, the TYP inside the map, or a `.typ` file, by day or with
 * the TYP's night colours. None has a menu; types a TYP does not define fall back to the built-in
 * styles.
 */
sealed class GarminStyle : MapStyle {

    override val menu: StyleMenu? = null

    internal abstract fun resolver(img: ImgFile): StyleResolver

    /**
     * The engine's built-in styles.
     */
    object BuiltIn : GarminStyle() {

        override val id: String = "garmin-img:built-in"

        override fun resolver(img: ImgFile): StyleResolver {
            return StyleResolver.internalDefaults()
        }
    }

    /**
     * Each file's first TYP for all its maps, the built-in styles for a file without one; a dataset
     * lists it when one of its files has a TYP, with that first file's id, and the night one too
     * when that TYP has night colours.
     *
     * @param isNight the TYP's night colours, its day ones where a record has none
     */
    class Embedded internal constructor(
        val file: File,
        lastModified: Long,
        val isNight: Boolean = false,
    ) : GarminStyle() {

        override val id: String = "${file.absolutePath}@$lastModified#typ${if (isNight) "-night" else ""}"

        override fun resolver(img: ImgFile): StyleResolver {
            val table = img.typStyles ?: return StyleResolver.internalDefaults()
            return StyleResolver.typAware(if (isNight) table.night else table)
        }
    }

    /**
     * A standalone `.typ` file, read by [GarminDriver.loadStyle].
     *
     * @param lastModified file time read with the table; the id holds for it only
     * @param isNight whether this is the file's [night] style
     */
    class External internal constructor(
        val file: File,
        lastModified: Long,
        private val table: TypStyleTable,
        val isNight: Boolean = false,
    ) : GarminStyle() {

        override val id: String = "${file.absolutePath}@$lastModified${if (isNight) "#night" else ""}"

        /**
         * The same file with its night colours; null when the TYP has none.
         */
        val night: External? = if (!isNight && table.hasNight) {
            External(file = file, lastModified = lastModified, table = table.night, isNight = true)
        } else {
            null
        }

        override fun resolver(img: ImgFile): StyleResolver {
            return StyleResolver.typAware(table)
        }
    }
}
