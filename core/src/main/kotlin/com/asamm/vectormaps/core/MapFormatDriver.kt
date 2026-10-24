/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core

import java.io.File

/**
 * One vector map format: recognises its files, opens them as a [MapDataset] and loads the styles
 * that render them. Implementations are stateless and thread-safe; register them in [MapFormats].
 */
interface MapFormatDriver {

    /**
     * Stable format name, such as `mapsforge`.
     */
    val id: String

    /**
     * Lower-case file extensions without the dot; [MapFormats] probes only files that have one.
     */
    val extensions: Set<String>

    /**
     * Reads the file header; returns null or throws when the file is not in this format or is
     * unreadable.
     */
    fun probe(file: File): MapInfo?

    /**
     * Opens `files` as one dataset, such as a Mapsforge multi-map or an IMG with its TYP.
     */
    fun open(files: List<File>): MapDataset

    /**
     * Reads a style file of this format and its menu; compiling it for a selection and
     * configuration may wait for [MapDataset.createRenderer]. Reusable across datasets.
     */
    fun loadStyle(file: File): MapStyle
}

/**
 * What a probe learns from a file header without opening the file as a dataset.
 *
 * @param languages preferred label languages the file offers, empty when it has no such list
 */
data class MapInfo(
    val bounds: GeoBounds,
    val languages: List<String>,
)
