/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2016 devemux86
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
package org.mapsforge.core.mapelements

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Matrix
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation

/**
 * The MapElementContainer is the abstract base class for annotations that can be placed on the
 * map, e.g. labels and icons.
 *
 * A MapElementContainer has a central pivot point, which denotes the geographic point for the
 * entity translated into absolute map pixels. The boundary denotes the space that the item requires
 * around this central point.
 *
 * A MapElementContainer has a priority (higher value means higher priority) that should be used to
 * determine the drawing order, i.e. elements with higher priority should be drawn before elements
 * with lower priority. If there is not enough space on the map, elements with lower priority should
 * then not be drawn.
 */
abstract class MapElementContainer protected constructor(
    xy: Point,
    display: Display,
    priority: Int,
) : Comparable<MapElementContainer> {

    @JvmField
    protected var boundaryAbsolute: Rectangle? = null

    @JvmField
    protected val display: Display

    val priority: Int

    @JvmField
    protected val xy: Point

    @Volatile
    @JvmField
    protected var clashRotationDegrees: Double = 0.0

    @Volatile
    @JvmField
    protected var clashRect: Rectangle? = null

    init {
        this.xy = xy
        this.display = display
        this.priority = priority
    }

    // lets subclasses read the display of another container, as Java's package access did
    internal val displayInternal: Display
        get() {
            return display
        }

    protected abstract fun getBoundary(): Rectangle?

    /**
     * Compares elements according to their priority, then display, then position.
     * The compare is consistent with equals.
     */
    override fun compareTo(other: MapElementContainer): Int {
        if (this.priority < other.priority) {
            return -1
        }
        if (this.priority > other.priority) {
            return 1
        }

        if (this.display != other.display) {
            if (this.display != Display.ALWAYS
                && other.display == Display.ALWAYS
            ) {
                return -1
            }
            if (this.display == Display.ALWAYS) {
                return 1
            }

            if (this.display != Display.ORDER
                && other.display == Display.ORDER
            ) {
                return -1
            }
            if (this.display == Display.ORDER) {
                return 1
            }

            when (this.display) {
                Display.NEVER -> {
                    return -1
                }
                Display.IFSPACE -> {
                    return 1
                }
                else -> {
                    throw IllegalArgumentException(
                        "Unknown Display state for MapElementContainer.compareTo()",
                    )
                }
            }
        }

        // If the priorities and display are the same, make a more detailed ordering.
        // Basically we don't want to allow two elements to be arbitrarily ordered,
        // because that makes drawing the elements non-deterministic.
        // This also makes the natural ordering of elements consistent with equals,
        // as it should be.
        return xy.compareTo(other.xy)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is MapElementContainer) {
            return false
        }
        if (this.priority != other.priority) {
            return false
        } else if (this.display != other.display) {
            return false
        } else if (this.xy != other.xy) {
            return false
        }
        return true
    }

    /**
     * Drawing method: element will draw itself on canvas shifted by origin point of canvas and
     * using the matrix if rotation is required.
     */
    abstract fun draw(canvas: Canvas?, origin: Point?, matrix: Matrix?, rotation: Rotation?)

    /**
     * Gets the pixel absolute boundary for this element.
     */
    protected open fun getBoundaryAbsolute(): Rectangle {
        if (boundaryAbsolute == null) {
            boundaryAbsolute = this.getBoundary()!!.shift(xy)
        }
        return boundaryAbsolute!!
    }

    fun intersects(rectangle: Rectangle, rotation: Rotation?): Boolean {
        val rect = this.getClashRect(rotation)

        return rect != null
            && rect.intersects(rectangle)
    }

    /**
     * Returns if MapElementContainers clash with each other, i.e. overlap.
     */
    open fun clashesWith(other: MapElementContainer, rotation: Rotation?): Boolean {
        // If any element is always drawn, the elements do not clash, otherwise do more checks
        if (Display.ALWAYS == this.display
            || Display.ALWAYS == other.display
        ) {
            return false
        }

        // If exactly one of the elements is order drawn, the elements do not clash, otherwise do
        // more checks
        if ((Display.ORDER == this.display) xor (Display.ORDER == other.display)) {
            return false
        }

        val rect1 = this.getClashRect(rotation)
        val rect2 = other.getClashRect(rotation)

        if (rect1 != null
            && rect1.intersects(rect2)
        ) {
            return true
        }

        return false
    }

    /**
     * The clash rectangle in absolute map pixels, cached for the last rotation; null without a
     * boundary.
     */
    open fun getClashRect(rotation: Rotation?): Rectangle? {
        val pointTextContainer: MapElementContainer = this

        var output: Rectangle? = null

        val boundary = getBoundary()
        if (boundary != null) {
            // All other fields used are final (read only)
            if (rotation!!.degrees.toDouble() == pointTextContainer.clashRotationDegrees
                && pointTextContainer.clashRect != null
            ) {
                return pointTextContainer.clashRect
            }

            val newRotation = Rotation(rotation.degrees, 0f, 0f)

            // We work in the absolute coordinate space of the map (intentionally)
            var x = pointTextContainer.xy.x
            var y = pointTextContainer.xy.y

            if (!Rotation.noRotation(newRotation)) {
                val rotated = newRotation.rotate(x, y, true)
                x = rotated.x
                y = rotated.y
            }

            output = Rectangle(
                x + boundary.left,
                y + boundary.top,
                x + boundary.right,
                y + boundary.bottom,
            )

            pointTextContainer.clashRect = output
            pointTextContainer.clashRotationDegrees = newRotation.degrees.toDouble()
        }

        return output
    }

    override fun hashCode(): Int {
        var result = 7
        result = 31 * result + xy.hashCode()
        result = 31 * result + priority
        return result
    }

    /**
     * The center point of this element, in absolute pixel coordinates.
     */
    val point: Point
        get() {
            return this.xy
        }

    /**
     * True if this is definitely not visible; false if it may or may not be visible.
     */
    open val isNotVisible: Boolean
        get() {
            return Display.NEVER == this.display
        }

    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("xy=")
        stringBuilder.append(this.xy)
        stringBuilder.append(", priority=")
        stringBuilder.append(this.priority)
        return stringBuilder.toString()
    }
}
