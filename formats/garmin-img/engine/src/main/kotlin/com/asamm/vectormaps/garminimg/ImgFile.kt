/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg

import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader
import com.asamm.vectormaps.garminimg.model.MapObject
import com.asamm.vectormaps.garminimg.model.ObjectKind
import com.asamm.vectormaps.garminimg.parser.ImgArchive
import com.asamm.vectormaps.garminimg.parser.LblHeader
import com.asamm.vectormaps.garminimg.parser.LblSubFile
import com.asamm.vectormaps.garminimg.parser.NetHeader
import com.asamm.vectormaps.garminimg.parser.NetSubFile
import com.asamm.vectormaps.garminimg.parser.RgnHeader
import com.asamm.vectormaps.garminimg.parser.RgnSubFile
import com.asamm.vectormaps.garminimg.parser.SubDivision
import com.asamm.vectormaps.garminimg.parser.SubDivisionParser
import com.asamm.vectormaps.garminimg.parser.SubDivisionTree
import com.asamm.vectormaps.garminimg.parser.TreHeader
import com.asamm.vectormaps.garminimg.parser.TreLevels
import com.asamm.vectormaps.garminimg.parser.TreSubFile
import com.asamm.vectormaps.garminimg.parser.TypHeader
import com.asamm.vectormaps.garminimg.parser.TypSubFile

/**
 * Top-level API for reading a Garmin IMG container.
 *
 * Replaces the Java predecessor's `ImgFilesBag` + `ImgFileBag` orchestration
 * pair with a single Kotlin-idiomatic entry point. Open an IMG by handing
 * three reader instances over the same byte source (one per sub-file we'll
 * stream concurrently — TRE / RGN / LBL); the resulting [ImgFile] exposes:
 *
 * - parsed headers for every sub-file present (TRE, RGN, LBL, NET, TYP)
 * - the sub-division tree (with TRE7 extended-types attached and RGN ends
 *   back-filled)
 * - a [readMap] sequence emitting every [MapObject] in the IMG
 *
 * The `lblReader` is mandatory because every label resolution requires it;
 * the others are optional and gated by the IMG's actual content (no NET in
 * the IMG → no `netHeader` / `netReader` to pass).
 *
 * # Threading
 *
 * **One `ImgFile` per worker thread.** Each [ImgRandomAccessReader] tracks
 * its own logical position, so two threads driving the *same* `ImgFile`
 * race on the seek positions and silently produce garbled output. Give
 * each worker its own copy with [reopen]: it shares the parsed headers,
 * sub-division tree and TYP, which are read-only, and reads through its
 * own readers. Maps sharing readers, from one [openAll] or [reopenOn] over
 * the same containers, stay on one thread together and close once.
 *
 * @param typStyles parsed TYP body — `(type, subType)` → resolved styles for the polygon and
 *   line sections. Populated when the IMG has a TYP sub-file and its body parses successfully;
 *   null otherwise (and the renderer falls back to
 *   [com.asamm.vectormaps.garminimg.style.InternalStyles]).
 */
