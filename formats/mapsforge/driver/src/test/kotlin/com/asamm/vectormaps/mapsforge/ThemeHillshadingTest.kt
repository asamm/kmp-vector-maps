/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.vectormaps.mapsforge

import org.mapsforge.map.awt.graphics.AwtGraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The `<hillshading>` element is parsed but not rendered (C5): it is still validated and takes its
 * drawing level, so the levels of the rules after it stay as upstream.
 */
class ThemeHillshadingTest {

    @Test
    fun hillshadingTakesOneLevel() {
        val without = parse(hillshading = "")
        val with = parse(hillshading = """<hillshading magnitude="200" layer="3" cat="relief"/>""")

        assertEquals(without.levels + 1, with.levels)
    }

    @Test
    fun hillshadingStillValidated() {
        assertFailsWith<XmlPullParserException> {
            parse(hillshading = """<hillshading magnitude="300"/>""")
        }
    }

    private fun parse(hillshading: String): RenderTheme {
        val xml = """
            <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5">
              <rule e="way" k="highway" v="*"><line stroke="#000000"/></rule>
              $hillshading
              <rule e="way" k="natural" v="*"><line stroke="#ffffff"/></rule>
            </rendertheme>
        """.trimIndent()
        val file: File = createTempFile(prefix = "theme", suffix = ".xml").toFile()
        try {
            file.writeText(xml)
            return RenderThemeHandler.getRenderTheme(AwtGraphicFactory.INSTANCE, DisplayModel(), ExternalRenderTheme(file))
        } finally {
            file.delete()
        }
    }
}
