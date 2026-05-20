/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.utils.io.ImgRandomAccessReader

/**
 * Parses the TRE sub-divisions section into an immutable tree of [SubDivision]s.
 *
 * # On-disk layout
 *
 * The sub-divisions section is a flat byte run. Every record at a non-leaf zoom
 * level is 16 bytes wide; every record at the leaf level (= [TreLevels.minLevel])
 * is 14 bytes wide (no `indexNextLevel` field). Records for one level are
 * contiguous; the last record of each level has the high bit of its `width`
 * field set as a "last in level" marker.
 *
 * Layout per record:
 * - bytes 0..2 — RGN data offset (UInt24)
 * - byte 3 — `objectTypes`. Low nibble is dataOffset overflow (×2^24), high
 *   nibble is the [ObjectKind]-bit set.
 * - bytes 4..6 — `longitudeCenter` (Int24)
 * - bytes 7..9 — `latitudeCenter` (Int24)
 * - bytes 10..11 — `halfWidth` UInt16 (high bit = last-in-level)
 * - bytes 12..13 — `halfHeight` UInt16
 * - bytes 14..15 — `indexNextLevel` UInt16 (FULL records only)
 *
 * # Tree assembly
 *
 * The Java predecessor walks levels top-down: at the maxLevel (coarsest), records
 * sit at indices 1..N1; each parent's `indexNextLevel` points at its first child
 * at the next-present finer level; recursion ends at minLevel (where records are
 * 14-byte SMALL).
 *
 * A flat by-index lookup is built alongside the tree; that lets the
 * `assignDataEnds` pass back-fill RGN end offsets from neighbour information
 * without re-walking the tree.
 *
 * # End-offset back-fill
 *
 * For each non-last sub-division `s`, `s.dataEnd = nextRecord.dataOffset`. The
 * last sub-division's `dataEnd` requires the RGN sub-file's plain-data end,
 * passed in by the caller. Same scheme for extended-types ends. When [rgnInfo]
 * is null (no RGN reader available yet), the last record's `dataEnd` stays 0
 * — call sites that depend on it must defer this work.
 */
object SubDivisionParser {

    /**
     * Sub-file offset of the sub-divisions descriptor: UInt32 offset + UInt32
     * length pointing to the records section.
     */
    private const val OFFSET_DESCRIPTOR: Long = 0x29L

    private const val FULL_RECORD_SIZE: Int = 16
    private const val SMALL_RECORD_SIZE: Int = 14

    /**
     * High-nibble mask of the `objectTypes` byte. The low nibble is the
     * dataOffset overflow (each unit = 2^24 bytes); the high nibble carries the
     * actual [ObjectKind] flags.
     */
    private const val OBJECT_TYPES_DATAOFFSET_OVERFLOW_MASK: Int = 0x0F

    /**
     * High bit of the `width` UInt16 — "last sub-division in this level".
     */
    private const val WIDTH_LAST_BIT: Int = 0x8000

