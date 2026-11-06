/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.TileRequest
import com.asamm.vectormaps.core.label.BoxAlignment
import com.asamm.vectormaps.core.label.CollisionBox
import com.asamm.vectormaps.core.label.LabelCandidate
import com.asamm.vectormaps.core.label.LabelDisplay
import com.asamm.vectormaps.core.label.LabelId
import com.asamm.vectormaps.core.label.LabelKind
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PathTextDirection
import com.asamm.vectormaps.core.label.PreparedLabels
import com.asamm.vectormaps.core.label.TileLabelSource
import com.asamm.vectormaps.core.label.TiledLabelPass
import com.asamm.vectormaps.mapsforge.core.graphics.Bitmap
import com.asamm.vectormaps.mapsforge.core.graphics.Canvas
import com.asamm.vectormaps.mapsforge.core.graphics.Display
import com.asamm.vectormaps.mapsforge.core.graphics.GraphicFactory
import com.asamm.vectormaps.mapsforge.core.graphics.TextOrientation
import com.asamm.vectormaps.mapsforge.core.mapelements.MapElementContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.PointTextContainer
import com.asamm.vectormaps.mapsforge.core.mapelements.SymbolContainer
import com.asamm.vectormaps.mapsforge.core.model.Point
import com.asamm.vectormaps.mapsforge.core.model.Rotation
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShape
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShapeKind
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShapes
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap

/**
 * The label pass of a [MapsforgeRenderer] (D39): places the labels its tiles stored in the
 * vendored label store and draws them through Mapsforge's own containers. The store holds at least
 * the tiles one screen covers (`MapsforgeDataset.createRenderer`'s `labelStoreTiles`).
 */
