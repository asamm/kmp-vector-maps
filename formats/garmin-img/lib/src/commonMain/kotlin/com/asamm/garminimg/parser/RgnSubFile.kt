/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.model.MapObject
import com.asamm.garminimg.model.ObjectKind
import com.asamm.garminimg.utils.collections.IntArrayList
import com.asamm.garminimg.utils.io.ImgRandomAccessReader

/**
 * Parser for an `.RGN` sub-file. RGN holds the geometry of every map object
 * (POIs, indexed points, polylines, polygons, plus their extended-types
 * counterparts) keyed by sub-division.
 *
 * Layout per sub-file:
 * - `0x00..0x14` — common header (length + lock-flag at 0x0D)
 * - `0x15..0x18` — plain-types data section offset (UInt32)
 * - `0x19..0x1C` — plain-types data section length (UInt32)
 * - `0x1D..0x24` — extended polygons section offset/length (when headerLength ≥ 0x55)
 * - `0x39..0x40` — extended polylines section offset/length
 * - `0x55..0x5C` — extended POIs section offset/length
 *
 * Per sub-division, the plain-types section contains a small "segment pointer
 * table" listing where each kind of object (POINT / INDEXED_POINT / POLYLINE /
 * POLYGON) lives within that sub-division's data range. The extended-types
 * sections live at the file level and are addressed through the sub-division's
 * extended-types offsets.
 *
 * # Phase B.2.5
 *
 * This commit ports all branches of the Java predecessor's RGN parsing:
 * - plain points (POINT, INDEXED_POINT)
 * - plain poly (POLYLINE, POLYGON, with the variable-bit-width coordinate
 *   delta stream)
 * - extended points / polylines / polygons
 *
 * The renderer-side helper `getIndexPointLabel(subDivision, index)` is also
 * ported as it's needed by [LblSubFile] for city-name back-references in
 * B.2.4.b.
 */
object RgnSubFile {

    /**
     * Hard cap for the per-poly bit-stream length. Real records ship a
     * few hundred bytes at most; this is a DoS guard against a corrupt
     * record carrying `bitStreamLen = 0xFFFF`.
     */
    private const val MAX_POLY_BIT_STREAM_LEN: Int = 4096

    private const val OFFSET_DATA_DESCRIPTOR: Long = 0x15L
    private const val OFFSET_EXT_POLYGONS_DESCRIPTOR: Long = 0x1DL
    private const val OFFSET_EXT_POLYLINES_DESCRIPTOR: Long = 0x39L
    private const val OFFSET_EXT_POIS_DESCRIPTOR: Long = 0x55L

    /** Header length below which the extended-types descriptors don't exist. */
    private const val MIN_HEADER_LENGTH_FOR_EXTENDED: Int = 0x55

    /**
     * Reads the RGN header. Same single-pass shape as [TreSubFile.parseHeader]
     * and [LblSubFile.parseHeader].
     */
    fun parseHeader(reader: ImgRandomAccessReader): RgnHeader {
        val common = reader.readSubFileCommonHeader()
        require(common.headerLength >= 0x1D) {
            "RGN header length 0x${common.headerLength.toString(16)} too small"
        }

        reader.seek(OFFSET_DATA_DESCRIPTOR)
        val dataOffset = reader.readUInt32()
        val dataLength = reader.readUInt32()

        var extPolygons: SectionRange? = null
        var extPolylines: SectionRange? = null
        var extPois: SectionRange? = null
        if (common.headerLength >= MIN_HEADER_LENGTH_FOR_EXTENDED) {
            reader.seek(OFFSET_EXT_POLYGONS_DESCRIPTOR)
            extPolygons = SectionRange(reader.readUInt32(), reader.readUInt32())
                .nullIfEmpty()
            reader.seek(OFFSET_EXT_POLYLINES_DESCRIPTOR)
            extPolylines = SectionRange(reader.readUInt32(), reader.readUInt32())
                .nullIfEmpty()
            reader.seek(OFFSET_EXT_POIS_DESCRIPTOR)
            extPois = SectionRange(reader.readUInt32(), reader.readUInt32())
                .nullIfEmpty()
        }

        return RgnHeader(
            headerLength = common.headerLength,
            dataOffset = dataOffset,
            dataLength = dataLength,
            extendedPolygons = extPolygons,
            extendedPolylines = extPolylines,
            extendedPois = extPois,
        )
    }

    /**
     * Returns the [RgnEndInfo] suitable for back-filling the last sub-division's
     * data ranges in [SubDivisionParser.parse].
     */
    fun endInfo(header: RgnHeader): RgnEndInfo {
        return RgnEndInfo(
            dataEnd = header.dataLength,
            extendedPolygonEnd = header.extendedPolygons?.length ?: 0L,
            extendedPolylineEnd = header.extendedPolylines?.length ?: 0L,
            extendedPoiEnd = header.extendedPois?.length ?: 0L,
        )
    }

