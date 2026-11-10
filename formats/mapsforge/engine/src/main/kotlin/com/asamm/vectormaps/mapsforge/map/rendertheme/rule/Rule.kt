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
package com.asamm.vectormaps.mapsforge.map.rendertheme.rule

import com.asamm.vectormaps.mapsforge.core.model.Tag
import com.asamm.vectormaps.mapsforge.core.model.Tile
import com.asamm.vectormaps.mapsforge.map.datastore.PointOfInterest
import com.asamm.vectormaps.mapsforge.map.layer.renderer.PolylineContainer
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderCallback
import com.asamm.vectormaps.mapsforge.map.rendertheme.RenderContext
import com.asamm.vectormaps.mapsforge.map.rendertheme.renderinstruction.RenderInstruction

abstract class Rule internal constructor(ruleBuilder: RuleBuilder) {

    internal var cat: String? = ruleBuilder.cat
    internal val closedMatcher: ClosedMatcher = ruleBuilder.closedMatcher
    internal val elementMatcher: ElementMatcher = ruleBuilder.elementMatcher
    internal val zoomMax: Byte = ruleBuilder.zoomMax
    internal val zoomMin: Byte = ruleBuilder.zoomMin

    // conversion to Array[] tested, but performance difference was not measurable
    private val renderInstructions: ArrayList<RenderInstruction> = ArrayList(4)
    private val subRules: ArrayList<Rule> = ArrayList(4)

    // guards the lists while the theme is built and destroyed; once built they only are read, so
    // matching, scaling and eviction run without it (C14)
    private val mySync = Any()

    internal fun addRenderingInstruction(renderInstruction: RenderInstruction) {
        synchronized(mySync) {
            this.renderInstructions.add(renderInstruction)
        }
    }

    internal fun addSubRule(rule: Rule) {
        synchronized(mySync) {
            this.subRules.add(rule)
        }
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

    internal fun evictZoom(zoomLevel: Byte) {
        for (i in this.renderInstructions.indices) {
            this.renderInstructions[i].evictZoom(zoomLevel)
        }
        for (i in this.subRules.indices) {
            this.subRules[i].evictZoom(zoomLevel)
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
        if (matchesNode(pointOfInterest.tags, renderContext.rendererJob.tile.zoomLevel)) {
            for (i in this.renderInstructions.indices) {
                this.renderInstructions[i].renderNode(renderCallback, renderContext, pointOfInterest)
                matchingList.add(this.renderInstructions[i])
            }
            for (i in this.subRules.indices) {
                this.subRules[i].matchNode(renderCallback, renderContext, matchingList, pointOfInterest)
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
        if (matchesWay(way.tags, tile.zoomLevel, closed)) {
            for (i in this.renderInstructions.indices) {
                this.renderInstructions[i].renderWay(renderCallback, renderContext, way)
                matchingList.add(this.renderInstructions[i])
            }
            for (i in this.subRules.indices) {
                this.subRules[i].matchWay(renderCallback, way, tile, closed, matchingList, renderContext)
            }
        }
    }

    internal fun onComplete() {
        MATCHERS_CACHE_KEY.clear()
        MATCHERS_CACHE_VALUE.clear()

        synchronized(mySync) {
            this.renderInstructions.trimToSize()
            this.subRules.trimToSize()
            for (i in this.subRules.indices) {
                this.subRules[i].onComplete()
            }
        }
    }

    internal fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        for (i in this.renderInstructions.indices) {
            this.renderInstructions[i].scaleStrokeWidth(scaleFactor, zoomLevel)
        }
        for (i in this.subRules.indices) {
            this.subRules[i].scaleStrokeWidth(scaleFactor, zoomLevel)
        }
    }

    internal fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        for (i in this.renderInstructions.indices) {
            this.renderInstructions[i].scaleTextSize(scaleFactor, zoomLevel)
        }
        for (i in this.subRules.indices) {
            this.subRules[i].scaleTextSize(scaleFactor, zoomLevel)
        }
    }

    companion object {

        internal val MATCHERS_CACHE_KEY: MutableMap<List<String>, AttributeMatcher> = HashMap()

        internal val MATCHERS_CACHE_VALUE: MutableMap<List<String>, AttributeMatcher> = HashMap()
    }
}
