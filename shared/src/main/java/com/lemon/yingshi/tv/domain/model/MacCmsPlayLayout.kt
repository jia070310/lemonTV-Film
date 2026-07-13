package com.lemon.yingshi.tv.domain.model

import com.lemon.yingshi.tv.data.remote.model.MacCmsEpisodeUrl
import com.lemon.yingshi.tv.data.remote.model.MacCmsPlaySource

/** MacCMS 详情页 / 播放器共用的「选集 vs 线路」布局判定。 */
fun MediaType.usesMacCmsEpisodeLayout(): Boolean =
    this == MediaType.TV_SHOW ||
        this == MediaType.VARIETY ||
        this == MediaType.ANIME ||
        this == MediaType.DOCUMENTARY

object MacCmsPlayLayout {

    fun resolveDisplayType(rawType: MediaType, episodeCountInSource: Int): MediaType =
        if (episodeCountInSource > 1 && rawType != MediaType.MOVIE) rawType else MediaType.MOVIE

    /** 多个播放组且每组仅 1 条地址，典型电影多线路布局。 */
    fun isMultiLineMovie(sources: List<MacCmsPlaySource>): Boolean {
        if (sources.size <= 1) return false
        return sources.all { it.episodes.size == 1 }
    }

    /** 播放器选集列表是否应展示（电影多线路返回 false）。 */
    fun shouldShowEpisodePicker(
        rawType: MediaType,
        sources: List<MacCmsPlaySource>,
        sourceIndex: Int
    ): Boolean {
        if (sources.isEmpty()) return false
        if (isMultiLineMovie(sources)) return false

        val episodes = sources.getOrNull(sourceIndex)?.episodes.orEmpty()
        if (episodes.size <= 1) return false

        val displayType = resolveDisplayType(rawType, episodes.size)
        if (!displayType.usesMacCmsEpisodeLayout()) return false

        return episodesLookLikeSeries(episodes, displayType)
    }

    private fun episodesLookLikeSeries(episodes: List<MacCmsEpisodeUrl>, displayType: MediaType): Boolean {
        if (displayType == MediaType.MOVIE) return false

        val hasEpisodeMarker = episodes.any { episode ->
            episode.title.contains('集') ||
                EPISODE_NUMBER_PATTERN.containsMatchIn(episode.title)
        }
        if (hasEpisodeMarker) return true

        val lineLikeCount = episodes.count { looksLikeMovieLineTitle(it.title) }
        if (lineLikeCount >= (episodes.size + 1) / 2) return false

        return displayType.usesMacCmsEpisodeLayout()
    }

    private fun looksLikeMovieLineTitle(title: String): Boolean {
        val normalized = title.trim()
        if (normalized.isBlank()) return true
        return MOVIE_LINE_KEYWORDS.any { normalized.contains(it, ignoreCase = true) }
    }

    private val EPISODE_NUMBER_PATTERN = Regex("""\b[Ee]\d+\b""")

    private val MOVIE_LINE_KEYWORDS = listOf(
        "正片", "预告", "片花", "花絮", "样本", "HD", "TC", "4K", "1080", "720",
        "国语", "粤语", "英语", "中字", "线路", "蓝光", "超清", "高清"
    )
}
