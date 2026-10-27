/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2018 b3nn0
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
package org.mapsforge.map.rendertheme.renderinstruction

import org.mapsforge.core.model.Tag
import org.mapsforge.core.util.Utils

internal class TextKey private constructor(key: Int) {

    private val keyCode: Int = key

    fun getValue(tags: List<Tag>): String? {
        for (i in 0 until tags.size) {
            if (this.keyCode == tags[i].keyCode) {
                return tags[i].value
            }
        }
        return null
    }

    companion object {

        private val TEXT_KEYS: MutableMap<Int, TextKey> = HashMap()

        fun getInstance(key: String): TextKey {
            val keyCode = Utils.hashTagParameter(key)
            var textKey = TEXT_KEYS[keyCode]
            if (textKey == null) {
                textKey = TextKey(keyCode)
                TEXT_KEYS[keyCode] = textKey
            }
            return textKey
        }
    }
}
