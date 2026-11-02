/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.utils.io.ImgRandomAccessReader
import com.asamm.garminimg.style.Bitmap
import com.asamm.garminimg.style.Color

/**
 * Phase C.7 TYP body parser. Walks the polygon / line / (later) point indexes
 * and extracts a minimal style record per `(type, subType)` for the renderer:
 *
 * - **Polygons** → fill colour (and optional border colour).
 * - **Lines** → stroke colour, border colour, total width.
 * - **Points** → currently skipped (icon-bitmap parsing is more involved;
 *   point styling falls back to [com.asamm.garminimg.style.InternalStyles]
 *   for now).
 *
 * # On-disk format reference
 *
 * Format derived from mkgmap source `uk.me.parabola.imgfmt.app.typ.*`
 * (read-only — no code copied; LGPL/GPL hygiene per CLAUDE.md). Each
 * record starts with a `scheme` byte that tells us how many colours
 * follow, whether a bitmap is embedded, and whether the record carries
 * label / extended-font sections we need to skip past.
 *
 * Polygon `scheme` byte bits:
 * - 0x01 — has night colours (4 colours total instead of 2).
 * - 0x02 — day background transparent (1 day colour, not 2).
 * - 0x04 — night background transparent (1 night colour, not 2).
 * - 0x08 — has bitmap pattern (32×32 1bpp = 128 bytes per fg/bg pair).
 * - 0x10 — has label block following.
 * - 0x20 — has extended font info following.
 *
 * Line records use the same low 3 bits but pack a bitmap height in
 * bits 3..7 of the first byte; the second byte carries flags
 * (label / orientation / extended). When height == 0 the record carries
 * line-width / total-width bytes after the colours.
 *
 * Each colour is 3 bytes (BGR? RGB?) — see [readRgb] for the exact
 * byte order we use.
 */
object TypBodyParser {

    /**
     * Hard upper bound for entries read from any TYP index / section
     * length encoded on disk. Real TYPs ship a few thousand records
     * per section; this cap is set well above real-world maxima and
     * exists only to refuse `range.length` values from a corrupt or
     * attacker-crafted header that would otherwise drive multi-GB
     * allocations before the per-record reads fail.
     */
    private const val MAX_TYP_INDEX_ENTRIES: Int = 65_536

    /**
     * Reads every record in the polygon and line bodies, returning a
     * [TypStyleTable] keyed by `(type, subType)` for fast `(t, st)` →
     * `Style` lookup at render time.
     *
     * Records that fail to parse (corrupt or use an unknown variant) are
     * dropped silently — the resolver falls back to InternalStyles for
     * those, so a partially-valid TYP still gives partial styling.
     */
    fun parse(reader: ImgRandomAccessReader, header: TypHeader): TypStyleTable {
        val polygons = parsePolygonRecords(reader, header)
        val lines = parseLineRecords(reader, header)
        val points = parsePointRecords(reader, header)
        val drawOrder = parseShapeStacking(reader, header)
        // Apply DRAWORDER priorities so the renderer can sort polygons
        // before drawing — without this, large background polygons can
        // paint over smaller foreground features.
        val withPriority = if (drawOrder.isEmpty()) {
            polygons
        } else {
            polygons.mapValues { (typeForFile, style) ->
                val type = typeForFile ushr 5
                val subType = typeForFile and 0x1F
                val priority = findPriority(drawOrder, type, subType)
                if (priority != null) style.copy(drawPriority = priority) else style
            }
        }
        return TypStyleTable(polygons = withPriority, lines = lines, points = points)
    }

    /**
     * Reads the shape-stacking (DRAWORDER) section. Returns entries in
     * file order — the entry's index IS its draw priority. Empty
     * `[0, 0, 0, 0, 0]` entries (zoom-level separators) are kept in place
     * so priorities match the on-disk layout.
     */
    private fun parseShapeStacking(
        reader: ImgRandomAccessReader,
        header: TypHeader,
    ): List<DrawOrderEntry> {
        val descriptor = header.shapeStacking ?: return emptyList()
        val entrySize = descriptor.itemSize
        if (entrySize < 5) {
            return emptyList()
        }
        // Cap entry count against the reader size and a hard limit so a
        // corrupt TYP header carrying `range.length = 2^32-1` can't
        // drive a multi-GB ArrayList allocation. Real TYPs ship a few
        // thousand entries at most.
        val entryCount = (descriptor.range.length / entrySize.toLong())
            .coerceAtMost(MAX_TYP_INDEX_ENTRIES.toLong())
            .toInt()
        val out = ArrayList<DrawOrderEntry>(entryCount)
        for (i in 0 until entryCount) {
            reader.seek(descriptor.range.offset + i.toLong() * entrySize)
            val type = reader.readByte()
            val subTypeMask = reader.readUInt32()
            out.add(DrawOrderEntry(type = type, subTypeMask = subTypeMask, priority = i))
        }
        return out
    }

