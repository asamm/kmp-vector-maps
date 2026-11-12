/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.golden

import com.asamm.vectormaps.mvt.render.AwtFonts
import com.asamm.vectormaps.mvt.render.AwtSprite
import com.asamm.vectormaps.mvt.render.TileRenderer
import com.asamm.vectormaps.mvt.style.SpriteIndex
import com.asamm.vectormaps.mvt.style.Style
import com.asamm.vectormaps.mvt.symbol.SymbolLayout
import com.asamm.vectormaps.mvt.tile.VectorTile
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The MVT fixtures of `dev/fixtures/mvt` and what the goldens build from them.
 */
internal object MvtFixtures {

    const val SIZE = 512

    // the server references of dev/fixtures/mvt
    const val TILES = 8

    val fixturesDir = File(requireNotNull(System.getProperty("fixturesDir")))

    val mvtDir = File(fixturesDir, "mvt")

    val outDir = File(requireNotNull(System.getProperty("outDir")))

    val style: Style by lazy {
        Style.parse(File(mvtDir, "styles/hike_bike/style.json").readText())
    }

    val spriteIndex: SpriteIndex by lazy {
        SpriteIndex.parse(File(mvtDir, "styles/hike_bike/sprite.json").readText())
    }

    val sprite: AwtSprite by lazy {
        AwtSprite(spriteIndex, ImageIO.read(File(mvtDir, "styles/hike_bike/sprite.png")))
    }

    // the bundled font, so label goldens match on every system
    val fonts: AwtFonts by lazy {
        AwtFonts(Font.createFont(Font.TRUETYPE_FONT, File(fixturesDir, "fonts/dejavu/DejaVuSans.ttf")))
    }

    fun tile(z: Int, x: Int, y: Int): VectorTile? {
        return File(mvtDir, "tiles/planet/$z/$x/$y.pbf")
            .takeIf { it.isFile }
            ?.let { VectorTile.decode(it.readBytes()) }
    }

    fun renderer(): TileRenderer {
        return TileRenderer(style, maxDataZoom = 14, tileSource = ::tile)
    }

    fun layout(): SymbolLayout {
        return SymbolLayout(style, spriteIndex, fonts, maxDataZoom = 14, tileSource = ::tile)
    }

    /**
     * The server reference tiles, as z, x, y and their file.
     */
    fun references(): List<Reference> {
        return File(mvtDir, "reference/server/hike_bike").walk()
            .filter { it.extension == "png" }
            .map {
                Reference(
                    z = it.parentFile.parentFile.name.toInt(),
                    x = it.parentFile.name.toInt(),
                    y = it.nameWithoutExtension.toInt(),
                    server = it,
                )
            }
            .sortedWith(compareBy({ it.z }, { it.x }, { it.y }))
            .toList()
    }

    class Reference(val z: Int, val x: Int, val y: Int, val server: File) {

        val name = "${z}_${x}_$y"

        fun maplibre(variant: String): File {
            return File(mvtDir, "reference/maplibre/$variant/$z/$x/$y.png")
        }
    }

    /**
     * Mean absolute RGB difference (0-255) and the percentage of pixels off by more than
     * `tolerance` in a channel.
     */
    fun difference(a: BufferedImage, b: BufferedImage, tolerance: Int = 32): Pair<Double, Double> {
        var sum = 0L
        var off = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                var worst = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = abs((p ushr shift and 0xff) - (q ushr shift and 0xff))
                    sum += d
                    worst = maxOf(worst, d)
                }
                if (worst > tolerance) {
                    off++
                }
            }
        }
        val n = a.width * a.height
        return sum / (n * 3.0) to off * 100.0 / n
    }

    /**
     * `images` side by side, written to `outDir` as `name`.png.
     */
    fun writeStrip(name: String, vararg images: BufferedImage?) {
        val strip = BufferedImage(SIZE * images.size, SIZE, BufferedImage.TYPE_INT_ARGB)
        strip.createGraphics().apply {
            for ((i, image) in images.withIndex()) {
                image?.let { drawImage(it, SIZE * i, 0, null) }
            }
            dispose()
        }
        outDir.mkdirs()
        ImageIO.write(strip, "png", File(outDir, "$name.png"))
    }
}
