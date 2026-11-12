/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A parsed MapLibre style: its layers in draw order. Properties and filters that do not compile
 * are left out of their layer and listed in [problems].
 */
class Style(
    val name: String,
    val sprite: String?,
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
            return Style(
                name = root.string("name") ?: "",
                sprite = root.string("sprite"),
                layers = layers,
                problems = problems,
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
                    compile(id, key, problems) { Expression.compile(value) }?.let { key to it }
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
