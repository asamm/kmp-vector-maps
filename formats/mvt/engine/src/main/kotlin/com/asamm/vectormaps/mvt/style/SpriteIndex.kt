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

    companion object {

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
                        pixelRatio = (o["pixelRatio"] as? JsonPrimitive)?.doubleOrNull ?: 1.0,
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
