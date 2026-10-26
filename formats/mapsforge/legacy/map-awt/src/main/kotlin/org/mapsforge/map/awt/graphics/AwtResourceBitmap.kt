/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015 devemux86
 * Copyright 2018 Adrian Batzill
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

import org.mapsforge.core.graphics.GraphicUtils
import org.mapsforge.core.graphics.ResourceBitmap
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

open class AwtResourceBitmap : AwtBitmap, ResourceBitmap {

    @Throws(IOException::class)
    internal constructor(
        inputStream: InputStream,
        scaleFactor: Float,
        width: Int,
        height: Int,
        percent: Int,
    ) : super(inputStream) {
        val newSize = GraphicUtils.imageSize(
            this.width.toFloat(),
            this.height.toFloat(),
            scaleFactor,
            width,
            height,
            percent,
        )
        scaleTo(newSize[0].toInt(), newSize[1].toInt())
    }

    internal constructor(bufferedImage: BufferedImage) : super(bufferedImage)
}
