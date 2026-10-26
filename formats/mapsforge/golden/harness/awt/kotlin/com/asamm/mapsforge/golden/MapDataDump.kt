/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.graphics.SymbolOrientation
import org.mapsforge.core.graphics.TextOrientation
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.awt.graphics.AwtPaint
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.datastore.Way
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.layer.renderer.StandardRenderer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File
import java.lang.reflect.Field
import java.util.Locale

/**
 * Font-independent text dumps of what the reader returns and of what the theme asks to draw, per
 * tile; they gate the reader and theme conversions.
 */
object MapDataDump {

    /**
     * Every POI and way `readMapData` returns per tile, in reader order; way coordinates as point
     * counts per ring and one hash.
     */
    fun writeReader(store: MapDataStore, tiles: List<Tile>, file: File) {
        val out = mutableListOf<String>()
        for (tile in tiles) {
            val result = checkNotNull(store.readMapData(tile)) {
                "writeReader(), no data for $tile"
            }
            out += "tile ${tile.zoomLevel}/${tile.tileX}/${tile.tileY} water=${result.isWater}"
            result.pois.forEach { out += "  poi ${describePoi(it)}" }
            result.ways.forEach { out += "  way ${describeWay(it)}" }
        }
        file.writeText(out.joinToString(separator = "\n", postfix = "\n"))
    }

