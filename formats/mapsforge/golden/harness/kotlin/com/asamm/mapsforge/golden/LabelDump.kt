/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.mapelements.PointTextContainer
import org.mapsforge.core.mapelements.SymbolContainer
import org.mapsforge.core.mapelements.WayTextContainer
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.layer.labels.LabelStore
import java.io.File
import java.util.Locale

/**
 * Text dump of a label store, one section per tile in store order — the order Locus draws in its
 * separate pass — so labels compare without depending on fonts.
 */
object LabelDump {

    fun write(store: LabelStore, tiles: List<Tile>, file: File) {
        val lines = mutableListOf<String>()
        for (tile in tiles) {
            lines += "tile ${tile.zoomLevel}/${tile.tileX}/${tile.tileY}"
            // one tile per query, so the list keeps the order the renderer stored
            store.getVisibleItems(tile, tile)
                .forEach { lines += "  ${describe(it)}" }
        }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun describe(item: MapElementContainer): String {
        // null for labels that are never shown, such as a transparent caption
        val clash = item.getClashRect(Rotation.NULL_ROTATION)
        val clashText = if (clash == null) {
            "null"
        } else {
            String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", clash.left, clash.top, clash.right, clash.bottom)
        }
        val geometry = String.format(
            Locale.ROOT,
            "p=%d xy=%.2f,%.2f clash=%s",
            item.priority,
            item.point.x,
            item.point.y,
            clashText,
        )
        return when (item) {
            is PointTextContainer -> "text $geometry \"${item.text}\" withSymbol=${item.symbolContainer != null}"
            is SymbolContainer -> "symbol $geometry ${symbolKey(item.symbol)}"
            // the way text itself is private in Mapsforge; priority and clash box identify it
            is WayTextContainer -> "waytext $geometry"
            else -> "other ${item.javaClass.simpleName} $geometry"
        }
    }

    private fun symbolKey(symbol: Bitmap): String {
        val image = AwtGraphicFactory.getBitmap(symbol)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return "${image.width}x${image.height}#${Integer.toHexString(pixels.contentHashCode())}"
    }
}
