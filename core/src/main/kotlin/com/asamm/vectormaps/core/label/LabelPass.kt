/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * What a screen shows, for placing labels: [widthPx] by [heightPx] centered on a world point at
 * [zoom], the map turned clockwise by [rotationDeg]. World pixels follow the renderer's tile size.
 *
 * @param centerX screen center in world pixels at [zoom]
 */
data class LabelViewport(
    val zoom: Int,
    val centerX: Double,
    val centerY: Double,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDeg: Double = 0.0,
) {

    init {
        require(widthPx > 0
            && heightPx > 0
        ) {
            "LabelViewport(), size $widthPx x $heightPx"
        }
        require(centerX.isFinite()
            && centerY.isFinite()
            && rotationDeg.isFinite()
        ) {
            "LabelViewport(), center $centerX, $centerY, rotation $rotationDeg"
        }
    }

    /**
     * The world-pixel rectangle the turned screen covers.
     */
    fun worldBounds(): WorldBounds {
        val radians = Math.toRadians(rotationDeg)
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val halfX = c * widthPx / 2.0 + s * heightPx / 2.0
        val halfY = s * widthPx / 2.0 + c * heightPx / 2.0
        return WorldBounds(
            minX = centerX - halfX,
            minY = centerY - halfY,
            maxX = centerX + halfX,
            maxY = centerY + halfY,
        )
    }
}

data class WorldBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

/**
 * The labels placed for one view.
 *
 * @param placed in draw order, lowest priority first
 */
class PreparedLabels(
    val view: LabelViewport,
    val placed: List<PlacedLabel>,
)

/**
 * Places labels per screen, apart from the tiles ([com.asamm.vectormaps.core.LabelMode.SEPARATE]).
 * Drawing is the format's, as it owns the label graphics.
 */
interface LabelPass : Closeable {

    /**
     * The latest placement; null until the first one finishes.
     */
    val prepared: PreparedLabels?

    /**
     * Called on the preparing thread each time [prepared] changes.
     */
    var onPrepared: (() -> Unit)?

    /**
     * Places the labels for [view] off the caller's thread, cancelling a run still going.
     */
    fun prepare(view: LabelViewport)
}

/**
 * A [LabelPass] that collects a view's candidates and places them on one background coroutine;
 * a newer view cancels the run before it.
 */
abstract class BackgroundLabelPass : LabelPass {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val lock = Any()

    // guarded by lock
    private var running: Job? = null

    @Volatile
    final override var prepared: PreparedLabels? = null
        private set

    @Volatile
    final override var onPrepared: (() -> Unit)? = null

    /**
     * The candidates [view] may show; checks [isCancelled] where it can stop early.
     */
    protected abstract fun collect(
        view: LabelViewport,
        isCancelled: () -> Boolean,
    ): List<LabelCandidate>

    final override fun prepare(view: LabelViewport) {
        synchronized(lock) {
            running?.cancel()
            running = scope.launch {
                val candidates = collect(view = view, isCancelled = { !isActive })
                ensureActive()
                val placed = LabelPlacer.place(
                    candidates = candidates,
                    rotationDeg = view.rotationDeg,
                )
                ensureActive()
                prepared = PreparedLabels(view = view, placed = placed)
                onPrepared?.invoke()
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
