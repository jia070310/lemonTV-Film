package com.lemon.yingshi.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.local.database.dao.WatchHistoryDao
import com.lemon.yingshi.tv.data.local.database.entity.WatchHistoryEntity
import com.lemon.yingshi.tv.data.remote.model.MacCmsEpisodeRef
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.data.remote.model.MacCmsPlaySource
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.remote.parser.MacCmsPlayUrlParser
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MediaType
import com.lemon.yingshi.tv.domain.model.mapMacCmsTypeName
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    private val watchHistoryService: WatchHistoryService,
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _availableSeasons = MutableStateFlow<List<Int>>(listOf(1))
    val availableSeasons: StateFlow<List<Int>> = _availableSeasons.asStateFlow()

    private var allEpisodesBySeason: Map<Int, List<EpisodeItem>> = emptyMap()
    private var currentMediaId: String? = null
    private var macCmsPlaySources: List<MacCmsPlaySource> = emptyList()
    private var selectedPlaySourceIndex: Int = 0
    private var currentPosterUrl: String? = null

    fun loadMediaDetail(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            currentMediaId = null
            macCmsPlaySources = emptyList()
            selectedPlaySourceIndex = 0

            val vodId = MacCmsIds.decode(mediaId)
            if (vodId == null) {
                _uiState.value = DetailUiState.Error("无效的影片 ID")
                return@launch
            }
            loadMacCmsDetail(vodId, mediaId)
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
        val currentState = _uiState.value as? DetailUiState.Success ?: return
        val filteredEpisodes = allEpisodesBySeason[season] ?: emptyList()
        _uiState.value = currentState.copy(
            episodes = filteredEpisodes,
            media = currentState.media.copy(seasonCount = season)
        )
    }

    fun selectPlaySource(index: Int) {
        if (index !in macCmsPlaySources.indices || currentMediaId == null) return
        viewModelScope.launch {
            selectedPlaySourceIndex = index
            val currentState = _uiState.value as? DetailUiState.Success ?: return@launch
            val rawEpisodes = buildMacCmsEpisodes(currentMediaId!!, index, currentState.media.type)
            val episodes = enrichEpisodesWithWatchHistory(rawEpisodes, currentMediaId!!)
            allEpisodesBySeason = mapOf(1 to episodes)
            val isEpisodic = currentState.media.type.usesMacCmsEpisodeLayout()
            _uiState.value = currentState.copy(
                episodes = if (isEpisodic) episodes else emptyList(),
                selectedPlaySourceIndex = index,
                media = currentState.media.copy(
                    path = if (isEpisodic) {
                        episodes.firstOrNull()?.path
                    } else {
                        rawEpisodes.firstOrNull()?.path ?: currentState.media.path
                    },
                    totalEpisodes = episodes.size.takeIf { it > 0 }
                )
            )
        }
    }

    private suspend fun loadMacCmsDetail(vodId: Int, mediaId: String) {
        try {
            if (macCmsRepository.getServerUrl().isBlank()) {
                _uiState.value = DetailUiState.Error("请先在设置中配置 MacCMS 服务器")
                return
            }

            val vod = macCmsRepository.fetchVodDetail(vodId)
            if (vod == null) {
                _uiState.value = DetailUiState.Error("未找到影片信息")
                return
            }

            var playerNames = macCmsRepository.getPlayerShowNames()
            if (playerNames.isEmpty()) {
                playerNames = macCmsRepository.getPlayerShowNames(forceRefresh = true)
            }
            macCmsPlaySources = MacCmsPlayUrlParser.parse(
                vodPlayFrom = vod.vodPlayFrom,
                vodPlayUrl = vod.vodPlayUrl,
                vodPlayNote = vod.vodPlayNote,
                playerShowNames = playerNames
            )
            if (macCmsPlaySources.isEmpty()) {
                _uiState.value = DetailUiState.Error("暂无可用播放源")
                return
            }

            currentMediaId = mediaId
            currentPosterUrl = vod.vodPic
            val rawType = mapMacCmsTypeName(vod.typeName)
            val episodes = buildMacCmsEpisodes(mediaId, selectedPlaySourceIndex, rawType)
            val displayType = if (episodes.size > 1 && rawType != MediaType.MOVIE) rawType else MediaType.MOVIE

            if (episodes.isNotEmpty()) {
                allEpisodesBySeason = mapOf(1 to enrichEpisodesWithWatchHistory(episodes, mediaId))
                _availableSeasons.value = listOf(1)
                _selectedSeason.value = 1
            } else {
                allEpisodesBySeason = emptyMap()
            }

            val displayEpisodes = if (displayType.usesMacCmsEpisodeLayout()) {
                allEpisodesBySeason[1].orEmpty()
            } else {
                emptyList()
            }

            val playPath = if (displayType == MediaType.MOVIE) {
                macCmsPlaySources.getOrNull(selectedPlaySourceIndex)
                    ?.episodes?.firstOrNull()?.url
            } else {
                displayEpisodes.firstOrNull()?.path
            }

            _uiState.value = DetailUiState.Success(
                media = MediaDetail(
                    id = mediaId,
                    title = vod.vodName,
                    originalTitle = null,
                    overview = vod.vodContent?.takeIf { it.isNotBlank() } ?: vod.vodBlurb,
                    posterUrl = vod.vodPic,
                    backdropUrl = vod.vodPic,
                    rating = vod.vodScore?.toFloatOrNull(),
                    year = vod.vodYear,
                    genres = listOfNotNull(vod.typeName, vod.vodArea, vod.vodLang).filter { it.isNotBlank() },
                    type = displayType,
                    seasonCount = 1,
                    totalEpisodes = episodes.size.takeIf { it > 0 },
                    path = playPath,
                    director = vod.vodDirector?.trim()?.takeIf { it.isNotBlank() },
                    actors = vod.vodActor?.trim()?.takeIf { it.isNotBlank() },
                    releaseDate = vod.vodYear?.trim()?.takeIf { it.isNotBlank() }
                ),
                episodes = displayEpisodes,
                cast = emptyList(),
                playSources = macCmsPlaySources.map { it.name },
                selectedPlaySourceIndex = selectedPlaySourceIndex
            )
        } catch (e: Exception) {
            _uiState.value = DetailUiState.Error(
                MacCmsErrorMessages.fromThrowable(e, "加载失败")
            )
        }
    }

    private fun buildMacCmsEpisodes(
        mediaId: String,
        sourceIndex: Int,
        mediaType: MediaType
    ): List<EpisodeItem> {
        val source = macCmsPlaySources.getOrNull(sourceIndex) ?: return emptyList()
        return source.episodes.mapIndexed { index, episode ->
            EpisodeItem(
                id = MacCmsEpisodeRef.buildEpisodeId(mediaId, sourceIndex, index),
                episodeNumber = episode.episodeNumber,
                seasonNumber = 1,
                title = episode.title.takeIf { it.isNotBlank() },
                stillUrl = null,
                path = episode.url
            )
        }
    }

    private fun MediaType.usesMacCmsEpisodeLayout(): Boolean =
        this == MediaType.TV_SHOW ||
            this == MediaType.VARIETY ||
            this == MediaType.ANIME ||
            this == MediaType.DOCUMENTARY

    private suspend fun enrichEpisodesWithWatchHistory(
        episodes: List<EpisodeItem>,
        mediaId: String
    ): List<EpisodeItem> {
        return episodes.map { episode ->
            val history = resolveEpisodeWatchHistory(mediaId, episode.id)
            if (history != null) {
                episode.copy(
                    progress = history.progress,
                    duration = history.duration,
                    isWatched = history.progress > history.duration * 0.9
                )
            } else {
                episode
            }
        }
    }

    suspend fun getResumePlaybackInfo(): ResumePlaybackInfo? {
        val mediaId = currentMediaId ?: return null
        val currentState = _uiState.value as? DetailUiState.Success ?: return null
        val media = currentState.media

        if (media.type == MediaType.MOVIE) {
            val history = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
            return ResumePlaybackInfo(
                videoUrl = media.path.orEmpty(),
                title = media.title,
                episodeTitle = null,
                mediaId = mediaId,
                episodeId = null,
                startPosition = history?.progress?.takeIf { it > 0 } ?: 0L,
                posterUrl = currentPosterUrl
            )
        }

        val allEpisodes = allEpisodesBySeason.values.flatten()
        if (allEpisodes.isEmpty()) {
            val path = media.path?.takeIf { it.isNotBlank() } ?: return null
            val history = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
            return ResumePlaybackInfo(
                videoUrl = path,
                title = media.title,
                episodeTitle = null,
                mediaId = mediaId,
                episodeId = null,
                startPosition = history?.progress?.takeIf { it > 0 } ?: 0L,
                posterUrl = currentPosterUrl
            )
        }

        val latestHistory = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
        val targetEpisode = latestHistory?.episodeId?.let { watchedId ->
            allEpisodes.find { it.id == watchedId }
        } ?: allEpisodes.minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }))

        return targetEpisode?.let { episode ->
            val start = latestHistory?.takeIf { it.episodeId == episode.id }?.progress ?: 0L
            ResumePlaybackInfo(
                videoUrl = episode.path.orEmpty(),
                title = media.title,
                episodeTitle = buildEpisodeLabel(episode),
                mediaId = mediaId,
                episodeId = episode.id,
                startPosition = start,
                posterUrl = currentPosterUrl
            )
        }
    }

    suspend fun getEpisodePlaybackInfo(episodeId: String): EpisodePlaybackInfo? {
        val mediaId = currentMediaId ?: return null
        val currentState = _uiState.value as? DetailUiState.Success ?: return null
        val episode = allEpisodesBySeason.values.flatten().find { it.id == episodeId } ?: return null
        val history = resolveEpisodeWatchHistory(mediaId, episodeId)
        return EpisodePlaybackInfo(
            videoUrl = episode.path.orEmpty(),
            title = currentState.media.title,
            episodeTitle = buildEpisodeLabel(episode),
            mediaId = mediaId,
            episodeId = episodeId,
            startPosition = history?.progress ?: 0,
            posterUrl = currentPosterUrl
        )
    }

    private suspend fun resolveEpisodeWatchHistory(mediaId: String, episodeId: String): WatchHistoryEntity? {
        return watchHistoryDao.getWatchHistory(mediaId, episodeId)
            ?: watchHistoryDao.getLatestWatchHistoryByEpisodeId(episodeId)
    }

    private fun buildEpisodeLabel(episode: EpisodeItem): String {
        val subtitle = episode.title?.takeIf { it.isNotBlank() }
        return if (subtitle != null) "第${episode.episodeNumber}集 $subtitle" else "第${episode.episodeNumber}集"
    }
}

data class ResumePlaybackInfo(
    val videoUrl: String,
    val title: String,
    val episodeTitle: String?,
    val mediaId: String,
    val episodeId: String?,
    val startPosition: Long,
    val posterUrl: String? = null
)

data class EpisodePlaybackInfo(
    val videoUrl: String,
    val title: String,
    val episodeTitle: String?,
    val mediaId: String,
    val episodeId: String?,
    val startPosition: Long,
    val posterUrl: String? = null
)
