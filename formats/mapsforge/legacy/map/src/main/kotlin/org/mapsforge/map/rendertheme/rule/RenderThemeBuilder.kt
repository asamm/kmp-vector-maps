/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016-2019 devemux86
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
package org.mapsforge.map.rendertheme.rule

import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * A builder for [RenderTheme] instances, from the attributes of the `rendertheme` element.
 */
open class RenderThemeBuilder @Throws(XmlPullParserException::class) constructor(
    graphicFactory: GraphicFactory,
    internal val displayModel: DisplayModel,
    elementName: String,
    pullParser: XmlPullParser,
) {

    internal var baseStrokeWidth: Float = 1f
    internal var baseTextSize: Float = 1f
    internal var hasBackgroundOutside: Boolean = false
    internal var mapBackground: Int = graphicFactory.createColor(Color.WHITE)
    internal var mapBackgroundOutside: Int = 0
    private var version: Int? = null

    init {
        extractValues(graphicFactory, elementName, pullParser)
    }

    fun build(): RenderTheme {
        return RenderTheme(this)
    }

    @Throws(XmlPullParserException::class)
    private fun extractValues(
        graphicFactory: GraphicFactory,
        elementName: String,
        pullParser: XmlPullParser,
    ) {
        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (XMLNS == name) {
                continue
            } else if (XMLNS_XSI == name) {
                continue
            } else if (XSI_SCHEMALOCATION == name) {
                continue
            } else if (VERSION == name) {
                this.version = XmlUtils.parseNonNegativeInteger(name, value)
            } else if (MAP_BACKGROUND == name) {
                this.mapBackground =
                    XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, null)
            } else if (MAP_BACKGROUND_OUTSIDE == name) {
                this.mapBackgroundOutside =
                    XmlUtils.getColor(graphicFactory, value, displayModel.themeCallback, null)
                this.hasBackgroundOutside = true
            } else if (BASE_STROKE_WIDTH == name) {
                this.baseStrokeWidth = XmlUtils.parseNonNegativeFloat(name, value)
            } else if (BASE_TEXT_SIZE == name) {
                this.baseTextSize = XmlUtils.parseNonNegativeFloat(name, value)
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }

        validate(elementName)
    }

    @Throws(XmlPullParserException::class)
    private fun validate(elementName: String) {
        XmlUtils.checkMandatoryAttribute(elementName, VERSION, this.version)

        if (this.version!! > RENDER_THEME_VERSION) {
            throw XmlPullParserException("unsupported render theme version: " + this.version)
        }
    }

    private companion object {

        private const val BASE_STROKE_WIDTH = "base-stroke-width"
        private const val BASE_TEXT_SIZE = "base-text-size"
        private const val MAP_BACKGROUND = "map-background"
        private const val MAP_BACKGROUND_OUTSIDE = "map-background-outside"
        private const val RENDER_THEME_VERSION = 6
        private const val VERSION = "version"
        private const val XMLNS = "xmlns"
        private const val XMLNS_XSI = "xmlns:xsi"
        private const val XSI_SCHEMALOCATION = "xsi:schemaLocation"
    }
}
