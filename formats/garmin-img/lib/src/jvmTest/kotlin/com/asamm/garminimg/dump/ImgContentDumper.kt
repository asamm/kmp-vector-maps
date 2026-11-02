/*
 * kmp-garmin-img — Kotlin (rewrite) reference dumper.
 *
 * Counterpart to the Java dumper at
 *   c:/menion/asamm/locus-core/libraries/libGarminImg/lib/src/main/java/
 *     org/free/garminimg/dump/ImgContentDumper.java
 *
 * Diff the two outputs to spot parser-level discrepancies between the
 * legacy libGarminImg and our KMP rewrite. Output format must match the
 * Java dumper *byte for byte* (after sort) — keep it in sync if you
 * change either side.
 */
package com.asamm.garminimg.dump

import com.asamm.garminimg.ImgFile
import com.asamm.garminimg.utils.io.ByteArrayImgRandomAccessReader
import com.asamm.garminimg.model.MapObject
import com.asamm.garminimg.model.ObjectKind
import com.asamm.garminimg.parser.RgnSubFile
import java.io.File

object ImgContentDumper {

    /**
     * Walks the IMG using the same per-sub-division iteration as the
     * legacy parser and emits one record per parsed object. Sorts the
     * output before writing so byte-for-byte diff against the Java
     * dumper is order-insensitive.
     *
     * @param imgPath absolute path to the .img file
     * @param outPath where to write the dump
     */
    fun dump(imgPath: String, outPath: String): String {
        val imgFile = File(imgPath)
        require(imgFile.exists()) { "IMG file not found: $imgPath" }
        val bytes = imgFile.readBytes()
        val img = ImgFile.open { ByteArrayImgRandomAccessReader(bytes) }

        val lines = ArrayList<String>(50_000)
        for (sd in img.tre.tree.all()) {
            val level = sd.level
            for (obj in RgnSubFile.parseSubDivision(
                reader = img.rgn.reader,
                header = img.rgn.header,
                subDivision = sd,
                levels = img.tre.levels,
                lblReader = img.lbl.reader,
                lblHeader = img.lbl.header,
                netReader = img.net?.reader,
                netHeader = img.net?.header,
                cityResolver = img.cityResolverInternal,
                objectKindFilter = ObjectKind.ALL,
            )) {
                lines.add(formatObject(level, obj))
            }
        }
        lines.sort()

        val out = File(outPath)
        out.parentFile?.mkdirs()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("# img=${imgFile.name}\n")
            w.write("# size=${imgFile.length()}\n")
            val n = img.tre.header.northBoundary
            val s = img.tre.header.southBoundary
            val e = img.tre.header.eastBoundary
            val ww = img.tre.header.westBoundary
            w.write("# tre_bbox lon=$ww..$e lat=$s..$n\n")
            w.write("# total_records=${lines.size}\n")
            for (line in lines) {
                w.write(line)
                w.write("\n")
            }
        }
        return outPath
    }

    private fun formatObject(level: Int, obj: MapObject): String {
        return when (obj) {
            is MapObject.Point -> formatPoint(level, obj)
            is MapObject.PolyShape -> formatPoly(level, obj)
        }
    }

    private fun formatPoint(level: Int, p: MapObject.Point): String {
        val sb = StringBuilder(96)
        sb.append("PT")
        sb.append(" L=").append(level)
        sb.append(" type=0x").append(hex(p.type))
        sb.append("/0x").append(hex(p.subType))
        sb.append(" lon=").append(p.longitude)
        sb.append(" lat=").append(p.latitude)
        sb.append(" indexed=").append(if (p.indexed) 1 else 0)
        appendLabel(sb, p.label)
        // Emit POI metadata when present so the diff covers it. Empty
        // / null fields are omitted to keep records compact for points
        // that don't carry that attribute.
        val meta = p.poi
        if (meta != null) {
            if (!meta.streetNumber.isNullOrEmpty()) {
                appendKv(sb, "street_num", meta.streetNumber)
            }
            if (!meta.street.isNullOrEmpty()) {
                appendKv(sb, "street", meta.street)
            }
            if (!meta.cityName.isNullOrEmpty()) {
                appendKv(sb, "city", meta.cityName)
            }
            if (!meta.zipCode.isNullOrEmpty()) {
                appendKv(sb, "zip", meta.zipCode)
            }
            if (!meta.phone.isNullOrEmpty()) {
                appendKv(sb, "phone", meta.phone)
            }
        }
        return sb.toString()
    }

    private fun appendKv(sb: StringBuilder, key: String, value: String) {
        sb.append(' ').append(key).append("=\"")
        for (c in value) {
            when {
                c == '"' || c == '\\' -> sb.append('\\').append(c)
                c.code < 0x20 -> sb.append("\\u%04X".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun formatPoly(level: Int, p: MapObject.PolyShape): String {
        val sb = StringBuilder(128)
        sb.append(if (p.closed) "PG" else "LN")
        sb.append(" L=").append(level)
        // Match Java's `addPoly` "fullTipo" encoding: plain polylines
        // emit the raw type byte; extended polylines emit
        // `((type << 8) | (subType & 0x1F))` so a record with our
        // (type=0x110, subType=0x02) reads as `0x11002` — same as the
        // legacy `MapDrawer` listener saw.
        val printedType = if (p.type >= 0x100) (p.type shl 8) or (p.subType and 0x1F) else p.type
        sb.append(" type=0x").append(hex(printedType))
        val n = p.longitudes.size
        sb.append(" verts=").append(n)
        var minLon = Int.MAX_VALUE
        var maxLon = Int.MIN_VALUE
        var minLat = Int.MAX_VALUE
        var maxLat = Int.MIN_VALUE
        for (i in 0 until n) {
            val x = p.longitudes[i]
            val y = p.latitudes[i]
            if (x < minLon) minLon = x
            if (x > maxLon) maxLon = x
            if (y < minLat) minLat = y
            if (y > maxLat) maxLat = y
        }
        sb.append(" bbox=").append(minLon).append(',').append(minLat)
            .append(',').append(maxLon).append(',').append(maxLat)
        sb.append(" first=").append(p.longitudes[0]).append(',').append(p.latitudes[0])
        sb.append(" last=").append(p.longitudes[n - 1]).append(',').append(p.latitudes[n - 1])
        appendLabel(sb, p.label)
        return sb.toString()
    }

    private fun appendLabel(sb: StringBuilder, label: String?) {
        sb.append(" label=\"")
        if (label != null) {
            for (c in label) {
                when {
                    c == '"' || c == '\\' -> sb.append('\\').append(c)
                    c.code < 0x20 -> sb.append("\\u%04X".format(c.code))
                    else -> sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private fun hex(v: Int): String {
        return Integer.toHexString(v).uppercase()
    }
}
