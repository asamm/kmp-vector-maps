/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A parsed MapLibre style: its sources and its layers in draw order. Properties and filters that
 * do not compile are left out of their layer and listed in [problems].
 */
class Style(
    val name: String,
    val sprite: String?,
    val sources: Map<String, StyleSource>,
    val layers: List<StyleLayer>,
    val problems: List<String>,
) {

    companion object {

        fun parse(text: String): Style {
            val root = Json.parseToJsonElement(text).jsonObject
            val problems = ArrayList<String>()
            val layers = (root["layers"] as? JsonArray).orEmpty().mapNotNull {
                if (it is JsonObject) {
                    parseLayer(it, problems)
                } else {
                    problems.add("layer $it: not an object")
                    null
                }
            }
            val sources = (root["sources"] as? JsonObject).orEmpty().mapNotNull { (id, json) ->
                (json as? JsonObject)?.let { id to parseSource(it) }
            }.toMap()
            return Style(
                name = root.string("name") ?: "",
                sprite = root.string("sprite"),
                sources = sources,
                layers = layers,
                problems = problems,
            )
        }

        private fun parseSource(json: JsonObject): StyleSource {
            return StyleSource(
                type = json.string("type") ?: "",
                url = json.string("url"),
                tiles = (json["tiles"] as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonPrimitive)?.takeIf { it.isString }?.content
                },
                encoding = json.string("encoding"),
            )
        }

        private fun parseLayer(json: JsonObject, problems: MutableList<String>): StyleLayer {
            val id = json.string("id") ?: ""
            val type = LayerType.of(json.string("type"))
            val filter = json["filter"]?.let {
                compile(id, "filter", problems) { Expression.compileFilter(it) }
            }
            fun properties(section: String): Map<String, Expression> {
                val obj = json[section] as? JsonObject ?: return emptyMap()
                return obj.mapNotNull { (key, value) ->
                    compile(id, key, problems) {
                        if (key == "color-relief-color") reliefStops(value) else Expression.compile(value)
                    }?.let { key to it }
                }.toMap()
            }
            val layout = properties("layout")
            return StyleLayer(
                id = id,
                type = type,
                source = json.string("source"),
                sourceLayer = json.string("source-layer"),
                minZoom = (json["minzoom"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                maxZoom = (json["maxzoom"] as? JsonPrimitive)?.doubleOrNull ?: 24.0,
                visible = (layout["visibility"] as? Constant)?.eval(EvalContext()) != "none",
                filter = filter,
                paint = properties("paint"),
                layout = layout,
            )
        }

        // ["interpolate", ["linear"], ["elevation"], metres, colour, ...] as a constant list of
        // metres to colour, for the host that draws the layer
        private fun reliefStops(json: JsonElement): Expression {
            val raw = json as? JsonArray
            require(raw != null
                && raw.size >= 5
                && raw.size % 2 == 1
                && raw[0] == JsonPrimitive("interpolate")
                && raw[1] == JsonArray(listOf(JsonPrimitive("linear")))
                && raw[2] == JsonArray(listOf(JsonPrimitive("elevation")))
            ) {
                "not a linear interpolation of elevation"
            }
            val stops = (3 until raw.size step 2).map { i ->
                val metres = (raw[i] as? JsonPrimitive)?.doubleOrNull
                val color = (raw[i + 1] as? JsonPrimitive)?.takeIf { it.isString }?.let { Colors.parse(it.content) }
                require(metres != null
                    && color != null
                ) {
                    "stop ${raw[i]}, ${raw[i + 1]}"
                }
                metres to color
            }
            return Constant(stops)
        }

        private fun compile(
            id: String,
            key: String,
            problems: MutableList<String>,
            block: () -> Expression,
        ): Expression? {
            // any malformed JSON shape leaves this property out, not the whole style
            return try {
                block()
            } catch (e: RuntimeException) {
                problems.add("$id $key: ${e.message}")
                null
            }
        }

        private fun JsonObject.string(key: String): String? {
            return (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
    }
}

enum class LayerType {
    BACKGROUND,
    FILL,
    LINE,
    SYMBOL,
    CIRCLE,
    RASTER,
    HILLSHADE,
    COLOR_RELIEF,
    FILL_EXTRUSION,
    HEATMAP,
    UNKNOWN,
    ;

    companion object {

        fun of(name: String?): LayerType {
            return entries.firstOrNull { it.name.lowercase().replace('_', '-') == name } ?: UNKNOWN
        }
    }
}

/**
 * A style source; only what the renderer passes on to the host.
 */
class StyleSource(
    val type: String,
    val url: String?,
    val tiles: List<String>,
    val encoding: String?,
)

class StyleLayer(
    val id: String,
    val type: LayerType,
    val source: String?,
    val sourceLayer: String?,
    val minZoom: Double,
    val maxZoom: Double,
    val visible: Boolean,
    val filter: Expression?,
    val paint: Map<String, Expression>,
    val layout: Map<String, Expression>,
) {

    /**
     * True when the layer draws at `zoom`: visible, `minzoom <= zoom < maxzoom`.
     */
    fun isActive(zoom: Double): Boolean {
        return visible
            && zoom >= minZoom
            && zoom < maxZoom
    }
}
