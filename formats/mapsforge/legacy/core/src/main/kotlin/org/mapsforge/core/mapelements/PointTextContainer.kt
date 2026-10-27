/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
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

import org.mapsforge.core.graphics.Display
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Position
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation

/**
 * A point container, that holds the x-y coordinates of a point, a text variable, two paint
 * objects, and a reference on a symbolContainer, if the text is connected with a POI.
 */
abstract class PointTextContainer protected constructor(
    point: Point,
    horizontalOffset: Double,
    verticalOffset: Double,
    display: Display,
    priority: Int,
    text: String,
    paintFront: Paint,
    paintBack: Paint?,
    symbolContainer: SymbolContainer?,
    position: Position,
    maxTextWidth: Int,
) : MapElementContainer(point, display, priority) {

    @JvmField
    val horizontalOffset: Double

    final override val isNotVisible: Boolean

    @JvmField
    val maxTextWidth: Int

    @JvmField
    val paintBack: Paint?

    @JvmField
    val paintFront: Paint

    @JvmField
    val position: Position

    @JvmField
    val symbolContainer: SymbolContainer?

    @JvmField
    val text: String

    @JvmField
    val verticalOffset: Double

    init {
        this.maxTextWidth = maxTextWidth
        this.text = text
        this.symbolContainer = symbolContainer
        this.paintFront = paintFront
        this.paintBack = paintBack
        this.position = position
        this.horizontalOffset = horizontalOffset
        this.verticalOffset = verticalOffset
        this.isNotVisible = super.isNotVisible
            || (this.paintFront.isTransparent
                && (this.paintBack == null || this.paintBack.isTransparent))
    }

    final override fun clashesWith(other: MapElementContainer, rotation: Rotation?): Boolean {
        // If any element is always drawn, the elements do not clash, otherwise do more checks
        if (Display.ALWAYS == this.display
            || Display.ALWAYS == other.displayInternal
        ) {
            return false
        }

        // If exactly one of the elements is order drawn, the elements do not clash, otherwise do
        // more checks
        if ((Display.ORDER == this.display) xor (Display.ORDER == other.displayInternal)) {
            return false
        }

        val rect1 = this.getClashRect(rotation)
        val rect2 = other.getClashRect(rotation)

        if (rect1 != null
            && rect2 != null
            && rect1.intersects(rect2)
        ) {
            return true
        }

        if (other !is PointTextContainer) {
            return false
        }

        val ptc: PointTextContainer = other

        if (this.text == ptc.text
            && this.xy.distance(ptc.xy) < 200
        ) {
            return true
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) {
            return false
        }
        if (other !is PointTextContainer) {
            return false
        }
        if (this.text != other.text) {
            return false
        }
        return true
    }

    /**
     * Gets the pixel absolute boundary for this element.
     */
    override fun getBoundaryAbsolute(): Rectangle {
        val result = super.getBoundaryAbsolute()
        // we need to add the offset in this case as it is not applied automatically.
        return result.shift(Point(this.horizontalOffset, this.verticalOffset))
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + text.hashCode()
        return result
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(super.toString())
        stringBuilder.append(", text=")
        stringBuilder.append(this.text)
        return stringBuilder.toString()
    }

    /**
     * New rotated x,y position of the container relative to the origin point.
     */
    protected open fun getRotatedRelativePosition(
        originX: Double,
        originY: Double,
        rotation: Rotation?,
    ): Point {
        return getRotatedRelativePosition(this, originX, originY, rotation)
    }

    // the body of the static getRotatedRelativePosition, here for protected access
    private fun rotatedRelativePosition(
        originX: Double,
        originY: Double,
        rotation: Rotation?,
    ): Point {
        val pointTextContainer = this
        var x = pointTextContainer.xy.x - originX
        var y = pointTextContainer.xy.y - originY

        if (!Rotation.noRotation(rotation)) {
            val rotated = rotation!!.rotate(x, y, true)
            x = rotated.x
            y = rotated.y
        }

        // The offsets can only be applied after rotation
        x += pointTextContainer.horizontalOffset + pointTextContainer.getBoundary()!!.left
        y += pointTextContainer.verticalOffset + pointTextContainer.getBoundary()!!.top

        return Point(x, y)
    }

    /**
     * See org.mapsforge.map.rendertheme.renderinstruction.Caption#Caption,
     * org.mapsforge.map.android.graphics.AndroidPointTextContainer#AndroidPointTextContainer,
     * or org.mapsforge.map.awt.graphics.AwtPointTextContainer#AwtPointTextContainer.
     *
     * @return Clash rectangle in absolute coordinate space
     */
    final override fun getClashRect(rotation: Rotation?): Rectangle? {
        val pointTextContainer = this

        if (isNotVisible) {
            return null
        }

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

        // The offsets can only be applied after rotation
        x += pointTextContainer.horizontalOffset
        y += pointTextContainer.verticalOffset

        var textOffsetX: Double
        var textOffsetY: Double

        // Implementation of the constructors mentioned above allows such use of boundary dimensions
        textOffsetX = pointTextContainer.getBoundary()!!.width
        textOffsetY = pointTextContainer.getBoundary()!!.height

        val output: Rectangle

        when (pointTextContainer.position) {
            Position.BELOW -> {
                // Text is positioned centered-below x,y
                textOffsetX /= 2
                output = Rectangle(x - textOffsetX, y, x + textOffsetX, y + textOffsetY)
            }
            Position.ABOVE -> {
                // Text is positioned centered-above x,y
                textOffsetX /= 2
                output = Rectangle(x - textOffsetX, y - textOffsetY, x + textOffsetX, y)
            }
            Position.BELOW_LEFT -> {
                // Text is positioned below-left of x,y
                output = Rectangle(x - textOffsetX, y, x, y + textOffsetY)
            }
            Position.ABOVE_LEFT -> {
                // Text is positioned above-left of x,y
                output = Rectangle(x - textOffsetX, y - textOffsetY, x, y)
            }
            Position.LEFT -> {
                // Text is positioned centered-left of x,y
                textOffsetY /= 2
                output = Rectangle(x - textOffsetX, y - textOffsetY, x, y + textOffsetY)
            }
            Position.BELOW_RIGHT -> {
                // Text is positioned below-right of x,y
                output = Rectangle(x, y, x + textOffsetX, y + textOffsetY)
            }
            Position.ABOVE_RIGHT -> {
                // Text is positioned above-right of x,y
                output = Rectangle(x, y - textOffsetY, x + textOffsetX, y)
            }
            Position.RIGHT -> {
                // Text is positioned centered-right of x,y
                textOffsetY /= 2
                output = Rectangle(x, y - textOffsetY, x + textOffsetX, y + textOffsetY)
            }
            // CENTER and the default for any other position
            else -> {
                // Text is positioned centered on x,y
                textOffsetX /= 2
                textOffsetY /= 2
                output = Rectangle(
                    x - textOffsetX,
                    y - textOffsetY,
                    x + textOffsetX,
                    y + textOffsetY,
                )
            }
        }

        pointTextContainer.clashRect = output
        pointTextContainer.clashRotationDegrees = newRotation.degrees.toDouble()

        return output
    }

    // the body of the static getClashRectangleTransformed, here for protected access
    private fun clashRectangleTransformed(
        originX: Double,
        originY: Double,
        rotation: Rotation?,
    ): Rectangle? {
        val pointTextContainer = this

        var output: Rectangle? = null

        val clashBounds = pointTextContainer.clashRect

        if (clashBounds != null) {
            val myRotation = Rotation(rotation!!.degrees, 0f, 0f)

            // "Undo" clash rectangle offset
            val rect1 = clashBounds.shift(
                Point(-pointTextContainer.horizontalOffset, -pointTextContainer.verticalOffset),
            )

            // "Undo" clash rectangle rotation
            val lt = myRotation.rotate(rect1.left, rect1.top, false)
            val rb = myRotation.rotate(rect1.right, rect1.bottom, false)

            // Imitate drawing clash rectangle as a label
            val lt2 = lt.offset(-originX, -originY)
            val rb2 = rb.offset(-originX, -originY)
            val lt3 = rotation.rotate(lt2, true)
            val rb3 = rotation.rotate(rb2, true)
            val rect2 = Rectangle(lt3.x, lt3.y, rb3.x, rb3.y)
            output = rect2.shift(
                Point(pointTextContainer.horizontalOffset, pointTextContainer.verticalOffset),
            )
        }

        return output
    }

    companion object {

        /**
         * This will draw clash rectangles around labels (for debugging purposes).
         */
        protected const val DEBUG_CLASH_BOUNDS = false

        /**
         * New rotated x,y position of the container relative to the origin point.
         */
        @JvmStatic
        fun getRotatedRelativePosition(
            pointTextContainer: PointTextContainer,
            originX: Double,
            originY: Double,
            rotation: Rotation?,
        ): Point {
            return pointTextContainer.rotatedRelativePosition(originX, originY, rotation)
        }

        /**
         * Mainly used for debugging purposes.
         *
         * @return Clash rectangle transformed back to label space
         */
        @JvmStatic
        fun getClashRectangleTransformed(
            pointTextContainer: PointTextContainer,
            originX: Double,
            originY: Double,
            rotation: Rotation?,
        ): Rectangle? {
            return pointTextContainer.clashRectangleTransformed(originX, originY, rotation)
        }
    }
}
