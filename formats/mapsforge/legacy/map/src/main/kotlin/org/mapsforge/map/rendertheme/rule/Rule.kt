/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2016 devemux86
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
package org.mapsforge.map.rendertheme.rule

import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.renderinstruction.RenderInstruction

abstract class Rule internal constructor(ruleBuilder: RuleBuilder) {

    /**
     * Walks the sub-rules of a rule depth first, under the lock of the rule it was created on.
     */
    open inner class RuleVisitor {

        open fun apply(r: Rule) {
            synchronized(mySync) {
                for (subRule in r.subRules) {
                    this.apply(subRule)
                }
            }
        }
    }

    @JvmField
    internal var cat: String? = ruleBuilder.cat
    internal val closedMatcher: ClosedMatcher = ruleBuilder.closedMatcher
    internal val elementMatcher: ElementMatcher = ruleBuilder.elementMatcher
    internal val zoomMax: Byte = ruleBuilder.zoomMax
    internal val zoomMin: Byte = ruleBuilder.zoomMin

    // conversion to Array[] tested, but performance difference was not measurable
    private val renderInstructions: ArrayList<RenderInstruction> = ArrayList(4)
    private val subRules: ArrayList<Rule> = ArrayList(4)

    private val mySync = Any()

    fun addRenderingInstruction(renderInstruction: RenderInstruction) {
        synchronized(mySync) {
            this.renderInstructions.add(renderInstruction)
        }
    }

    fun addSubRule(rule: Rule) {
        synchronized(mySync) {
            this.subRules.add(rule)
        }
    }

    internal fun apply(v: RuleVisitor) {
        v.apply(this)
    }

    internal fun destroy() {
        synchronized(mySync) {
            for (ri in this.renderInstructions) {
                ri.destroy()
            }
            for (sr in this.subRules) {
                sr.destroy()
            }
        }
    }

    internal abstract fun matchesNode(tags: List<Tag>, zoomLevel: Byte): Boolean

    internal abstract fun matchesWay(tags: List<Tag>, zoomLevel: Byte, closed: Closed): Boolean

    internal fun matchNode(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        matchingList: MutableList<RenderInstruction>,
        pointOfInterest: PointOfInterest,
    ) {
        synchronized(mySync) {
            if (matchesNode(pointOfInterest.tags, renderContext.rendererJob.tile.zoomLevel)) {
                for (i in 0 until this.renderInstructions.size) {
                    this.renderInstructions[i].renderNode(renderCallback, renderContext, pointOfInterest)
                    matchingList.add(this.renderInstructions[i])
                }
                for (i in 0 until this.subRules.size) {
                    this.subRules[i].matchNode(renderCallback, renderContext, matchingList, pointOfInterest)
                }
            }
        }
    }

    internal fun matchWay(
        renderCallback: RenderCallback,
        way: PolylineContainer,
        tile: Tile,
        closed: Closed,
        matchingList: MutableList<RenderInstruction>,
        renderContext: RenderContext,
    ) {
        synchronized(mySync) {
            if (matchesWay(way.tags, tile.zoomLevel, closed)) {
                for (i in 0 until this.renderInstructions.size) {
                    this.renderInstructions[i].renderWay(renderCallback, renderContext, way)
                    matchingList.add(this.renderInstructions[i])
                }
                for (i in 0 until this.subRules.size) {
                    this.subRules[i].matchWay(renderCallback, way, tile, closed, matchingList, renderContext)
                }
            }
        }
    }

    internal fun onComplete() {
        MATCHERS_CACHE_KEY.clear()
        MATCHERS_CACHE_VALUE.clear()

        synchronized(mySync) {
            this.renderInstructions.trimToSize()
            this.subRules.trimToSize()
            for (i in 0 until this.subRules.size) {
                this.subRules[i].onComplete()
            }
        }
    }

    internal fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            for (i in 0 until this.renderInstructions.size) {
                this.renderInstructions[i].scaleStrokeWidth(scaleFactor, zoomLevel)
            }
            for (i in 0 until this.subRules.size) {
                this.subRules[i].scaleStrokeWidth(scaleFactor, zoomLevel)
            }
        }
    }

    internal fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(mySync) {
            for (i in 0 until this.renderInstructions.size) {
                this.renderInstructions[i].scaleTextSize(scaleFactor, zoomLevel)
            }
            for (i in 0 until this.subRules.size) {
                this.subRules[i].scaleTextSize(scaleFactor, zoomLevel)
            }
        }
    }

    companion object {

        @JvmField
        internal val MATCHERS_CACHE_KEY: MutableMap<List<String>, AttributeMatcher> = HashMap()

        @JvmField
        internal val MATCHERS_CACHE_VALUE: MutableMap<List<String>, AttributeMatcher> = HashMap()
    }
}
