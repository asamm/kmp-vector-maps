/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.tile

/**
 * One decoded Mapbox Vector Tile (spec 2.1): its layers by name. Feature geometry stays encoded
 * until [Feature.decodeGeometry] is called.
 *
 * @param encodedBytes the size of the protobuf it was decoded from, the base of [retainedBytes]
 */
class VectorTile(
    val layers: Map<String, Layer>,
    val encodedBytes: Int = 0,
) {

    /**
     * An estimate of the heap the tile keeps with every feature's geometry decoded: about 7 times
     * its protobuf on the Asamm tiles, dense Prague and sparse fixture tiles alike
     * (docs/performance.md M10.3d), at least the objects of its features, plus those of the tile.
     */
    val retainedBytes: Long = maxOf(
        encodedBytes * RETAINED_PER_ENCODED,
        layers.values.sumOf { it.features.size } * RETAINED_PER_FEATURE,
    ) + RETAINED_PER_TILE

    companion object {

        private const val RETAINED_PER_ENCODED = 7L

        // a Feature with its tag and geometry arrays, for tiles of many tiny features
        private const val RETAINED_PER_FEATURE = 96L

        private const val RETAINED_PER_TILE = 2048L

        /**
         * Decodes an uncompressed MVT protobuf.
         */
        fun decode(bytes: ByteArray): VectorTile {
            val reader = PbfReader(bytes, 0, bytes.size)
            val layers = LinkedHashMap<String, Layer>()
            while (reader.hasNext()) {
                val tag = reader.readTag()
                if (tag == TAG_TILE_LAYER) {
                    val layer = decodeLayer(reader.readMessage())
                    layers[layer.name] = layer
                } else {
                    reader.skip(tag)
                }
            }
            return VectorTile(layers = layers, encodedBytes = bytes.size)
        }

        private fun decodeLayer(reader: PbfReader): Layer {
            var name = ""
            var extent = DEFAULT_EXTENT
            val keys = ArrayList<String>()
            val values = ArrayList<Any>()
            val features = ArrayList<PbfReader>()
            while (reader.hasNext()) {
                when (val tag = reader.readTag()) {
                    TAG_LAYER_NAME -> name = reader.readString()
                    TAG_LAYER_FEATURE -> features.add(reader.readMessage())
                    TAG_LAYER_KEY -> keys.add(reader.readString())
                    TAG_LAYER_VALUE -> values.add(decodeValue(reader.readMessage()))
                    TAG_LAYER_EXTENT -> extent = reader.readVarint().toInt().takeIf { it > 0 } ?: DEFAULT_EXTENT
                    else -> reader.skip(tag)
                }
            }
            val layer = Layer(name, extent, keys.toTypedArray(), values.toTypedArray())
            layer.features = features.map { decodeFeature(it, layer) }
            return layer
        }

        private fun decodeValue(reader: PbfReader): Any {
            var value: Any = ""
            while (reader.hasNext()) {
                val tag = reader.readTag()
                value = when (tag) {
                    TAG_VALUE_STRING -> reader.readString()
                    TAG_VALUE_FLOAT -> reader.readFloat().toDouble()
                    TAG_VALUE_DOUBLE -> reader.readDouble()
                    TAG_VALUE_INT, TAG_VALUE_UINT -> reader.readVarint().toDouble()
                    TAG_VALUE_SINT -> reader.readSVarint().toDouble()
                    TAG_VALUE_BOOL -> reader.readVarint() != 0L
                    else -> {
                        reader.skip(tag)
                        value
                    }
                }
            }
            return value
        }

        private fun decodeFeature(reader: PbfReader, layer: Layer): Feature {
            var id: Long? = null
            var tags = IntArray(0)
            var type = GeometryType.UNKNOWN
            var geometry = IntArray(0)
            while (reader.hasNext()) {
                val tag = reader.readTag()
                when (tag) {
                    TAG_FEATURE_ID -> id = reader.readVarint()
                    TAG_FEATURE_TAGS -> tags = reader.readPackedVarints()
                    TAG_FEATURE_TYPE -> type = GeometryType.of(reader.readVarint().toInt())
                    TAG_FEATURE_GEOMETRY -> geometry = reader.readPackedVarints()
                    else -> reader.skip(tag)
                }
            }
            return Feature(layer, id, tags, type, geometry)
        }

        private const val DEFAULT_EXTENT = 4096

        private const val TAG_TILE_LAYER = (3 shl 3) or 2
        private const val TAG_LAYER_NAME = (1 shl 3) or 2
        private const val TAG_LAYER_FEATURE = (2 shl 3) or 2
        private const val TAG_LAYER_KEY = (3 shl 3) or 2
        private const val TAG_LAYER_VALUE = (4 shl 3) or 2
        private const val TAG_LAYER_EXTENT = (5 shl 3) or 0
        private const val TAG_VALUE_STRING = (1 shl 3) or 2
        private const val TAG_VALUE_FLOAT = (2 shl 3) or 5
        private const val TAG_VALUE_DOUBLE = (3 shl 3) or 1
        private const val TAG_VALUE_INT = (4 shl 3) or 0
        private const val TAG_VALUE_UINT = (5 shl 3) or 0
        private const val TAG_VALUE_SINT = (6 shl 3) or 0
        private const val TAG_VALUE_BOOL = (7 shl 3) or 0
        private const val TAG_FEATURE_ID = (1 shl 3) or 0
        private const val TAG_FEATURE_TAGS = (2 shl 3) or 2
        private const val TAG_FEATURE_TYPE = (3 shl 3) or 0
        private const val TAG_FEATURE_GEOMETRY = (4 shl 3) or 2
    }
}

