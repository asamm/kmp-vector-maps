/*
 * Copyright 2017 usrusr
 * Copyright 2024 Sublimis
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

import org.mapsforge.core.graphics.HillshadingBitmap
import org.mapsforge.core.model.BoundingBox
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer

open class AwtHillshadingBitmap(
    bufferedImage: BufferedImage,
    final override val padding: Int,
    final override val areaRect: BoundingBox?,
) : AwtBitmap(bufferedImage), HillshadingBitmap {

    final override val sizeBytes: Long
        get() {
            var retVal = 0L

            val myBufferedImage = bufferedImage

            val dataBuffer: DataBuffer? = myBufferedImage
                .data
                .dataBuffer

            if (dataBuffer != null) {
                retVal = dataBuffer.size.toLong() * DataBuffer.getDataTypeSize(dataBuffer.dataType) / 8
            }

            return retVal
        }
}
