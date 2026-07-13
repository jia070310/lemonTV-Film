package com.lemon.yingshi.tv.ui.screens.detail

import com.lemon.yingshi.tv.domain.model.MediaType

data class MediaDetail(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Float?,
    val year: String?,
    val genres: List<String>,
    val type: MediaType,
    val seasonCount: Int = 0,
    val totalEpisodes: Int? = null,
    val path: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val releaseDate: String? = null
)

data class EpisodeQualityVariant(
    val mediaId: String,
    val label: String,
    val path: String
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: Int,
    val seasonNumber: Int = 1,
    val title: String?,
    val stillUrl: String?,
    val progress: Long = 0,
    val duration: Long = 0,
    val isWatched: Boolean = false,
    val path: String? = null,
    val qualityVariants: List<EpisodeQualityVariant> = emptyList()
)

data class CastItem(
    val id: Int,
    val name: String,
    val role: String?,
    val profileUrl: String?
)

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(
        val media: MediaDetail,
        val episodes: List<EpisodeItem>,
        val cast: List<CastItem> = emptyList(),
        val playSources: List<String> = emptyList(),
        val selectedPlaySourceIndex: Int = 0,
        val isLoadingPlayInfo: Boolean = false
    ) : DetailUiState()

    data class Error(val message: String) : DetailUiState()
}
