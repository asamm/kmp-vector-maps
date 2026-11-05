/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import com.asamm.vectormaps.core.label.BackgroundLabelPass
import com.asamm.vectormaps.core.label.BoxAlignment
import com.asamm.vectormaps.core.label.CollisionBox
import com.asamm.vectormaps.core.label.LabelCandidate
import com.asamm.vectormaps.core.label.LabelDisplay
import com.asamm.vectormaps.core.label.LabelKind
import com.asamm.vectormaps.core.label.LabelText
import com.asamm.vectormaps.core.label.LabelViewport
import com.asamm.vectormaps.core.label.PathTextDirection
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
import com.asamm.vectormaps.mapsforge.core.util.MercatorProjection
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShape
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShapeKind
import com.asamm.vectormaps.mapsforge.map.layer.labels.LabelShapes
import com.asamm.vectormaps.mapsforge.map.layer.labels.TileBasedLabelStore
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap

/**
 * The label pass of a [MapsforgeRenderer] in `SEPARATE` mode: places the labels its tiles stored
 * and draws them through Mapsforge's own containers. A tile's labels show while the label store
 * keeps it ([TileBasedLabelStore.capacity] tiles, least recently used dropped), so the store must
 * hold at least the tiles one screen covers (`MapsforgeDataset.createRenderer`'s
 * `labelStoreTiles`), and a host keeps no more rendered tiles than it holds.
 */
class MapsforgeLabelPass internal constructor(
    private val labelStore: TileBasedLabelStore,
    private val tileSize: Int,
    graphicFactory: GraphicFactory,
) : BackgroundLabelPass() {

    // draw runs on one thread at a time
    private val matrix = graphicFactory.createMatrix()

    // the theme's symbol bitmaps live as long as the renderer; weak, so a dropped one goes
    private val symbolHashes = WeakHashMap<Bitmap, Long>()

    override fun collect(view: LabelViewport, isCancelled: () -> Boolean): List<LabelCandidate> {
        val bounds = view.worldBounds()
        val zoom = view.zoom.toByte()
        val upperLeft = Tile(
            MercatorProjection.pixelXToTileX(bounds.minX, zoom, tileSize),
            MercatorProjection.pixelYToTileY(bounds.minY, zoom, tileSize),
            zoom,
            tileSize,
        )
        val lowerRight = Tile(
            MercatorProjection.pixelXToTileX(bounds.maxX, zoom, tileSize),
            MercatorProjection.pixelYToTileY(bounds.maxY, zoom, tileSize),
            zoom,
            tileSize,
        )
        val items = labelStore.getVisibleItems(upperLeft, lowerRight)
        val candidates = ArrayList<LabelCandidate>(items.size)
        for (item in items) {
            if (isCancelled()) {
                return emptyList()
            }
            val shape = LabelShapes.of(item) ?: continue
            candidates.add(candidateOf(shape))
        }
        return candidates
    }

    /**
     * Draws the latest placement on a canvas the size of [view], not turned; nothing while the
     * placement is for another zoom. One thread at a time; AWT canvases only until M8, as Android
     * captions turn the canvas back themselves.
     */
    fun draw(canvas: Canvas, view: LabelViewport) {
        val prepared = prepared ?: return
        if (prepared.view.zoom != view.zoom) {
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
            source = shape.container,
        )
    }

    /**
     * A fingerprint of what makes two stored containers the same label: the same label from
     * neighbouring tiles gets the same id, in every run (A7).
     */
    private fun idOf(shape: LabelShape): Long {
        var hash = FNV_OFFSET
        hash = mix(hash = hash, value = shape.kind.ordinal.toLong())
        hash = mix(hash = hash, value = shape.priority.toLong())
        hash = mix(hash = hash, value = shape.display.ordinal.toLong())
        hash = mix(hash = hash, value = shape.x.toRawBits())
        hash = mix(hash = hash, value = shape.y.toRawBits())
        // every box, so labels sharing an anchor but placed apart differ
        for (box in shape.boxes) {
            hash = mix(hash = hash, value = box.centerX.toRawBits())
            hash = mix(hash = hash, value = box.centerY.toRawBits())
            hash = mix(hash = hash, value = box.halfWidth.toRawBits())
            hash = mix(hash = hash, value = box.halfHeight.toRawBits())
            hash = mix(hash = hash, value = box.angleDeg.toRawBits())
        }
        shape.text?.value?.forEach { char ->
            hash = mix(hash = hash, value = char.code.toLong())
        }
        // two symbols of one size at one anchor differ only by their image
        val symbol = (shape.container as? SymbolContainer)?.symbol
        if (symbol != null) {
            hash = mix(hash = hash, value = contentHash(symbol))
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
        var hash = FNV_OFFSET
        for (byte in png.toByteArray()) {
            hash = (hash xor (byte.toLong() and 0xFF)) * FNV_PRIME
        }
        synchronized(symbolHashes) {
            symbolHashes[bitmap] = hash
        }
        return hash
    }

    private fun mix(hash: Long, value: Long): Long {
        var result = hash
        for (shift in 0 until 64 step 8) {
            result = (result xor ((value ushr shift) and 0xFF)) * FNV_PRIME
        }
        return result
    }

    private companion object {

        const val FNV_OFFSET = -0x340d631b7bdddcdbL

        const val FNV_PRIME = 0x100000001b3L
    }
}