/**
 * A tile layer; [values] hold `String`, `Double` or `Boolean`.
 */
class Layer(
    val name: String,
    val extent: Int,
    val keys: Array<String>,
    val values: Array<Any>,
) {

    var features: List<Feature> = emptyList()
        internal set

    private val keyIndices = HashMap<String, Int>(keys.size * 2).also { map ->
        for (i in keys.indices) {
            map[keys[i]] = i
        }
    }

    /**
     * The index of `key` in [keys], or -1 when no feature of this layer has it.
     */
    fun keyIndex(key: String): Int {
        return keyIndices[key] ?: -1
    }
}

enum class GeometryType {
    UNKNOWN,
    POINT,
    LINESTRING,
    POLYGON,
    ;

    companion object {

        fun of(value: Int): GeometryType {
            return entries.getOrElse(value) { UNKNOWN }
        }
    }
}

/**
 * A tile feature; its properties resolve through its [layer]'s key and value tables.
 */
class Feature(
    val layer: Layer,
    val id: Long?,
    private val tags: IntArray,
    val type: GeometryType,
    private val geometry: IntArray,
) {

    // decoded once, as the layers of a tile and the tiles cut from it share it
    @Volatile
    private var decoded: Geometry? = null

    /**
     * The value of property `key`, or null.
     */
    fun property(key: String): Any? {
        val keyIndex = layer.keyIndex(key)
        if (keyIndex < 0) {
            return null
        }
        var i = 0
        while (i + 1 < tags.size) {
            if (tags[i] == keyIndex) {
                return layer.values.getOrNull(tags[i + 1])
            }
            i += 2
        }
        return null
    }

    /**
     * The geometry command stream in tile coordinates (0 to [Layer.extent], may reach beyond for
     * the tile buffer), decoded on the first call; callers must not change it.
     */
    fun decodeGeometry(): Geometry {
        decoded?.let {
            return it
        }
        return decode().also { decoded = it }
    }

    private fun decode(): Geometry {
        val coords = FloatArray(geometry.size)
        val parts = ArrayList<Int>()
        var n = 0
        var x = 0
        var y = 0
        var i = 0
        var partStart = 0
        while (i < geometry.size) {
            val command = geometry[i] and 0x7
            val count = geometry[i] ushr 3
            i++
            when (command) {
                CMD_MOVE_TO,
                CMD_LINE_TO -> {
                    for (c in 0 until count) {
                        if (i + 1 >= geometry.size) {
                            break
                        }
                        if (command == CMD_MOVE_TO
                            && n > partStart
                        ) {
                            parts.add(n)
                            partStart = n
                        }
                        x += zigzag(geometry[i])
                        y += zigzag(geometry[i + 1])
                        i += 2
                        coords[n++] = x.toFloat()
                        coords[n++] = y.toFloat()
                    }
                }
                CMD_CLOSE_PATH -> {
                    // rings are closed by the renderer
                }
                else -> break
            }
        }
        if (n > partStart) {
            parts.add(n)
        }
        return Geometry(coords.copyOf(n), parts.toIntArray())
    }

    private fun zigzag(value: Int): Int {
        return (value ushr 1) xor -(value and 1)
    }

    private companion object {

        private const val CMD_MOVE_TO = 1
        private const val CMD_LINE_TO = 2
        private const val CMD_CLOSE_PATH = 7
    }
}

/**
 * Decoded geometry: x, y pairs in [coords]; part `i` ends before `partEnds[i]` (an index into
 * [coords]). Points are one coordinate per part, polygons one ring per part, outer rings
 * clockwise in screen coordinates and holes counter-clockwise. [minX] to [maxY] bound [coords],
 * empty (min above max) without coordinates.
 */
class Geometry(
    val coords: FloatArray,
    val partEnds: IntArray,
) {

    val minX: Float

    val minY: Float

    val maxX: Float

    val maxY: Float

    init {
        var x0 = Float.POSITIVE_INFINITY
        var y0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        var y1 = Float.NEGATIVE_INFINITY
        var i = 0
        while (i + 1 < coords.size) {
            x0 = minOf(x0, coords[i])
            x1 = maxOf(x1, coords[i])
            y0 = minOf(y0, coords[i + 1])
            y1 = maxOf(y1, coords[i + 1])
            i += 2
        }
        minX = x0
        minY = y0
        maxX = x1
        maxY = y1
    }
}