class ImgFile internal constructor(
    val archive: ImgArchive,
    val tre: TreData,
    val rgn: RgnData,
    val lbl: LblData,
    val net: NetData?,
    val typ: TypHeader?,
    val typStyles: com.asamm.vectormaps.garminimg.parser.TypStyleTable?,
    /**
     * File name of this map's sub-files in [archive]; a multi-map IMG holds several.
     */
    val mapName: String,
    /**
     * The readers [close] closes, shared by all maps of one [openAll]. Empty
     * for the explicit-readers overload of [open] and for [reopenOn] — their
     * callers keep ownership and manage the readers themselves.
     */
    private val ownedReaders: List<ImgRandomAccessReader> = emptyList(),
    // shared with the reopened copies, so the tree is indexed once per parsed map
    private val levelIndex: LevelIndex = LevelIndex(tre.tree, tre.levels),
) : AutoCloseable {

    /**
     * Releases the readers this `ImgFile` owns. Safe to call on the
     * explicit-readers overload too — `ownedReaders` is empty there
     * so close is a no-op. Idempotent if the underlying readers'
     * close is idempotent (the file-backed reader's is).
     */
    override fun close() {
        closeQuietly(ownedReaders)
    }

    /**
     * A copy reading through new readers from `byteSourceFactory`, which shares this file's parsed
     * headers, sub-division tree and TYP; closing either closes only its own readers.
     */
    fun reopen(byteSourceFactory: () -> ImgRandomAccessReader): ImgFile {
        val owned = mutableListOf<ImgRandomAccessReader>()
        return reopen(owned = owned, container = { byteSourceFactory().also { owned += it } })
    }

    /**
     * Like [reopen], over containers the caller owns and closes: `container` returns the reader for
     * a sub-file type, which the maps of one archive may share, one thread at a time.
     */
    fun reopenOn(container: (fileType: String) -> ImgRandomAccessReader): ImgFile {
        return reopen(owned = mutableListOf(), container = container)
    }

    private fun reopen(
        owned: MutableList<ImgRandomAccessReader>,
        container: (fileType: String) -> ImgRandomAccessReader,
    ): ImgFile {
        try {
            fun subFile(type: String): ImgRandomAccessReader {
                val entry = archive.find(mapName, type) ?: error("IMG map $mapName has no $type sub-file")
                return archive.openSubFile(entry, container(type))
            }
            val treReader = subFile("TRE")
            val rgnReader = subFile("RGN")
            val lblReader = subFile("LBL")
            val netData = net?.let { NetData(reader = subFile("NET"), header = it.header) }
            return ImgFile(
                archive = archive,
                tre = TreData(reader = treReader, header = tre.header, levels = tre.levels, tree = tre.tree),
                rgn = RgnData(reader = rgnReader, header = rgn.header),
                lbl = LblData(reader = lblReader, header = lbl.header),
                net = netData,
                typ = typ,
                typStyles = typStyles,
                mapName = mapName,
                ownedReaders = owned,
                levelIndex = levelIndex,
            )
        } catch (e: Throwable) {
            closeQuietly(owned)
            throw e
        }
    }

    /**
     * Internal city-name resolver — exposed so test harnesses (the
     * dump-diff rig) can pass it back into [RgnSubFile.parseSubDivision]
     * directly. Production callers should use [readMap] /
     * [readMapInBounds], which already wire it for them.
     */
    internal val cityResolverInternal: LblSubFile.IndexedPointLabelResolver
        get() = cityResolver

    /**
     * Resolves a `(subDivisionIndex, indexedPointIndex)` pair to the
     * label of that indexed point — used by LBL POI city resolution
     * when a city encodes its name as a point reference rather than a
     * direct LBL offset. Runs the tree lookup only when a POI asks
     * for it.
     */
    private val cityResolver: LblSubFile.IndexedPointLabelResolver =
        LblSubFile.IndexedPointLabelResolver { sdIndex, pointIndex ->
            val sd = tre.tree.byIndex[sdIndex] ?: return@IndexedPointLabelResolver null
            RgnSubFile.readIndexedPointLabel(
                reader = rgn.reader,
                header = rgn.header,
                subDivision = sd,
                pointIndex = pointIndex,
                lblReader = lbl.reader,
                lblHeader = lbl.header,
            )
        }

    /**
     * Walks every sub-division and yields every [MapObject] in draw order
     * (polygons first, then extended polygons, then polylines, then points).
     *
     * @param objectKindFilter bitmask of [ObjectKind] flags. Defaults to
     *   `ObjectKind.ALL`.
     */
    fun readMap(objectKindFilter: Int = ObjectKind.ALL): Sequence<MapObject> {
        return sequence {
            for (subDivision in tre.tree.all()) {
                if (!subDivisionHasAnyData(subDivision)) {
                    continue
                }
                yieldAll(
                    RgnSubFile.parseSubDivision(
                        reader = rgn.reader,
                        header = rgn.header,
                        subDivision = subDivision,
                        levels = tre.levels,
                        lblReader = lbl.reader,
                        lblHeader = lbl.header,
                        netReader = net?.reader,
                        netHeader = net?.header,
                        cityResolver = cityResolver,
                        objectKindFilter = objectKindFilter,
                    ),
                )
            }
        }
    }

    /**
     * Walks just the sub-divisions whose extent overlaps the given Garmin
     * fixed-point bounding box. Useful for tile rendering where you only want
     * features near the visible viewport. Inclusive on all four bounds.
     *
     * @param poiDetails false reads only a POI's name, without the address and city lookups.
     */
    fun readMapInBounds(
        minLong: Int,
        maxLong: Int,
        minLat: Int,
        maxLat: Int,
        objectKindFilter: Int = ObjectKind.ALL,
        levelFilter: Int? = null,
        poiDetails: Boolean = true,
    ): Sequence<MapObject> {
        return sequence {
            val candidates = if (levelFilter == null) {
                tre.tree.all().filter { sd ->
                    subDivisionHasAnyData(sd)
                        && levelIndex.intersects(sd, minLong, maxLong, minLat, maxLat)
                }
            } else {
                levelIndex.withData(levelFilter, minLong, maxLong, minLat, maxLat).asSequence()
            }
            for (subDivision in candidates) {
                yieldAll(
                    RgnSubFile.parseSubDivision(
                        reader = rgn.reader,
                        header = rgn.header,
                        subDivision = subDivision,
                        levels = tre.levels,
                        lblReader = lbl.reader,
                        lblHeader = lbl.header,
                        netReader = net?.reader,
                        netHeader = net?.header,
                        cityResolver = cityResolver,
                        objectKindFilter = objectKindFilter,
                        poiDetails = poiDetails,
                    ),
                )
            }
        }
    }

    /**
     * Levels that have at least one sub-division in the tree, data or not.
     */
    internal val materialisedLevels: Set<Int>
        get() = levelIndex.materialisedLevels

    /**
     * True when `level` has a sub-division with drawable data overlapping the bounds.
     */
    internal fun hasDataInBounds(level: Int, minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): Boolean {
        return levelIndex.hasData(level, minLong, maxLong, minLat, maxLat)
    }

    /**
     * TRE-derived state: header, levels, and the sub-division tree.
     * Non-data class so future fields don't break `.copy()` ABI for
     * consumers; the underlying [reader] is `internal` so a consumer
     * can't accidentally race the parser's seek pointer.
     */
    class TreData internal constructor(
        internal val reader: ImgRandomAccessReader,
        val header: TreHeader,
        val levels: TreLevels,
        val tree: SubDivisionTree,
    )

    /** RGN-derived state. See [TreData] for the non-data + internal-reader rationale. */
    class RgnData internal constructor(
        internal val reader: ImgRandomAccessReader,
        val header: RgnHeader,
    )

    /** LBL-derived state. See [TreData] for the non-data + internal-reader rationale. */
    class LblData internal constructor(
        internal val reader: ImgRandomAccessReader,
        val header: LblHeader,
    )

    /** NET-derived state (when the IMG includes routing data; rare). */
    class NetData internal constructor(
        internal val reader: ImgRandomAccessReader,
        val header: NetHeader,
    )

    companion object {

        /**
         * Opens an IMG container and parses every sub-file present.
         *
         * @param archiveReader reader positioned at the start of the IMG container.
         *   Used to parse the header + FAT.
         * @param treReader reader for the TRE sub-file (logical view, supplied by
         *   [ImgArchive.openSubFile]). Required — every IMG has a TRE.
         * @param rgnReader reader for the RGN sub-file. Required.
         * @param lblReader reader for the LBL sub-file. Required.
         * @param netReader reader for the NET sub-file. Pass null to skip.
         * @param typReader reader for the TYP sub-file. Pass null to skip.
         */
        fun open(
            archiveReader: ImgRandomAccessReader,
            treReader: ImgRandomAccessReader,
            rgnReader: ImgRandomAccessReader,
            lblReader: ImgRandomAccessReader,
            netReader: ImgRandomAccessReader? = null,
            typReader: ImgRandomAccessReader? = null,
        ): ImgFile {
            // Explicit-readers form: caller owns and closes the readers.
            // Pass an empty owned-readers list so [ImgFile.close] is a
            // no-op for these instances.
            val archive = ImgArchive.open(archiveReader)
            val (typHeader, typStyles) = readTyp(typReader)
            return openInternal(
                archive = archive,
                mapName = archive.mapNames().firstOrNull() ?: error("IMG has no map with TRE, RGN and LBL"),
                treReader = treReader,
                rgnReader = rgnReader,
                lblReader = lblReader,
                netReader = netReader,
                typHeader = typHeader,
                typStyles = typStyles,
                ownedReaders = emptyList(),
            )
        }

        /**
         * Convenience: opens an IMG using a [byteSourceFactory] that produces
         * a fresh [ImgRandomAccessReader] each time it's invoked. The
         * factory is called exactly once per sub-file plus once for the
         * archive scan — 4-6 calls total, never re-allocates the
         * archive reader. The returned `ImgFile` owns every reader it
         * was handed; calling [ImgFile.close] (or wrapping the open in
         * `.use { … }`) releases them all.
         *
         * Useful for the common case where the IMG bytes live in a
         * single ByteArray or a file on disk and the caller doesn't
         * want to hand-build readers.
         */
        fun open(byteSourceFactory: () -> ImgRandomAccessReader): ImgFile {
            // Collect every reader we create here so we can own them
            // through ImgFile and close on .close(). If anything below
            // throws, the catch closes whatever we built so far before
            // re-raising.
            val owned = mutableListOf<ImgRandomAccessReader>()
            try {
                // the archive keeps no reader after the FAT parse
                val archive = byteSourceFactory().use { ImgArchive.open(it) }
                // the first map, its sub-files by name, as reopen finds them
                val mapName = archive.mapNames().firstOrNull() ?: error("IMG has no map with TRE, RGN and LBL")
                val tre = checkNotNull(archive.find(mapName, "TRE"))
                val rgn = checkNotNull(archive.find(mapName, "RGN"))
                val lbl = checkNotNull(archive.find(mapName, "LBL"))
                val net = archive.find(mapName, "NET")
                val typ = archive.findFirst("TYP")

                fun rawForSubFile(): ImgRandomAccessReader {
                    return byteSourceFactory().also { owned += it }
                }
                val treReader = archive.openSubFile(tre, rawForSubFile())
                val rgnReader = archive.openSubFile(rgn, rawForSubFile())
                val lblReader = archive.openSubFile(lbl, rawForSubFile())
                val netReader = net?.let { archive.openSubFile(it, rawForSubFile()) }
                val (typHeader, typStyles) = readTyp(typ?.let { archive.openSubFile(it, rawForSubFile()) })

                return openInternal(
                    archive = archive,
                    mapName = tre.fileName,
                    treReader = treReader,
                    rgnReader = rgnReader,
                    lblReader = lblReader,
                    netReader = netReader,
                    typHeader = typHeader,
                    typStyles = typStyles,
                    ownedReaders = owned,
                )
            } catch (e: Throwable) {
                closeQuietly(owned)
                throw e
            }
        }

        /**
         * Opens every map of a multi-map IMG, such as a gmapsupp with one map per tile; a single-map
         * IMG gives one. The archive's first TYP, decoded once, styles all its maps, also those of
         * another product family; the maps share one reader per sub-file type, so closing one map
         * closes them all.
         *
         * @param onUnreadable receives each map that does not parse, which is then skipped; null
         *   throws on the first. Throws when no map is left. A read error mid-parse counts as such
         *   a map, as the reader's own end-of-data checks throw the same `IOException`.
         */
        fun openAll(
            onUnreadable: ((mapName: String, cause: Exception) -> Unit)? = null,
            byteSourceFactory: () -> ImgRandomAccessReader,
        ): List<ImgFile> {
            val archive = byteSourceFactory().use { ImgArchive.open(it) }
            val names = archive.mapNames()
            require(names.isNotEmpty()) {
                "IMG has no map with TRE, RGN and LBL sub-files"
            }
            val (typHeader, typStyles) = archive.findFirst("TYP")?.let { entry ->
                byteSourceFactory().use { readTyp(archive.openSubFile(entry, it)) }
            } ?: (null to null)
            // a few handles for the whole archive, however many maps it holds
            val shared = mutableListOf<ImgRandomAccessReader>()
            val containers = HashMap<String, ImgRandomAccessReader>()
            try {
                // opened before the maps, so a file that does not open fails as such, not as damage
                for (type in SUB_FILE_TYPES) {
                    if (names.any { archive.find(it, type) != null }) {
                        containers[type] = byteSourceFactory().also { shared += it }
                    }
                }
                var firstFailure: Exception? = null
                val maps = names.mapNotNull { name ->
                    fun subFile(type: String): ImgRandomAccessReader? {
                        val entry = archive.find(name, type) ?: return null
                        return archive.openSubFile(entry, checkNotNull(containers[type]))
                    }
                    try {
                        openInternal(
                            archive = archive,
                            mapName = name,
                            treReader = checkNotNull(subFile("TRE")),
                            rgnReader = checkNotNull(subFile("RGN")),
                            lblReader = checkNotNull(subFile("LBL")),
                            netReader = subFile("NET"),
                            typHeader = typHeader,
                            typStyles = typStyles,
                            ownedReaders = shared,
                        )
                    } catch (e: Exception) {
                        if (onUnreadable == null) {
                            throw e
                        }
                        onUnreadable(name, e)
                        firstFailure = firstFailure ?: e
                        null
                    }
                }
                if (maps.isEmpty()) {
                    throw IllegalArgumentException("IMG has no readable map of ${names.size}", firstFailure)
                }
                return maps
            } catch (e: Throwable) {
                closeQuietly(shared)
                throw e
            }
        }

        /**
         * TYP header and styles, both null when the TYP is absent or does not parse: TYP is optional
         * styling, so a malformed one drops the TYP, not the map.
         */
        private fun readTyp(
            typReader: ImgRandomAccessReader?,
        ): Pair<TypHeader?, com.asamm.vectormaps.garminimg.parser.TypStyleTable?> {
            if (typReader == null) {
                return null to null
            }
            val header = try {
                TypSubFile.parseHeader(typReader)
            } catch (_: Exception) {
                return null to null
            }
            val styles = try {
                com.asamm.vectormaps.garminimg.parser.TypBodyParser.parse(typReader, header)
            } catch (_: Exception) {
                null
            }
            return header to styles
        }

        /**
         * Shared implementation for the open functions.
         */
        private fun openInternal(
            archive: ImgArchive,
            mapName: String,
            treReader: ImgRandomAccessReader,
            rgnReader: ImgRandomAccessReader,
            lblReader: ImgRandomAccessReader,
            netReader: ImgRandomAccessReader?,
            typHeader: TypHeader?,
            typStyles: com.asamm.vectormaps.garminimg.parser.TypStyleTable?,
            ownedReaders: List<ImgRandomAccessReader>,
        ): ImgFile {
            val treHeader = TreSubFile.parseHeader(treReader)
            val rgnHeader = RgnSubFile.parseHeader(rgnReader)
            val lblHeader = LblSubFile.parseHeader(lblReader)
            val netHeader = netReader?.let { NetSubFile.parseHeader(it) }

            val levels = TreSubFile.parseLevels(treReader)
            val tree = SubDivisionParser.parse(
                reader = treReader,
                levels = levels,
                extendedTypes = treHeader.extendedTypes,
                rgnInfo = RgnSubFile.endInfo(rgnHeader),
            )

            return ImgFile(
                archive = archive,
                tre = TreData(treReader, treHeader, levels, tree),
                rgn = RgnData(rgnReader, rgnHeader),
                lbl = LblData(lblReader, lblHeader),
                net = netHeader?.let { h -> NetData(netReader, h) },
                typ = typHeader,
                typStyles = typStyles,
                mapName = mapName,
                ownedReaders = ownedReaders,
            )
        }
    }
}