    /**
     * Resolves a polygon's `(type, subType)` to a draw priority via the
     * DRAWORDER entries. Plain types (T < 0x100) match by `type` alone —
     * the byte stored on disk IS the type. Extended types are stored
     * modulo 256 with the subType bit set in [DrawOrderEntry.subTypeMask].
     */
    private fun findPriority(
        entries: List<DrawOrderEntry>,
        type: Int,
        subType: Int,
    ): Int? {
        for (entry in entries) {
            if (entry.type == 0 && entry.subTypeMask == 0L) {
                continue // level separator
            }
            if (entry.type != (type and 0xFF)) {
                continue
            }
            // Plain types: subTypeMask is typically 0 ("any subType matches").
            // Extended types: the bitmask carries which subtypes are at this
            // priority.
            if (entry.subTypeMask == 0L ||
                (entry.subTypeMask and (1L shl subType)) != 0L
            ) {
                return entry.priority
            }
        }
        return null
    }

    //********************************
    // POLYGONS
    //********************************

    private fun parsePolygonRecords(
        reader: ImgRandomAccessReader,
        header: TypHeader,
    ): Map<Int, TypPolygonStyle> {
        val index = header.polygonIndex ?: return emptyMap()
        val data = header.polygons ?: return emptyMap()
        val out = HashMap<Int, TypPolygonStyle>()
        forEachIndexEntry(reader, index, data) { typeForFile, recordOffset ->
            try {
                reader.seek(recordOffset)
                val style = parseOnePolygon(reader)
                out[typeForFile] = style
            } catch (_: Exception) {
                // Skip the bad record; resolver will fall back to
                // InternalStyles for this (type, subType).
            }
        }
        return out
    }

    /**
     * Parses one polygon record at the reader's current position. Throws on
     * malformed data so the caller can drop just that one record.
     */
    private fun parseOnePolygon(reader: ImgRandomAccessReader): TypPolygonStyle {
        val scheme = reader.readByte()
        val hasNight = scheme and 0x01 != 0
        val dayTransparent = scheme and 0x02 != 0
        val nightTransparent = scheme and 0x04 != 0
        val hasBitmap = scheme and 0x08 != 0
        val hasLabel = scheme and 0x10 != 0
        val hasExtended = scheme and 0x20 != 0

        // mkgmap special-case: simple 2-colour polygon (no bitmap, no border)
        // patches the scheme to set all three transparency flags
        // (scheme & 0x07 == 0x07) but writes only 1 BGR triple (day fg).
        // Naively reading the implied "night" colours would shift the
        // byte cursor 3 bytes past the next record's actual start.
        val simple2Colour = !hasBitmap && (scheme and 0x07) == 0x07

        // Day-side colours: 1 if day background is transparent (only fg
        // stored), 2 otherwise (fg + bg).
        val dayColours = readSolidColours(reader, count = if (dayTransparent) 1 else 2)
        val nightColours = if (hasNight && !simple2Colour) {
            readSolidColours(reader, count = if (nightTransparent) 1 else 2)
        } else {
            emptyList()
        }
        // 32×32 1-bit-per-pixel pattern is 32×32/8 = 128 bytes. Only ONE
        // bitmap regardless of day/night colour count: TypPolygon.write
        // emits the bitmap exactly once, and the day/night switch
        // happens at render time using the same 1bpp pattern with the
        // appropriate fg/bg colour pair.
        val bitmapBytes = if (hasBitmap) {
            ByteArray(POLYGON_BITMAP_BYTES).also { reader.readBlock(it) }
        } else {
            null
        }
        if (hasLabel) {
            skipLabelBlock(reader)
        }
        val font = if (hasExtended) readExtendedFontInfo(reader) else null

        // Polygons with a 32×32 1bpp pattern carry fg+bg colours plus the
        // bit pattern; we expand it to an RGBA tile that the renderer
        // tiles via TexturePaint. Solid polygons (no bitmap) keep their
        // raw fg colour; the optional second day colour acts as a thin
        // outline so adjacent same-fg polygons stay separable.
        val fg = dayColours.firstOrNull() ?: Color.Transparent
        val bg = if (dayColours.size >= 2 && !dayTransparent) dayColours[1] else null
        val pattern = if (bitmapBytes != null) {
            expandPattern(bitmapBytes, fg, bg ?: Color.Transparent)
        } else {
            null
        }
        // When `dayTransparent` is set on a patterned polygon (military
        // hatching, restricted-zone overlays, etc.), there's no ground
        // colour — the pattern paints over whatever's underneath. Using
        // `fg` as the fill in that case bleeds the pattern colour across
        // the whole polygon and obliterates the layers below; QMS / mkgmap
        // both correctly leave the base showing through. The non-pattern
        // path keeps `fg` because solid 1-colour polygons (parks, marshes
        // without a stipple) need a ground colour.
        val fill = when {
            bitmapBytes != null && dayTransparent -> Color.Transparent
            bitmapBytes != null -> bg ?: fg
            else -> fg
        }
        val stroke = if (bitmapBytes == null) bg else null
        return TypPolygonStyle(
            fillColor = fill,
            strokeColor = stroke,
            pattern = pattern,
            font = font,
        )
    }

