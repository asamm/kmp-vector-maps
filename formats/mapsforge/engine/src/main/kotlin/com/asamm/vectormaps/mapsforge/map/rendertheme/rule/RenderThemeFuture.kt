/*
 * Copyright 2014 Ludwig M Brinckmann
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
package com.asamm.vectormaps.mapsforge.map.rendertheme.rule

import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.map.model.DisplayModel
import com.asamm.vectormaps.mapsforge.map.rendertheme.XmlRenderTheme
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger

/**
 * Parses an [XmlRenderTheme] when run, off the UI thread. Reference counted to share it between
 * threads: a thread first calls [incrementRefCount], so the theme is not destroyed while it waits,
 * and [decrementRefCount] when done; the last release destroys the theme or cancels the parse.
 */
open class RenderThemeFuture(
    graphicFactory: GraphicFactory,
    xmlRenderTheme: XmlRenderTheme,
    displayModel: DisplayModel,
) : FutureTask<RenderTheme>(RenderThemeCallable(graphicFactory, xmlRenderTheme, displayModel)) {

    private val refCount = AtomicInteger(1)

    /**
     * Parses the theme through [RenderThemeHandler].
     */
    private class RenderThemeCallable(
        private val graphicFactory: GraphicFactory,
        private val xmlRenderTheme: XmlRenderTheme,
        private val displayModel: DisplayModel,
    ) : Callable<RenderTheme> {

        override fun call(): RenderTheme {
            try {
                return RenderThemeHandler.getRenderTheme(
                    this.graphicFactory,
                    this.displayModel,
                    this.xmlRenderTheme,
                )
            } catch (e: XmlPullParserException) {
                throw IllegalArgumentException("Parse error for XML rendertheme", e)
            } catch (e: IOException) {
                throw IllegalArgumentException("File error for XML rendertheme", e)
            }
        }
    }

    fun decrementRefCount() {
        val c = this.refCount.decrementAndGet()
        if (c <= 0) {
            try {
                if (this.isDone) {
                    get().destroy()
                } else {
                    cancel(true)
                }
            } catch (e: Exception) {
                // just cleaning up
            }
        }
    }

    fun incrementRefCount() {
        this.refCount.incrementAndGet()
    }
}