/**
 * Indexes over a sub-division tree, built on first use and shared by an [ImgFile] and its
 * [ImgFile.reopen] copies.
 */
internal class LevelIndex(
    private val tree: SubDivisionTree,
    private val levels: TreLevels,
) {

    val materialisedLevels: Set<Int> by lazy {
        tree.all().mapTo(HashSet()) { it.level }
    }

    // tile renders query one level each; walking the whole tree per tile is the cost (PR #28)
    private val withDataByLevel: Map<Int, LevelBoxes> by lazy {
        tree.all()
            .filter { subDivisionHasAnyData(it) }
            .groupBy { it.level }
            .mapValues { (level, subDivisions) -> LevelBoxes(subDivisions, levels.bitsPerCoordOf(level)) }
    }

    /**
     * Sub-divisions of `level` with drawable data whose extent overlaps the bounds, in tree order.
     */
    fun withData(level: Int, minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): List<SubDivision> {
        val boxes = withDataByLevel[level] ?: return emptyList()
        return boxes.overlapping(minLong, maxLong, minLat, maxLat)
    }

    /**
     * True when [withData] would return any; stops at the first.
     */
    fun hasData(level: Int, minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): Boolean {
        val boxes = withDataByLevel[level] ?: return false
        return boxes.anyOverlapping(minLong, maxLong, minLat, maxLat)
    }

    fun intersects(sd: SubDivision, minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): Boolean {
        val bits = levels.bitsPerCoordOf(sd.level) ?: return true
        val scale = scaleOf(bits)
        return overlaps(
            minLon = sd.longitudeCenter - sd.halfWidth * scale,
            maxLon = sd.longitudeCenter + sd.halfWidth * scale,
            minLat = sd.latitudeCenter - sd.halfHeight * scale,
            maxLat = sd.latitudeCenter + sd.halfHeight * scale,
            minLong = minLong,
            maxLong = maxLong,
            viewMinLat = minLat,
            viewMaxLat = maxLat,
        )
    }
}

