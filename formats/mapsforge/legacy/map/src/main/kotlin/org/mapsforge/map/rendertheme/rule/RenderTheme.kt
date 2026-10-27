/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2015 Ludwig M Brinckmann
 * Copyright 2016 devemux86
 * Copyright 2017 usrusr
 * Copyright 2024 Sublimis
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
import org.mapsforge.core.util.LRUCache
import org.mapsforge.core.util.Utils
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.RenderCallback
import org.mapsforge.map.rendertheme.RenderContext
import org.mapsforge.map.rendertheme.renderinstruction.RenderInstruction

/**
 * A RenderTheme defines how ways and nodes are drawn. Matches are cached by a hash of the tags
 * (without `name`), zoom level and closed state; stroke and text scales are applied once per zoom level and
 * factor.
 */
open class RenderTheme internal constructor(renderThemeBuilder: RenderThemeBuilder) {

    private val baseStrokeWidth: Float = renderThemeBuilder.baseStrokeWidth
    private val baseTextSize: Float = renderThemeBuilder.baseTextSize
    private val displayModel: DisplayModel = renderThemeBuilder.displayModel
    private val hasBackgroundOutside: Boolean = renderThemeBuilder.hasBackgroundOutside

    /**
     * The number of distinct drawing levels required by this theme.
     */
    @Volatile
    var levels: Int = 0

    /**
     * The map background color.
     */
    val mapBackground: Int = renderThemeBuilder.mapBackground

    /**
     * The background color that applies to areas outside the map.
     */
    val mapBackgroundOutside: Int = renderThemeBuilder.mapBackgroundOutside

    // Note: rulesList is not synchronized because it seems it is not being updated once the theme
    // is loaded.
    private val rulesList: ArrayList<Rule> = ArrayList()
    private val poiMatchingCache: LRUCache<Int, Array<RenderInstruction>> = LRUCache(MATCHING_CACHE_SIZE)
    private val wayMatchingCache: LRUCache<Int, Array<RenderInstruction>> = LRUCache(MATCHING_CACHE_SIZE)

    private val strokeScales: MutableMap<Byte, Float> = HashMap()
    private val textScales: MutableMap<Byte, Float> = HashMap()

    /**
     * Must be called when this RenderTheme gets destroyed to clean up and free resources.
     */
    fun destroy() {
        synchronized(this.poiMatchingCache) {
            this.poiMatchingCache.clear()
        }

        synchronized(this.wayMatchingCache) {
            this.wayMatchingCache.clear()
        }

        for (r in this.rulesList) {
            r.destroy()
        }
    }

    /**
     * True if a map color is defined for outside areas.
     */
    fun hasMapBackgroundOutside(): Boolean {
        return this.hasBackgroundOutside
    }

    /**
     * Matches a closed way against this theme; [renderCallback] is called on each match.
     */
    fun matchClosedWay(renderCallback: RenderCallback, renderContext: RenderContext, way: PolylineContainer) {
        matchWay(renderCallback, renderContext, Closed.YES, way)
    }

    /**
     * Matches a linear way against this theme; [renderCallback] is called on each match.
     */
    fun matchLinearWay(renderCallback: RenderCallback, renderContext: RenderContext, way: PolylineContainer) {
        matchWay(renderCallback, renderContext, Closed.NO, way)
    }

    /**
     * Matches a node against this theme; [renderCallback] is called on each match.
     */
    fun matchNode(renderCallback: RenderCallback, renderContext: RenderContext, poi: PointOfInterest) {
        // check cached instructions
        val matchingCacheKey = computeMatchingCacheKey(
            poi.tags,
            renderContext.rendererJob.tile.zoomLevel,
            Closed.NO,
        )

        val instructions = synchronized(this.poiMatchingCache) {
            this.poiMatchingCache[matchingCacheKey]
        }
        if (instructions != null) {
            for (i in instructions.indices) {
                instructions[i].renderNode(renderCallback, renderContext, poi)
            }
            return
        }

        // cache miss
        val matchingList: MutableList<RenderInstruction> = ArrayList()
        for (i in 0 until rulesList.size) {
            rulesList[i].matchNode(renderCallback, renderContext, matchingList, poi)
        }
        val matchingListA = matchingList.toTypedArray()

        synchronized(this.poiMatchingCache) {
            this.poiMatchingCache.put(matchingCacheKey, matchingListA)
        }
    }

