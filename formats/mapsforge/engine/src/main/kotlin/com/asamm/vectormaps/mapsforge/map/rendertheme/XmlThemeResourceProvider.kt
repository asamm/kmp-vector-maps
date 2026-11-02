/*
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
 * Interface for a provider of resources referenced inside XML themes.
 */
fun interface XmlThemeResourceProvider {

    /**
     * Opens the resource [source], a theme `src` attribute, relative to [relativePath]; null or an
     * [IOException] falls back to the standard lookup.
     */
    @Throws(IOException::class)
    fun createInputStream(relativePath: String?, source: String): InputStream?
}