/**
 * Extents of one level's sub-divisions in Garmin units, in primitive arrays a tile scans without
 * touching the objects. The half sizes count in units of the level, `1 shl (24 - bitsPerCoord)`
 * Garmin units each; a level without a record overlaps everything.
 */
private class LevelBoxes(
    private val subDivisions: List<SubDivision>,
    bitsPerCoord: Int?,
) {

    // min lon, max lon, min lat, max lat per sub-division
    private val boxes: LongArray? = bitsPerCoord?.let { bits ->
        val scale = scaleOf(bits)
        val array = LongArray(subDivisions.size * 4)
        for ((i, sd) in subDivisions.withIndex()) {
            val halfW = sd.halfWidth * scale
            val halfH = sd.halfHeight * scale
            array[i * 4] = sd.longitudeCenter - halfW
            array[i * 4 + 1] = sd.longitudeCenter + halfW
            array[i * 4 + 2] = sd.latitudeCenter - halfH
            array[i * 4 + 3] = sd.latitudeCenter + halfH
        }
        array
    }

    fun overlapping(minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): List<SubDivision> {
        if (boxes == null) {
            return subDivisions
        }
        return subDivisions.filterIndexed { i, _ -> overlapsAt(i, minLong, maxLong, minLat, maxLat) }
    }

    fun anyOverlapping(minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): Boolean {
        if (boxes == null) {
            return subDivisions.isNotEmpty()
        }
        return subDivisions.indices.any { i -> overlapsAt(i, minLong, maxLong, minLat, maxLat) }
    }

    private fun overlapsAt(i: Int, minLong: Int, maxLong: Int, minLat: Int, maxLat: Int): Boolean {
        val boxes = checkNotNull(boxes)
        return overlaps(
            minLon = boxes[i * 4],
            maxLon = boxes[i * 4 + 1],
            minLat = boxes[i * 4 + 2],
            maxLat = boxes[i * 4 + 3],
            minLong = minLong,
            maxLong = maxLong,
            viewMinLat = minLat,
            viewMaxLat = maxLat,
        )
    }
}