    //********************************
    // LINES
    //********************************

    private fun parseLineRecords(
        reader: ImgRandomAccessReader,
        header: TypHeader,
    ): Map<Int, TypLineStyle> {
        val index = header.lineIndex ?: return emptyMap()
        val data = header.lines ?: return emptyMap()
        val out = HashMap<Int, TypLineStyle>()
        forEachIndexEntry(reader, index, data) { typeForFile, recordOffset ->
            try {
                reader.seek(recordOffset)
                val style = parseOneLine(reader)
                out[typeForFile] = style
            } catch (_: Exception) {
                // Drop bad record; fall through to InternalStyles.
            }
        }
        return out
    }

    private fun parseOneLine(reader: ImgRandomAccessReader): TypLineStyle {
        // Byte 0 packs scheme (low 3 bits) + bitmap height (high 5 bits).
        // Byte 1 is the flags byte (label / orientation / extended).
        val first = reader.readByte()
        val scheme = first and 0x07
        val bitmapHeight = first ushr 3
        val flags = reader.readByte()
        val hasLabel = flags and 0x01 != 0
        val hasExtended = flags and 0x04 != 0

        val hasNight = scheme and 0x01 != 0
        val dayTransparent = scheme and 0x02 != 0
        val nightTransparent = scheme and 0x04 != 0

        val dayColours = readSolidColours(reader, count = if (dayTransparent) 1 else 2)
        val nightColours = if (hasNight) {
            readSolidColours(reader, count = if (nightTransparent) 1 else 2)
        } else {
            emptyList()
        }

        // Lines and polygons use mkgmap's `simple=true` ColourInfo path
        // which forces 1 bit per pixel regardless of the colour count:
        // the bit selects between fg/bg of the active day colour pair.
        // Width is fixed at 32 → 4 bytes per row.
        val linePattern: Bitmap? = if (bitmapHeight > 0) {
            val rowBytes = (LINE_BITMAP_WIDTH * 1 + 7) / 8
            val raw = ByteArray(rowBytes * bitmapHeight)
            reader.readBlock(raw)
            val fg = dayColours.firstOrNull() ?: Color.Black
            val bg = if (dayColours.size >= 2 && !dayTransparent) {
                dayColours[1]
            } else {
                Color.Transparent
            }
            expandLinePattern(raw, bitmapHeight, fg, bg)
        } else {
            null
        }

        // When there's no bitmap, the record carries line widths. The
        // border-width byte is omitted when scheme matches (~1) == 6 —
        // that's the "no border" form.
        val lineWidth: Int
        val totalWidth: Int
        if (bitmapHeight == 0) {
            lineWidth = reader.readByte()
            totalWidth = if ((scheme and 0x06) != 0x06) {
                reader.readByte()
            } else {
                lineWidth
            }
        } else {
            lineWidth = bitmapHeight
            totalWidth = bitmapHeight
        }

        if (hasLabel) {
            skipLabelBlock(reader)
        }
        val font = if (hasExtended) readExtendedFontInfo(reader) else null

        val stroke = dayColours.firstOrNull() ?: Color.Black
        val border = if (dayColours.size >= 2 && !dayTransparent) {
            dayColours[1]
        } else {
            null
        }
        return TypLineStyle(
            strokeColor = stroke,
            borderColor = border,
            strokeWidthPx = lineWidth.toFloat().coerceAtLeast(1f),
            totalWidthPx = totalWidth.toFloat().coerceAtLeast(lineWidth.toFloat()),
            pattern = linePattern,
            font = font,
        )
    }

