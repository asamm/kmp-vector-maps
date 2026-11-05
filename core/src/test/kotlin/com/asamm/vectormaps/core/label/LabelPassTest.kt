/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.core.label

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelPassTest {

    @Test
    fun newerViewCancelsTheRunBeforeIt() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val done = CountDownLatch(1)
        val published = AtomicInteger()
        val pass = object : BackgroundLabelPass() {
            override fun collect(
                view: LabelViewport,
                isCancelled: () -> Boolean,
            ): List<LabelCandidate> {
                if (view.zoom == 1) {
                    firstStarted.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                }
                return listOf(candidate(id = view.zoom.toLong()))
            }
        }
        pass.onPrepared = {
            published.incrementAndGet()
            done.countDown()
        }

        pass.use {
            it.prepare(view(zoom = 1))
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            it.prepare(view(zoom = 2))
            releaseFirst.countDown()

            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(2, it.prepared!!.view.zoom)
            assertEquals(listOf(2L), it.prepared!!.placed.map { placed -> placed.candidate.id })
            assertEquals(1, published.get())
        }
    }

    @Test
    fun worldBoundsCoverTheTurnedScreen() {
        val straight = view(zoom = 1).worldBounds()
        val turned = view(zoom = 1).copy(rotationDeg = 90.0).worldBounds()

        assertEquals(
            WorldBounds(minX = 900.0, minY = 950.0, maxX = 1100.0, maxY = 1050.0),
            straight,
        )
        assertEquals(950.0, turned.minX, 1e-9)
        assertEquals(900.0, turned.minY, 1e-9)
    }

    private fun view(zoom: Int): LabelViewport {
        return LabelViewport(
            zoom = zoom,
            centerX = 1000.0,
            centerY = 1000.0,
            widthPx = 200,
            heightPx = 100,
        )
    }

    private fun candidate(id: Long): LabelCandidate {
        return LabelCandidate(
            id = id,
            kind = LabelKind.POINT,
            x = 0.0,
            y = 0.0,
            priority = 1,
            boxes = emptyList(),
        )
    }
}
