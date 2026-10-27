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

/**
 * A rule whose value list is negated.
 *
 * - (-) 'exclusive negation' matches when either KEY is not present or KEY is present and any
 *   VALUE is NOT present.
 * - (\\) 'except negation' matches when KEY is present none items of VALUE is present (TODO);
 *   can be emulated by `<rule k="a"><rule k=a v="-|b|c">...</rule></rule>`.
 * - (~) 'non-exclusive negation' matches when either KEY is not present or KEY is present and any
 *   VALUE is present.
 */
internal open class NegativeRule(
    ruleBuilder: RuleBuilder,
    internal val attributeMatcher: NegativeMatcher,
    internal val exclusive: Boolean,
) : Rule(ruleBuilder) {

    final override fun matchesNode(tags: List<Tag>, zoomLevel: Byte): Boolean {
        return this.zoomMin <= zoomLevel
            && this.zoomMax >= zoomLevel
            && this.elementMatcher.matches(Element.NODE)
            && matchesTags(tags)
    }

    final override fun matchesWay(tags: List<Tag>, zoomLevel: Byte, closed: Closed): Boolean {
        return this.zoomMin <= zoomLevel
            && this.zoomMax >= zoomLevel
            && this.elementMatcher.matches(Element.WAY)
            && this.closedMatcher.matches(closed)
            && matchesTags(tags)
    }

    private fun matchesTags(tags: List<Tag>): Boolean {
        if (attributeMatcher.keyListDoesNotContainKeys(tags)) {
            return true
        }

        // check tags
        for (i in 0 until tags.size) {
            if (attributeMatcher.matches(tags[i])) {
                return !exclusive
            }
        }
        return exclusive
    }
}