    /**
     * Parses the sub-divisions section into a tree. Returns the root list (the
     * sub-divisions at [TreLevels.maxLevel]); each carries its children
     * recursively. The flat [byIndex] map is also returned for callers that
     * want O(1) lookup (e.g. RGN parsing).
     *
     * @param reader logical sub-file reader for the TRE
     * @param levels the parsed [TreLevels]
     * @param extendedTypes the parsed [ExtendedTypesInfo], or null if absent
     * @param rgnInfo end offsets from RGN sub-file, used to back-fill the very
     *   last sub-division's data ranges. Pass null if RGN hasn't been parsed
     *   yet — the last record's `dataEnd` will be 0.
     */
    fun parse(
        reader: ImgRandomAccessReader,
        levels: TreLevels,
        extendedTypes: ExtendedTypesInfo? = null,
        rgnInfo: RgnEndInfo? = null,
    ): SubDivisionTree {
        if (levels.levels.isEmpty()) {
            return SubDivisionTree(roots = emptyList(), byIndex = emptyMap())
        }

        reader.seek(OFFSET_DESCRIPTOR)
        val sectionOffset = reader.readUInt32()
        val sectionLength = reader.readUInt32()
        // Bound the descriptor against the sub-file. A corrupt header
        // carrying sectionLength = 2^32-1 would otherwise drive the
        // walkLeafChildren loop billions of iterations before the
        // record reads fail, growing `parent.children` unbounded.
        require(sectionOffset + sectionLength <= reader.size) {
            "TRE sub-divisions section [0x${sectionOffset.toString(16)}, " +
                "0x${(sectionOffset + sectionLength).toString(16)}) past EOF " +
                "(size=${reader.size})"
        }
        val sectionEnd = sectionOffset + sectionLength

        val state = ParseState(
            reader = reader,
            sectionOffset = sectionOffset,
            sectionEnd = sectionEnd,
            levels = levels,
            extendedTypes = extendedTypes,
        )

        val rootBuilders = mutableListOf<SubDivisionBuilder>()
        val maxLevel = levels.maxLevel
        // Top-level records start at index 1 by convention (the implicit "root"
        // counts as index 0).
        state.parseLevel(
            level = maxLevel,
            startIndex = 1,
            recordSize = FULL_RECORD_SIZE,
            collectInto = rootBuilders,
        )

        // Descend into finer levels. Each parent's indexNextLevel points at its
        // first child at the next-present-level; siblings are read until the
        // "last in level" bit appears, walking the same on-disk run.
        state.expandChildren(rootBuilders, parentLevel = maxLevel)

        // Second pass: parse the leaf level (typically level 0) using
        // SMALL records. mkgmap-style IMGs commonly emit their finest
        // detail level here. Mirrors libGarminImg's parseLastSubDivision.
        state.parseLastSubDivisions(rootBuilders)

        // Back-fill end offsets from neighbour records.
        state.assignEnds(rgnInfo)

        val roots = rootBuilders.map { it.freeze() }
        val byIndex = state.builders.values.associate { it.index to it.freeze() }
        return SubDivisionTree(roots = roots, byIndex = byIndex)
    }