    /**
     * Expands a 32×height 1bpp line bitmap to a 32×height RGBA tile.
     * Bits are LSB-first within each byte (matching mkgmap's
     * [BitWriter] packing for `simple=true` line records). Set bits
     * paint with [fg]; clear bits paint with [bg].
     */
    private fun expandLinePattern(
        bits: ByteArray,
        height: Int,
        fg: Color,
        bg: Color,
    ): Bitmap {
        val width = LINE_BITMAP_WIDTH
        val rgba = ByteArray(width * height * 4)
        val rowBytes = (width + 7) / 8
        var dst = 0
        for (row in 0 until height) {
            val rowStart = row * rowBytes
            for (col in 0 until width) {
                val byteIdx = col ushr 3
                val bit = col and 0x7
                val on = (bits[rowStart + byteIdx].toInt() ushr bit) and 0x1 != 0
                val c = if (on) fg else bg
                rgba[dst] = c.red.toByte()
                rgba[dst + 1] = c.green.toByte()
                rgba[dst + 2] = c.blue.toByte()
                rgba[dst + 3] = c.alpha.toByte()
                dst += 4
            }
        }
        return Bitmap(width = width, height = height, rgba = rgba)
    }

    //********************************
    // POINTS / ICONS
    //********************************

    /**
     * Reads every point/icon record from the TYP body. We only attempt to
     * decode records that use mode 0x10 palette images (the common case);
     * mode 0x20 (per-pixel alpha) and mode 0 (24-bit true colour) are
     * skipped silently — the renderer falls back to the InternalStyles
     * default for those `(type, subType)` pairs.
     */
    private fun parsePointRecords(
        reader: ImgRandomAccessReader,
        header: TypHeader,
    ): Map<Int, TypPointStyle> {
        val index = header.pointIndex ?: return emptyMap()
        val data = header.points ?: return emptyMap()
        val out = HashMap<Int, TypPointStyle>()
        forEachIndexEntry(reader, index, data) { typeForFile, recordOffset ->
            try {
                reader.seek(recordOffset)
                val style = parseOnePoint(reader)
                if (style != null) {
                    out[typeForFile] = style
                }
            } catch (_: Exception) {
                // Drop bad record; resolver falls back to InternalStyles.
            }
        }
        return out
    }

    /**
     * Parses one point record. Format (per mkgmap `TypPoint.write` /
     * `TypElement.writeImage` / `BitmapImage.write`):
     *
     * - byte 0: flags (bit 0 = bitmap present, bit 1 = night XPM, bit 2 =
     *   label, bit 3 = extended font).
     * - byte 1: width.
     * - byte 2: height.
     * - byte 3: numColForCM.
     * - byte 4: colour mode (0 = true colour, 0x10 = palette, 0x20 =
     *   palette + per-colour alpha).
     * - colour table (mode 0x10: numColForCM × 3 BGR; mode 0x20: bit-packed
     *   24-bit BGR + 4-bit alpha per colour, padded to byte at end).
     * - bitmap rows: each row `ceil(width × bitsPerPixel / 8)` bytes,
     *   pixel indices packed LSB-first per byte; row starts byte-aligned.
     *   `bitsPerPixel` is 1/2/4/8 depending on numberOfColours.
     * - optional night image (same shape as day) — we skip it.
     * - optional label / extended-font blocks — skipped.
     *
     * Returns null when the record uses a mode we don't yet decode (the
     * caller drops it; the resolver then falls back to InternalStyles).
     */
    private fun parseOnePoint(reader: ImgRandomAccessReader): TypPointStyle? {
        val flags = reader.readByte()
        val hasBitmap = flags and 0x01 != 0
        val hasNight = flags and 0x02 != 0
        val hasLabel = flags and 0x04 != 0
        val hasExtended = flags and 0x08 != 0

        val width = reader.readByte()
        val height = reader.readByte()
        if (!hasBitmap || width <= 0 || height <= 0 || width > MAX_POINT_DIM ||
            height > MAX_POINT_DIM
        ) {
            return null
        }

        val icon = readPointImage(reader, width, height) ?: return null

        // Skip night image so subsequent label / font sections align —
        // even though we ignore the result.
        if (hasNight) {
            readPointImage(reader, width, height)
        }
        if (hasLabel) {
            skipLabelBlock(reader)
        }
        val font = if (hasExtended) readExtendedFontInfo(reader) else null
        return TypPointStyle(icon = icon, font = font)
    }

