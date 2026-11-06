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
 * The PNG of each theme symbol bitmap and its content hash, the same in every run, cached per
 * bitmap. The theme's bitmaps live as long as the renderer; weak keys let a dropped one go.
 */
internal class SymbolPngs {

    private val pngs = WeakHashMap<Bitmap, SymbolPng>()

    fun of(bitmap: Bitmap): SymbolPng {
        synchronized(pngs) {
            pngs[bitmap]?.let {
                return it
            }
        }
        val out = ByteArrayOutputStream()
        synchronized(bitmap.mutex) {
            bitmap.compress(out)
        }
        val png = out.toByteArray()
        var hash = LabelId.START
        for (byte in png) {
            hash = LabelId.mixByte(hash = hash, byte = byte.toInt())
        }
        val result = SymbolPng(id = hash, png = png)
        synchronized(pngs) {
            pngs[bitmap] = result
        }
        return result
    }
}

/**
 * @param id FNV-1a over [png]
 */
internal class SymbolPng(
    val id: Long,
    val png: ByteArray,
)