    private class ParseState(
        val reader: ImgRandomAccessReader,
        val sectionOffset: Long,
        val sectionEnd: Long,
        val levels: TreLevels,
        val extendedTypes: ExtendedTypesInfo?,
    ) {

        /** Index → builder, populated as records are parsed. */
        val builders: MutableMap<Int, SubDivisionBuilder> = LinkedHashMap()

        /**
         * Index after which records become 14-byte SMALL. Updated each time we
         * cross into the leaf level. Mirrors the Java `lastBigIndex`.
         */
        var lastBigIndex: Int = 0

        /**
         * Parses a contiguous run of records starting at `startIndex` for
         * `level`. Each record's "last in level" bit terminates the run.
         */
        fun parseLevel(
            level: Int,
            startIndex: Int,
            recordSize: Int,
            collectInto: MutableList<SubDivisionBuilder>,
        ) {
            if (startIndex == 0) {
                return
            }
            var index = startIndex
            val basePos = recordPosition(startIndex, recordSize)
            reader.seek(basePos)

            while (reader.position() < sectionEnd) {
                val record = parseOneRecord(index = index, level = level, recordSize = recordSize)
                builders[index] = record
                collectInto.add(record)
                if (record.lastInLevel) {
                    break
                }
                index++
            }
            if (recordSize == FULL_RECORD_SIZE && lastBigIndex < index) {
                lastBigIndex = index
            }
        }

        /**
         * Recursively expands child sub-divisions. For each `parent` record at
         * `parentLevel`, we descend into the next *present* finer level
         * starting at `parent.indexNextLevel`. When the next-present level is
         * the leaf level ([TreLevels.minLevel]), records are SMALL (14 bytes).
         */
        fun expandChildren(
            parents: List<SubDivisionBuilder>,
            parentLevel: Int,
        ) {
            // Skip levels that aren't recorded in the levels section.
            // Note: the leaf level (typically 0) is parsed in a separate
            // pass via [parseLastSubDivisions]; this loop only walks the
            // FULL-record levels above it.
            var childLevel = parentLevel - 1
            while (childLevel > 0 && !levels.isPresent(childLevel)) {
                childLevel--
            }
            if (childLevel <= 0) {
                return
            }

            val isLeafLevel = childLevel == levels.minLevel
            val recordSize = if (isLeafLevel) SMALL_RECORD_SIZE else FULL_RECORD_SIZE

            for (parent in parents) {
                val firstChildIndex = parent.indexNextLevel
                if (firstChildIndex == 0) {
                    continue
                }
                val children = mutableListOf<SubDivisionBuilder>()
                parseLevel(
                    level = childLevel,
                    startIndex = firstChildIndex,
                    recordSize = recordSize,
                    collectInto = children,
                )
                parent.children.addAll(children)
                if (!isLeafLevel) {
                    expandChildren(children, parentLevel = childLevel)
                }
            }
        }

        /**
         * Walks the existing tree and reads the leaf level (typically
         * level=0) using 14-byte SMALL records. The leaf is *not* part
         * of the recursive [expandChildren] pass — its records sit past
         * `lastBigIndex` in the section and need the SMALL-record
         * record-size formula to position the reader correctly.
         *
         * Mirrors `TreSubFile.parseLastSubDivision` in the legacy Java
         * parser. For each parent at the level just above the leaf, we
         * follow [SubDivisionBuilder.indexNextLevel] into the SMALL
         * region; siblings stop when the "last in level" bit appears.
         */
        fun parseLastSubDivisions(roots: List<SubDivisionBuilder>) {
            val leafLevel = levels.minLevel
            // mkgmap conventionally emits the leaf at level 0. If the
            // smallest level is already above 0, there's no SMALL-record
            // pass to do — every level was covered by expandChildren.
            if (!levels.isPresent(leafLevel)) {
                return
            }
            // Java's parseLastSubDivision returns early if leafLevel < 1
            // would be reachable through the FULL-records descent (e.g.
            // an IMG whose smallest level table entry is level 1).
            // Walk the tree top-down; for each node whose direct child
            // level is the leaf, parse SMALL records.
            walkLeafChildren(roots)
        }

        private fun walkLeafChildren(parents: List<SubDivisionBuilder>) {
            val leafLevel = levels.minLevel
            for (parent in parents) {
                val nextIndex = parent.indexNextLevel
                if (nextIndex == 0) {
                    continue
                }
                if (parent.children.isNotEmpty() &&
                    parent.children.first().level > leafLevel
                ) {
                    walkLeafChildren(parent.children)
                    continue
                }
                if (parent.children.isNotEmpty()) {
                    // Already at leaf level; no SMALL pass needed for
                    // this branch.
                    continue
                }
                // Parent is the level just above the leaf and has no
                // children parsed yet — read SMALL records starting at
                // nextIndex.
                val basePos = recordPosition(nextIndex, SMALL_RECORD_SIZE)
                if (basePos < sectionOffset || basePos >= sectionEnd) {
                    continue
                }
                reader.seek(basePos)
                var index = nextIndex
                while (reader.position() < sectionEnd) {
                    val record = parseOneRecord(
                        index = index,
                        level = leafLevel,
                        recordSize = SMALL_RECORD_SIZE,
                    )
                    builders[index] = record
                    parent.children.add(record)
                    if (record.lastInLevel) {
                        break
                    }
                    index++
                }
            }
        }

        /**
         * Reads exactly one sub-division record at the reader's current position
         * and returns a builder. Advances the reader by `recordSize` bytes.
         */
        private fun parseOneRecord(
            index: Int,
            level: Int,
            recordSize: Int,
        ): SubDivisionBuilder {
            var dataOffset = reader.readUInt24().toLong() and 0xFFFFFFL
            val objectTypesByte = reader.readByte()
            // Low nibble is dataOffset overflow at 2^24-byte granularity. The Java
            // accumulates `dataOffset += (objectTypesByte & 0xf) * (1 << 24)`.
            val overflow = (objectTypesByte and OBJECT_TYPES_DATAOFFSET_OVERFLOW_MASK).toLong()
            dataOffset += overflow shl 24
            val longitudeCenter = reader.readInt24()
            val latitudeCenter = reader.readInt24()
            val widthRaw = reader.readUInt16()
            val lastInLevel = (widthRaw and WIDTH_LAST_BIT) != 0
            val halfWidth = widthRaw and WIDTH_LAST_BIT.inv() and 0xFFFF
            val halfHeight = reader.readUInt16()
            val indexNextLevel = if (recordSize >= FULL_RECORD_SIZE) {
                reader.readUInt16()
            } else {
                0
            }

            val builder = SubDivisionBuilder(
                index = index,
                level = level,
                dataOffset = dataOffset,
                objectTypes = objectTypesByte,
                longitudeCenter = longitudeCenter,
                latitudeCenter = latitudeCenter,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                indexNextLevel = indexNextLevel,
                lastInLevel = lastInLevel,
            )

            // TRE7 extended-types per-sub-division block (see TreSubFile docs).
            // Each extended-types record is `extendedTypes.recordSize` bytes wide,
            // laid out at offset `extendedTypes.offset + (extIndex - 1) * recordSize`
            // where `extIndex = index - decalage`. The body is up to 3 UInt32
            // offsets: extPolygon, extPolyline, extPoi. Without an RGN reader on
            // hand we cannot tell which of the three are present — the Java
            // predecessor consults RgnSubFile.hasExtendedXxx flags. We provide a
            // best-effort read that captures all three offsets unconditionally;
            // RGN-side filtering lives in RGN parsing.
            val ext = extendedTypes
            if (ext != null && ext.recordSize > 0) {
                val decalage = levels.extendedTypesDecalage(ext.recordCount)
                val extIndex = index - decalage
                if (extIndex >= 1) {
                    val savedPos = reader.position()
                    val extPos = ext.offset + (extIndex - 1) * ext.recordSize.toLong()
                    reader.seek(extPos)
                    builder.extendedPolygonOffset = reader.readUInt32()
                    if (ext.recordSize >= 8) {
                        builder.extendedPolylineOffset = reader.readUInt32()
                    }
                    if (ext.recordSize >= 12) {
                        builder.extendedPoiOffset = reader.readUInt32()
                    }
                    reader.seek(savedPos)
                }
            }

            return builder
        }

        /**
         * Computes the absolute byte offset of the sub-division record at
         * `index`, taking into account that the on-disk run switches from
         * 16-byte FULL records to 14-byte SMALL records past `lastBigIndex`.
         */
        private fun recordPosition(index: Int, recordSize: Int): Long {
            val isFull = recordSize == FULL_RECORD_SIZE
            return if (isFull) {
                sectionOffset + (index - 1L) * FULL_RECORD_SIZE
            } else {
                sectionOffset +
                    lastBigIndex.toLong() * FULL_RECORD_SIZE +
                    (index - lastBigIndex - 1L) * SMALL_RECORD_SIZE
            }
        }

        /**
         * Walks builders in index order and back-fills `dataEnd` and the three
         * extended-types end offsets. For non-last records the end is the next
         * record's matching offset; for the very last record we use [rgnInfo].
         */
        fun assignEnds(rgnInfo: RgnEndInfo?) {
            val ordered = builders.values.sortedBy { it.index }
            for (i in 0 until ordered.size - 1) {
                val cur = ordered[i]
                val next = ordered[i + 1]
                cur.dataEnd = next.dataOffset
                if (extendedTypes != null) {
                    cur.extendedPolygonEnd = next.extendedPolygonOffset
                    cur.extendedPolylineEnd = next.extendedPolylineOffset
                    cur.extendedPoiEnd = next.extendedPoiOffset
                }
            }
            // The final record's ends are derived from the RGN sub-file. If
            // we don't have RGN info yet, the ends stay 0; downstream code that
            // depends on them will need to call back here once RGN is parsed.
            if (rgnInfo != null && ordered.isNotEmpty()) {
                val last = ordered.last()
                last.dataEnd = rgnInfo.dataEnd
                if (extendedTypes != null) {
                    last.extendedPolygonEnd = rgnInfo.extendedPolygonEnd
                    last.extendedPolylineEnd = rgnInfo.extendedPolylineEnd
                    last.extendedPoiEnd = rgnInfo.extendedPoiEnd
                }
            }
        }
    }
}

