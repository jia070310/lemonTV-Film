package com.lemon.yingshi.tv.domain.model

enum class MediaType {
    MOVIE,      // 电影
    TV_SHOW,    // 电视剧
    ANIME,      // 动漫
    CONCERT,    // 演唱会
    VARIETY,    // 综艺
    DOCUMENTARY,// 纪录片
    OTHER       // 其它
}

/** 本地库 MovieEntity 下挂分集表的多集类型（与 WebDAV 分集类型一致）。 */
fun MediaType.isLocalEpisodicSeries(): Boolean =
    this == MediaType.TV_SHOW ||
        this == MediaType.VARIETY ||
        this == MediaType.ANIME ||
        this == MediaType.DOCUMENTARY