    /**
     * Reads one (day or night) point image from the current reader
     * position, returning an RGBA [Bitmap] or null when the image uses a
     * mode we don't decode.
     */
    private fun readPointImage(
        reader: ImgRandomAccessReader,
        width: Int,
        height: Int,
    ): Bitmap? {
        val numForCM = reader.readByte()
        val colourMode = reader.readByte()
        if (numForCM == 0) {
            // True-colour image (no palette). Per mkgmap `TrueImage.write`:
            // - mode 0   : 24bpp BGR per pixel, no transparent index.
            // - mode 0x10: 3-byte BGR transparent-pixel marker, then
            //              24bpp BGR per pixel; pixels matching the
            //              marker render fully transparent.
            // - mode 0x20: 28-bit per pixel (24 BGR + 4 alpha, alpha
            //              stored inverted/rounded), packed LSB-first
            //              with no row padding.
            return readTrueColourImage(reader, width, height, colourMode)
        }
        // Mode 0x10: numColours = numForCM solid colours + 1 implicit
        // transparent at end. Mode 0x20: numColours = numForCM, each
        // colour carries its own 4-bit alpha.
        val numColours = if (colourMode == 0x10) numForCM + 1 else numForCM
        val bpp = bitsPerPixelFor(numColours)

        val palette = ArrayList<Color>(numColours)
        when (colourMode) {
            0x10 -> {
                // Solid colours written as BGR triples; transparent slot
                // is implicit (NOT stored on disk) — we append it.
                repeat(numForCM) { palette.add(readRgb(reader)) }
                palette.add(Color.Transparent)
            }
            0x20 -> {
                // Each colour is BGR (24 bits) + alpha (4 bits) packed
                // LSB-first. Total stream: numColours × 28 bits, padded
                // up to the next byte at the end.
                val totalBits = numColours * 28
                val bytes = ByteArray((totalBits + 7) / 8)
                reader.readBlock(bytes)
                val br = BitStream(bytes)
                repeat(numColours) {
                    val b = br.read(8)
                    val g = br.read(8)
                    val r = br.read(8)
                    val invAlpha4 = br.read(4)
                    // Stored alpha is `0xff - actualAlpha` rounded to 4
                    // bits; reconstitute by inverting and expanding the
                    // nibble across the full byte.
                    val actualAlpha = 0xFF - (invAlpha4 * 0x11)
                    palette.add(Color.rgb(r, g, b, actualAlpha))
                }
            }
            else -> return null
        }

        // Bitmap: each row is byte-aligned; pixels packed LSB-first.
        val rgba = ByteArray(width * height * 4)
        val rowBytes = (width * bpp + 7) / 8
        val rowBuf = ByteArray(rowBytes)
        var dst = 0
        for (row in 0 until height) {
            reader.readBlock(rowBuf)
            val br = BitStream(rowBuf)
            for (col in 0 until width) {
                val idx = br.read(bpp)
                val c = if (idx in 0 until palette.size) palette[idx] else Color.Transparent
                rgba[dst] = c.red.toByte()
                rgba[dst + 1] = c.green.toByte()
                rgba[dst + 2] = c.blue.toByte()
                rgba[dst + 3] = c.alpha.toByte()
                dst += 4
            }
        }
        return Bitmap(width = width, height = height, rgba = rgba)
    }

