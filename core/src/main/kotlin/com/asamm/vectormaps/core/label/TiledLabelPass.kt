/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import com.asamm.vectormaps.core.TileRequest
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The labels a renderer stored per rendered tile, for a [TiledLabelPass]; a tile's labels show
 * while the source keeps it, so it keeps at least the tiles one screen covers.
 */
interface TileLabelSource<T> {

    /**
     * Tiles the source keeps.
     */
    val capacity: Int

    /**
     * The labels stored for [tile], null or empty when there are none; called from any thread.
     */
    fun labelsOf(tile: TileRequest): List<T>?
}

/**
 * A [TileLabelSource] keeping the last [capacity] tiles stored or read, least recently used
 * dropped.
 */
class TileLabelStore<T>(override val capacity: Int) : TileLabelSource<T> {

    init {
        require(capacity > 0) {
            "TileLabelStore(), capacity $capacity"
        }
    }

    private val tiles = object : LinkedHashMap<TileRequest, List<T>>(capacity, 0.75f, true) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileRequest, List<T>>): Boolean {
            return size > capacity
        }
    }

    /**
     * Keeps [labels] for [tile], which the caller no longer changes.
     */
    @Synchronized
    fun store(tile: TileRequest, labels: List<T>) {
        tiles[tile] = labels
    }

    @Synchronized
    override fun labelsOf(tile: TileRequest): List<T>? {
        return tiles[tile]
    }
}

/**
 * A [BackgroundLabelPass] over the tiles a renderer stored labels for: collects the tiles under
 * the turned screen and turns each stored label into a candidate through the format's
 * [candidateOf]. A host keeps no more rendered tiles than [storeCapacity], or labels of shown
 * tiles drop out.
 */
abstract class TiledLabelPass<T>(
    private val source: TileLabelSource<T>,
    private val tileSize: Int,
) : BackgroundLabelPass() {

    /**
     * Tiles the label store keeps.
     */
    val storeCapacity: Int
        get() {
            return source.capacity
        }

    /**
     * The candidate of [label], stored for [tile]; null to leave it out.
     */
    protected abstract fun candidateOf(label: T, tile: TileRequest): LabelCandidate?

    final override fun collect(view: LabelViewport, isCancelled: () -> Boolean): List<LabelCandidate> {
        val bounds = view.worldBounds()
        val maxTile = (1 shl view.zoom) - 1
        val candidates = ArrayList<LabelCandidate>()
        for (y in tileOf(bounds.minY, maxTile)..tileOf(bounds.maxY, maxTile)) {
            for (x in tileOf(bounds.minX, maxTile)..tileOf(bounds.maxX, maxTile)) {
                if (isCancelled()) {
                    return emptyList()
                }
                val tile = TileRequest(x = x, y = y, zoom = view.zoom)
                val labels = source.labelsOf(tile) ?: continue
                for (label in labels) {
                    if (isCancelled()) {
                        return emptyList()
                    }
                    candidates += candidateOf(label = label, tile = tile) ?: continue
                }
            }
        }
        return candidates
    }

    private fun tileOf(value: Double, maxTile: Int): Int {
        return floor(value / tileSize).toInt().coerceIn(0, maxTile)
    }
}

/**
 * World pixels to the turned screen of [view], as [LabelPlacer] places: the screen centre at the
 * view's centre, the map turned clockwise by its rotation.
 */
class ScreenTransform(private val view: LabelViewport) {

    private val cos = cos(Math.toRadians(view.rotationDeg))

    private val sin = sin(Math.toRadians(view.rotationDeg))

    fun x(worldX: Double, worldY: Double): Double {
        return (worldX - view.centerX) * cos - (worldY - view.centerY) * sin + view.widthPx / 2.0
    }

    fun y(worldX: Double, worldY: Double): Double {
        return (worldX - view.centerX) * sin + (worldY - view.centerY) * cos + view.heightPx / 2.0
    }
}

/**
 * FNV-1a (64 bit) for [LabelCandidate.id]: a fingerprint of what makes two stored labels the same,
 * equal from neighbouring tiles and in every run (A7).
 */
object LabelId {

    const val START: Long = -0x340d631b7bdddcdbL

    private const val PRIME: Long = 0x100000001b3L

    /**
     * Mixes the 8 bytes of [value], lowest first.
     */
    fun mix(hash: Long, value: Long): Long {
        var result = hash
        for (shift in 0 until 64 step 8) {
            result = mixByte(hash = result, byte = ((value ushr shift) and 0xFF).toInt())
        }
        return result
    }

    /**
     * Mixes each character of [text] as a value.
     */
    fun mix(hash: Long, text: String): Long {
        var result = hash
        for (char in text) {
            result = mix(hash = result, value = char.code.toLong())
        }
        return result
    }

    fun mixByte(hash: Long, byte: Int): Long {
        return (hash xor (byte.toLong() and 0xFF)) * PRIME
    }
}
