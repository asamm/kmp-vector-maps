/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 *
 * IntArrayList shape mirrors the locus-core equivalent in
 * `com.asamm.utils.base.collections.IntArrayList` (originally TJDO, 2004).
 * Copied here rather than depended on — the lib must stay free of any
 * locus-core dep per CLAUDE.md. KMP-clean: no `Cloneable`,
 * `System.arraycopy`, or `java.lang.*` references.
 */
package com.asamm.garminimg.utils.collections

import kotlin.math.max
import kotlin.math.min

/**
 * Growable `IntArray`-backed list, drop-in alternative to
 * `ArrayList<Int>` that avoids boxing each element into
 * `kotlin.Int`'s heap representation.
 *
 * Used in the parser's coordinate-stream decoder where each polyline
 * grows an N-element coordinate list — `ArrayList<Int>` allocates
 * `Int` boxes and pads each through `Object[]`, whereas this list
 * stores the values directly in an `IntArray`. Net effect on a
 * country-scale IMG: hundreds of thousands of fewer allocations per
 * tile.
 *
 * Single-threaded. Mirrors the public surface of the equivalent class
 * in locus-core's `libUtilsBase` so future maintainers see a familiar
 * API.
 */
class IntArrayList(initialCapacity: Int = 10) {

    private var elements: IntArray = IntArray(initialCapacity)

    /** Number of elements currently stored. */
    var size: Int = 0
        private set

    init {
        require(initialCapacity >= 0) { "Illegal capacity: $initialCapacity" }
    }

    /** Builds a list pre-loaded with [initialContents]'s entries. */
    constructor(initialContents: IntArray) : this(
        min(initialContents.size * 11L / 10, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        initialContents.copyInto(elements, destinationOffset = 0)
        size = initialContents.size
    }

    /** True when empty. */
    val isEmpty: Boolean
        get() = size == 0

    /** Range of valid indices. */
    val indices: IntRange
        get() = 0 until size

    /** Reads index `i`. Throws on out-of-range. */
    operator fun get(index: Int): Int {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        return elements[index]
    }

    /** Reads index `i` without bounds-checking — caller's responsibility. */
    fun getQuick(index: Int): Int {
        return elements[index]
    }

    /** Overwrites index `i`. Returns the old value. */
    operator fun set(index: Int, element: Int): Int {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        val old = elements[index]
        elements[index] = element
        return old
    }

    /** Appends one element. Amortised O(1). */
    fun add(element: Int) {
        ensureCapacity(size + 1)
        elements[size++] = element
    }

    /** Empties the list. Backing array is retained. */
    fun clear() {
        size = 0
    }

    /**
     * Returns a fresh `IntArray` sized exactly to [size], containing
     * the current elements. Use when handing the list off to a
     * downstream API that expects a primitive `IntArray`.
     */
    fun toIntArray(): IntArray {
        val out = IntArray(size)
        elements.copyInto(out, destinationOffset = 0, startIndex = 0, endIndex = size)
        return out
    }

    private fun ensureCapacity(minCapacity: Int) {
        val oldCapacity = elements.size
        if (minCapacity > oldCapacity) {
            val idealSize = max(minCapacity.toLong(), oldCapacity * 3L / 2)
            val newSize = min(idealSize, Int.MAX_VALUE.toLong()).toInt()
            val newElements = IntArray(newSize)
            elements.copyInto(newElements, destinationOffset = 0, startIndex = 0, endIndex = size)
            elements = newElements
        }
    }

    override fun toString(): String {
        return buildString {
            append('[')
            for (i in 0 until size) {
                if (i > 0) append(", ")
                append(elements[i])
            }
            append(']')
        }
    }
}