    /**
     * Reads a true-colour (paletteless) point image. Length depends on
     * [colourMode]:
     * - 0   : `width × height × 3` bytes (BGR per pixel, no padding).
     * - 0x10: `3 + width × height × 3` bytes (BGR transparent marker,
     *   then BGR pixels; pixels matching the marker emit full transparency).
     * - 0x20: `(width × height × 28 + 7) / 8` bytes (24-bit BGR + 4-bit
     *   inverted-alpha per pixel, packed LSB-first, padded to a byte at
     *   the END only).
     */
    private fun readTrueColourImage(
        reader: ImgRandomAccessReader,
        width: Int,
        height: Int,
        colourMode: Int,
    ): Bitmap? {
        val n = width * height
        if (n <= 0 || n > MAX_POINT_DIM * MAX_POINT_DIM) {
            return null
        }
        val rgba = ByteArray(n * 4)
        when (colourMode) {
            0x00 -> {
                val raw = ByteArray(n * 3)
                reader.readBlock(raw)
                var src = 0
                var dst = 0
                repeat(n) {
                    rgba[dst] = raw[src + 2]
                    rgba[dst + 1] = raw[src + 1]
                    rgba[dst + 2] = raw[src]
                    rgba[dst + 3] = 0xFF.toByte()
                    src += 3
                    dst += 4
                }
            }
            0x10 -> {
                val markerB = reader.readByte()
                val markerG = reader.readByte()
                val markerR = reader.readByte()
                val raw = ByteArray(n * 3)
                reader.readBlock(raw)
                var src = 0
                var dst = 0
                repeat(n) {
                    val b = raw[src].toInt() and 0xFF
                    val g = raw[src + 1].toInt() and 0xFF
                    val r = raw[src + 2].toInt() and 0xFF
                    val transparent = (b == markerB && g == markerG && r == markerR)
                    rgba[dst] = r.toByte()
                    rgba[dst + 1] = g.toByte()
                    rgba[dst + 2] = b.toByte()
                    rgba[dst + 3] = if (transparent) 0 else 0xFF.toByte()
                    src += 3
                    dst += 4
                }
            }
            0x20 -> {
                val totalBits = n * 28
                val bytes = ByteArray((totalBits + 7) / 8)
                reader.readBlock(bytes)
                val br = BitStream(bytes)
                var dst = 0
                repeat(n) {
                    val b = br.read(8)
                    val g = br.read(8)
                    val r = br.read(8)
                    val invAlpha4 = br.read(4)
                    // Stored alpha is 0xff − actual rounded to 4 bits;
                    // expand the nibble and invert.
                    val actualAlpha = (0xFF - (invAlpha4 * 0x11)).coerceIn(0, 0xFF)
                    rgba[dst] = r.toByte()
                    rgba[dst + 1] = g.toByte()
                    rgba[dst + 2] = b.toByte()
                    rgba[dst + 3] = actualAlpha.toByte()
                    dst += 4
                }
            }
            else -> return null
        }
        return Bitmap(width = width, height = height, rgba = rgba)
    }

    /**
     * Tiny LSB-first bit reader over a fixed byte buffer. Mirrors mkgmap's
     * `BitWriter` packing: the first bit goes into bit 0 of byte 0.
     */
    private class BitStream(private val bytes: ByteArray) {

        private var bitPos: Int = 0

        fun read(n: Int): Int {
            var value = 0
            for (i in 0 until n) {
                val byteIdx = bitPos ushr 3
                val bit = bitPos and 0x7
                val b = (bytes[byteIdx].toInt() ushr bit) and 0x1
                value = value or (b shl i)
                bitPos++
            }
            return value
        }
    }

    //********************************
    // SHARED HELPERS
    //********************************

    /**
     * Walks every (typeForFile, recordOffset) pair in `index`, calling
     * `handler` with the relative-to-data-section record offset translated
     * into an absolute reader offset.
     */
    private fun forEachIndexEntry(
        reader: ImgRandomAccessReader,
        index: TypIndexDescriptor,
        data: SectionRange,
        handler: (typeForFile: Int, recordOffset: Long) -> Unit,
    ) {
        // Index entry: typeForFile (UInt16 = 2 bytes) + offset (1..3 bytes).
        val typeSize = 2
        val offsetSize = (index.itemSize - typeSize).coerceIn(1, 3)
        // Cap against MAX_TYP_INDEX_ENTRIES so a corrupt `range.length`
        // can't drive an oversized iteration. Real TYP indexes ship
        // a few thousand entries at most.
        val entryCount = (index.range.length / index.itemSize.toLong())
            .coerceAtMost(MAX_TYP_INDEX_ENTRIES.toLong())
            .toInt()
        for (i in 0 until entryCount) {
            reader.seek(index.range.offset + i.toLong() * index.itemSize)
            val typeForFile = reader.readUInt16()
            val recordOffsetWithin = readVariableSize(reader, offsetSize).toLong()
            handler(typeForFile, data.offset + recordOffsetWithin)
        }
    }

    private fun readVariableSize(reader: ImgRandomAccessReader, n: Int): Int {
        return when (n) {
            1 -> reader.readByte()
            2 -> reader.readUInt16()
            3 -> reader.readUInt24()
            else -> error("unsupported variable-size width: $n")
        }
    }

    /**
     * Reads `count` solid (3-byte BGR) colours.
     */
    private fun readSolidColours(reader: ImgRandomAccessReader, count: Int): List<Color> {
        if (count <= 0) {
            return emptyList()
        }
        val out = ArrayList<Color>(count)
        for (i in 0 until count) {
            out.add(readRgb(reader))
        }
        return out
    }

    /**
     * Reads a 3-byte BGR (Garmin's on-disk colour order is blue-first).
     */
    private fun readRgb(reader: ImgRandomAccessReader): Color {
        val b = reader.readByte()
        val g = reader.readByte()
        val r = reader.readByte()
        return Color.rgb(r, g, b)
    }

