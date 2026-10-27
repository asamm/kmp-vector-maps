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
package org.mapsforge.map.rendertheme.rule

import org.mapsforge.core.model.Tag

internal open class PositiveRule internal constructor(
    ruleBuilder: RuleBuilder,
    internal val keyMatcher: AttributeMatcher,
    internal val valueMatcher: AttributeMatcher,
) : Rule(ruleBuilder) {

    final override fun matchesNode(tags: List<Tag>, zoomLevel: Byte): Boolean {
        return this.zoomMin <= zoomLevel
            && this.zoomMax >= zoomLevel
            && this.elementMatcher.matches(Element.NODE)
            && matchesAnyTag(tags)
    }

    final override fun matchesWay(tags: List<Tag>, zoomLevel: Byte, closed: Closed): Boolean {
        return this.zoomMin <= zoomLevel
            && this.zoomMax >= zoomLevel
            && this.elementMatcher.matches(Element.WAY)
            && this.closedMatcher.matches(closed)
            && matchesAnyTag(tags)
    }

    private fun matchesAnyTag(tags: List<Tag>): Boolean {
        for (i in 0 until tags.size) {
            val tag = tags[i]
            if (keyMatcher.matches(tag)
                && valueMatcher.matches(tag)
            ) {
                return true
            }
        }
        return false
    }
}
