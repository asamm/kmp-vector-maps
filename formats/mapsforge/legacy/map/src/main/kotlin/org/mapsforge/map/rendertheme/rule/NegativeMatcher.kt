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
import org.mapsforge.core.util.Utils

internal open class NegativeMatcher(
    keyList: List<String>,
    valueList: List<String>,
) : AttributeMatcher {

    private val keyList: IntArray = Utils.convertListString(keyList)
    private val valueList: IntArray = Utils.convertListString(valueList)

    final override fun matches(tag: Tag): Boolean {
        return Utils.contains(keyList, tag.keyCode)
            && Utils.contains(valueList, tag.valueCode)
    }

    internal fun keyListDoesNotContainKeys(tags: List<Tag>): Boolean {
        for (i in 0 until tags.size) {
            if (Utils.contains(keyList, tags[i].keyCode)) {
                return false
            }
        }
        return true
    }
}
