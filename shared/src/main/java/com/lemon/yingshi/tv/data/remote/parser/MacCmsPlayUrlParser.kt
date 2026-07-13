package com.lemon.yingshi.tv.data.remote.parser

import com.lemon.yingshi.tv.data.remote.model.MacCmsEpisodeUrl
import com.lemon.yingshi.tv.data.remote.model.MacCmsPlaySource

object MacCmsPlayUrlParser {

    private const val GROUP_SEPARATOR = "$$$"
    private val playerCodePattern = Regex("""^[a-zA-Z0-9_-]+$""")

    fun parse(
        vodPlayFrom: String?,
        vodPlayUrl: String?,
        vodPlayNote: String? = null,
        playerShowNames: Map<String, String> = emptyMap()
    ): List<MacCmsPlaySource> {
        if (vodPlayUrl.isNullOrBlank()) return emptyList()

        val sourceCodes = splitPlayGroups(vodPlayFrom)
        val noteNames = splitPlayGroups(vodPlayNote)
        val blocks = splitPlayGroups(vodPlayUrl)

        return blocks.mapIndexed { index, block ->
            val episodes = block
                .split("#")
                .mapNotNull { segment -> parseSegment(segment.trim()) }
                .mapIndexed { epIndex, episode ->
                    episode.copy(
                        episodeNumber = episode.episodeNumber.takeIf { it > 0 } ?: (epIndex + 1)
                    )
                }
            MacCmsPlaySource(
                name = resolveSourceName(index, sourceCodes, noteNames, playerShowNames),
                episodes = episodes
            )
        }.filter { it.episodes.isNotEmpty() }
    }

    private fun splitPlayGroups(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val separator = if (raw.contains(GROUP_SEPARATOR)) GROUP_SEPARATOR else ","
        return raw.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * vod_play_from 存播放器编码，展示名需查后台 vodplayer 配置中的 show 字段。
     */
    private fun resolveSourceName(
        index: Int,
        sourceCodes: List<String>,
        noteNames: List<String>,
        playerShowNames: Map<String, String>
    ): String {
        val code = sourceCodes.getOrNull(index)?.trim().orEmpty()
        if (code.isNotBlank()) {
            lookupPlayerShowName(code, playerShowNames)?.let { return it }
            noteNames.getOrNull(index)
                ?.trim()
                ?.takeIf { isDisplayName(it, sourceCodes) }
                ?.let { return it }
        } else {
            noteNames.getOrNull(index)
                ?.trim()
                ?.takeIf { isDisplayName(it, sourceCodes) }
                ?.let { return it }
        }

        return "线路${index + 1}"
    }

    private fun lookupPlayerShowName(code: String, playerShowNames: Map<String, String>): String? {
        playerShowNames[code]?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return playerShowNames.entries
            .firstOrNull { it.key.equals(code, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun isDisplayName(name: String, sourceCodes: List<String>): Boolean {
        if (name.isBlank()) return false
        if (sourceCodes.any { it.equals(name, ignoreCase = true) }) return false
        if (name.contains(GROUP_SEPARATOR)) return false
        if (containsCjk(name)) return true
        return !looksLikePlayerCode(name)
    }

    private fun containsCjk(value: String): Boolean =
        value.any { char -> char.code in 0x4E00..0x9FFF }

    private fun looksLikePlayerCode(value: String): Boolean {
        if (value.length > 24) return false
        return playerCodePattern.matches(value)
    }

    private fun parseSegment(segment: String): MacCmsEpisodeUrl? {
        if (segment.isBlank()) return null

        val dollarIndex = segment.lastIndexOf('$')
        if (dollarIndex in 1 until segment.lastIndex) {
            val title = segment.substring(0, dollarIndex).trim()
            val url = segment.substring(dollarIndex + 1).trim()
            if (url.startsWith("http", ignoreCase = true)) {
                return MacCmsEpisodeUrl(
                    title = title,
                    url = url,
                    episodeNumber = extractEpisodeNumber(title)
                )
            }
        }

        if (segment.startsWith("http", ignoreCase = true)) {
            return MacCmsEpisodeUrl(
                title = "",
                url = segment,
                episodeNumber = 1
            )
        }
        return null
    }

    private fun extractEpisodeNumber(title: String): Int {
        Regex("""第?\s*(\d+)\s*集""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        Regex("""\b[Ee](\d+)\b""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        return 0
    }
}
