package com.lemon.yingshi.tv.domain.model

fun mapMacCmsTypeName(typeName: String?): MediaType {
    val name = typeName?.trim().orEmpty()
    if (name.isBlank()) return MediaType.MOVIE
    return when {
        name.contains("动漫") || name.contains("动画") -> MediaType.ANIME
        name.contains("综艺") -> MediaType.VARIETY
        name.contains("纪录") -> MediaType.DOCUMENTARY
        name.contains("电影") -> MediaType.MOVIE
        name.contains("剧") || name.contains("电视") -> MediaType.TV_SHOW
        else -> MediaType.TV_SHOW
    }
}