class MapsforgeLabelPass internal constructor(
    labelStore: TileBasedLabelStore,
    tileSize: Int,
    graphicFactory: GraphicFactory,
) : TiledLabelPass<MapElementContainer>(
    source = StoreTiles(store = labelStore, tileSize = tileSize),
    tileSize = tileSize,
) {

    // draw runs on one thread at a time
    private val matrix = graphicFactory.createMatrix()

    // the theme's symbol bitmaps live as long as the renderer; weak, so a dropped one goes
    private val symbolHashes = WeakHashMap<Bitmap, Long>()

    override fun candidateOf(label: MapElementContainer, tile: TileRequest): LabelCandidate? {
        return LabelShapes.of(label)?.let(::candidateOf)
    }

    /**
     * Draws [prepared], by default the latest placement, on a canvas the size of [view], not
     * turned; nothing while it is for another zoom. One thread at a time; a turned [view] on AWT
     * canvases only until M8, as Android captions turn the canvas back themselves.
     */
    fun draw(canvas: Canvas, view: LabelViewport, prepared: PreparedLabels? = this.prepared) {
        if (prepared == null
            || prepared.view.zoom != view.zoom
        ) {
            return
        }
        val origin = Point(view.centerX - view.widthPx / 2.0, view.centerY - view.heightPx / 2.0)
        val rotation = Rotation(view.rotationDeg.toFloat(), view.widthPx / 2.0f, view.heightPx / 2.0f)
        val turned = !Rotation.noRotation(rotation)
        for (placed in prepared.placed) {
            val container = placed.candidate.source as MapElementContainer
            // AWT captions place themselves upright on the screen; symbols and way texts draw in
            // the map's frame on the turned canvas
            if (container is PointTextContainer
                || !turned
            ) {
                container.draw(canvas, origin, matrix, rotation)
                continue
            }
            canvas.rotate(rotation)
            try {
                container.draw(canvas, origin, matrix, rotation)
            } finally {
                canvas.rotate(-rotation.degrees, rotation.px, rotation.py)
            }
        }
    }

    private fun candidateOf(shape: LabelShape): LabelCandidate {
        val text = shape.text?.takeIf { it.heightPx > 0.0 }?.let {
            LabelText(
                value = it.value,
                sizePx = it.heightPx.toFloat(),
                fillArgb = it.fillArgb,
                haloArgb = it.haloArgb,
                haloWidthPx = it.haloWidthPx,
            )
        }
        return LabelCandidate(
            id = idOf(shape),
            kind = when (shape.kind) {
                LabelShapeKind.POINT -> LabelKind.POINT
                LabelShapeKind.LINE_SYMBOL -> LabelKind.LINE_SYMBOL
                LabelShapeKind.PATH_TEXT -> LabelKind.PATH_TEXT
            },
            x = shape.x,
            y = shape.y,
            priority = shape.priority,
            boxes = shape.boxes.map {
                CollisionBox(
                    centerX = it.centerX,
                    centerY = it.centerY,
                    halfWidth = it.halfWidth,
                    halfHeight = it.halfHeight,
                    angleDeg = it.angleDeg,
                    alignment = if (it.turnsWithMap) BoxAlignment.MAP else BoxAlignment.VIEWPORT,
                )
            },
            display = when (shape.display) {
                Display.ALWAYS -> LabelDisplay.ALWAYS
                Display.ORDER -> LabelDisplay.ORDER
                else -> LabelDisplay.IF_SPACE
            },
            text = text,
            path = shape.path,
            direction = when (shape.orientation) {
                TextOrientation.AUTO_DOWN -> PathTextDirection.UPSIDE_DOWN
                TextOrientation.RIGHT -> PathTextDirection.FORWARD
                TextOrientation.LEFT -> PathTextDirection.REVERSE
                else -> PathTextDirection.UPRIGHT
            },
            // equal captions close together clash, as Mapsforge's PointTextContainer
            sameTextRadiusPx = if (shape.kind == LabelShapeKind.POINT) SAME_TEXT_RADIUS_PX else 0.0,
            source = shape.container,
        )
    }

    /**
     * A fingerprint of what makes two stored containers the same label: the same label from
     * neighbouring tiles gets the same id, in every run (A7).
     */
    private fun idOf(shape: LabelShape): Long {
        var hash = LabelId.START
        hash = LabelId.mix(hash = hash, value = shape.kind.ordinal.toLong())
        hash = LabelId.mix(hash = hash, value = shape.priority.toLong())
        hash = LabelId.mix(hash = hash, value = shape.display.ordinal.toLong())
        hash = LabelId.mix(hash = hash, value = shape.x.toRawBits())
        hash = LabelId.mix(hash = hash, value = shape.y.toRawBits())
        // every box, so labels sharing an anchor but placed apart differ
        for (box in shape.boxes) {
            hash = LabelId.mix(hash = hash, value = box.centerX.toRawBits())
            hash = LabelId.mix(hash = hash, value = box.centerY.toRawBits())
            hash = LabelId.mix(hash = hash, value = box.halfWidth.toRawBits())
            hash = LabelId.mix(hash = hash, value = box.halfHeight.toRawBits())
            hash = LabelId.mix(hash = hash, value = box.angleDeg.toRawBits())
        }
        shape.text?.value?.let {
            hash = LabelId.mix(hash = hash, text = it)
        }
        // two symbols of one size at one anchor differ only by their image
        val symbol = (shape.container as? SymbolContainer)?.symbol
        if (symbol != null) {
            hash = LabelId.mix(hash = hash, value = contentHash(symbol))
        }
        return hash
    }

    /**
     * A hash of the bitmap's PNG, the same in every run; computed once per bitmap.
     */
    private fun contentHash(bitmap: Bitmap): Long {
        synchronized(symbolHashes) {
            symbolHashes[bitmap]?.let {
                return it
            }
        }
        val png = ByteArrayOutputStream()
        synchronized(bitmap.mutex) {
            bitmap.compress(png)
        }
        var hash = LabelId.START
        for (byte in png.toByteArray()) {
            hash = LabelId.mixByte(hash = hash, byte = byte.toInt())
        }
        synchronized(symbolHashes) {
            symbolHashes[bitmap] = hash
        }
        return hash
    }

    // the vendored store, read a tile at a time; its `requiresTile`, unused, then sees one tile
    private class StoreTiles(
        private val store: TileBasedLabelStore,
        private val tileSize: Int,
    ) : TileLabelSource<MapElementContainer> {

        override val capacity: Int
            get() {
                return store.capacity
            }

        override fun labelsOf(tile: TileRequest): List<MapElementContainer>? {
            val mapsforgeTile = Tile(tile.x, tile.y, tile.zoom.toByte(), tileSize)
            return store.getVisibleItems(mapsforgeTile, mapsforgeTile)
        }
    }

    private companion object {

        // Mapsforge's value
        const val SAME_TEXT_RADIUS_PX = 200.0
    }
}