    private fun skipBytes(reader: ImgRandomAccessReader, n: Int) {
        if (n <= 0) {
            return
        }
        val buf = ByteArray(n)
        reader.readBlock(buf)
    }

    /**
     * Skips a TYP label block. The block is `(varlen-encoded length)` plus
     * `length` bytes of label data. mkgmap encodes the length with a
     * variable-byte scheme (low bit = length-of-length). We only need to
     * consume the bytes — we don't currently surface label text in the
     * renderer.
     */
    private fun skipLabelBlock(reader: ImgRandomAccessReader) {
        // Read first byte; if its low bit is set, the length is encoded
        // in this single byte (length = byte >> 1). Otherwise the length
        // continues into the next byte (UInt16 LE; length = uint16 >> 2).
        val first = reader.readByte()
        val length = if (first and 0x01 != 0) {
            first ushr 1
        } else {
            val second = reader.readByte()
            ((second shl 8) or first) ushr 2
        }
        skipBytes(reader, length)
    }

    /**
     * Parses a TYP extended-font-info block per mkgmap
     * `TypElement.writeExtendedFontInfo`:
     * - 1 byte fontExt: low 3 bits = font style (0=default, 1=nocaps,
     *   2=normal, 3=small, 4=large), bit 0x08 = has day colour, bit
     *   0x10 = has night colour.
     * - optional 3-byte day BGR colour.
     * - optional 3-byte night BGR colour.
     *
     * Returns a parsed [TypFontInfo] (with `dayColour` populated when
     * present), or `null` if the block reads as zero (no info).
     */
    private fun readExtendedFontInfo(reader: ImgRandomAccessReader): TypFontInfo? {
        val fontExt = reader.readByte()
        val style = fontExt and 0x07
        val hasDay = fontExt and 0x08 != 0
        val hasNight = fontExt and 0x10 != 0
        val dayColour = if (hasDay) readRgb(reader) else null
        if (hasNight) {
            // Night colour bytes consumed but ignored — the renderer is
            // currently day-mode only.
            skipBytes(reader, 3)
        }
        if (style == 0 && dayColour == null) {
            return null
        }
        return TypFontInfo(style = style, dayColour = dayColour)
    }

    /**
     * Bits-per-pixel for a bitmap given the total colour count, mirroring
     * mkgmap `ColourInfo.getBitsPerPixel`.
     */
    private fun bitsPerPixelFor(numColours: Int): Int {
        return when {
            numColours < 2 -> 1
            numColours < 4 -> 2
            numColours < 16 -> 4
            else -> 8
        }
    }

    /**
     * Expands a 32×32 1bpp bit pattern into a 32×32 RGBA [Bitmap]. Bits
     * are read MSB-first within each byte, in row-major order. Set bits
     * paint with [fg]; clear bits paint with [bg].
     */
    private fun expandPattern(bits: ByteArray, fg: Color, bg: Color): Bitmap {
        val side = 32
        val rgba = ByteArray(side * side * 4)
        var dst = 0
        var src = 0
        for (row in 0 until side) {
            // Each row is 32 bits = 4 bytes.
            for (byteIdx in 0 until 4) {
                val byte = bits[src + byteIdx].toInt() and 0xFF
                for (bit in 7 downTo 0) {
                    val on = (byte ushr bit) and 0x1 != 0
                    val c = if (on) fg else bg
                    rgba[dst] = c.red.toByte()
                    rgba[dst + 1] = c.green.toByte()
                    rgba[dst + 2] = c.blue.toByte()
                    rgba[dst + 3] = c.alpha.toByte()
                    dst += 4
                }
            }
            src += 4
        }
        return Bitmap(width = side, height = side, rgba = rgba)
    }

    private const val POLYGON_BITMAP_BYTES: Int = 32 * 32 / 8 // 32x32, 1bpp = 128
    private const val LINE_BITMAP_WIDTH: Int = 32

    /**
     * Sanity cap on a TYP point bitmap dimension. Real TYP icons are
     * 16-64 px on a side; anything larger almost certainly means we mis-
     * parsed the record header and would otherwise allocate gigabytes.
     */
    private const val MAX_POINT_DIM: Int = 256
}

/**
 * One polygon's resolved TYP style: foreground fill, optional border,
 * and draw priority from the TYP's shape-stacking section (when present).
 *
 * Priorities are *file positions* in the shape-stacking section — lower
 * priority values draw first (background); higher draw later (foreground).
 * `drawPriority = Int.MAX_VALUE` is the sentinel for "no DRAWORDER entry
 * matched" — those polygons are drawn last (which is the conservative
 * choice; a TYP-aware renderer with proper styling rarely emits unmatched
 * polygons).
 *
 * @param pattern optional 32×32 RGBA tile expanded from the TYP record's 1bpp bit pattern. When
 *   present, the renderer tiles this across the polygon instead of using [fillColor] solid. Set
 *   bits paint with the day foreground; clear bits paint with the day background (or transparent
 *   when the day-bg-transparent flag was set).
 */
