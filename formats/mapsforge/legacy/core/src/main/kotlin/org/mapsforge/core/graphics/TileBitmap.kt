/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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
package org.mapsforge.core.graphics

interface TileBitmap : Bitmap {

    /**
     * True once the expiration time has passed, never for an expiration of 0; the tile may still
     * show until a fresh copy is available.
     */
    val isExpired: Boolean

    /**
     * When the tile's source data was last retrieved, in milliseconds since the epoch, 0 when
     * unknown: the map file date for a rendered tile, the file time for a cached one, else the
     * creation time, which implementations store until it is set.
     */
    var timestamp: Long

    /**
     * When the tile expires, in milliseconds since the epoch, 0 when unknown: the server's
     * expiration for a download, else now plus a TTL.
     */
    fun setExpiration(expiration: Long)
}
