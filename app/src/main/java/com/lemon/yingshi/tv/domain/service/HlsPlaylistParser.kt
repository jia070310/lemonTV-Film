package com.lemon.yingshi.tv.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

data class HlsPlaylistParseResult(
    val segmentUrls: List<String>,
    val mediaPlaylistUrl: String
)

/**
 * 解析 HLS 播放列表，提取 TS/m4s 分片 URL。
 * 逻辑参考 M3U8 下载工具：支持 master 嵌套、相对/绝对路径。
 */
object HlsPlaylistParser {

    private val segmentLinePattern = Regex("""^[a-zA-Z0-9_\-]+\.[a-zA-Z0-9]+$""")

    suspend fun resolveSegmentUrls(
        playlistUrl: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient,
        depth: Int = 0
    ): HlsPlaylistParseResult? = withContext(Dispatchers.IO) {
        if (depth > 3) return@withContext null
        val content = fetchPlaylist(playlistUrl, headers, okHttpClient) ?: return@withContext null

        if (content.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = pickVariantUrl(content, playlistUrl) ?: return@withContext null
            return@withContext resolveSegmentUrls(variantUrl, headers, okHttpClient, depth + 1)
        }

        val segments = parseMediaSegments(content, playlistUrl)
        if (segments.isEmpty()) return@withContext null
        HlsPlaylistParseResult(
            segmentUrls = segments,
            mediaPlaylistUrl = playlistUrl
        )
    }

    private fun fetchPlaylist(
        url: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient
    ): String? {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        return runCatching {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun pickVariantUrl(content: String, masterUrl: String): String? {
        val lines = content.lines()
        var bestUrl: String? = null
        var bestBandwidth = -1L
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull() ?: 0L
                val nextLine = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim()
                if (nextLine != null) {
                    val resolved = resolveUrl(masterUrl, nextLine)
                    if (bandwidth >= bestBandwidth) {
                        bestBandwidth = bandwidth
                        bestUrl = resolved
                    }
                }
            }
            i++
        }
        return bestUrl ?: lines.firstOrNull { !it.startsWith("#") && it.isNotBlank() }?.trim()?.let {
            resolveUrl(masterUrl, it)
        }
    }

    private fun parseMediaSegments(content: String, playlistUrl: String): List<String> {
        val segments = mutableListOf<String>()
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                if (line.contains(".ts", ignoreCase = true) ||
                    line.contains(".m4s", ignoreCase = true) ||
                    segmentLinePattern.matches(line)
                ) {
                    segments.add(resolveUrl(playlistUrl, line))
                }
            }
        return segments
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
        ) {
            return path
        }
        val base = URL(baseUrl)
        return if (path.startsWith("/")) {
            URL(base.protocol, base.host, base.port, path).toString()
        } else {
            URL(URL(baseUrl), path).toString()
        }
    }
}
