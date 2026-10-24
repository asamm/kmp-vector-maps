/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import java.io.Closeable
import java.io.File

/**
 * One or more open map files of one format, rendered together. Thread-safe: renderers created
 * from it read in parallel, and [addMap] may run while they do.
 */
interface MapDataset : Closeable {

    val driver: MapFormatDriver

    /**
     * Union of the bounds of all files; grows with [addMap].
     */
    val bounds: GeoBounds

    /**
     * Adds a file to the open dataset; false when the file is not readable in this format.
     */
    fun addMap(file: File): Boolean

    /**
     * Creates a renderer for one style, menu selection and configuration; `style` must come from
     * this dataset's driver. May compile the style, which is costly: reuse renderers per
     * [RenderKey].
     */
    fun createRenderer(style: MapStyle, selection: StyleSelection, config: RenderConfig): MapRenderer
}