    /**
     * Every render-theme callback per tile, in call order, as Mapsforge's own `StandardRenderer`
     * makes them for the read data.
     */
    fun writeTheme(
        store: MapDataStore,
        themeFuture: RenderThemeFuture,
        displayModel: DisplayModel,
        tiles: List<Tile>,
        file: File,
    ) {
        val out = mutableListOf<String>()
        val renderer = RecordingRenderer(store = store, out = out)
        for (tile in tiles) {
            out += "tile ${tile.zoomLevel}/${tile.tileX}/${tile.tileY}"
            val job = RendererJob(tile, store, themeFuture, displayModel, 1.0f, false, false)
            val context = RenderContext(job, AwtGraphicFactory.INSTANCE)
            try {
                renderer.processReadMapData(context, store.readMapData(tile))
            } finally {
                context.destroy()
            }
        }
        file.writeText(out.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun describePoi(poi: PointOfInterest): String {
        return "l=${poi.layer} ${latLong(poi.position)} ${tags(poi.tags)}"
    }

    private fun describeWay(way: Way): String {
        var hash = 1
        for (ring in way.latLongs) {
            for (point in ring) {
                hash = 31 * hash + point.latitude.hashCode()
                hash = 31 * hash + point.longitude.hashCode()
            }
        }
        val points = way.latLongs.joinToString(separator = "+") { it.size.toString() }
        val label = way.labelPosition?.let { latLong(it) } ?: "-"
        return "l=${way.layer} points=$points coords=${Integer.toHexString(hash)} label=$label ${tags(way.tags)}"
    }

    private fun latLong(point: LatLong): String {
        return String.format(Locale.ROOT, "%.7f,%.7f", point.latitude, point.longitude)
    }

    private fun tags(tags: List<Tag>): String {
        return tags.joinToString(separator = ";") { "${it.key}=${it.value}".replace("\n", "\\n") }
    }

    private fun num(value: Float): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

    /**
     * Mapsforge's own renderer with every callback recorded instead of drawn.
     */
    private class RecordingRenderer(store: MapDataStore, private val out: MutableList<String>) :
        StandardRenderer(store, AwtGraphicFactory.INSTANCE, false) {

        private val recorder = Recorder()

        override fun getRenderCallback(): MyRenderCallback {
            return recorder
        }

        /**
         * One line per callback; paints with their full drawing state, symbols as their size.
         */
        inner class Recorder : MyRenderCallback() {

            override fun renderArea(
                renderContext: RenderContext,
                fill: Paint,
                stroke: Paint,
                level: Int,
                way: PolylineContainer,
            ) {
                record("area level=$level fill=${paint(fill)} stroke=${paint(stroke)} ${way(way)}")
            }

            override fun renderAreaCaption(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                caption: String,
                horizontalOffset: Float,
                verticalOffset: Float,
                fill: Paint,
                stroke: Paint,
                position: Position,
                maxTextWidth: Int,
                way: PolylineContainer,
            ) {
                record(
                    "areaCaption \"$caption\" p=$priority ${display.name} ${position.name} " +
                        "off=${num(horizontalOffset)},${num(verticalOffset)} max=$maxTextWidth " +
                        "fill=${paint(fill)} stroke=${paint(stroke)} ${way(way)}",
                )
            }

            override fun renderAreaSymbol(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                symbol: Bitmap,
                way: PolylineContainer,
            ) {
                record("areaSymbol p=$priority ${display.name} ${symbol(symbol)} ${way(way)}")
            }

            override fun renderPointOfInterestCaption(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                caption: String,
                horizontalOffset: Float,
                verticalOffset: Float,
                fill: Paint,
                stroke: Paint,
                position: Position,
                maxTextWidth: Int,
                poi: PointOfInterest,
            ) {
                record(
                    "poiCaption \"$caption\" p=$priority ${display.name} ${position.name} " +
                        "off=${num(horizontalOffset)},${num(verticalOffset)} max=$maxTextWidth " +
                        "fill=${paint(fill)} stroke=${paint(stroke)} ${latLong(poi.position)}",
                )
            }

            override fun renderPointOfInterestCircle(
                renderContext: RenderContext,
                radius: Float,
                fill: Paint,
                stroke: Paint,
                level: Int,
                poi: PointOfInterest,
            ) {
                record(
                    "poiCircle r=${num(radius)} level=$level fill=${paint(fill)} stroke=${paint(stroke)} " +
                        latLong(poi.position),
                )
            }

            override fun renderPointOfInterestSymbol(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                boundary: Rectangle?,
                symbol: Bitmap,
                poi: PointOfInterest,
            ) {
                record("poiSymbol p=$priority ${display.name} ${symbol(symbol)} ${latLong(poi.position)}")
            }

            override fun renderWay(
                renderContext: RenderContext,
                stroke: Paint,
                dy: Float,
                curveStyle: Curve,
                level: Int,
                way: PolylineContainer,
            ) {
                record("way level=$level stroke=${paint(stroke)} dy=${num(dy)} ${curveStyle.name} ${way(way)}")
            }

            override fun renderWaySymbol(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                symbol: Bitmap,
                dy: Float,
                boundary: Rectangle?,
                repeat: Boolean,
                repeatGap: Float,
                repeatStart: Float,
                symbolOrientation: SymbolOrientation,
                way: PolylineContainer,
            ) {
                record(
                    "waySymbol p=$priority ${display.name} ${symbol(symbol)} dy=${num(dy)} repeat=$repeat " +
                        "gap=${num(repeatGap)} start=${num(repeatStart)} ${symbolOrientation.name} ${way(way)}",
                )
            }

            override fun renderWayText(
                renderContext: RenderContext,
                display: Display,
                priority: Int,
                text: String,
                dy: Float,
                fill: Paint,
                stroke: Paint,
                repeat: Boolean,
                repeatGap: Float,
                repeatStart: Float,
                textOrientation: TextOrientation,
                way: PolylineContainer,
            ) {
                record(
                    "wayText \"$text\" p=$priority ${display.name} dy=${num(dy)} " +
                        "fill=${paint(fill)} stroke=${paint(stroke)} repeat=$repeat " +
                        "gap=${num(repeatGap)} start=${num(repeatStart)} ${textOrientation.name} ${way(way)}",
                )
            }

            private fun record(line: String) {
                out += "  $line"
            }

            private fun paint(paint: Paint): String {
                val awtPaint = paint as? AwtPaint ?: return "?"
                return PaintFields.describe(awtPaint)
            }

            private fun symbol(symbol: Bitmap): String {
                return "${symbol.width}x${symbol.height}"
            }

            private fun way(way: PolylineContainer): String {
                return "on ${tags(way.tags)}"
            }
        }
    }

    /**
     * Drawing state of an `AwtPaint` that its public API hides: text size, dash pattern, cap, join,
     * style and pattern size, read reflectively from the class both Mapsforge copies share.
     */
    private object PaintFields {

        private val textSize = field("textSize")
        private val dashes = field("strokeDasharray")
        private val cap = field("cap")
        private val join = field("join")
        private val style = field("style")
        private val shaderWidth = field("shaderWidth")
        private val shaderHeight = field("shaderHeight")

        fun describe(paint: AwtPaint): String {
            val dash = (dashes.get(paint) as FloatArray?)?.joinToString(separator = ",") { num(it) } ?: "-"
            return "#${Integer.toHexString(paint.color)}/${num(paint.strokeWidth)}" +
                "/t${num(textSize.getFloat(paint))}/d$dash/c${cap.getInt(paint)}/j${join.getInt(paint)}" +
                "/s${(style.get(paint) as Enum<*>?)?.name ?: "-"}/p${shaderWidth.getInt(paint)}x${shaderHeight.getInt(paint)}"
        }

        private fun field(name: String): Field {
            return AwtPaint::class.java.getDeclaredField(name).apply {
                isAccessible = true
            }
        }
    }
}