    /**
     * Parses every object in `subDivision` and yields them as a lazy
     * [Sequence]. Iterating advances the [reader]; consumers must not start a
     * second iteration concurrently. Pass `lblHeader`/`lblReader` to resolve
     * label offsets to decoded strings — pass `null` for both to get
     * label=null on every object (useful for tests not exercising LBL).
     *
     * @param objectKindFilter bit-mask of [ObjectKind] flags. The default
     *   `ObjectKind.ALL` emits every kind.
     */
    fun parseSubDivision(
        reader: ImgRandomAccessReader,
        header: RgnHeader,
        subDivision: SubDivision,
        levels: TreLevels? = null,
        lblReader: ImgRandomAccessReader? = null,
        lblHeader: LblHeader? = null,
        netReader: ImgRandomAccessReader? = null,
        netHeader: NetHeader? = null,
        cityResolver: LblSubFile.IndexedPointLabelResolver? = null,
        objectKindFilter: Int = ObjectKind.ALL,
    ): Sequence<MapObject> {
        // Bits-per-coord for this sub-division's level controls how the
        // record-encoded deltas scale into absolute Garmin coords. When
        // [levels] is null (e.g. minimal parser tests) we fall back to 24,
        // which is the no-shift case — fine for unit tests, wrong for
        // rendering.
        val bitsPerCoord = levels?.bitsPerCoordOf(subDivision.level) ?: 24
        return sequence {
            val segments = computeSegments(reader, header, subDivision) ?: return@sequence
            // Emission order matches Java's readMapForDrawing background-first
            // bias: polygons first, then extended polygons, then polylines and
            // points in any order.
            segments[SEG_POLYGON]?.let { seg ->
                if (objectKindFilter and ObjectKind.POLYGON != 0) {
                    yieldAll(
                        parsePoly(
                            reader,
                            subDivision,
                            seg,
                            line = false,
                            bitsPerCoord,
                            lblReader,
                            lblHeader
                        )
                    )
                }
            }
            segments[SEG_EXT_POLYGON]?.let { seg ->
                if (objectKindFilter and ObjectKind.EXTENDED_POLYGON != 0) {
                    yieldAll(
                        parseExtPoly(
                            reader,
                            subDivision,
                            seg,
                            line = false,
                            bitsPerCoord,
                            lblReader,
                            lblHeader
                        )
                    )
                }
            }
            segments[SEG_POLYLINE]?.let { seg ->
                if (objectKindFilter and ObjectKind.POLYLINE != 0) {
                    yieldAll(
                        parsePoly(
                            reader,
                            subDivision,
                            seg,
                            line = true,
                            bitsPerCoord,
                            lblReader,
                            lblHeader,
                            netReader,
                            netHeader
                        )
                    )
                }
            }
            segments[SEG_EXT_POLYLINE]?.let { seg ->
                if (objectKindFilter and ObjectKind.EXTENDED_POLYLINE != 0) {
                    yieldAll(
                        parseExtPoly(
                            reader,
                            subDivision,
                            seg,
                            line = true,
                            bitsPerCoord,
                            lblReader,
                            lblHeader
                        )
                    )
                }
            }
            segments[SEG_POINT]?.let { seg ->
                if (objectKindFilter and ObjectKind.POINT != 0) {
                    yieldAll(
                        parsePoints(
                            reader,
                            subDivision,
                            seg,
                            indexed = false,
                            bitsPerCoord,
                            lblReader,
                            lblHeader,
                            cityResolver
                        )
                    )
                }
            }
            segments[SEG_IPOINT]?.let { seg ->
                if (objectKindFilter and ObjectKind.INDEXED_POINT != 0) {
                    yieldAll(
                        parsePoints(
                            reader,
                            subDivision,
                            seg,
                            indexed = true,
                            bitsPerCoord,
                            lblReader,
                            lblHeader,
                            cityResolver
                        )
                    )
                }
            }
            segments[SEG_EXT_POINT]?.let { seg ->
                if (objectKindFilter and ObjectKind.EXTENDED_POINT != 0) {
                    yieldAll(
                        parseExtPoints(
                            reader,
                            subDivision,
                            seg,
                            bitsPerCoord,
                            lblReader,
                            lblHeader
                        )
                    )
                }
            }
        }
    }

    //********************************
    // SEGMENT TABLE
    //********************************

    /** Indices into the per-sub-division segment array. Stable across this file. */
    private const val SEG_POINT: Int = 0
    private const val SEG_IPOINT: Int = 1
    private const val SEG_POLYLINE: Int = 2
    private const val SEG_POLYGON: Int = 3
    private const val SEG_EXT_POLYGON: Int = 4
    private const val SEG_EXT_POLYLINE: Int = 5
    private const val SEG_EXT_POINT: Int = 6
    private const val SEGMENT_COUNT: Int = 7