    /**
     * Scales the stroke width of the rules covering [zoomLevel] by [scaleFactor]; a no-op when that
     * factor is already applied.
     */
    fun scaleStrokeWidth(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(this.strokeScales) {
            if (!strokeScales.containsKey(zoomLevel)
                || scaleFactor != strokeScales[zoomLevel]!!
            ) {
                for (i in 0 until this.rulesList.size) {
                    val rule = this.rulesList[i]
                    if (rule.zoomMin <= zoomLevel
                        && rule.zoomMax >= zoomLevel
                    ) {
                        rule.scaleStrokeWidth(
                            scaleFactor * this.baseStrokeWidth * this.displayModel.lineScaleFactor,
                            zoomLevel,
                        )
                    }
                }
                strokeScales[zoomLevel] = scaleFactor
            }
        }
    }

    /**
     * Scales the text size of the rules covering [zoomLevel] by [scaleFactor]; a no-op when that
     * factor is already applied.
     */
    fun scaleTextSize(scaleFactor: Float, zoomLevel: Byte) {
        synchronized(this.textScales) {
            if (!textScales.containsKey(zoomLevel)
                || scaleFactor != textScales[zoomLevel]!!
            ) {
                for (i in 0 until this.rulesList.size) {
                    val rule = this.rulesList[i]
                    if (rule.zoomMin <= zoomLevel
                        && rule.zoomMax >= zoomLevel
                    ) {
                        rule.scaleTextSize(
                            scaleFactor * this.baseTextSize * this.displayModel.textScaleFactor,
                            zoomLevel,
                        )
                    }
                }
                textScales[zoomLevel] = scaleFactor
            }
        }
    }

    fun addRule(rule: Rule) {
        this.rulesList.add(rule)
    }

    fun complete() {
        this.rulesList.trimToSize()
        for (i in 0 until this.rulesList.size) {
            this.rulesList[i].onComplete()
        }
    }

    private fun matchWay(
        renderCallback: RenderCallback,
        renderContext: RenderContext,
        closed: Closed,
        way: PolylineContainer,
    ) {
        // check cached instructions
        val matchingCacheKey = computeMatchingCacheKey(way.tags, way.upperLeft.zoomLevel, closed)

        val instructions = synchronized(this.wayMatchingCache) {
            this.wayMatchingCache[matchingCacheKey]
        }
        if (instructions != null) {
            for (i in instructions.indices) {
                instructions[i].renderWay(renderCallback, renderContext, way)
            }
            return
        }

        // cache miss
        val matchingList: MutableList<RenderInstruction> = ArrayList()
        for (i in 0 until rulesList.size) {
            rulesList[i].matchWay(renderCallback, way, way.upperLeft, closed, matchingList, renderContext)
        }
        val matchingListA = matchingList.toTypedArray()

        synchronized(this.wayMatchingCache) {
            this.wayMatchingCache.put(matchingCacheKey, matchingListA)
        }
    }

    fun traverseRules(visitor: Rule.RuleVisitor) {
        for (rule in this.rulesList) {
            rule.apply(visitor)
        }
    }

    /**
     * Compute key for matching cached set of instructions.
     */
    private fun computeMatchingCacheKey(tags: List<Tag>?, zoomLevel: Byte, closed: Closed?): Int {
        var result = 1
        result = 31 * result + (closed?.hashCode() ?: 0)
        if (tags != null) {
            for (i in 0 until tags.size) {
                val tag = tags[i]
                if (keyCodeName != tag.keyCode) {
                    result = 31 * result + tag.hashCode()
                }
            }
        }
        result = 31 * result + zoomLevel
        return result
    }

    private companion object {

        private const val MATCHING_CACHE_SIZE = 8192

        private val keyCodeName = Utils.hashTagParameter("name")
    }
}
