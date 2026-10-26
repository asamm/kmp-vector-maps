/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2016 devemux86
 * Copyright 2014 Develar
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

import org.mapsforge.core.graphics.BaseBitmap
import org.mapsforge.core.graphics.Bitmap
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * A [Bitmap] backed by a [BufferedImage]; reference counting is a no-op.
 */
open class AwtBitmap : BaseBitmap, Bitmap {

    @Volatile
    internal var bufferedImage: BufferedImage

    @Throws(IOException::class)
    constructor(inputStream: InputStream) {
        val image: BufferedImage? = ImageIO.read(inputStream)
        if (image == null) {
            throw IOException("ImageIO failed to read inputStream")
        }
        this.bufferedImage = image
    }

    constructor(width: Int, height: Int) : this(width, height, true)

    constructor(width: Int, height: Int, hasAlpha: Boolean) : this(
        BufferedImage(
            width,
            height,
            if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
        ),
    )

    constructor(bufferedImage: BufferedImage) {
        this.bufferedImage = bufferedImage
    }

    @Throws(IOException::class)
    final override fun compress(outputStream: OutputStream) {
        ImageIO.write(this.bufferedImage, "png", outputStream)
    }

    final override fun decrementRefCount() {
        // no-op
    }

    final override val height: Int
        get() {
            return this.bufferedImage.height
        }

    final override val width: Int
        get() {
            return this.bufferedImage.width
        }

    final override fun incrementRefCount() {
        // no-op
    }

    // the image is never null, so never destroyed
    final override val isDestroyed: Boolean
        get() {
            return false
        }

    final override fun scaleTo(width: Int, height: Int) {
        if (this.width != width
            || this.height != height
        ) {
            val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = resizedImage.createGraphics()
            graphics.composite = AlphaComposite.Src
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(bufferedImage, 0, 0, width, height, null)
            graphics.dispose()
            this.bufferedImage = resizedImage
        }
    }

    final override fun setBackgroundColor(color: Int) {
        val myBufImage = bufferedImage

        val graphics = myBufImage.createGraphics()
        graphics.color = Color(color, true)
        graphics.fillRect(0, 0, myBufImage.width, myBufImage.height)
        graphics.dispose()
    }
}