/**
 * Mutable scratch buffer used by [SubDivisionParser] while building the tree.
 * Becomes an immutable [SubDivision] via [freeze].
 */
private class SubDivisionBuilder(
    val index: Int,
    val level: Int,
    val dataOffset: Long,
    val objectTypes: Int,
    val longitudeCenter: Int,
    val latitudeCenter: Int,
    val halfWidth: Int,
    val halfHeight: Int,
    val indexNextLevel: Int,
    val lastInLevel: Boolean,
) {

    val children: MutableList<SubDivisionBuilder> = mutableListOf()

    var dataEnd: Long = 0L
    var extendedPolygonOffset: Long = 0L
    var extendedPolygonEnd: Long = 0L
    var extendedPolylineOffset: Long = 0L
    var extendedPolylineEnd: Long = 0L
    var extendedPoiOffset: Long = 0L
    var extendedPoiEnd: Long = 0L

    fun freeze(): SubDivision {
        return SubDivision(
            index = index,
            level = level,
            dataOffset = dataOffset,
            dataEnd = dataEnd,
            objectTypes = objectTypes,
            longitudeCenter = longitudeCenter,
            latitudeCenter = latitudeCenter,
            halfWidth = halfWidth,
            halfHeight = halfHeight,
            indexNextLevel = indexNextLevel,
            children = children.map { it.freeze() },
            extendedPolygonOffset = extendedPolygonOffset,
            extendedPolygonEnd = extendedPolygonEnd,
            extendedPolylineOffset = extendedPolylineOffset,
            extendedPolylineEnd = extendedPolylineEnd,
            extendedPoiOffset = extendedPoiOffset,
            extendedPoiEnd = extendedPoiEnd,
        )
    }
}

/**
 * RGN sub-file end offsets used to back-fill the very last sub-division's data
 * ranges. Provided by [RgnSubFile] (B.2.5).
 */
data class RgnEndInfo(
    val dataEnd: Long,
    val extendedPolygonEnd: Long = 0L,
    val extendedPolylineEnd: Long = 0L,
    val extendedPoiEnd: Long = 0L,
)

/**
 * Output of [SubDivisionParser.parse]: the root list (top-level sub-divisions
 * at [TreLevels.maxLevel]) and a flat by-index lookup of every sub-division in
 * the tree.
 */
data class SubDivisionTree(
    val roots: List<SubDivision>,
    val byIndex: Map<Int, SubDivision>,
) {

    /**
     * Total sub-divisions across all levels in the tree.
     */
    val size: Int
        get() = byIndex.size

    /**
     * Walk the tree depth-first, yielding every sub-division.
     */
    fun all(): Sequence<SubDivision> {
        return sequence {
            for (root in roots) {
                yieldSubTree(root)
            }
        }
    }

    private suspend fun SequenceScope<SubDivision>.yieldSubTree(node: SubDivision) {
        yield(node)
        for (child in node.children) {
            yieldSubTree(child)
        }
    }
}
