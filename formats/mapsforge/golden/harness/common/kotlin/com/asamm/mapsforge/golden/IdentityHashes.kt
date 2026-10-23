/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

/**
 * Aligns the JVM's identity-hash counter, so an upstream and a vendored render in two JVMs hand out
 * the same identity hashes whatever their startup consumed. Mapsforge breaks label ties in `HashSet`
 * order, which follows those hashes.
 */
object IdentityHashes {

    // counter value both render JVMs start from; far above what JVM startup consumes
    private const val START = 1_000_000

    /**
     * Moves the counter to the start value; needs `-XX:hashCode=3` and fails loudly otherwise.
     */
    fun align() {
        val first = System.identityHashCode(Any())
        val second = System.identityHashCode(Any())
        // -XX:hashCode=3 gives a global counter that steps by one; other modes cannot be aligned
        check(second == first + 1) {
            "align(), identity hashes not sequential ($first, $second); run with -XX:hashCode=3"
        }
        check(second < START) {
            "align(), counter already at $second"
        }
        while (System.identityHashCode(Any()) < START) {
            // consume hashes until the counter reaches the start value
        }
    }
}
