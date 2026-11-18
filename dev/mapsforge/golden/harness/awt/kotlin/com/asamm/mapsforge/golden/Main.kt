/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File

/**
 * Renders the golden corpus; arguments are the fixtures directory and the output directory, or
 * `sweep ...` for [Sweep].
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "sweep") {
        Sweep.run(args.drop(1))
        return
    }
    require(args.size == 2) {
        "usage: <fixturesDir> <outDir>"
    }
    GoldenCorpus.renderAll(fixturesDir = File(args[0]), outDir = File(args[1]))
}
