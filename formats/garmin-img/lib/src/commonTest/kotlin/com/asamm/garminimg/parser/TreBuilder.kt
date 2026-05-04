package com.asamm.garminimg.parser

/**
 * Builder for synthetic TRE sub-file byte payloads, mirroring the layout described
 * on [TreSubFile]. Used by [TreSubFileTest] and (later) by RGN/LBL parser tests
 * that need a TRE sibling in the same synthetic IMG.
 *
 * Defaults: header length 0x21 (just enough for bounds), unlocked, no TRE7. Set
 * fields explicitly when a test needs them.
 */
internal class TreBuilder {

    private var headerLength: Int = 0x21
    private var locked: Boolean = false
    private var north: Int = 0
    private var east: Int = 0
    private var south: Int = 0
    private var west: Int = 0
    private var tre7: ExtendedTypesSpec? = null
    private val levelSpecs = mutableListOf<LevelSpec>()
    private val subDivSpecs = mutableListOf<SubDivSpec>()
    private val tre7Records = mutableListOf<Tre7Record>()

    private data class ExtendedTypesSpec(val offset: Long, val length: Long, val recordSize: Int)

    private data class LevelSpec(
        val level: Int,
        val bitsPerCoord: Int,
        val subDivisionCount: Int,
        val inherited: Boolean,
    )

    private data class SubDivSpec(
        val recordSize: Int,
        val dataOffset: Int,
        val objectTypes: Int,
        val longitudeCenter: Int,
        val latitudeCenter: Int,
        val halfWidth: Int,
        val halfHeight: Int,
        val indexNextLevel: Int,
        val lastInLevel: Boolean,
    )

    private data class Tre7Record(
        val extendedPolygonOffset: Long,
        val extendedPolylineOffset: Long,
        val extendedPoiOffset: Long,
    )

    fun headerLength(value: Int) = apply { headerLength = value }

    fun locked(value: Boolean) = apply { locked = value }

    fun bounds(north: Int, east: Int, south: Int, west: Int) = apply {
        this.north = north
        this.east = east
        this.south = south
        this.west = west
    }

    fun extendedTypes(offset: Long, length: Long, recordSize: Int) = apply {
        tre7 = ExtendedTypesSpec(offset, length, recordSize)
    }

    /**
     * Append one level record. The TRE's levels-section descriptor (offset 0x21)
     * and the records themselves are emitted by [build] when at least one level
     * has been added.
     */
    fun level(
        level: Int,
        bitsPerCoord: Int,
        subDivisionCount: Int = 0,
        inherited: Boolean = false,
    ) = apply {
        levelSpecs.add(LevelSpec(level, bitsPerCoord, subDivisionCount, inherited))
    }

    /**
     * Emits a FULL (16-byte) sub-division record at the next index. Records are
     * appended in the order calls are made.
     */
    fun subDivisionFull(
        dataOffset: Int = 0,
        objectTypes: Int = 0,
        longitudeCenter: Int = 0,
        latitudeCenter: Int = 0,
        halfWidth: Int = 0,
        halfHeight: Int = 0,
        indexNextLevel: Int = 0,
        lastInLevel: Boolean = false,
    ) = apply {
        subDivSpecs.add(
            SubDivSpec(
                recordSize = 16,
                dataOffset = dataOffset,
                objectTypes = objectTypes,
                longitudeCenter = longitudeCenter,
                latitudeCenter = latitudeCenter,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                indexNextLevel = indexNextLevel,
                lastInLevel = lastInLevel,
            ),
        )
    }

    /**
     * Emits a SMALL (14-byte) sub-division record. Used for leaf-level records.
     */
    fun subDivisionSmall(
        dataOffset: Int = 0,
        objectTypes: Int = 0,
        longitudeCenter: Int = 0,
        latitudeCenter: Int = 0,
        halfWidth: Int = 0,
        halfHeight: Int = 0,
        lastInLevel: Boolean = false,
    ) = apply {
        subDivSpecs.add(
            SubDivSpec(
                recordSize = 14,
                dataOffset = dataOffset,
                objectTypes = objectTypes,
                longitudeCenter = longitudeCenter,
                latitudeCenter = latitudeCenter,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                indexNextLevel = 0,
                lastInLevel = lastInLevel,
            ),
        )
    }

    /**
     * Adds one TRE7 extended-types record. Records are emitted sequentially at
     * the offset declared by [extendedTypes]. Each record carries up to three
     * UInt32 offsets (polygon, polyline, POI).
     */
    fun tre7Record(
        extendedPolygonOffset: Long = 0L,
        extendedPolylineOffset: Long = 0L,
        extendedPoiOffset: Long = 0L,
    ) = apply {
        tre7Records.add(
            Tre7Record(
                extendedPolygonOffset,
                extendedPolylineOffset,
                extendedPoiOffset,
            ),
        )
    }

