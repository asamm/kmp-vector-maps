/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.mvt.source.MvtTileSource
import com.asamm.vectormaps.mvt.tile.VectorTile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Online vector tiles for the viewer, from the first URL template of a TileJSON file such as the
 * Asamm server's `planet.json`; downloaded tiles stay in [cacheDir], tiles the server lacks as
 * empty files. For UI testing only: no expiry, retries or size limit, and a tile that fails to
 * download stays blank until Rerender or Reload.
 */
class HttpTileSource private constructor(
    private val template: String,
    override val minZoom: Int,
    override val maxZoom: Int,
    override val bounds: DoubleArray?,
    internal val cacheDir: File,
) : MvtTileSource {

    override fun tile(zoom: Int, x: Int, y: Int): ByteArray? {
        val cached = File(cacheDir, "$zoom/$x/$y.pbf")
        if (cached.isFile) {
            return cached.readBytes().takeIf { it.isNotEmpty() }
        }
        val uri = URI(template.replace("{z}", "$zoom").replace("{x}", "$x").replace("{y}", "$y"))
        // without the query, which may hold a key
        val where = "${uri.host}${uri.rawPath}"
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(TIMEOUT_S))
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", "kmp-vector-maps-viewer")
            .build()
        val future = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        // the request timeout ends at the headers; this one also bounds the body
        val response = try {
            future.get(TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw IOException("tile($zoom, $x, $y), no answer in $TIMEOUT_S s from $where", e)
        } catch (e: ExecutionException) {
            throw IOException("tile($zoom, $x, $y), ${e.cause} from $where", e.cause)
        }
        val type = response.headers().firstValue("Content-Type").orElse("")
        val bytes = when {
            response.statusCode() == 200
                && !type.startsWith("text/") -> {
                // only a tile is cached, not an error page
                response.body().also { VectorTile.decode(MvtTileSource.uncompressed(it)) }
            }
            response.statusCode() == 204
                || response.statusCode() == 404 -> ByteArray(0)
            else -> throw IOException("tile($zoom, $x, $y), HTTP ${response.statusCode()} $type from $where")
        }
        try {
            store(file = cached, bytes = bytes)
        } catch (e: IOException) {
            // the cache is best effort, such as another thread replacing the same tile on Windows
        }
        return bytes.takeIf { it.isNotEmpty() }
    }

    // through a temporary file, as render threads may ask for one tile at once
    private fun store(file: File, bytes: ByteArray) {
        val folder = file.parentFile
        folder.mkdirs()
        val temp = Files.createTempFile(folder.toPath(), "tile", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {

        private const val TIMEOUT_S = 30L

        private val CLIENT: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        private val NOT_PLAIN = Regex("[^A-Za-z0-9._-]")

        /**
         * `~/.kmp-vector-maps/tiles`, shared by every checkout.
         */
        val userCacheDir: File
            get() {
                return File(System.getProperty("user.home"), ".kmp-vector-maps/tiles")
            }

        /**
         * True for a TileJSON file with an http(s) tile template; it opens as an online map.
         */
        fun isTileJson(file: File): Boolean {
            return file.extension.lowercase() == "json"
                && file.isFile
                && runCatching { templateOf(read(file)) }.getOrNull() != null
        }

        /**
         * The source of the TileJSON `file`, its tiles cached in a folder per server and tileset
         * within [cacheRoot]. Throws when `file` is not a TileJSON with an http(s) template.
         */
        fun open(file: File, cacheRoot: File = userCacheDir): HttpTileSource {
            val json = read(file)
            val template = requireNotNull(templateOf(json)) {
                "open($file), no http(s) tile template"
            }
            // the host and port, then the path up to the first placeholder and a hash of the whole
            // template, so templates differing in port, query or suffix keep apart
            val uri = URI(template.substringBefore("{"))
            val tileset = uri.rawPath.trim('/') + "-" + "%08x".format(template.hashCode())
            val bounds = (json["bounds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                ?.takeIf { it.size == 4 }
            return HttpTileSource(
                template = template,
                minZoom = (json["minzoom"] as? JsonPrimitive)?.intOrNull ?: 0,
                maxZoom = (json["maxzoom"] as? JsonPrimitive)?.intOrNull ?: 14,
                bounds = bounds?.toDoubleArray(),
                cacheDir = File(cacheRoot, "${plain(uri.rawAuthority)}/${plain(tileset)}"),
            )
        }

        // plain characters only and no `..`, so a crafted template cannot leave the cache
        private fun plain(name: String): String {
            return name.replace(NOT_PLAIN, "_").replace("..", "__")
        }

        private fun read(file: File): JsonObject {
            return Json.parseToJsonElement(file.readText()).jsonObject
        }

        private fun templateOf(json: JsonObject): String? {
            val template = ((json["tiles"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content
            return template?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        }
    }
}
