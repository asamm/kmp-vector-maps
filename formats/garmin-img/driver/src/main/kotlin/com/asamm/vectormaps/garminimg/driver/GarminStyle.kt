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
 * A Garmin style: the built-in styles, the TYP inside the map, or a `.typ` file. None has a menu;
 * types a TYP does not define fall back to the built-in styles.
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
     * The TYP inside the map file; only the dataset of that file lists it.
     */
    class Embedded internal constructor(
        val file: File,
        lastModified: Long,
    ) : GarminStyle() {

        override val id: String = "${file.absolutePath}@$lastModified#typ"

        override fun resolver(img: ImgFile): StyleResolver {
            val table = img.typStyles ?: return StyleResolver.internalDefaults()
            return StyleResolver.typAware(table)
        }
    }

    /**
     * A standalone `.typ` file, read by [GarminDriver.loadStyle].
     *
     * @param lastModified file time read with the table; the id holds for it only
     */
    class External internal constructor(
        val file: File,
        lastModified: Long,
        private val table: TypStyleTable,
    ) : GarminStyle() {

        override val id: String = "${file.absolutePath}@$lastModified"

        override fun resolver(img: ImgFile): StyleResolver {
            return StyleResolver.typAware(table)
        }
    }
}
