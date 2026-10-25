/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.MapRenderer
import com.asamm.vectormaps.core.RenderConfig
import com.asamm.vectormaps.core.RenderKey
import com.asamm.vectormaps.core.RenderResult
import com.asamm.vectormaps.core.RenderTarget
import com.asamm.vectormaps.core.StyleSelection
import com.asamm.vectormaps.core.TileRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileEngineTest {

    private val tile = TileRequest(x = 1, y = 2, zoom = 3)

    @Test
    fun drawnTilesAreKeptUntilClose() {
        val renderer = FakeRenderer { RenderResult.DRAWN }
        val images = FakeImages()
        val arrived = CountDownLatch(1)
        val engine = TileEngine(renderer = renderer, images = images, threads = 2) {
            arrived.countDown()
        }

        engine.show(listOf(tile))

        assertTrue(arrived.await(5, TimeUnit.SECONDS))
        assertEquals("3/1/2", engine[tile])
        engine.close()
        assertNull(engine[tile])
        assertEquals(listOf("3/1/2"), images.released)
        assertTrue(renderer.closed)
    }

    @Test
    fun tilesLeavingTheViewAreCancelled() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val renderer = FakeRenderer { isCancelled ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            if (isCancelled()) {
                cancelled.countDown()
                RenderResult.CANCELLED
            } else {
                RenderResult.DRAWN
            }
        }
        TileEngine(renderer = renderer, images = FakeImages(), threads = 1) {}.use { engine ->
            engine.show(listOf(tile))
            assertTrue(started.await(5, TimeUnit.SECONDS))

            engine.show(emptyList())
            release.countDown()

            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            assertNull(engine[tile])
        }
    }

    @Test
    fun tileBackOnScreenWhileStoppingIsDrawn() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val arrived = CountDownLatch(1)
        val calls = AtomicInteger()
        val renderer = FakeRenderer {
            if (calls.incrementAndGet() == 1) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                // stopped by a cancel check made while the tile was off screen
                RenderResult.CANCELLED
            } else {
                RenderResult.DRAWN
            }
        }
        TileEngine(renderer = renderer, images = FakeImages(), threads = 1) { arrived.countDown() }.use { engine ->
            engine.show(listOf(tile))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            engine.show(emptyList())
            engine.show(listOf(tile))

            release.countDown()

            assertTrue(arrived.await(5, TimeUnit.SECONDS))
            assertEquals("3/1/2", engine[tile])
        }
    }

    @Test
    fun failedTileWaitsForClear() {
        val failed = CountDownLatch(1)
        val retried = CountDownLatch(2)
        val calls = AtomicInteger()
        val renderer = FakeRenderer {
            calls.incrementAndGet()
            throw IllegalStateException("broken")
        }
        val engine = TileEngine(renderer = renderer, images = FakeImages(), threads = 1) {
            failed.countDown()
            retried.countDown()
        }
        engine.use {
            engine.show(listOf(tile))
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertTrue(assertNotNull(engine.error).contains("broken"))

            repeat(5) {
                engine.show(listOf(tile))
                Thread.sleep(20)
            }
            assertEquals(1, calls.get())

            engine.clear()
            assertNull(engine.error)
            engine.show(listOf(tile))
            assertTrue(retried.await(5, TimeUnit.SECONDS))
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun emptyTileIsNotRenderedAgain() {
        val calls = AtomicInteger()
        val rendered = CountDownLatch(1)
        val renderer = FakeRenderer {
            calls.incrementAndGet()
            rendered.countDown()
            RenderResult.EMPTY
        }
        TileEngine(renderer = renderer, images = FakeImages(), threads = 1) {}.use { engine ->
            engine.show(listOf(tile))
            assertTrue(rendered.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)

            repeat(5) {
                engine.show(listOf(tile))
                Thread.sleep(20)
            }

            assertEquals(1, calls.get())
            assertNull(engine[tile])
        }
    }

    @Test
    fun closeLaterClosesInTheBackground() {
        val arrived = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val renderer = FakeRenderer { RenderResult.DRAWN }
        val images = FakeImages()
        val engine = TileEngine(renderer = renderer, images = images, threads = 1) { arrived.countDown() }
        engine.show(listOf(tile))
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        engine.closeLater {
            closed.countDown()
        }

        assertNull(engine[tile])
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(renderer.closed)
        assertEquals(listOf("3/1/2"), images.released)
    }

    @Test
    fun closeLaterFinishesWhenTheRendererFailsToClose() {
        val arrived = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val renderer = FakeRenderer(failClose = true) { RenderResult.DRAWN }
        val images = FakeImages()
        val engine = TileEngine(renderer = renderer, images = images, threads = 1) { arrived.countDown() }
        engine.show(listOf(tile))
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        engine.closeLater {
            closed.countDown()
        }

        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("3/1/2"), images.released)
    }

    @Test
    fun capacityMustKeepATile() {
        assertFailsWith<IllegalArgumentException> {
            TileEngine(renderer = FakeRenderer { RenderResult.DRAWN }, images = FakeImages(), threads = 1, capacity = 0) {}
        }
    }

    @Test
    fun keptTilesStayWithinTheCapacity() {
        val tiles = (0 until 6).map { TileRequest(x = it, y = 0, zoom = 3) }
        val arrived = CountDownLatch(tiles.size)
        val images = FakeImages()
        val engine = TileEngine(
            renderer = FakeRenderer { RenderResult.DRAWN },
            images = images,
            threads = 2,
            capacity = 2,
        ) {
            arrived.countDown()
        }
        engine.use {
            for (each in tiles) {
                engine.show(listOf(each))
            }
            assertTrue(arrived.await(5, TimeUnit.SECONDS))

            engine.show(listOf(tiles.last()))

            assertEquals(2, tiles.count { engine[it] != null })
            assertEquals(4, images.released.size)
        }
    }

    private class FakeTarget(override val widthPx: Int) : RenderTarget {

        override val heightPx: Int = widthPx

        var drawn: String? = null
    }

    private class FakeImages : TileImages<String> {

        val released = mutableListOf<String>()

        override fun newTarget(sizePx: Int): RenderTarget {
            return FakeTarget(sizePx)
        }

        override fun take(target: RenderTarget): String {
            return (target as FakeTarget).drawn!!
        }

        @Synchronized
        override fun release(image: String) {
            released += image
        }
    }

    private class FakeRenderer(
        private val failClose: Boolean = false,
        private val draw: (isCancelled: () -> Boolean) -> RenderResult,
    ) : MapRenderer {

        override val key = RenderKey(
            styleId = "fake",
            selection = StyleSelection.DEFAULT,
            config = RenderConfig(tileSizePx = 256),
        )

        override val maxParallelism = 4

        @Volatile
        var closed = false

        override fun render(tile: TileRequest, target: RenderTarget, isCancelled: () -> Boolean): RenderResult {
            val result = draw(isCancelled)
            (target as FakeTarget).drawn = "${tile.zoom}/${tile.x}/${tile.y}"
            return result
        }

        override fun close() {
            closed = true
            if (failClose) {
                throw IllegalStateException("cannot close")
            }
        }
    }
}
