/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015 devemux86
 * Copyright 2025 Sublimis
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
package org.mapsforge.core.model

import org.mapsforge.core.util.Utils
import java.io.Serializable

/**
 * An immutable key-value pair, with hash codes of key and value for fast rule matching.
 */
open class Tag(
    @JvmField val key: String?,
    @JvmField val value: String?,
) : Comparable<Tag>, Serializable {

    /**
     * The key's hash code, 0 for a null key.
     */
    @JvmField
    val keyCode: Int = if (key == null) 0 else Utils.hashTagParameter(key)

    /**
     * The value's hash code, 0 for a null value.
     */
    @JvmField
    val valueCode: Int = if (value == null) 0 else Utils.hashTagParameter(value)

    /**
     * Parses "key=value", split at the first '='.
     */
    constructor(tag: String) : this(tag, tag.indexOf(KEY_VALUE_SEPARATOR))

    private constructor(tag: String, splitPosition: Int) : this(
        tag.substring(0, splitPosition),
        tag.substring(splitPosition + 1),
    )

    /**
     * Orders by key, then value; null sorts last.
     */
    override fun compareTo(other: Tag): Int {
        var retVal = 0

        if (key != null
            && other.key != null
        ) {
            retVal = key.compareTo(other.key)
            if (retVal != 0) {
                return retVal
            }
        } else if (key != null) {
            return -1
        } else if (other.key != null) {
            return 1
        }

        if (value != null
            && other.value != null
        ) {
            retVal = value.compareTo(other.value)
            if (retVal != 0) {
                return retVal
            }
        } else if (value != null) {
            return -1
        } else if (other.value != null) {
            return 1
        }

        return retVal
    }

    // as upstream: two null keys compare equal whatever the values
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Tag) {
            return false
        }
        if (key == null) {
            if (other.key != null) {
                return false
            }
        } else if (key != other.key) {
            return false
        } else if (value == null) {
            if (other.value != null) {
                return false
            }
        } else if (value != other.value) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + keyCode
        result = prime * result + valueCode
        return result
    }

    override fun toString(): String {
        return "key=$key, value=$value"
    }

    companion object {

        private const val serialVersionUID = 1L

        const val KEY_VALUE_SEPARATOR = '='
    }
}
