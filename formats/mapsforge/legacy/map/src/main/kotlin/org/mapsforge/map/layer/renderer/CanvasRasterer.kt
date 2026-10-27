/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2016-2020 devemux86
 * Copyright 2017 usrusr
 * Copyright 2020 Adrian Batzill
 * Copyright 2024-2025 Sublimis
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.layer.renderer

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Curve
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.GraphicUtils
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.graphics.Path
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.map.rendertheme.RenderContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Draws the shapes and labels of one render onto its canvas.
 */
open class CanvasRasterer(
    private val renderContext: RenderContext,
    graphicFactory: GraphicFactory,
) {

    private val linesCount: AtomicInteger? = if (DEBUG_COUNTS) AtomicInteger() else null

    private val pathsCount: AtomicInteger? = if (DEBUG_COUNTS) AtomicInteger() else null

    private val canvas: Canvas = graphicFactory.createCanvas()

    private val path: Path = graphicFactory.createPath()

    private val symbolMatrix: Matrix = graphicFactory.createMatrix()

    fun destroy() {
        canvas.destroy()

        if (DEBUG_COUNTS) {
            println("LINES: " + linesCount!!.get() + "  PATHS: " + pathsCount!!.get())
        }
    }

    /**
     * Draws `elements`, assumed sorted by drawing priority.
     */
    internal fun drawMapElements(elements: List<MapElementContainer>, tile: Tile) {
        for (element in elements) {
            element.draw(canvas, tile.origin, symbolMatrix, Rotation.NULL_ROTATION)
        }
    }

    internal fun fill(color: Int) {
        if (GraphicUtils.getAlpha(color) > 0) {
            canvas.fillColor(color)
        }
    }

    /**
     * Fills the area outside `insideArea` with `color`, to blank out what lies outside the map;
     * use it to overpaint with a transparent color, as it sets the PorterDuff mode.
     */
    internal fun fillOutsideAreas(color: Color, insideArea: Rectangle) {
        // Clamp input to prevent overwhelming the canvas with extreme coordinate values
        val area = insideArea.clampClipCoordinates(canvas.width.toDouble(), canvas.height.toDouble())

        canvas.setClipDifference(
            area.left.toFloat(),
            area.top.toFloat(),
            area.width.toFloat(),
            area.height.toFloat(),
        )
        canvas.fillColor(color)
        canvas.resetClip()
    }

    /**
     * Fills the area outside `insideArea` with `color`, to blank out what lies outside the map.
     */
    internal fun fillOutsideAreas(color: Int, insideArea: Rectangle) {
        // Clamp input to prevent overwhelming the canvas with extreme coordinate values
        val area = insideArea.clampClipCoordinates(canvas.width.toDouble(), canvas.height.toDouble())

        canvas.setClipDifference(
            area.left.toFloat(),
            area.top.toFloat(),
            area.width.toFloat(),
            area.height.toFloat(),
        )
        canvas.fillColor(color)
        canvas.resetClip()
    }

    internal fun setCanvasBitmap(bitmap: Bitmap) {
        canvas.setBitmap(bitmap)
    }

    private fun drawCircleContainer(shapePaintContainer: ShapePaintContainer) {
        val circleContainer = shapePaintContainer.shapeContainer as CircleContainer
        val point = circleContainer.point
        canvas.drawCircle(
            point.x.toInt(),
            point.y.toInt(),
            circleContainer.radius.toInt(),
            shapePaintContainer.paint,
        )
    }

    private fun drawPath(
        shapePaintContainer: ShapePaintContainer,
        coordinates: Array<Array<Point>>,
        dy: Float,
    ) {
        if (shapePaintContainer.curveStyle == Curve.CUBIC) {
            // When cubic, paths must be used.
            makeCubicPath(coordinates, dy, path)
            drawPath(shapePaintContainer)
        } else if (shapePaintContainer.paint.isComplexStyle) {
            // When complex (e.g. filled) style, paths must be used.
            makeLinesPath(coordinates, dy, path)
            drawPath(shapePaintContainer)
        } else {
            // When neither cubic nor complex style, use lines (esp. on Android):
            //   * To prevent libhwui.so "null pointer dereference" SIGSEGV crashes.
            //   * For performance.
            drawLines(shapePaintContainer, coordinates, dy)
        }
    }

    private fun drawPath(shapePaintContainer: ShapePaintContainer) {
        if (!path.isEmpty) {
            if (renderContext.rendererJob.displayModel.numberOfThreads > 1) {
                // Make sure setting the shader shift and actual drawing is synchronized,
                // since the paint object is shared between multiple threads.
                synchronized(shapePaintContainer.paint) {
                    shapePaintContainer.paint.setBitmapShaderShift(renderContext.rendererJob.tile.origin)
                    canvas.drawPath(path, shapePaintContainer.paint)
                }
            } else {
                canvas.drawPath(path, shapePaintContainer.paint)
            }

            if (DEBUG_COUNTS) {
                pathsCount!!.incrementAndGet()
            }
        }
    }

    private fun drawLines(
        shapePaintContainer: ShapePaintContainer,
        coordinates: Array<Array<Point>>,
        dy: Float,
    ) {
        if (renderContext.rendererJob.displayModel.numberOfThreads > 1) {
            // Make sure setting the shader shift and actual drawing is synchronized,
            // since the paint object is shared between multiple threads.
            synchronized(shapePaintContainer.paint) {
                shapePaintContainer.paint.setBitmapShaderShift(renderContext.rendererJob.tile.origin)
                canvas.drawLines(coordinates, dy, shapePaintContainer.paint)
            }
        } else {
            canvas.drawLines(coordinates, dy, shapePaintContainer.paint)
        }

        if (DEBUG_COUNTS) {
            linesCount!!.incrementAndGet()
        }
    }

    fun drawShapePaintContainer(shapePaintContainer: ShapePaintContainer) {
        val shapeContainer = shapePaintContainer.shapeContainer
        when (shapeContainer.shapeType) {
            ShapeType.CIRCLE -> {
                drawCircleContainer(shapePaintContainer)
            }

            ShapeType.POLYLINE -> {
                val polylineContainer = shapeContainer as PolylineContainer
                drawPath(
                    shapePaintContainer,
                    polylineContainer.coordinatesRelativeToOrigin,
                    shapePaintContainer.dy,
                )
            }
        }
    }

    private companion object {

        // counts paths vs. lines usage for performance diagnostics
        private const val DEBUG_COUNTS = false

        fun makeCubicPath(coordinates: Array<Array<Point>>, dy: Float, path: Path) {
            path.clear()

            for (innerList in coordinates) {
                val points = if (dy == 0f) {
                    innerList
                } else {
                    RendererUtils.parallelPath(innerList, dy.toDouble())
                }
                if (points.size >= 2) {
                    val p1 = floatArrayOf(points[0].x.toFloat(), points[0].y.toFloat())
                    val p2 = floatArrayOf(0.0f, 0.0f)
                    val p3 = floatArrayOf(0.0f, 0.0f)

                    // add first point
                    path.moveTo(p1[0], p1[1])

                    for (i in 1 until points.size) {
                        // get ending coordinates
                        p3[0] = points[i].x.toFloat()
                        p3[1] = points[i].y.toFloat()
                        p2[0] = 0.5f * (p1[0] + p3[0])
                        p2[1] = 0.5f * (p1[1] + p3[1])

                        // add spline over middle point and end on 'end' point
                        path.quadTo(p1[0], p1[1], p2[0], p2[1])

                        // store end point as start point for next section
                        p1[0] = p3[0]
                        p1[1] = p3[1]
                    }

                    // add last segment
                    path.quadTo(p2[0], p2[1], p3[0], p3[1])
                }
            }
        }

        fun makeLinesPath(coordinates: Array<Array<Point>>, dy: Float, path: Path) {
            path.clear()

            for (innerList in coordinates) {
                val points = if (dy == 0f) {
                    innerList
                } else {
                    RendererUtils.parallelPath(innerList, dy.toDouble())
                }
                if (points.size >= 2) {
                    path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())

                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x.toFloat(), points[i].y.toFloat())
                    }
                }
            }
        }
    }
}