// Garmin units per level unit
private fun scaleOf(bitsPerCoord: Int): Long {
    return 1L shl (24 - bitsPerCoord).coerceAtLeast(0)
}

// rectangle overlap, edges included
private fun overlaps(
    minLon: Long,
    maxLon: Long,
    minLat: Long,
    maxLat: Long,
    minLong: Int,
    maxLong: Int,
    viewMinLat: Int,
    viewMaxLat: Int,
): Boolean {
    return maxLon >= minLong
        && minLon <= maxLong
        && maxLat >= viewMinLat
        && minLat <= viewMaxLat
}

/**
 * True when the sub-division has at least one byte of data the renderer could draw — either plain
 * (POINT / POLYLINE / POLYGON) or extended (TRE7 polygon / polyline / POI). Extended-only
 * sub-divisions have `objectTypes == 0` because that byte only encodes the four plain kinds; we
 * still need to descend into them to read buildings & friends.
 */
private fun subDivisionHasAnyData(sd: SubDivision): Boolean {
    return sd.objectTypes != 0
        || sd.extendedPolygonEnd > sd.extendedPolygonOffset
        || sd.extendedPolylineEnd > sd.extendedPolylineOffset
        || sd.extendedPoiEnd > sd.extendedPoiOffset
}

// the sub-files a map reads through the shared containers
private val SUB_FILE_TYPES = listOf("TRE", "RGN", "LBL", "NET")

// a failure closing one reader does not stop the others
private fun closeQuietly(readers: List<ImgRandomAccessReader>) {
    for (reader in readers) {
        try {
            reader.close()
        } catch (_: Throwable) {
            // keep closing the others
        }
    }
}
