package com.lemon.yingshi.tv.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

data class HlsSegment(
    val url: String,
    val startTimeSec: Double,
    val durationSec: Double
)

data class HlsPlaylistParseResult(
    val segments: List<HlsSegment>,
    val mediaPlaylistUrl: String,
    val mediaPlaylistContent: String
) {
    val segmentUrls: List<String> = segments.map { it.url }
}

/**
 * 解析 HLS 播放列表，提取 TS/m4s 分片 URL。
 * 逻辑参考 M3U8 下载工具：支持 master 嵌套、相对/绝对路径。
 */
object HlsPlaylistParser {

    const val LOCAL_KEY_FILE = "enc.key"

    private val segmentLinePattern = Regex("""^[a-zA-Z0-9_\-]+\.[a-zA-Z0-9]+$""")

    suspend fun resolveSegmentUrls(
        playlistUrl: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient,
        depth: Int = 0
    ): HlsPlaylistParseResult? = resolvePlaylistDetails(playlistUrl, headers, okHttpClient, depth)

    suspend fun resolvePlaylistDetails(
        playlistUrl: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient,
        depth: Int = 0
    ): HlsPlaylistParseResult? = withContext(Dispatchers.IO) {
        if (depth > 3) return@withContext null
        val content = fetchPlaylist(playlistUrl, headers, okHttpClient) ?: return@withContext null

        if (content.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = pickVariantUrl(content, playlistUrl) ?: return@withContext null
            return@withContext resolvePlaylistDetails(variantUrl, headers, okHttpClient, depth + 1)
        }

        val segments = parseMediaSegments(content, playlistUrl)
        if (segments.isEmpty()) return@withContext null
        HlsPlaylistParseResult(
            segments = segments,
            mediaPlaylistUrl = playlistUrl,
            mediaPlaylistContent = content
        )
    }

    fun extractEncryptionKeyUrl(mediaPlaylistContent: String, mediaPlaylistUrl: String): String? {
        val keyLine = mediaPlaylistContent.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#EXT-X-KEY") && it.contains("URI=") }
            ?: return null
        val uri = Regex("""URI="([^"]+)"""").find(keyLine)?.groupValues?.getOrNull(1) ?: return null
        return resolveUrl(mediaPlaylistUrl, uri)
    }

    fun buildLocalOfflinePlaylist(
        mediaPlaylistContent: String,
        localKeyFileName: String = LOCAL_KEY_FILE
    ): String {
        var segmentIndex = 0
        val output = StringBuilder()
        var hasEndList = false
        mediaPlaylistContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXT-X-KEY") && trimmed.contains("URI=") -> {
                    val rewritten = trimmed.replace(
                        Regex("""URI="[^"]+""""),
                        """URI="$localKeyFileName""""
                    )
                    output.appendLine(rewritten)
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    if (isSegmentLine(trimmed)) {
                        output.appendLine(segmentFileName(segmentIndex))
                        segmentIndex++
                    } else {
                        output.appendLine(line)
                    }
                }
                trimmed.equals("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    hasEndList = true
                    output.appendLine(line)
                }
                else -> output.appendLine(line)
            }
        }
        if (!hasEndList) {
            output.appendLine("#EXT-X-ENDLIST")
        }
        return output.toString().trimEnd()
    }

    fun segmentFileName(index: Int): String =
        "seg_${index.toString().padStart(5, '0')}.ts"

    private fun isSegmentLine(line: String): Boolean =
        line.contains(".ts", ignoreCase = true) ||
            line.contains(".m4s", ignoreCase = true) ||
            segmentLinePattern.matches(line)

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
                val body = response.body?.string() ?: return@use null
                if (!isValidPlaylistContent(body)) return@use null
                body
            }
        }.getOrNull()
    }

    private fun isValidPlaylistContent(content: String): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return false
        }
        return trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")
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

    private fun parseMediaSegments(content: String, playlistUrl: String): List<HlsSegment> {
        val segments = mutableListOf<HlsSegment>()
        var pendingDurationSec = DEFAULT_SEGMENT_DURATION_SEC
        var startTimeSec = 0.0
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    pendingDurationSec = line
                        .removePrefix("#EXTINF:")
                        .substringBefore(",")
                        .trim()
                        .toDoubleOrNull()
                        ?: DEFAULT_SEGMENT_DURATION_SEC
                }
                line.isNotEmpty() && !line.startsWith("#") && isSegmentLine(line) -> {
                    segments.add(
                        HlsSegment(
                            url = resolveUrl(playlistUrl, line),
                            startTimeSec = startTimeSec,
                            durationSec = pendingDurationSec
                        )
                    )
                    startTimeSec += pendingDurationSec
                }
            }
        }
        return segments
    }

    private const val DEFAULT_SEGMENT_DURATION_SEC = 6.0

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
