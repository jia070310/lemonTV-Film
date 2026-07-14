package com.lemon.yingshi.tv.utils

import com.lemon.yingshi.tv.domain.model.MacCmsPlayLayout

/**
 * 统一剧集 / 播放版本标签：优先展示接口返回的原始名称
 *（第1集、第01集、1、一集、HD中字、粤语等），避免强行改成 01/02。
 */
object EpisodeLabelFormatter {

    private val EPISODE_LABEL = Regex("""^第\s*0*(\d+)\s*集$""")
    private val EPISODE_RANGE = Regex("""^第\s*0*(\d+)\s*-\s*0*(\d+)\s*集$""")

    /** 详情页 / 缓存选择格：直接显示接口名，缺失时退回序号。 */
    fun cellLabel(episodeNumber: Int, rawTitle: String?): String {
        val subtitle = rawTitle?.trim().orEmpty()
        return subtitle.ifEmpty { episodeNumber.toString() }
    }

    fun build(episodeNumber: Int, rawTitle: String?, mediaTitle: String? = null): String {
        val subtitle = rawTitle?.trim().orEmpty()
        val seriesTitle = mediaTitle?.trim().orEmpty()
        return when {
            subtitle.isEmpty() || (seriesTitle.isNotEmpty() && subtitle == seriesTitle) ->
                "第${episodeNumber}集"
            // 电影清晰度/语种、已是完整集名：原样使用
            MacCmsPlayLayout.looksLikeMovieLineTitle(subtitle) -> subtitle
            EPISODE_LABEL.matches(subtitle) -> subtitle
            EPISODE_RANGE.matches(subtitle) -> "第${episodeNumber}集"
            // 「1」「01」「一集」等原始短名
            else -> subtitle
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
