/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

import java.io.File

// identity-hash counter value both render JVMs start from; far above what JVM startup consumes
private const val IDENTITY_HASH_START = 1_000_000

/**
 * Renders the golden corpus; arguments are the fixtures directory and the output directory.
 */
fun main(args: Array<String>) {
    require(args.size == 2) {
        "usage: <fixturesDir> <outDir>"
    }
    alignIdentityHashes()
    GoldenCorpus.renderAll(fixturesDir = File(args[0]), outDir = File(args[1]))
}

/**
 * Moves the JVM's identity-hash counter to a fixed value, so the upstream and the vendored render
 * JVMs hand out the same identity hashes whatever their startup consumed. Mapsforge breaks label
 * ties in `HashSet` order, which follows those hashes.
 */
private fun alignIdentityHashes() {
    val first = System.identityHashCode(Any())
    val second = System.identityHashCode(Any())
    // -XX:hashCode=3 gives a global counter that steps by one; other modes cannot be aligned
    check(second == first + 1) {
        "alignIdentityHashes(), identity hashes not sequential ($first, $second); run with -XX:hashCode=3"
    }
    check(second < IDENTITY_HASH_START) {
        "alignIdentityHashes(), counter already at $second"
    }
    while (System.identityHashCode(Any()) < IDENTITY_HASH_START) {
        // consume hashes until the counter reaches the start value
    }
}
