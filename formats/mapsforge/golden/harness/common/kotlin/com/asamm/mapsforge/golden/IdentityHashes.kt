/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.asamm.mapsforge.golden

/**
 * Aligns the JVM's identity-hash counter before every theme parse and tile, so an upstream and a
 * vendored render in two JVMs hand out the same identity hashes whatever their startup or another
 * thread consumed in between. Mapsforge breaks label ties in `HashSet` order, which follows those
 * hashes (ROADMAP A7).
 */
object IdentityHashes {

    // counter value of the first slot; far above what JVM startup consumes
    private const val START = 1_000_000

    // slot width; one theme parse or tile draws a few hundred hashes
    private const val STEP = 100_000

    private val used = mutableListOf<String>()
    private var slot: String? = null
    private var slotStart = 0

    /**
     * Moves the counter to the next slot boundary and opens a slot named `name` for [usage];
     * needs `-XX:hashCode=3` and fails loudly otherwise.
     */
    fun align(name: String) {
        if (slot == null) {
            // a lookup that misses opens every classpath jar now, not when a class first needs it
            IdentityHashes::class.java.classLoader.getResource("com/asamm/mapsforge/golden/missing")
        }
        val first = System.identityHashCode(Any())
        val current = System.identityHashCode(Any())
        // -XX:hashCode=3 gives a global counter that steps by one; other modes cannot be aligned
        check(current == first + 1) {
            "align($name), identity hashes not sequential ($first, $current); run with -XX:hashCode=3"
        }
        slot?.let { previous ->
            used += "$previous ${current - slotStart}"
            // a slot near the boundary could round up differently on the two sides
            check(current - slotStart < STEP / 2) {
                "align($name), slot $previous drew ${current - slotStart} hashes"
            }
        }
        val target = if (current < START) START else (current / STEP + 1) * STEP
        while (System.identityHashCode(Any()) < target) {
            // consume hashes until the counter reaches the slot boundary
        }
        slot = name
        slotStart = target
    }

    /**
     * Hashes each closed slot drew, one `<slot> <count>` line per slot; equal on both sides unless
     * something outside the render consumed hashes.
     */
    fun usage(): List<String> {
        return used.toList()
    }
}
