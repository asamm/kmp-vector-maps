/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg

import com.asamm.garminimg.io.ImgRandomAccessReader
import com.asamm.garminimg.model.MapObject
import com.asamm.garminimg.model.ObjectKind
import com.asamm.garminimg.parser.ImgArchive
import com.asamm.garminimg.parser.LblHeader
import com.asamm.garminimg.parser.LblSubFile
import com.asamm.garminimg.parser.NetHeader
import com.asamm.garminimg.parser.NetSubFile
import com.asamm.garminimg.parser.RgnHeader
import com.asamm.garminimg.parser.RgnSubFile
import com.asamm.garminimg.parser.SubDivision
import com.asamm.garminimg.parser.SubDivisionParser
import com.asamm.garminimg.parser.SubDivisionTree
import com.asamm.garminimg.parser.TreHeader
import com.asamm.garminimg.parser.TreLevels
import com.asamm.garminimg.parser.TreSubFile
import com.asamm.garminimg.parser.TypHeader
import com.asamm.garminimg.parser.TypSubFile

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
 * Each [ImgRandomAccessReader] tracks its own logical position; passing one
 * instance per sub-file lets the parser read three sub-files in parallel
 * without stepping on each other. For a single-thread caller, three readers
 * over the same backing bytes is the simplest correct setup. (Future
 * coroutine-based parallel parse can wrap this layer.)
 */
class ImgFile internal constructor(
    val archive: ImgArchive,
    val tre: TreData,
    val rgn: RgnData,
    val lbl: LblData,
    val net: NetData?,
    val typ: TypHeader?,
    /**
     * Parsed TYP body — `(type, subType)` → resolved styles for the
     * polygon and line sections. Populated when the IMG has a TYP
     * sub-file and its body parses successfully; null otherwise (and the
     * renderer falls back to [com.asamm.garminimg.style.InternalStyles]).
     */
    val typStyles: com.asamm.garminimg.parser.TypStyleTable?,
) {

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
                if (subDivision.objectTypes == 0) {
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
                        objectKindFilter = objectKindFilter,
                    ),
                )
            }
        }
    }

    /**
     * Walks just the sub-divisions whose centre lies within the given Garmin
     * fixed-point bounding box. Useful for tile rendering where you only want
     * features near the visible viewport. Inclusive on all four bounds.
     */
    fun readMapInBounds(
        minLong: Int,
        maxLong: Int,
        minLat: Int,
        maxLat: Int,
        objectKindFilter: Int = ObjectKind.ALL,
        levelFilter: Int? = null,
    ): Sequence<MapObject> {
        return sequence {
            for (subDivision in tre.tree.all()) {
                if (subDivision.objectTypes == 0) {
                    continue
                }
                if (levelFilter != null && subDivision.level != levelFilter) {
                    continue
                }
                if (!subDivisionIntersects(subDivision, minLong, maxLong, minLat, maxLat)) {
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
                        objectKindFilter = objectKindFilter,
                    ),
                )
            }
        }
    }

    private fun subDivisionIntersects(
        sd: SubDivision,
        minLong: Int,
        maxLong: Int,
        minLat: Int,
        maxLat: Int,
    ): Boolean {
        // SubDivision.halfWidth/halfHeight are in level-local coord units; for
        // a fast bbox-intersect check we compare against the centre alone.
        // This is conservative — it may include sub-divisions whose bbox is
        // partially outside the requested area. The parser layer doesn't have
        // a TreLevels here to do exact scaling; the renderer does that.
        return sd.longitudeCenter in minLong..maxLong &&
            sd.latitudeCenter in minLat..maxLat
    }

    /**
     * TRE-derived state: header, levels, and the sub-division tree.
     */
    data class TreData(
        val reader: ImgRandomAccessReader,
        val header: TreHeader,
        val levels: TreLevels,
        val tree: SubDivisionTree,
    )

    /** RGN-derived state. */
    data class RgnData(
        val reader: ImgRandomAccessReader,
        val header: RgnHeader,
    )

    /** LBL-derived state. */
    data class LblData(
        val reader: ImgRandomAccessReader,
        val header: LblHeader,
    )

    /** NET-derived state (when the IMG includes routing data; rare). */
    data class NetData(
        val reader: ImgRandomAccessReader,
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
            val archive = ImgArchive.open(archiveReader)

            val treHeader = TreSubFile.parseHeader(treReader)
            val rgnHeader = RgnSubFile.parseHeader(rgnReader)
            val lblHeader = LblSubFile.parseHeader(lblReader)
            val netHeader = netReader?.let { NetSubFile.parseHeader(it) }
            val typHeader = typReader?.let { TypSubFile.parseHeader(it) }

            val levels = TreSubFile.parseLevels(treReader)
            val tree = SubDivisionParser.parse(
                reader = treReader,
                levels = levels,
                extendedTypes = treHeader.extendedTypes,
                rgnInfo = RgnSubFile.endInfo(rgnHeader),
            )

            // Parse the TYP body when present. Failure to parse the body
            // doesn't fail the whole open — the renderer simply falls back
            // to InternalStyles for everything in that case.
            val typStyles = if (typHeader != null && typReader != null) {
                try {
                    com.asamm.garminimg.parser.TypBodyParser.parse(typReader, typHeader)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            return ImgFile(
                archive = archive,
                tre = TreData(treReader, treHeader, levels, tree),
                rgn = RgnData(rgnReader, rgnHeader),
                lbl = LblData(lblReader, lblHeader),
                net = netHeader?.let { h -> NetData(netReader as ImgRandomAccessReader, h) },
                typ = typHeader,
                typStyles = typStyles,
            )
        }

        /**
         * Convenience: opens an IMG using a [byteSourceFactory] that produces
         * a fresh [ImgRandomAccessReader] each time it's invoked. The
         * factory is called once per sub-file plus once for the archive
         * header. Useful for the common case where the IMG bytes live in a
         * single ByteArray and the caller doesn't want to hand-build readers.
         */
        fun open(byteSourceFactory: () -> ImgRandomAccessReader): ImgFile {
            val archiveReader = byteSourceFactory()
            val archive = ImgArchive.open(archiveReader)
            val tre = archive.findFirst("TRE") ?: error("IMG has no TRE sub-file")
            val rgn = archive.findFirst("RGN") ?: error("IMG has no RGN sub-file")
            val lbl = archive.findFirst("LBL") ?: error("IMG has no LBL sub-file")
            val net = archive.findFirst("NET")
            val typ = archive.findFirst("TYP")

            val treReader = archive.openSubFile(tre, byteSourceFactory())
            val rgnReader = archive.openSubFile(rgn, byteSourceFactory())
            val lblReader = archive.openSubFile(lbl, byteSourceFactory())
            val netReader = net?.let { archive.openSubFile(it, byteSourceFactory()) }
            val typReader = typ?.let { archive.openSubFile(it, byteSourceFactory()) }

            return open(
                archiveReader = byteSourceFactory(),
                treReader = treReader,
                rgnReader = rgnReader,
                lblReader = lblReader,
                netReader = netReader,
                typReader = typReader,
            )
        }
    }
}