// Non-data class — adding a field (e.g. night-mode bg) stays binary-
// compatible for consumers as long as we don't change the existing
// param positions. Explicit `copy()` exposes the one `.copy(drawPriority=…)`
// the parser itself uses.
class TypPolygonStyle(
    val fillColor: Color,
    val strokeColor: Color?,
    val drawPriority: Int = Int.MAX_VALUE,
    val pattern: Bitmap? = null,
    val font: TypFontInfo? = null,
) {

    fun copy(
        fillColor: Color = this.fillColor,
        strokeColor: Color? = this.strokeColor,
        drawPriority: Int = this.drawPriority,
        pattern: Bitmap? = this.pattern,
        font: TypFontInfo? = this.font,
    ): TypPolygonStyle {
        return TypPolygonStyle(
            fillColor = fillColor,
            strokeColor = strokeColor,
            drawPriority = drawPriority,
            pattern = pattern,
            font = font,
        )
    }
}

/**
 * One DRAWORDER entry: a (type, subTypeMask) tuple with its file-position
 * priority. See [TypBodyParser.parseShapeStacking].
 */
internal data class DrawOrderEntry(
    val type: Int,
    val subTypeMask: Long,
    val priority: Int,
)

/**
 * One line's resolved TYP style: stroke, optional border, foreground +
 * total widths in pixels.
 *
 * @param pattern optional 32×height RGBA tile expanded from the TYP record's 1bpp line bitmap.
 *   When present, the renderer paints the polyline as the pattern (rotated to follow the line
 *   direction) instead of a solid stroke — that's how dashed roads, railways, and multi-stripe
 *   casings get drawn.
 */
// Non-data class — see [TypPolygonStyle].
class TypLineStyle(
    val strokeColor: Color,
    val borderColor: Color?,
    val strokeWidthPx: Float,
    val totalWidthPx: Float,
    val pattern: Bitmap? = null,
    val font: TypFontInfo? = null,
)

/**
 * One point's resolved TYP style: the day icon as RGBA, plus optional
 * extended-font info that lets the TYP override label colour/style for
 * this `(type, subType)`.
 */
// Non-data class — see [TypPolygonStyle].
class TypPointStyle(
    val icon: Bitmap,
    val font: TypFontInfo? = null,
)

/**
 * TYP extended-font-info block (per mkgmap `TypElement.writeExtendedFontInfo`).
 * Surfaces just the day-side label colour and the font style flag — that's
 * what the renderer can act on right now. Night colour and font-family
 * variants are decoded but discarded.
 *
 * @property style 0..7 — 0 = default font, others map to mkgmap's nocaps /
 *   normal / small / large variants. Currently treated as a hint; the
 *   renderer doesn't yet swap font sizes per style.
 * @property dayColour Optional override for the label text colour. When
 *   null, the style resolver keeps its default label colour.
 */
// Non-data class — see [TypPolygonStyle].
class TypFontInfo(
    val style: Int,
    val dayColour: Color?,
)

/**
 * Lookup table built once from a TYP body parse. Keys are `typeForFile`
 * (`(type << 5) | (subType and 0x1F)`); use [lookupPolygon] / [lookupLine] /
 * [lookupPoint] for the by-(type, subType) call shape the renderer actually
 * wants.
 */
// Non-data class — see [TypPolygonStyle].
class TypStyleTable(
    val polygons: Map<Int, TypPolygonStyle>,
    val lines: Map<Int, TypLineStyle>,
    val points: Map<Int, TypPointStyle> = emptyMap(),
) {

    fun lookupPolygon(type: Int, subType: Int): TypPolygonStyle? {
        return polygons[typeForFile(type, subType)]
            ?: polygons[typeForFile(type, 0)]
    }

    fun lookupLine(type: Int, subType: Int): TypLineStyle? {
        return lines[typeForFile(type, subType)]
            ?: lines[typeForFile(type, 0)]
    }

    fun lookupPoint(type: Int, subType: Int): TypPointStyle? {
        return points[typeForFile(type, subType)]
            ?: points[typeForFile(type, 0)]
    }

    private fun typeForFile(type: Int, subType: Int): Int {
        return (type shl 5) or (subType and 0x1F)
    }
}
