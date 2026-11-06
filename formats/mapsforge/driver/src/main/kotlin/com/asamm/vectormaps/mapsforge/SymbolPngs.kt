/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.label.LabelId
import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap

/**
 * The content hash of each theme symbol bitmap's PNG, the same in every run, and the PNG itself
 * for a [FeatureQuery][com.asamm.vectormaps.core.FeatureQuery], each cached per bitmap; the PNG
 * only once a query asks. The theme's bitmaps live as long as the renderer; weak keys let a
 * dropped one go.
 */
internal class SymbolPngs {

    private val ids = WeakHashMap<Bitmap, Long>()

    private val pngs = WeakHashMap<Bitmap, SymbolPng>()

    fun idOf(bitmap: Bitmap): Long {
        synchronized(ids) {
            ids[bitmap]?.let {
                return it
            }
        }
        val id = encode(bitmap).id
        synchronized(ids) {
            ids[bitmap] = id
        }
        return id
    }

    fun of(bitmap: Bitmap): SymbolPng {
        synchronized(pngs) {
            pngs[bitmap]?.let {
                return it
            }
        }
        val result = encode(bitmap)
        synchronized(pngs) {
            pngs[bitmap] = result
        }
        return result
    }

    private fun encode(bitmap: Bitmap): SymbolPng {
        val out = ByteArrayOutputStream()
        synchronized(bitmap.mutex) {
            bitmap.compress(out)
        }
        val png = out.toByteArray()
        var hash = LabelId.START
        for (byte in png) {
            hash = LabelId.mixByte(hash = hash, byte = byte.toInt())
        }
        return SymbolPng(id = hash, png = png)
    }
}

/**
 * @param id FNV-1a over [png]
 */
internal class SymbolPng(
    val id: Long,
    val png: ByteArray,
)
