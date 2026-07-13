package com.lemon.yingshi.tv.utils

/**
 * 统一剧集标签：避免「第2集 第02集」「第20集 第1-20集」这类重复。
 */
object EpisodeLabelFormatter {

    private val EPISODE_LABEL = Regex("""^第\s*0*(\d+)\s*集$""")
    private val EPISODE_RANGE = Regex("""^第\s*0*(\d+)\s*-\s*0*(\d+)\s*集$""")

    fun build(episodeNumber: Int, rawTitle: String?, mediaTitle: String? = null): String {
        val subtitle = rawTitle?.trim().orEmpty()
        val seriesTitle = mediaTitle?.trim().orEmpty()
        return when {
            subtitle.isEmpty() || (seriesTitle.isNotEmpty() && subtitle == seriesTitle) ->
                "第${episodeNumber}集"
            EPISODE_LABEL.matches(subtitle) -> {
                val parsed = episodeNumberFromLabel(subtitle)
                if (parsed == episodeNumber) subtitle else "第${episodeNumber}集"
            }
            EPISODE_RANGE.matches(subtitle) -> "第${episodeNumber}集"
            else -> "第${episodeNumber}集 $subtitle"
        }
    }

    /** 兼容历史数据：合并重复/冗余的集数描述，只保留一条。 */
    fun normalizeForDisplay(label: String?): String? {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(Regex("""\s+"""))
        if (parts.size == 1) return trimmed

        val preferredSingle = parts
            .firstOrNull { EPISODE_LABEL.matches(it) }
            ?.let { episodeNumberFromLabel(it) }
            ?.let { "第${it}集" }

        if (preferredSingle != null) {
            val episodeNumber = episodeNumberFromLabel(preferredSingle)!!
            if (parts.all { isRedundantEpisodePart(it, episodeNumber, preferredSingle) }) {
                return preferredSingle
            }
        }

        return trimmed
    }

    private fun isRedundantEpisodePart(part: String, episodeNumber: Int, preferredSingle: String): Boolean {
        if (part == preferredSingle) return true
        if (EPISODE_LABEL.matches(part)) {
            return episodeNumberFromLabel(part) == episodeNumber
        }
        if (EPISODE_RANGE.matches(part)) {
            val range = parseRange(part)
            return episodeNumber in range
        }
        return false
    }

    private fun parseRange(label: String): IntRange {
        val match = EPISODE_RANGE.find(label) ?: return IntRange.EMPTY
        val start = match.groupValues[1].toIntOrNull() ?: return IntRange.EMPTY
        val end = match.groupValues[2].toIntOrNull() ?: return IntRange.EMPTY
        return start..end
    }

    private fun episodeNumberFromLabel(label: String): Int? =
        EPISODE_LABEL.find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
