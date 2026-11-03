/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.driver

import com.asamm.vectormaps.core.GeoBounds
import com.asamm.vectormaps.core.LabelMode
import com.asamm.vectormaps.core.MapDataset
import com.asamm.vectormaps.core.MapStyle
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.garminimg.ImgFile
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.utils.io.FileImgRandomAccessReader
import java.io.File

/**
 * Garmin `.img` files, each with one map or many (a gmapsupp holds one per tile), rendered
 * together. Every file is parsed once and shared by all renderers, whose workers read through
 * their own readers; renderers keep the files they were created with.
 */
class GarminDataset internal constructor(
    override val driver: GarminDriver,
    files: List<File>,
) : MapDataset {

    private val lock = Any()

    @Volatile
    private var snapshot: Snapshot? = null

    init {
        require(files.isNotEmpty()) {
            "GarminDataset(), no files"
        }
        for (file in files) {
            // the reason a file is unreadable reaches the caller
            require(add(file)) {
                "GarminDataset(), $file given twice"
            }
        }
    }

    /**
     * The files, in the order they were added.
     */
    val files: List<File>
        get() = current().files

    override val bounds: GeoBounds
        get() = current().bounds

    /**
     * TRE levels with their own data across the maps, ascending (finest first), for
     * [GarminOptions.level]; a level number is per map, so the bits shown are those of the first
     * map that has it.
     */
    val levels: List<GarminLevel>
        get() = current().levels

    /**
     * The embedded TYP when a file has one, by day and, when it has night colours, at night; then
     * the built-in styles. The same instances until [addMap].
     */
    override val styles: List<GarminStyle>
        get() = current().styles

    /**
     * Maps of the files that do not parse and are skipped, such as the damaged tiles of a
     * gmapsupp; a file without a readable map is not added at all.
     */
    val unreadableMaps: List<UnreadableMap>
        get() = current().unreadableMaps

    /**
     * Adds a file with all its readable maps; false when it has none or is already added.
     */
    override fun addMap(file: File): Boolean {
        return try {
            add(file)
        } catch (e: Exception) {
            false
        }
    }

    // throws when the file is not a readable Garmin map; false when it is already added
    private fun add(file: File): Boolean {
        // read before the content, so a later change always shows
        val lastModified = file.lastModified()
        val entry = Entry(file = file, lastModified = lastModified, summary = driver.readSummary(file = file, full = true))
        // a file that does not parse fails here, not in every later renderer
        entry.parsed()
        synchronized(lock) {
            val entries = snapshot?.entries.orEmpty()
            if (entries.any { it.isFile(file) }) {
                return false
            }
            snapshot = Snapshot(driver = driver, entries = entries + entry)
        }
        return true
    }

    private fun current(): Snapshot {
        return checkNotNull(snapshot)
    }

    /**
     * Renders with the default [GarminOptions]; throws for [LabelMode.SEPARATE] until M6.
     */
    override fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
    ): GarminRenderer {
        return createRenderer(style = style, selection = selection, config = config, options = GarminOptions())
    }

    /**
     * Like [createRenderer] with the Garmin [options], which [RenderKey] does not cover: a host
     * that varies them keys its tile cache itself. Throws when a map file changed since it was
     * added, for a level no map has, and for [LabelMode.SEPARATE] until M6.
     */
    fun createRenderer(
        style: MapStyle,
        selection: StyleSelection,
        config: RenderConfig,
        options: GarminOptions,
    ): GarminRenderer {
        require(style is GarminStyle) {
            "createRenderer(), not a Garmin style: ${style.id}"
        }
        val snapshot = current()
        val entries = snapshot.entries
        require(style !is GarminStyle.Embedded
            || entries.any { it.isFile(style.file) }
        ) {
            "createRenderer(), ${style.id} belongs to another map"
        }
        require(config.labelMode == LabelMode.IN_TILE) {
            "createRenderer(), label mode ${config.labelMode} not supported"
        }
        val sources = entries.map { entry ->
            // the bounds and style ids hold for the file as added: reopen after a map update
            entry.checkUnchanged("createRenderer()")
            val maps = entry.parsed()
            GarminRenderer.Source(
                maps = maps,
                bounds = maps.map { driver.toBounds(it.tre.header) },
                openReader = {
                    // workers open the file when they are first needed; it must still be the parsed one
                    entry.checkUnchanged("render()")
                    FileImgRandomAccessReader(entry.file)
                },
            )
        }
        val level = options.level
        require(level == null
            || sources.any { source -> source.maps.any { map -> map.tre.levels.levels.any { it.level == level } } }
        ) {
            "createRenderer(), no map has level $level"
        }
        return GarminRenderer(
            key = RenderKey(styleId = style.id, selection = selection, config = config),
            sources = sources,
            style = style,
            canvasFactory = driver.canvasFactory,
            options = options,
        )
    }

    override fun close() {
        // nothing open: the parsed maps hold no readers, renderers own theirs
    }

    /**
     * The files and what derives from them, replaced as a whole by [addMap].
     */
    private class Snapshot(
        private val driver: GarminDriver,
        val entries: List<Entry>,
    ) {

        val files: List<File> = entries.map { it.file }

        val unreadableMaps: List<UnreadableMap> = entries.flatMap { it.unreadable }

        // of the maps that parsed, which add has already done: a skipped map adds neither
        val bounds: GeoBounds = entries
            .flatMap { it.parsed() }
            .map { driver.toBounds(it.tre.header) }
            .reduce(GeoBounds::union)

        val levels: List<GarminLevel> = entries
            .flatMap { it.parsed() }
            .flatMap { map -> map.tre.levels.levels.filterNot { it.inherited } }
            .map { GarminLevel(level = it.level, bitsPerCoord = it.bitsPerCoord) }
            .distinctBy { it.level }
            .sortedBy { it.level }

        val styles: List<GarminStyle> = run {
            val typFile = entries.firstOrNull { it.summary.hasTyp }
            if (typFile == null) {
                listOf(GarminStyle.BuiltIn)
            } else {
                val day = GarminStyle.Embedded(file = typFile.file, lastModified = typFile.lastModified)
                // parsed on add, so this reads no file
                val hasNight = typFile.parsed().first().typStyles?.hasNight == true
                val night = GarminStyle.Embedded(file = typFile.file, lastModified = typFile.lastModified, isNight = true)
                listOfNotNull(day, night.takeIf { hasNight }, GarminStyle.BuiltIn)
            }
        }
    }

    private class Entry(
        val file: File,
        val lastModified: Long,
        val summary: ImgSummary,
    ) {

        private var parsed: List<ImgFile>? = null

        // set by the first parse, which add runs
        var unreadable: List<UnreadableMap> = emptyList()
            private set

        // a relative and an absolute path to one file are the same map
        fun isFile(other: File): Boolean {
            return file.absoluteFile.normalize() == other.absoluteFile.normalize()
        }

        fun checkUnchanged(caller: String) {
            check(file.lastModified() == lastModified) {
                "$caller, $file changed since it was added"
            }
        }

        // headers, sub-division trees and TYP; their shared readers are closed at once, as workers reopen
        @Synchronized
        fun parsed(): List<ImgFile> {
            parsed?.let { return it }
            val skipped = ArrayList<UnreadableMap>()
            val maps = ImgFile.openAll(
                onUnreadable = { mapName, cause ->
                    skipped += UnreadableMap(file = file, mapName = mapName, reason = cause.message ?: cause.javaClass.simpleName)
                },
            ) {
                FileImgRandomAccessReader(file)
            }
            maps.first().close()
            unreadable = skipped
            parsed = maps
            return maps
        }
    }
}

/**
 * A map of a file that does not parse, with the parser's reason.
 */
data class UnreadableMap(
    val file: File,
    val mapName: String,
    val reason: String,
)

/**
 * A TRE level of a map: lower levels are finer, `bitsPerCoord` is its coordinate precision.
 */
data class GarminLevel(
    val level: Int,
    val bitsPerCoord: Int,
)

/**
 * Garmin-only render settings for development tools; Locus' type toggles come with M8.2.
 *
 * @param objectKinds [ObjectKind] bits to draw
 * @param level TRE level to draw in the maps that have it; null, and every map without it, picks
 *   the level matching the zoom
 */
data class GarminOptions(
    val antiAliasing: Boolean = true,
    val objectKinds: Int = ObjectKind.ALL,
    val level: Int? = null,
)