    /**
     * Computes the segment table for `subDivision` — where each kind of
     * object's data lives within RGN. Returns `null` if the sub-division
     * advertises no objects.
     */
    private fun computeSegments(
        reader: ImgRandomAccessReader,
        header: RgnHeader,
        subDivision: SubDivision,
    ): Array<Segment?>? {
        if (subDivision.objectTypes == 0) {
            return null
        }
        val result = arrayOfNulls<Segment>(SEGMENT_COUNT)

        // --- Plain-types data: POINT / INDEXED_POINT / POLYLINE / POLYGON ---
        if (subDivision.hasData) {
            val nbTypes = countObjectTypes(subDivision.objectTypes)
            if (nbTypes > 0) {
                val plainOffset = subDivision.dataOffset + header.dataOffset
                val plainEnd = subDivision.dataEnd + header.dataOffset
                if (subDivision.objectTypes and ObjectKind.POINT != 0) {
                    result[SEG_POINT] = Segment()
                }
                if (subDivision.objectTypes and ObjectKind.INDEXED_POINT != 0) {
                    result[SEG_IPOINT] = Segment()
                }
                if (subDivision.objectTypes and ObjectKind.POLYLINE != 0) {
                    result[SEG_POLYLINE] = Segment()
                }
                if (subDivision.objectTypes and ObjectKind.POLYGON != 0) {
                    result[SEG_POLYGON] = Segment()
                }

                // The first nbTypes-1 UInt16s at plainOffset are end-of-segment
                // pointers (relative to plainOffset). The last segment runs to
                // plainEnd.
                reader.seek(plainOffset)
                val nbPointers = nbTypes - 1
                var segmentStart = plainOffset + nbPointers * 2L
                var curType = 0
                for (i in 0 until nbPointers) {
                    while (curType < SEG_POLYGON + 1 && result[curType] == null) {
                        curType++
                    }
                    val segmentEnd = reader.readUInt16().toLong() + plainOffset
                    if (segmentEnd in (segmentStart + 1)..plainEnd && curType < SEGMENT_COUNT) {
                        result[curType]?.set(segmentStart, segmentEnd)
                    } else {
                        result[curType] = null
                    }
                    segmentStart = segmentEnd
                    curType++
                }
                while (curType < SEG_POLYGON + 1 && result[curType] == null) {
                    curType++
                }
                if (curType < SEGMENT_COUNT) {
                    result[curType]?.set(segmentStart, plainEnd)
                }
            }
        }

        // --- Extended-types: each one is its own mini-section ---
        attachExtendedSegment(
            result,
            slot = SEG_EXT_POLYGON,
            requiredKind = ObjectKind.EXTENDED_POLYGON,
            objectTypes = subDivision.objectTypes,
            ini = subDivision.extendedPolygonOffset,
            fin = subDivision.extendedPolygonEnd,
            sectionBase = header.extendedPolygons?.offset,
        )
        attachExtendedSegment(
            result,
            slot = SEG_EXT_POLYLINE,
            requiredKind = ObjectKind.EXTENDED_POLYLINE,
            objectTypes = subDivision.objectTypes,
            ini = subDivision.extendedPolylineOffset,
            fin = subDivision.extendedPolylineEnd,
            sectionBase = header.extendedPolylines?.offset,
        )
        attachExtendedSegment(
            result,
            slot = SEG_EXT_POINT,
            requiredKind = ObjectKind.EXTENDED_POINT,
            objectTypes = subDivision.objectTypes,
            ini = subDivision.extendedPoiOffset,
            fin = subDivision.extendedPoiEnd,
            sectionBase = header.extendedPois?.offset,
        )
        return result
    }

    private fun attachExtendedSegment(
        result: Array<Segment?>,
        slot: Int,
        @Suppress("UNUSED_PARAMETER") requiredKind: Int,
        @Suppress("UNUSED_PARAMETER") objectTypes: Int,
        ini: Long,
        fin: Long,
        sectionBase: Long?,
    ) {
        // Extended-type presence is NOT signalled by the sub-division's
        // objectTypes byte (which only encodes POINT/IPOINT/POLYLINE/POLYGON
        // bits in the standard on-disk record). Instead, TRE7's per-sub-
        // division offset range is the source of truth — a non-empty range
        // means this sub-division has data of that extended kind. Gating on
        // objectTypes here permanently hid extended polygons (buildings)
        // and extended polylines from the renderer.
        if (sectionBase == null || fin <= ini) {
            return
        }
        result[slot] = Segment().apply { set(sectionBase + ini, sectionBase + fin) }
    }

    /**
     * Counts how many of POINT/INDEXED_POINT/POLYLINE/POLYGON bits are set
     * (mirrors `SubDivision.getNbObjectTypes` in the Java predecessor).
     */
    private fun countObjectTypes(objectTypes: Int): Int {
        var count = 0
        var bit = ObjectKind.POINT
        repeat(4) {
            if (objectTypes and bit != 0) {
                count++
            }
            bit = bit shl 1
        }
        return count
    }

