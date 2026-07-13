package com.lemon.yingshi.tv.domain.service

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 将第三方采集站常见的「分享页 / 网页链接」解析为 ExoPlayer 可播的真实地址（多为 m3u8）。
 */
class ThirdPartyPlayUrlResolver(
    private val okHttpClient: OkHttpClient
) {
    fun resolve(
        rawUrl: String,
        macCmsParseEndpoints: List<String> = emptyList()
    ): Result<MediaPlaybackInfo> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("播放地址为空"))
        }

        val normalized = normalizeLocalPlaybackUrl(trimmed)
        if (isDirectPlayableUrl(normalized)) {
            return Result.success(
                MediaPlaybackInfo(
                    url = normalized,
                    headers = buildPlaybackHeaders(normalized, referer = null),
                    subtitles = emptyList()
                )
            )
        }

        if (looksLikeShareOrPageUrl(normalized)) {
            resolveFromSharePage(normalized)?.let { return Result.success(it) }
        }

        for (endpoint in macCmsParseEndpoints) {
            resolveFromMacCmsParse(endpoint, normalized)?.let { return Result.success(it) }
        }

        if (!looksLikeShareOrPageUrl(normalized)) {
            resolveFromSharePage(normalized)?.let { return Result.success(it) }
        }

        return Result.failure(
            IllegalStateException("无法解析播放地址，请尝试切换线路或确认该集是否为 m3u8 直链")
        )
    }

    private fun resolveFromSharePage(pageUrl: String): MediaPlaybackInfo? {
        val html = fetchText(pageUrl) ?: return null
        val streamUrl = extractStreamUrlFromHtml(html, pageUrl) ?: return null
        return MediaPlaybackInfo(
            url = streamUrl,
            headers = buildPlaybackHeaders(streamUrl, referer = pageUrl),
            subtitles = emptyList()
        )
    }

    private fun resolveFromMacCmsParse(parseEndpoint: String, pageUrl: String): MediaPlaybackInfo? {
        val endpoint = parseEndpoint.trim()
        if (endpoint.isBlank()) return null

        val requestUrl = buildParseRequestUrl(endpoint, pageUrl)
        val body = fetchText(requestUrl) ?: return null
        val streamUrl = extractStreamUrlFromParseResponse(body, requestUrl) ?: return null
        return MediaPlaybackInfo(
            url = streamUrl,
            headers = buildPlaybackHeaders(streamUrl, referer = pageUrl),
            subtitles = emptyList()
        )
    }

    internal fun extractStreamUrlFromHtml(html: String, pageUrl: String): String? {
        MAIN_JS_PATTERN.find(html)?.groupValues?.getOrNull(1)
            ?.let { resolveUrl(pageUrl, it) }
            ?.takeIf { isDirectPlayableUrl(it) }
            ?.let { return it }

        PLAYER_URL_PATTERN.find(html)?.groupValues?.getOrNull(1)
            ?.let { resolveUrl(pageUrl, it) }
            ?.takeIf { isDirectPlayableUrl(it) }
            ?.let { return it }

        M3U8_URL_PATTERN.findAll(html)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim() }
            .map { resolveUrl(pageUrl, it) }
            .firstOrNull { isDirectPlayableUrl(it) }
            ?.let { return it }

        return null
    }

    internal fun extractStreamUrlFromParseResponse(body: String, baseUrl: String): String? {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching {
                val json = JsonParser.parseString(trimmed)
                if (json.isJsonObject) {
                    val obj = json.asJsonObject
                    listOf("url", "playurl", "play_url", "src", "link")
                        .forEach { key ->
                            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { candidate ->
                                    resolveUrl(baseUrl, candidate)
                                        .takeIf { isDirectPlayableUrl(it) }
                                        ?.let { return it }
                                }
                        }
                }
            }
        }

        M3U8_URL_PATTERN.find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { resolveUrl(baseUrl, it) }
            ?.takeIf { isDirectPlayableUrl(it) }
            ?.let { return it }

        return null
    }

    private fun fetchText(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/json,*/*")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun buildParseRequestUrl(parseEndpoint: String, pageUrl: String): String {
        val encoded = URLEncoder.encode(pageUrl, Charsets.UTF_8.name())
        return when {
            parseEndpoint.contains("{url}") ->
                parseEndpoint.replace("{url}", encoded)
            parseEndpoint.endsWith("=") || parseEndpoint.endsWith("?") ->
                "$parseEndpoint$encoded"
            parseEndpoint.contains("?") ->
                "$parseEndpoint&url=$encoded"
            else ->
                "$parseEndpoint?url=$encoded"
        }
    }

    private fun buildPlaybackHeaders(streamUrl: String, referer: String?): Map<String, String> {
        val headers = linkedMapOf(
            "User-Agent" to DEFAULT_USER_AGENT
        )
        val effectiveReferer = referer?.takeIf { it.isNotBlank() }
            ?: runCatching {
                val uri = URI(streamUrl)
                "${uri.scheme}://${uri.authority}/"
            }.getOrNull()
        if (!effectiveReferer.isNullOrBlank()) {
            headers["Referer"] = effectiveReferer
        }
        return headers
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val MAIN_JS_PATTERN = Regex(
            """var\s+main\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val PLAYER_URL_PATTERN = Regex(
            """(?:url|playurl|play_url|video)\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val M3U8_URL_PATTERN = Regex(
            """(https?://[^\s"'<>]+\.m3u8(?:\?[^\s"'<>]*)?)""",
            RegexOption.IGNORE_CASE
        )

        private val DIRECT_PLAYABLE_EXTENSIONS = listOf(
            ".m3u8", ".mp4", ".mkv", ".flv", ".mov", ".webm", ".ts", ".m4v", ".avi"
        )

        fun isDirectPlayableUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.startsWith("file://") || lower.startsWith("content://")) return true
            if (DIRECT_PLAYABLE_EXTENSIONS.any { lower.contains(it) }) return true
            if (lower.contains("format=m3u8")) return true
            if (lower.contains("type=m3u8")) return true
            return false
        }

        fun looksLikeShareOrPageUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (isDirectPlayableUrl(url)) return false
            return lower.contains("/share/") ||
                lower.contains("/play/") ||
                lower.contains("/video/") ||
                lower.contains("/vod/") ||
                lower.contains("url=") ||
                !lower.contains(".")
        }

        fun resolveUrl(baseUrl: String, candidate: String): String {
            val value = candidate.trim()
            if (value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true)
            ) {
                return value
            }
            return runCatching {
                URI(baseUrl).resolve(value).toString()
            }.getOrDefault(value)
        }
    }
}
