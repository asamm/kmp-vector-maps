/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 devemux86
 * Copyright 2014 Develar
 * Copyright 2016 ksaihtam
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
package org.mapsforge.map.awt.graphics

import org.mapsforge.core.graphics.TileBitmap
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

open class AwtTileBitmap : AwtBitmap, TileBitmap {

    private var expiration: Long = 0
    final override var timestamp: Long = System.currentTimeMillis()

    @Throws(IOException::class)
    constructor(inputStream: InputStream) : super(inputStream)

    constructor(tileSize: Int) : super(tileSize, tileSize)

    constructor(tileSize: Int, hasAlpha: Boolean) : super(tileSize, tileSize, hasAlpha)

    constructor(bufferedImage: BufferedImage) : super(bufferedImage)

    final override val isExpired: Boolean
        get() {
            if (expiration == 0L) {
                return false
            }
            return expiration <= System.currentTimeMillis()
        }

    final override fun setExpiration(expiration: Long) {
        this.expiration = expiration
    }
}
