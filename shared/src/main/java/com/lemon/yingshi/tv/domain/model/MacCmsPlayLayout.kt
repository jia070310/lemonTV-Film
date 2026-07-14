package com.lemon.yingshi.tv.domain.model

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

    /**
     * 当前线路下是否需要展示「选集/版本」列表。
     * 有播放项即展示（含电影仅一个版本如 HD国语），方便用户直观看到名称。
     */
    fun shouldShowEpisodePicker(
        @Suppress("UNUSED_PARAMETER") rawType: MediaType,
        sources: List<MacCmsPlaySource>,
        sourceIndex: Int
    ): Boolean {
        if (sources.isEmpty()) return false
        val episodes = sources.getOrNull(sourceIndex)?.episodes.orEmpty()
        if (episodes.isEmpty()) return false
        if (episodes.size > 1) return true
        // 单条：有可读名称才展示（避免空白标题格子）
        return episodes.first().title.isNotBlank()
    }

    /** 播放项标题是否像电影清晰度/语种版本（相对剧集序号）。 */
    fun looksLikeMovieLineTitle(title: String): Boolean {
        val normalized = title.trim()
        if (normalized.isBlank()) return true
        return MOVIE_LINE_KEYWORDS.any { normalized.contains(it, ignoreCase = true) }
    }

    private val MOVIE_LINE_KEYWORDS = listOf(
        "正片", "预告", "片花", "花絮", "样本", "HD", "TC", "4K", "1080", "720",
        "国语", "粤语", "英语", "中字", "线路", "蓝光", "超清", "高清"
    )
}
