/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2016-2021 devemux86
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

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * An XML render theme read from a file. Two instances are equal when they read the same file at
 * the same modification time.
 *
 * @throws FileNotFoundException if the file does not exist or cannot be read.
 */
open class ExternalRenderTheme @JvmOverloads @Throws(FileNotFoundException::class) constructor(
    private val renderThemeFile: File,
    final override var menuCallback: XmlRenderThemeMenuCallback? = null,
) : XmlRenderTheme {

    private val lastModifiedTime: Long

    final override var resourceProvider: XmlThemeResourceProvider? = null

    init {
        if (!renderThemeFile.exists()) {
            throw FileNotFoundException("file does not exist: " + renderThemeFile.absolutePath)
        } else if (!renderThemeFile.isFile) {
            throw FileNotFoundException("not a file: " + renderThemeFile.absolutePath)
        } else if (!renderThemeFile.canRead()) {
            throw FileNotFoundException("cannot read file: " + renderThemeFile.absolutePath)
        }

        this.lastModifiedTime = renderThemeFile.lastModified()
        if (this.lastModifiedTime == 0L) {
            throw FileNotFoundException(
                "cannot read last modified time: " + renderThemeFile.absolutePath,
            )
        }
    }

    @JvmOverloads
    @Throws(FileNotFoundException::class)
    constructor(
        renderThemePath: String,
        menuCallback: XmlRenderThemeMenuCallback? = null,
    ) : this(File(renderThemePath), menuCallback)

    final override val relativePathPrefix: String?
        get() = this.renderThemeFile.parent

    @Throws(IOException::class)
    final override fun getRenderThemeAsStream(): InputStream {
        return FileInputStream(this.renderThemeFile)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is ExternalRenderTheme) {
            return false
        }
        return this.lastModifiedTime == other.lastModifiedTime
            && this.renderThemeFile == other.renderThemeFile
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (this.lastModifiedTime xor (this.lastModifiedTime ushr 32)).toInt()
        result = prime * result + this.renderThemeFile.hashCode()
        return result
    }
}
