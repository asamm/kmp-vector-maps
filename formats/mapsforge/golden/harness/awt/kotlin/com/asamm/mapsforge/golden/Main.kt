/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File

/**
 * Renders the golden corpus; arguments are the fixtures directory and the output directory.
 */
fun main(args: Array<String>) {
    require(args.size == 2) {
        "usage: <fixturesDir> <outDir>"
    }
    GoldenCorpus.renderAll(fixturesDir = File(args[0]), outDir = File(args[1]))
}
