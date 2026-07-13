package com.lemon.yingshi.tv.domain.model

data class MediaItem(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val rating: Float? = null,
    val genres: List<String> = emptyList(),
    val type: MediaType = MediaType.MOVIE,
    val quarkFileId: String? = null,
    val quarkPath: String? = null,
    val tmdbId: Int? = null,
    val doubanId: String? = null,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val watchProgress: Long? = null,
    val duration: Long? = null
)

data class Episode(
    val id: String,
    val mediaId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val stillUrl: String? = null,
    val duration: Long? = null,
    val quarkFileId: String? = null,
    val quarkPath: String? = null,
    val watchProgress: Long = 0,
    val isWatched: Boolean = false
)

data class WatchHistory(
    val id: String,
    val mediaItem: MediaItem,
    val episode: Episode? = null,
    val progress: Long = 0,
    val duration: Long = 0,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val watchCount: Int = 1
)
