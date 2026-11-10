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
package com.asamm.vectormaps.mapsforge.map.rendertheme.rule

import com.asamm.vectormaps.mapsforge.map.rendertheme.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.regex.Pattern

/**
 * A builder for [Rule] instances, from the attributes of a `rule` element.
 */
open class RuleBuilder @Throws(XmlPullParserException::class) constructor(
    elementName: String,
    pullParser: XmlPullParser,
) {

    internal var cat: String? = null
    internal lateinit var closedMatcher: ClosedMatcher
    internal lateinit var elementMatcher: ElementMatcher
    internal var zoomMax: Byte = Byte.MAX_VALUE
    internal var zoomMin: Byte = 0
    private var closed: Closed = Closed.ANY
    private var element: Element? = null
    private lateinit var keyList: MutableList<String>
    private var keys: String? = null
    private lateinit var valueList: MutableList<String>
    private var values: String? = null

    init {
        extractValues(elementName, pullParser)
    }

    fun build(): Rule {
        if (this.valueList.remove(STRING_NEGATION)) {
            val attributeMatcher = NegativeMatcher(this.keyList, this.valueList)
            return NegativeRule(this, attributeMatcher, false)
        }

        if (this.valueList.remove(STRING_EXCLUSIVE)) {
            val attributeMatcher = NegativeMatcher(this.keyList, this.valueList)
            return NegativeRule(this, attributeMatcher, true)
        }

        val keyMatcher = getKeyMatcher(this.keyList)
        val valueMatcher = getValueMatcher(this.valueList)

        return PositiveRule(this, keyMatcher, valueMatcher)
    }

    @Throws(XmlPullParserException::class)
    private fun extractValues(elementName: String, pullParser: XmlPullParser) {
        for (i in 0 until pullParser.attributeCount) {
            val name = pullParser.getAttributeName(i)
            val value = pullParser.getAttributeValue(i)

            if (E == name) {
                this.element = Element.fromString(value)
            } else if (K == name) {
                this.keys = value
            } else if (V == name) {
                this.values = value
            } else if (CAT == name) {
                this.cat = value
            } else if (CLOSED == name) {
                this.closed = Closed.fromString(value)
            } else if (ZOOM_MIN == name) {
                this.zoomMin = XmlUtils.parseNonNegativeByte(name, value)
            } else if (ZOOM_MAX == name) {
                this.zoomMax = XmlUtils.parseNonNegativeByte(name, value)
            } else {
                XmlUtils.logUnknownAttribute(elementName, name, value, i)
            }
        }

        validate(elementName)

        this.keyList = SPLIT_PATTERN.split(this.keys!!).toMutableList()
        this.valueList = SPLIT_PATTERN.split(this.values!!).toMutableList()

        this.elementMatcher = getElementMatcher(this.element!!)
        this.closedMatcher = getClosedMatcher(this.closed)
    }

    @Throws(XmlPullParserException::class)
    private fun validate(elementName: String) {
        XmlUtils.checkMandatoryAttribute(elementName, E, this.element)
        XmlUtils.checkMandatoryAttribute(elementName, K, this.keys)
        XmlUtils.checkMandatoryAttribute(elementName, V, this.values)

        if (this.zoomMin > this.zoomMax) {
            throw XmlPullParserException("'$ZOOM_MIN' > '$ZOOM_MAX': ${this.zoomMin} ${this.zoomMax}")
        }
    }

    private companion object {

        private const val CAT = "cat"
        private const val CLOSED = "closed"
        private const val E = "e"
        private const val K = "k"
        private val SPLIT_PATTERN: Pattern = Pattern.compile("\\|")
        private const val STRING_NEGATION = "~"
        private const val STRING_EXCLUSIVE = "-"
        private const val STRING_WILDCARD = "*"
        private const val V = "v"
        private const val ZOOM_MAX = "zoom-max"
        private const val ZOOM_MIN = "zoom-min"

        private fun getClosedMatcher(closed: Closed): ClosedMatcher {
            return when (closed) {
                Closed.YES -> ClosedWayMatcher
                Closed.NO -> LinearWayMatcher
                Closed.ANY -> AnyMatcher
            }
        }

        private fun getElementMatcher(element: Element): ElementMatcher {
            return when (element) {
                Element.NODE -> ElementNodeMatcher
                Element.WAY -> ElementWayMatcher
                Element.ANY -> AnyMatcher
            }
        }

        private fun getKeyMatcher(keyList: List<String>): AttributeMatcher {
            if (STRING_WILDCARD == keyList[0]) {
                return AnyMatcher
            }
            return Rule.MATCHERS_CACHE_KEY.getOrPut(keyList) { KeyMatcher(keyList) }
        }

        private fun getValueMatcher(valueList: List<String>): AttributeMatcher {
            if (STRING_WILDCARD == valueList[0]) {
                return AnyMatcher
            }
            return Rule.MATCHERS_CACHE_VALUE.getOrPut(valueList) { ValueMatcher(valueList) }
        }
    }
}
