/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.utils.collections

/**
 * Growable list of unboxed `Int`s, for the RGN coordinate decoder's hot loop where `ArrayList<Int>`
 * would box every coordinate. Single-threaded.
 */
class IntArrayList(initialCapacity: Int = 10) {

    private var values = IntArray(initialCapacity)

    var size: Int = 0
        private set

    fun add(value: Int) {
        if (size == values.size) {
            values = values.copyOf(maxOf(values.size * 2, 8))
        }
        values[size++] = value
    }

    /**
     * Returns a copy of the stored values, sized to [size].
     */
    fun toIntArray(): IntArray {
        return values.copyOf(size)
    }
}
