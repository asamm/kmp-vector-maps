/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014-2021 devemux86
 * Copyright 2017 usrusr
 * Copyright 2017 MarcelHeckel
 * Copyright 2021 eddiemuc
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
package org.mapsforge.map.rendertheme.rule

import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.util.IOUtils
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu
import org.mapsforge.map.rendertheme.XmlUtils
import org.mapsforge.map.rendertheme.renderinstruction.Area
import org.mapsforge.map.rendertheme.renderinstruction.Caption
import org.mapsforge.map.rendertheme.renderinstruction.Circle
import org.mapsforge.map.rendertheme.renderinstruction.Line
import org.mapsforge.map.rendertheme.renderinstruction.LineSymbol
import org.mapsforge.map.rendertheme.renderinstruction.PathText
import org.mapsforge.map.rendertheme.renderinstruction.RenderInstruction
import org.mapsforge.map.rendertheme.renderinstruction.Symbol
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.Stack
import java.util.logging.Logger

/**
 * KXML handler to parse XML render theme files.
 */
class RenderThemeHandler private constructor(
    private val graphicFactory: GraphicFactory,
    private val displayModel: DisplayModel,
    private val xmlRenderTheme: XmlRenderTheme,
    private val pullParser: XmlPullParser,
) {

    private enum class Element {
        RENDER_THEME, RENDERING_INSTRUCTION, RULE, RENDERING_STYLE
    }

    private var categories: Set<String>? = null
    private var currentLayer: XmlRenderThemeStyleLayer? = null
    private var currentRule: Rule? = null
    private val elementStack = Stack<Element>()
    private var level: Int = 0
    private var renderTheme: RenderTheme? = null
    private var renderThemeStyleMenu: XmlRenderThemeStyleMenu? = null
    private val ruleStack = Stack<Rule>()
    private val symbols: MutableMap<String, Symbol> = HashMap()

    @Throws(XmlPullParserException::class, IOException::class)
    private fun processRenderTheme(): RenderTheme {
        var eventType = pullParser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                startElement()
            } else if (eventType == XmlPullParser.END_TAG) {
                endElement()
            }
            eventType = pullParser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)
        return endDocument()
    }

    private fun endDocument(): RenderTheme {
        val renderTheme = this.renderTheme
            ?: throw IllegalArgumentException("missing element: rules")

        renderTheme.levels = this.level
        renderTheme.complete()
        return renderTheme
    }

    private fun endElement() {
        val qName = pullParser.name

        this.elementStack.pop()

        if (ELEMENT_NAME_RULE == qName) {
            this.ruleStack.pop()
            if (this.ruleStack.empty()) {
                if (isVisible(this.currentRule!!)) {
                    this.renderTheme!!.addRule(this.currentRule!!)
                }
            } else {
                this.currentRule = this.ruleStack.peek()
            }
        } else if ("stylemenu" == qName) {
            // when we are finished parsing the menu part of the file, we can get the
            // categories to render from the initiator. This allows the creating action
            // to select which of the menu options to choose
            // if there is no callback, there is no menu, so the categories will be null
            this.xmlRenderTheme.menuCallback?.let { menuCallback ->
                this.categories = menuCallback.getCategories(this.renderThemeStyleMenu!!)
            }
        }
    }

    @Throws(XmlPullParserException::class)
    private fun startElement() {
        val qName = pullParser.name

        try {
            if ("rendertheme" == qName) {
                checkState(qName, Element.RENDER_THEME)
                this.renderTheme =
                    RenderThemeBuilder(this.graphicFactory, this.displayModel, qName, pullParser).build()
            } else if (ELEMENT_NAME_RULE == qName) {
                checkState(qName, Element.RULE)
                val rule = RuleBuilder(qName, pullParser, this.ruleStack).build()
                if (!this.ruleStack.empty()
                    && isVisible(rule)
                ) {
                    this.currentRule!!.addSubRule(rule)
                }
                this.currentRule = rule
                this.ruleStack.push(rule)
            } else if ("area" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val area = Area(
                    this.graphicFactory,
                    this.displayModel,
                    qName,
                    pullParser,
                    this.level++,
                    this.xmlRenderTheme.relativePathPrefix,
                    this.xmlRenderTheme.resourceProvider,
                )
                addInstruction(area)
            } else if ("caption" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val caption = Caption(this.graphicFactory, this.displayModel, qName, pullParser, symbols)
                addInstruction(caption)
            } else if ("cat" == qName) {
                checkState(qName, Element.RENDERING_STYLE)
                val currentLayer = this.currentLayer!!
                // a null category is never looked up: an instruction without one is always visible
                getStringAttribute("id")?.let { currentLayer.addCategory(it) }
            } else if ("circle" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val circle = Circle(this.graphicFactory, this.displayModel, qName, pullParser, this.level++)
                addInstruction(circle)
            }

            // rendertheme menu layer
            else if ("layer" == qName) {
                checkState(qName, Element.RENDERING_STYLE)
                val enabled = getStringAttribute("enabled")?.toBoolean() ?: false
                val visible = getStringAttribute("visible")?.toBoolean() ?: false
                val currentLayer =
                    this.renderThemeStyleMenu!!.createLayer(getStringAttribute("id"), visible, enabled)
                this.currentLayer = currentLayer
                val parent = getStringAttribute("parent")
                if (null != parent) {
                    val parentEntry = this.renderThemeStyleMenu!!.getLayer(parent)
                    if (null != parentEntry) {
                        for (cat in parentEntry.categories) {
                            currentLayer.addCategory(cat)
                        }
                        for (overlay in parentEntry.overlays) {
                            currentLayer.addOverlay(overlay)
                        }
                    }
                }
            } else if ("line" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val line = Line(
                    this.graphicFactory,
                    this.displayModel,
                    qName,
                    pullParser,
                    this.level++,
                    this.xmlRenderTheme.relativePathPrefix,
                    this.xmlRenderTheme.resourceProvider,
                )
                addInstruction(line)
            } else if ("lineSymbol" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val lineSymbol = LineSymbol(
                    this.graphicFactory,
                    this.displayModel,
                    qName,
                    pullParser,
                    this.xmlRenderTheme.relativePathPrefix,
                    this.xmlRenderTheme.resourceProvider,
                )
                addInstruction(lineSymbol)
            }

            // render theme menu name
            else if ("name" == qName) {
                checkState(qName, Element.RENDERING_STYLE)
                val currentLayer = this.currentLayer!!
                val language = getStringAttribute("lang")
                val value = getStringAttribute("value")
                // without a value getTitle falls back to the default language anyway; a title
                // without a language is dropped (upstream kept it under a null key)
                if (language != null
                    && value != null
                ) {
                    currentLayer.addTranslation(language, value)
                }
            }

            // render theme menu overlay
            else if ("overlay" == qName) {
                checkState(qName, Element.RENDERING_STYLE)
                val overlay = this.renderThemeStyleMenu!!.getLayer(getStringAttribute("id"))
                if (overlay != null) {
                    this.currentLayer!!.addOverlay(overlay)
                }
            } else if ("pathText" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val pathText = PathText(this.graphicFactory, this.displayModel, qName, pullParser)
                addInstruction(pathText)
            } else if ("stylemenu" == qName) {
                checkState(qName, Element.RENDERING_STYLE)

                this.renderThemeStyleMenu = XmlRenderThemeStyleMenu(
                    getStringAttribute("id"),
                    getStringAttribute("defaultlang"),
                    getStringAttribute("defaultvalue"),
                )
            } else if ("symbol" == qName) {
                checkState(qName, Element.RENDERING_INSTRUCTION)
                val symbol = Symbol(
                    this.graphicFactory,
                    this.displayModel,
                    qName,
                    pullParser,
                    this.xmlRenderTheme.relativePathPrefix,
                    this.xmlRenderTheme.resourceProvider,
                )
                addInstruction(symbol)
                val symbolId = symbol.id
                if (symbolId != null) {
                    this.symbols[symbolId] = symbol
                }
            } else if ("hillshading" == qName) {
                // not rendered: the element is still validated and takes its level, as upstream
                checkState(qName, Element.RULE)
                for (i in 0 until pullParser.attributeCount) {
                    val name = pullParser.getAttributeName(i)
                    val value = pullParser.getAttributeValue(i)

                    if ("zoom-min" == name
                        || "zoom-max" == name
                        || "layer" == name
                    ) {
                        XmlUtils.parseNonNegativeByte(name, value)
                    } else if ("magnitude" == name) {
                        val magnitude = XmlUtils.parseNonNegativeInteger(name, value)
                        if (magnitude > 255) {
                            throw XmlPullParserException("Attribute 'magnitude' must not be > 255")
                        }
                    } else if ("color" == name) {
                        XmlUtils.getColor(this.graphicFactory, value)
                    }
                }
                this.level++
            } else {
                throw XmlPullParserException("unknown element: $qName")
            }
        } catch (e: IOException) {
            LOGGER.warning("Rendertheme missing or invalid resource $e")
        }
    }

    private fun addInstruction(renderInstruction: RenderInstruction) {
        if (isVisible(renderInstruction)) {
            this.currentRule!!.addRenderingInstruction(renderInstruction)
        }
    }

    @Throws(XmlPullParserException::class)
    private fun checkElement(elementName: String, element: Element) {
        when (element) {
            Element.RENDER_THEME -> {
                if (!this.elementStack.empty()) {
                    throw XmlPullParserException(UNEXPECTED_ELEMENT + elementName)
                }
            }

            Element.RULE -> {
                val parentElement = this.elementStack.peek()
                if (parentElement != Element.RENDER_THEME
                    && parentElement != Element.RULE
                ) {
                    throw XmlPullParserException(UNEXPECTED_ELEMENT + elementName)
                }
            }

            Element.RENDERING_INSTRUCTION -> {
                if (this.elementStack.peek() != Element.RULE) {
                    throw XmlPullParserException(UNEXPECTED_ELEMENT + elementName)
                }
            }

            Element.RENDERING_STYLE -> {
                // allowed anywhere
            }
        }
    }

    @Throws(XmlPullParserException::class)
    private fun checkState(elementName: String, element: Element) {
        checkElement(elementName, element)
        this.elementStack.push(element)
    }

    private fun getStringAttribute(name: String): String? {
        val n = pullParser.attributeCount
        for (i in 0 until n) {
            if (pullParser.getAttributeName(i) == name) {
                return pullParser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun isVisible(renderInstruction: RenderInstruction): Boolean {
        val category = renderInstruction.category
        return this.categories == null
            || category == null
            || this.categories!!.contains(category)
    }

    private fun isVisible(rule: Rule): Boolean {
        // a rule is visible if categories is not set, the rule has not category or the
        // categories contain this rule's category
        val cat = rule.cat
        return this.categories == null
            || cat == null
            || this.categories!!.contains(cat)
    }

    companion object {

        private val LOGGER: Logger = Logger.getLogger(RenderThemeHandler::class.java.name)
        private const val ELEMENT_NAME_RULE = "rule"
        private const val UNEXPECTED_ELEMENT = "unexpected element: "
        private var xmlPullParserFactory: XmlPullParserFactory? = null

        /**
         * Parses [xmlRenderTheme] into a new [RenderTheme]. Images load on first render; a missing or
         * invalid one leaves its instruction drawing without it.
         */
        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getRenderTheme(
            graphicFactory: GraphicFactory,
            displayModel: DisplayModel,
            xmlRenderTheme: XmlRenderTheme,
        ): RenderTheme {
            val pullParser = getXmlPullParserFactory().newPullParser()

            val renderThemeHandler =
                RenderThemeHandler(graphicFactory, displayModel, xmlRenderTheme, pullParser)
            var inputStream: InputStream? = null
            try {
                inputStream = xmlRenderTheme.getRenderThemeAsStream()
                pullParser.setInput(inputStream, null)
                return renderThemeHandler.processRenderTheme()
            } finally {
                IOUtils.closeQuietly(inputStream)
            }
        }

        @JvmStatic
        @Throws(XmlPullParserException::class)
        fun getXmlPullParserFactory(): XmlPullParserFactory {
            return xmlPullParserFactory
                ?: XmlPullParserFactory.newInstance().also { xmlPullParserFactory = it }
        }

        @JvmStatic
        fun setXmlPullParserFactory(xmlPullParserFactory: XmlPullParserFactory?) {
            this.xmlPullParserFactory = xmlPullParserFactory
        }
    }
}
