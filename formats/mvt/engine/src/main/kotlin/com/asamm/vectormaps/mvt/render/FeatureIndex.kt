/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.render

import com.asamm.vectormaps.mvt.style.Expression
import com.asamm.vectormaps.mvt.tile.Feature
import com.asamm.vectormaps.mvt.tile.Layer

/**
 * One render's features by property value, so each style layer evaluates its filter only on the
 * features its [Expression.selector] leaves, not on all of its source layer; built once per
 * source layer and key, dropped with the render.
 */
internal class FeatureIndex {

    // null for a key of mostly distinct values, which no bucket would narrow
    private val buckets = HashMap<Layer, HashMap<String, Map<Any?, IntArray>?>>()

    /**
     * The features of `layer` that may pass `filter`, in their draw order.
     */
    fun candidates(layer: Layer, filter: Expression?): List<Feature> {
        val selector = filter?.selector ?: return layer.features
        val byKey = buckets.getOrPut(layer) { HashMap() }
        if (!byKey.containsKey(selector.key)) {
            byKey[selector.key] = bucket(layer = layer, key = selector.key)
        }
        val byValue = byKey[selector.key] ?: return layer.features
        val indices = if (selector.values.size == 1) {
            byValue[selector.values.first()] ?: return emptyList()
        } else {
            val parts = selector.values.mapNotNull { byValue[it] }
            val all = IntArray(parts.sumOf { it.size })
            var n = 0
            for (part in parts) {
                part.copyInto(all, n)
                n += part.size
            }
            // a feature holds one value, so the parts never overlap
            all.sort()
            all
        }
        val features = layer.features
        return List(indices.size) { features[indices[it]] }
    }

    private fun bucket(layer: Layer, key: String): Map<Any?, IntArray>? {
        val keyIndex = layer.keyIndex(key)
        val features = layer.features
        val values = arrayOfNulls<Any>(features.size)
        val counts = HashMap<Any?, IntArray>()
        // a class has few values; an id-like key, as a crafted tile may use, is not worth buckets
        val maxValues = features.size / 4 + MIN_VALUES
        for (i in features.indices) {
            val value = features[i].propertyAt(keyIndex)
            values[i] = value
            counts.getOrPut(value) { IntArray(1) }[0]++
            if (counts.size > maxValues) {
                return null
            }
        }
        val buckets = HashMap<Any?, IntArray>(counts.size * 2)
        for ((value, count) in counts) {
            buckets[value] = IntArray(count[0])
        }
        // the counts become fill positions
        for (count in counts.values) {
            count[0] = 0
        }
        for (i in features.indices) {
            val count = counts.getValue(values[i])
            buckets.getValue(values[i])[count[0]++] = i
        }
        return buckets
    }

    private companion object {

        const val MIN_VALUES = 64
    }
}