    fun build(): ByteArray {
        // Header is the bytes at offsets 0..headerLength. We append level records
        // immediately after the header; sub-divisions next; TRE7 records last.
        val levelsOffset = maxOf(headerLength, 0x40)
        val levelsLength = levelSpecs.size * 4
        val subDivOffset = levelsOffset + levelsLength
        val subDivLength = subDivSpecs.sumOf { it.recordSize }
        val tre7Offset = subDivOffset + subDivLength
        val tre7RecordSize = if (tre7Records.isEmpty()) 0 else 12 // 3 UInt32 fields
        val tre7Length = tre7Records.size * tre7RecordSize

        val computedTotalSize = tre7Offset + tre7Length
        val totalSize = maxOf(computedTotalSize, maxOf(headerLength, 0x40))

        val out = ByteArray(totalSize)

        // 0x00..0x01: headerLength UInt16 LE
        writeUInt16LE(out, 0x00, headerLength)

        // 0x0D: lock flag
        out[0x0D] = if (locked) 0x01 else 0x00

        // 0x15..0x20: bounds (4 × Int24)
        if (totalSize >= 0x21) {
            writeInt24LE(out, 0x15, north)
            writeInt24LE(out, 0x18, east)
            writeInt24LE(out, 0x1B, south)
            writeInt24LE(out, 0x1E, west)
        }

        // 0x21..0x28: levels descriptor (offset + length)
        if (totalSize >= 0x29) {
            writeUInt32LE(out, 0x21, levelsOffset.toLong())
            writeUInt32LE(out, 0x25, levelsLength.toLong())
        }

        // 0x29..0x30: sub-divisions descriptor (offset + length)
        if (totalSize >= 0x31 && subDivSpecs.isNotEmpty()) {
            writeUInt32LE(out, 0x29, subDivOffset.toLong())
            writeUInt32LE(out, 0x2D, subDivLength.toLong())
        }

        // 0x7C..0x85: TRE7 — overridden by tre7Records if those are emitted.
        val ext = tre7
        if (ext != null && totalSize >= 0x86) {
            writeUInt32LE(out, 0x7C, ext.offset)
            writeUInt32LE(out, 0x80, ext.length)
            writeUInt16LE(out, 0x84, ext.recordSize)
        } else if (tre7Records.isNotEmpty() && totalSize >= 0x86) {
            // Auto-fill TRE7 descriptor when caller used `tre7Record(...)` but
            // didn't set the descriptor explicitly.
            writeUInt32LE(out, 0x7C, tre7Offset.toLong())
            writeUInt32LE(out, 0x80, tre7Length.toLong())
            writeUInt16LE(out, 0x84, tre7RecordSize)
        }

        // Levels records, 4 bytes each, immediately after the header
        var off = levelsOffset
        for (lvl in levelSpecs) {
            val zoomByte = (lvl.level and 0x0F) or (if (lvl.inherited) 0x80 else 0)
            out[off] = zoomByte.toByte()
            out[off + 1] = lvl.bitsPerCoord.toByte()
            writeUInt16LE(out, off + 2, lvl.subDivisionCount)
            off += 4
        }

        // Sub-division records (FULL=16 or SMALL=14 bytes each)
        off = subDivOffset
        for (sd in subDivSpecs) {
            // bytes 0..2: dataOffset UInt24
            out[off] = (sd.dataOffset and 0xFF).toByte()
            out[off + 1] = ((sd.dataOffset ushr 8) and 0xFF).toByte()
            out[off + 2] = ((sd.dataOffset ushr 16) and 0xFF).toByte()
            // byte 3: objectTypes (low nibble = dataOffset overflow, here 0)
            out[off + 3] = sd.objectTypes.toByte()
            // bytes 4..6: longitudeCenter Int24
            writeInt24LE(out, off + 4, sd.longitudeCenter)
            // bytes 7..9: latitudeCenter Int24
            writeInt24LE(out, off + 7, sd.latitudeCenter)
            // bytes 10..11: halfWidth UInt16 (high bit = lastInLevel)
            val widthRaw = (sd.halfWidth and 0x7FFF) or (if (sd.lastInLevel) 0x8000 else 0)
            writeUInt16LE(out, off + 10, widthRaw)
            // bytes 12..13: halfHeight UInt16
            writeUInt16LE(out, off + 12, sd.halfHeight)
            // bytes 14..15: indexNextLevel UInt16 (FULL only)
            if (sd.recordSize >= 16) {
                writeUInt16LE(out, off + 14, sd.indexNextLevel)
            }
            off += sd.recordSize
        }

        // TRE7 records (12 bytes each: 3 × UInt32)
        off = tre7Offset
        for (rec in tre7Records) {
            writeUInt32LE(out, off, rec.extendedPolygonOffset)
            writeUInt32LE(out, off + 4, rec.extendedPolylineOffset)
            writeUInt32LE(out, off + 8, rec.extendedPoiOffset)
            off += 12
        }

        return out
    }

    private fun writeUInt16LE(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeInt24LE(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dest[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    }

    private fun writeUInt32LE(dest: ByteArray, offset: Int, value: Long) {
        dest[offset] = (value and 0xFFL).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFFL).toByte()
        dest[offset + 2] = ((value ushr 16) and 0xFFL).toByte()
        dest[offset + 3] = ((value ushr 24) and 0xFFL).toByte()
    }
}
