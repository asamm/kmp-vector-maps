/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A sprite sheet's index (`sprite.json`): each image's rectangle in the sheet PNG.
 */
class SpriteIndex(
    val images: Map<String, SpriteImage>,
) {

    /**
     * Image pixels per style pixel of image `name`, 2 in an `@2x` sheet; 1 when unknown.
     */
    fun pixelRatio(name: String): Float {
        return images[name]?.pixelRatio?.toFloat() ?: 1f
    }

    companion object {

        private const val MIN_PIXEL_RATIO = 0.25

        private const val MAX_PIXEL_RATIO = 16.0

        fun parse(text: String): SpriteIndex {
            val root = Json.parseToJsonElement(text).jsonObject
            return SpriteIndex(
                root.mapNotNull { (name, value) ->
                    val o = value as? JsonObject ?: return@mapNotNull null
                    fun int(key: String): Int {
                        return (o[key] as? JsonPrimitive)?.intOrNull ?: 0
                    }
                    name to SpriteImage(
                        x = int("x"),
                        y = int("y"),
                        width = int("width"),
                        height = int("height"),
                        // a malformed ratio would size icons and patterns to nothing or infinity
                        pixelRatio = (o["pixelRatio"] as? JsonPrimitive)?.doubleOrNull
                            ?.takeIf { it in MIN_PIXEL_RATIO..MAX_PIXEL_RATIO }
                            ?: 1.0,
                    )
                }.toMap(),
            )
        }
    }
}

class SpriteImage(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pixelRatio: Double,
)
