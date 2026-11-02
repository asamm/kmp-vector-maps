/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2017-2021 devemux86
 * Copyright 2021 eddiemuc
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.asamm.vectormaps.mapsforge.map.rendertheme

import java.io.IOException
import java.io.InputStream

/**
 * Interface for a render theme which is defined in XML.
 */
interface XmlRenderTheme {

    /**
     * The callback that selects the categories to render once the style menu is parsed.
     */
    var menuCallback: XmlRenderThemeMenuCallback?

    /**
     * The prefix for all relative resource paths.
     */
    val relativePathPrefix: String?

    /**
     * A custom provider of the resources referenced by `src` attributes (images, icons).
     */
    var resourceProvider: XmlThemeResourceProvider?

    /**
     * Opens a new stream of the theme XML; the caller closes it.
     */
    @Throws(IOException::class)
    fun getRenderThemeAsStream(): InputStream
}
