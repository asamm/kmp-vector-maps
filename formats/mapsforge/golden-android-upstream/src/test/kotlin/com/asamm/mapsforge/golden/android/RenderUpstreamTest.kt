/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden.android

import com.asamm.mapsforge.golden.AndroidCorpus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the golden corpus with upstream Mapsforge 0.30.0 through real Android graphics; the
 * PNGs are the reference for `:formats:mapsforge:golden-android`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RenderUpstreamTest {

    @Test
    fun renderCorpus() {
        AndroidCorpus.renderAll(
            application = RuntimeEnvironment.getApplication(),
            fixturesDir = File(requireNotNull(System.getProperty("golden.fixturesDir"))),
            outDir = File(requireNotNull(System.getProperty("golden.outDir"))),
        )
    }
}