    //********************************
    // PLAIN POINT PARSING
    //********************************

    private fun parsePoints(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        segment: Segment,
        indexed: Boolean,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
        cityResolver: LblSubFile.IndexedPointLabelResolver? = null,
    ): Sequence<MapObject.Point> = sequence {
        reader.seek(segment.start)
        while (reader.position() < segment.end) {
            val type = reader.readByte()
            val info = reader.readUInt24()
            val hasSubType = info and 0x800000 != 0
            val isPoi = info and 0x400000 != 0
            val labelOffset = info and 0x3FFFFF
            val longitudeDelta = readInt16(reader)
            val latitudeDelta = readInt16(reader)
            val subType = if (hasSubType) reader.readByte() else 0
            val poiMetadata: com.asamm.garminimg.model.PoiMetadata? =
                if (isPoi && lblReader != null && lblHeader != null && lblHeader.poiLength > 0L) {
                    try {
                        LblSubFile.readPoi(
                            reader = lblReader,
                            header = lblHeader,
                            offset = labelOffset.toLong(),
                            indexedPointLabelResolver = cityResolver,
                        )
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            val rawLabel = if (poiMetadata != null) {
                poiMetadata.name
            } else {
                resolveLabel(labelOffset.toLong(), lblReader, lblHeader)
            }
            val label = translatePointLabel(type, subType, rawLabel)
            // For elevation points the legacy parser swaps the POILabel
            // for a fresh SimpleLabel after translateLabel — losing all
            // structured metadata. Match that, so downstream consumers
            // (and the dump-diff rig) see the same model shape.
            val effectivePoiMetadata = if (label !== rawLabel && poiMetadata != null) {
                null
            } else {
                poiMetadata
            }
            yield(
                MapObject.Point(
                    type = type,
                    subType = subType,
                    longitude = scaleAndAdd(
                        subDivision.longitudeCenter,
                        longitudeDelta,
                        bitsPerCoord,
                        additionalAccuracy = 0
                    ),
                    latitude = scaleAndAdd(
                        subDivision.latitudeCenter,
                        latitudeDelta,
                        bitsPerCoord,
                        additionalAccuracy = 0
                    ),
                    label = label,
                    indexed = indexed,
                    isPoi = isPoi,
                    poi = effectivePoiMetadata,
                ),
            )
        }
    }

    private fun parseExtPoints(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        segment: Segment,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
    ): Sequence<MapObject.Point> = sequence {
        reader.seek(segment.start)
        while (reader.position() < segment.end) {
            val tipo = reader.readByte()
            val subtipo = reader.readByte()
            val fullType = ((tipo + 0x100) shl 8) + (subtipo and 0x1F)
            val hasLabel = (subtipo and 0x20) != 0
            val longitudeDelta = readInt16(reader)
            val latitudeDelta = readInt16(reader)
            val label = if (hasLabel) {
                resolveLabel(reader.readUInt24().toLong(), lblReader, lblHeader)
            } else {
                null
            }
            yield(
                MapObject.Point(
                    type = fullType ushr 8,
                    subType = fullType and 0xFF,
                    longitude = scaleAndAdd(
                        subDivision.longitudeCenter,
                        longitudeDelta,
                        bitsPerCoord,
                        additionalAccuracy = 0
                    ),
                    latitude = scaleAndAdd(
                        subDivision.latitudeCenter,
                        latitudeDelta,
                        bitsPerCoord,
                        additionalAccuracy = 0
                    ),
                    label = label,
                    indexed = false,
                ),
            )
        }
    }

    //********************************
    // PLAIN POLY (POLYLINE / POLYGON) PARSING
    //********************************

    private fun parsePoly(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        segment: Segment,
        line: Boolean,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
        netReader: ImgRandomAccessReader? = null,
        netHeader: NetHeader? = null,
    ): Sequence<MapObject.PolyShape> = sequence {
        reader.seek(segment.start)
        while (reader.position() < segment.end) {
            val shape = try {
                parseOnePoly(
                    reader,
                    subDivision,
                    line,
                    bitsPerCoord,
                    lblReader,
                    lblHeader,
                    netReader,
                    netHeader
                )
            } catch (_: Exception) {
                // Bit-stream over-ran or coordinate decode failed mid-record.
                // The reader's position is now mid-record; resuming from
                // there would decode misaligned bytes into nonsense
                // coordinates that surface as spurious straight lines in the
                // rendered map (visible at wide bbox renders).
                //
                // The Java predecessor's `parseExtPoly` handles this by
                // bailing the whole segment on any exception; we mirror
                // that here so a single corrupt record can't poison the
                // rest of the segment.
                break
            }
            yield(shape)
        }
    }

    private fun parseOnePoly(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        line: Boolean,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
        netReader: ImgRandomAccessReader? = null,
        netHeader: NetHeader? = null,
    ): MapObject.PolyShape {
        val infoByte = reader.readByte()
        val type: Int
        val direction: Boolean
        if (line) {
            type = infoByte and 0x3F
            direction = infoByte and 0x40 != 0
        } else {
            type = infoByte and 0x7F
            direction = false
        }
        val twoBytesLen = infoByte and 0x80 != 0

        val info2 = reader.readUInt24()
        val labelOffset = info2 and 0x3FFFFF
        val extraBit = info2 and 0x400000 != 0
        // High bit (0x800000) of info2 — `dataInNet`. When set on a
        // polyline, `labelOffset` indexes the NET sub-file's road
        // definitions table, *not* LBL directly. NET resolves through
        // to its underlying LBL offset. We were treating every
        // labelOffset as a direct LBL offset, which produced garbage
        // labels for every road in IMGs with NET (a *lot* of records).
        val dataInNet = info2 and 0x800000 != 0
        val longitudeDelta = readInt16(reader)
        val latitudeDelta = readInt16(reader)
        val bitStreamLen = if (twoBytesLen) reader.readUInt16() else reader.readByte()
        val bitStreamInfo = reader.readByte()

        val (longs, lats) = decodePolyCoordinates(
            reader = reader,
            bitStreamLen = bitStreamLen,
            bitStreamInfo = bitStreamInfo,
            subDivision = subDivision,
            longitudeDelta = longitudeDelta,
            latitudeDelta = latitudeDelta,
            bitsPerCoord = bitsPerCoord,
            extraBit = extraBit,
            fixedExtraBitLong = if (extraBit) 1 else 0,
            fixedExtraBitLat = 0,
            readPolyExtraSignBit = false,
        )

        val label = if (line && dataInNet && netReader != null && netHeader != null) {
            // dataInNet: labelOffset points into NET's road-definitions
            // table; NET dereferences to the underlying LBL string.
            try {
                NetSubFile.resolveRoadName(
                    netReader = netReader,
                    netHeader = netHeader,
                    offset = labelOffset.toLong(),
                    lblReader = lblReader ?: error("LBL required when NET is in play"),
                    lblHeader = lblHeader ?: error("LBL required when NET is in play"),
                )
            } catch (_: Exception) {
                null
            }
        } else {
            resolveLabel(labelOffset.toLong(), lblReader, lblHeader)
        }
        return MapObject.PolyShape(
            type = type,
            subType = 0,
            longitudes = longs,
            latitudes = lats,
            label = label,
            closed = !line,
            direction = direction,
            extraBit = extraBit,
            dataInNet = dataInNet,
        )
    }

    private fun parseExtPoly(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        segment: Segment,
        line: Boolean,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
    ): Sequence<MapObject.PolyShape> = sequence {
        reader.seek(segment.start)
        while (reader.position() < segment.end) {
            val shape = try {
                parseOneExtPoly(reader, subDivision, line, bitsPerCoord, lblReader, lblHeader)
            } catch (_: Exception) {
                // Same rationale as parsePoly — bail the segment on
                // mid-record decode failure rather than continuing from a
                // misaligned read position.
                break
            }
            yield(shape)
        }
    }

    private fun parseOneExtPoly(
        reader: ImgRandomAccessReader,
        subDivision: SubDivision,
        line: Boolean,
        bitsPerCoord: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
    ): MapObject.PolyShape {
        val tipo = reader.readByte()
        val subtipo = reader.readByte()
        val fullType = ((tipo + 0x100) shl 8) + (subtipo and 0x1F)
        val hasLabel = subtipo and 0x20 != 0
        val longitudeDelta = readInt16(reader)
        val latitudeDelta = readInt16(reader)

        // Extended poly's bit-stream length encoding is a quirky 2-byte LEN:
        //  - if first byte is even, read second byte and length = (b1 + b2*256)/4 - 1
        //  - else length = b1/2 - 1
        var bitStreamLen = reader.readByte()
        bitStreamLen = if (bitStreamLen and 0x01 == 0) {
            (bitStreamLen + reader.readByte() * 256) / 4 - 1
        } else {
            bitStreamLen / 2 - 1
        }
        val bitStreamInfo = reader.readByte()

        val (longs, lats) = decodePolyCoordinates(
            reader = reader,
            bitStreamLen = bitStreamLen,
            bitStreamInfo = bitStreamInfo,
            subDivision = subDivision,
            longitudeDelta = longitudeDelta,
            latitudeDelta = latitudeDelta,
            bitsPerCoord = bitsPerCoord,
            extraBit = false,
            fixedExtraBitLong = 0,
            fixedExtraBitLat = 0,
            readPolyExtraSignBit = true,
        )

        val label = if (hasLabel) {
            resolveLabel(reader.readUInt24().toLong(), lblReader, lblHeader)
        } else {
            null
        }
        return MapObject.PolyShape(
            type = fullType ushr 8,
            subType = fullType and 0xFF,
            longitudes = longs,
            latitudes = lats,
            label = label,
            closed = !line,
        )
    }

    /**
     * Decodes the variable-bit-width coordinate-delta stream that's the most
     * intricate part of an RGN polyline/polygon record. Returns the absolute
     * Garmin coordinates (lon, lat) of every point.
     *
     * Mirrors the Java predecessor's `parsePoly` / `parseExtPoly` inner loop
     * so a faithful by-byte port comes out the same. The two parameters
     * `fixedExtraBitLong`/`fixedExtraBitLat` distinguish plain-types polys
     * (where extraBit comes from the record's info byte) from extended-types
     * polys (where extraBit is read from the bit stream's first bits).
     */
    private fun decodePolyCoordinates(
        reader: ImgRandomAccessReader,
        bitStreamLen: Int,
        bitStreamInfo: Int,
        subDivision: SubDivision,
        longitudeDelta: Int,
        latitudeDelta: Int,
        bitsPerCoord: Int,
        @Suppress("UNUSED_PARAMETER") extraBit: Boolean,
        fixedExtraBitLong: Int,
        fixedExtraBitLat: Int,
        readPolyExtraSignBit: Boolean,
    ): Pair<IntArray, IntArray> {
        // Cap per-record bit-stream length so a corrupt poly record
        // can't tie up a tile worker for ~0.5 MB of uninterrupted
        // decode (`bitStreamLen = 0xFFFF`). Real polylines top out at
        // a few hundred bytes; the cap is set well above that. Throws
        // so the caller's per-record catch + segment-bail kicks in.
        require(bitStreamLen in 0..MAX_POLY_BIT_STREAM_LEN) {
            "polyline bit-stream length $bitStreamLen exceeds cap $MAX_POLY_BIT_STREAM_LEN"
        }
        val bsr = BitStreamReader(reader, bitStreamLen)

        val longSign = readSign(bsr)
        val latSign = readSign(bsr)
        val longExtraBit = if (readPolyExtraSignBit) bsr.readNextBits(1) else fixedExtraBitLong
        val latExtraBit = fixedExtraBitLat

        val longBits = convertCoordinateLength(bitStreamInfo and 0xF, longSign, longExtraBit)
        val latBits = convertCoordinateLength(bitStreamInfo ushr 4, latSign, latExtraBit)

        var curLong = longitudeDelta
        var curLat = latitudeDelta

        // Primitive `IntArrayList` (no boxing) — polyline coord streams
        // hit `.add` thousands of times per record on country IMGs.
        val longs = IntArrayList(8)
        val lats = IntArrayList(8)
        // First point uses the (longitudeDelta, latitudeDelta) directly.
        longs.add(
            scaleAndAdd(
                subDivision.longitudeCenter,
                curLong,
                bitsPerCoord,
                additionalAccuracy = 0
            )
        )
        lats.add(
            scaleAndAdd(
                subDivision.latitudeCenter,
                curLat,
                bitsPerCoord,
                additionalAccuracy = 0
            )
        )

        // Increase precision if the extra-bit flag is on.
        curLong = curLong shl longExtraBit
        curLat = curLat shl latExtraBit

        // Sanity-window for the cumulative deltas: a sub-division at
        // bitsPerCoord=B has a maximum half-extent of (2^(B-1) << (24-B))
        // = 2^23 absolute Garmin units. Anything beyond ~2x that is a
        // bit-stream that drifted into garbage; bail to avoid emitting
        // nonsense polylines that render as long straight bars.
        val driftLimit = (1 shl 22) // 2^22 abs Garmin units ~ 90° at the equator;
        // wide enough to never reject valid sub-division-internal deltas.

        while (bsr.hasNext(longBits + latBits)) {
            val longOffset = bsr.readCoordOffset(longBits, longSign, longExtraBit)
            val latOffset = bsr.readCoordOffset(latBits, latSign, latExtraBit)
            curLong += longOffset
            curLat += latOffset
            if (kotlin.math.abs(curLong) > driftLimit || kotlin.math.abs(curLat) > driftLimit) {
                // The cumulative delta has drifted to an absurd value —
                // the bit-stream got out of sync. Drop the rest of this
                // record's points; the caller's segment-loop will move on.
                bsr.finish()
                throw IllegalStateException("polyline coordinates drifted out of sub-division bounds")
            }
            longs.add(scaleAndAdd(subDivision.longitudeCenter, curLong, bitsPerCoord, longExtraBit))
            lats.add(scaleAndAdd(subDivision.latitudeCenter, curLat, bitsPerCoord, latExtraBit))
        }
        bsr.finish()

        return longs.toIntArray() to lats.toIntArray()
    }

    private fun readSign(bsr: BitStreamReader): Int {
        return if (bsr.readNextBits(1) != 0) {
            // Constant sign — second bit indicates positive (0) or negative (1).
            if (bsr.readNextBits(1) == 0) 1 else -1
        } else {
            // Variable sign — encoded per delta in [readCoordOffset].
            0
        }
    }

    /**
     * Converts the 4-bit nibble at the start of a polyline/polygon bit stream
     * into the actual number of bits per coordinate delta. Mirrors the Java
     * predecessor's `convertCoordinateLength`.
     */
    private fun convertCoordinateLength(nibble: Int, sign: Int, extraBit: Int): Int {
        var additional = 0
        if (sign == 0) {
            additional++
        }
        additional += extraBit
        return if (nibble <= 9) {
            nibble + 2 + additional
        } else {
            2 * nibble - 9 + 2 + additional
        }
    }

    /**
     * Scales a coordinate delta `value` by `1 shl (24 - bitsPerCoord -
     * additionalAccuracy)` and adds it to `centre`. Mirrors the Java
     * `SubDivision.convertMapUnits` formula: smaller `bitsPerCoord` (coarser
     * zoom levels) means deltas represent larger jumps in absolute Garmin
     * coords, so they shift up by more bits.
     *
     * Falls back to "no shift" when `bitsPerCoord >= 24` — used by the parser
     * unit tests that don't have a real [TreLevels] handle.
     */
    private fun scaleAndAdd(
        centre: Int,
        value: Int,
        bitsPerCoord: Int,
        additionalAccuracy: Int,
    ): Int {
        val shift = 24 - bitsPerCoord - additionalAccuracy
        val scaled = if (shift >= 0) value shl shift else value shr -shift
        return centre + scaled
    }

    private fun readInt16(reader: ImgRandomAccessReader): Int {
        val v = reader.readUInt16()
        return if (v > 0x7FFF) v - 0x10000 else v
    }

    /**
     * Resolves a label offset to its decoded text via [LblSubFile.readLabel].
     * Returns null when [labelOffset] is 0 (sentinel for "no label") or when
     * the LBL handles are missing.
     */
    /**
     * Mirrors the legacy parser's `RgnSubFile.translateLabel` for point
     * records: a few `(type, subType)` combinations are known to carry
     * an elevation / numeric value with a leading 0x1F (Unit Separator)
     * byte. The Java path rewrites these by parsing the numeric tail
     * and re-emitting it as a plain integer string. Without this step
     * our dump diff carries ~500 spurious "elevation" mismatches.
     */
    private fun translatePointLabel(type: Int, subType: Int, raw: String?): String? {
        if (raw.isNullOrEmpty()) {
            return raw
        }
        val fullType = (type shl 8) or subType
        if (fullType != 0x6616 && fullType != 0x6300) {
            return raw
        }
        // Java's Double.parseDouble strips leading whitespace, which
        // includes the 0x1F US separator. Mirror that so the output
        // matches.
        val trimmed = raw.trimStart { it.isWhitespace() || it.code <= 0x1F }
        val value = trimmed.toDoubleOrNull() ?: return raw
        return value.toInt().toString()
    }

    /**
     * Reads the `pointIndex`-th indexed-point label from `subDivision`'s
     * IPOINT segment. Used by LBL POI city resolution when a city
     * record encodes its name as a `(SubDivision, indexed-point)` pair
     * rather than a direct LBL offset. Mirrors libGarminImg's
     * `RgnSubFile.getIndexPointLabel`.
     *
     * Returns null when the sub-division has no indexed points or the
     * requested index isn't in range. Saves and restores the reader's
     * position so callers can resume parsing wherever they were.
     */
    fun readIndexedPointLabel(
        reader: ImgRandomAccessReader,
        header: RgnHeader,
        subDivision: SubDivision,
        pointIndex: Int,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
    ): String? {
        if (pointIndex <= 0) {
            return null
        }
        val savedPos = reader.position()
        val segments = computeSegments(reader, header, subDivision) ?: return null
        val seg = segments[SEG_IPOINT] ?: return null
        reader.seek(seg.start)
        var counter = 0
        var result: String? = null
        try {
            while (reader.position() < seg.end) {
                @Suppress("UNUSED_VARIABLE")
                val type = reader.readByte()
                val info = reader.readUInt24()
                val hasSubType = info and 0x800000 != 0
                val isPoi = info and 0x400000 != 0
                val recLabelOffset = info and 0x3FFFFF

                @Suppress("UNUSED_VARIABLE")
                val longitudeDelta = readInt16(reader)

                @Suppress("UNUSED_VARIABLE")
                val latitudeDelta = readInt16(reader)
                if (hasSubType) {
                    reader.readByte()
                }
                counter++
                if (counter == pointIndex && recLabelOffset != 0 && lblReader != null && lblHeader != null) {
                    result = if (isPoi && lblHeader.poiLength > 0L) {
                        LblSubFile.readPoiName(lblReader, lblHeader, recLabelOffset.toLong())
                    } else {
                        LblSubFile.readLabel(lblReader, lblHeader, recLabelOffset.toLong())
                    }
                    break
                }
            }
        } finally {
            reader.seek(savedPos)
        }
        return result
    }

    private fun resolveLabel(
        labelOffset: Long,
        lblReader: ImgRandomAccessReader?,
        lblHeader: LblHeader?,
    ): String? {
        if (labelOffset == 0L || lblReader == null || lblHeader == null) {
            return null
        }
        return try {
            LblSubFile.readLabel(lblReader, lblHeader, labelOffset)
        } catch (_: IllegalArgumentException) {
            // Out-of-range label offset — a corrupt RGN record. Don't fail the
            // whole sub-division parse; return null and let downstream
            // diagnostic tools log it.
            null
        }
    }

    /**
     * Mutable scratch slot in the per-sub-division segment table. `start`
     * defaults to `Long.MIN_VALUE` to make "uninitialised" uses fail loudly.
     */
    private class Segment {
        var start: Long = Long.MIN_VALUE
        var end: Long = Long.MIN_VALUE
        fun set(start: Long, end: Long) {
            require(end >= start) { "segment end $end < start $start" }
            this.start = start
            this.end = end
        }
    }
}

/**
 * Parsed RGN header. Each [SectionRange] is null when the IMG has no records
 * of that extended-types kind.
 */
data class RgnHeader(
    val headerLength: Int,
    val dataOffset: Long,
    val dataLength: Long,
    val extendedPolygons: SectionRange?,
    val extendedPolylines: SectionRange?,
    val extendedPois: SectionRange?,
)

/**
 * Half-open `[offset, offset+length)` byte range within an IMG sub-file.
 */
data class SectionRange(val offset: Long, val length: Long) {

    /**
     * Returns null if [length] is zero — the section is absent. Used to keep
     * `RgnHeader.extended*` strictly nullable when the IMG has no records.
     */
    internal fun nullIfEmpty(): SectionRange? {
        return if (length > 0L) this else null
    }
}

/**
 * Variable-bit-width reader operating over a fixed-length byte run from an
 * [ImgRandomAccessReader]. Used by RGN poly parsing to decode the
 * coordinate-delta stream.
 *
 * Mirrors the Java predecessor's `BitStreamReader`. The semantics aren't
 * obvious from the format; we follow the original's behaviour by-byte.
 */
internal class BitStreamReader(
    private val reader: ImgRandomAccessReader,
    initialLength: Int,
) {
    private var length: Int = initialLength
    private var remainingBits: Int = 0
    private var curByte: Int = 0

    fun finish() {
        while (length > 0) {
            reader.readByte()
            length--
        }
    }

    fun hasNext(nbBits: Int): Boolean {
        return length * 8 + remainingBits >= nbBits
    }

    fun readNextBits(toGet: Int): Int {
        var pos = 0
        var result = 0
        while (pos < toGet) {
            ensureBitsAvailable()
            val remaining = toGet - pos
            if (remaining >= remainingBits) {
                result = result or (curByte shl pos)
                pos += remainingBits
                remainingBits = 0
            } else {
                val mask = (1 shl remaining) - 1
                result = result or ((curByte and mask) shl pos)
                curByte = curByte ushr remaining
                remainingBits -= remaining
                return result
            }
        }
        return result
    }

    fun readCoordOffset(nbBits: Int, sign: Int, extraBit: Int): Int {
        if (sign == 0) {
            // Variable-sign value
            val value = readNextBits(nbBits)
            val signMask = 1 shl (nbBits - 1)
            return if (value and signMask != 0) {
                val comp = value xor signMask
                if (extraBit == 0) {
                    if (comp != 0) {
                        comp - signMask
                    } else {
                        val other = readCoordOffset(nbBits, sign, extraBit)
                        if (other < 0) 1 - value + other else value - 1 + other
                    }
                } else {
                    if (comp and 0xFFFFFE != 0) {
                        (comp and 0xFFFFFE) - signMask
                    } else {
                        val other = readCoordOffset(nbBits - 1, sign, 0)
                        if (other < 0) {
                            1 - signMask + 1 + (other shl 1)
                        } else {
                            signMask - 1 - 1 + (other shl 1)
                        }
                    }
                }
            } else {
                if (extraBit > 0) value and 0xFFFFFE else value
            }
        } else {
            val value = readNextBits(nbBits)
            return if (extraBit > 0) {
                ((value ushr 1) * sign) shl 1
            } else {
                value * sign
            }
        }
    }

    private fun ensureBitsAvailable() {
        if (remainingBits == 0) {
            check(length > 0) { "BitStreamReader EOF" }
            remainingBits = 8
            length--
            curByte = reader.readByte()
        }
    }
}
