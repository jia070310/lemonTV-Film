package com.lomen.tv.ui.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lomen.tv.data.local.database.dao.EpisodeDao
import com.lomen.tv.data.local.database.dao.MovieDao
import com.lomen.tv.data.local.database.dao.WebDavMediaDao
import com.lomen.tv.data.local.database.dao.WatchHistoryDao
import com.lomen.tv.data.local.database.dao.TmdbEpisodeDao
import com.lomen.tv.data.local.database.dao.TmdbMediaDao
import com.lomen.tv.data.local.database.entity.WatchHistoryEntity
import com.lomen.tv.data.local.database.entity.WebDavMediaEntity
import com.lomen.tv.data.local.database.entity.TmdbEpisodeEntity
import com.lomen.tv.data.scraper.ScrapeResult
import com.lomen.tv.data.scraper.DoubanScraper
import com.lomen.tv.data.scraper.FolderSeriesNameParser
import com.lomen.tv.data.scraper.TmdbScraper
import com.lomen.tv.domain.service.TmdbMetadataSyncManager
import com.lomen.tv.domain.model.MediaType
import com.lomen.tv.domain.model.isLocalEpisodicSeries
import com.lomen.tv.domain.service.MetadataService
import com.lomen.tv.domain.service.WatchHistoryService
import com.lomen.tv.utils.FileNameParser
import com.lomen.tv.utils.VideoQualityDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val movieDao: MovieDao,
    private val episodeDao: EpisodeDao,
    private val webDavMediaDao: WebDavMediaDao,
    private val tmdbMediaDao: TmdbMediaDao,
    private val tmdbEpisodeDao: TmdbEpisodeDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val metadataService: MetadataService,
    private val tmdbMetadataSyncManager: TmdbMetadataSyncManager,
    private val watchHistoryService: WatchHistoryService
) : ViewModel() {

    private val tmdbScraper = TmdbScraper.getInstance()
    private val doubanScraper = DoubanScraper()  // 添加豆瓣Scraper

    companion object {
        private const val TAG = "DetailViewModel"

        /** 综艺详情页标题用节目名（季在 Hero 区切换）；与首页合并卡片标题一致 */
        fun varietyDisplayTitleForDetail(media: WebDavMediaEntity): String {
            if (media.type != MediaType.VARIETY && media.type != MediaType.DOCUMENTARY) return media.title
            return FolderSeriesNameParser.stripAllSeasonMarkers(media.title.trim())
                .ifBlank { media.title.trim() }
        }

        /** 同系列搜索：用剥离季标记后的剧名，避免库内是「圆桌派」而当前条是「圆桌派 第八季」时搜不到 */
        fun titleForSeriesSearch(media: WebDavMediaEntity): String {
            if (media.type != MediaType.VARIETY &&
                media.type != MediaType.TV_SHOW &&
                media.type != MediaType.ANIME &&
                media.type != MediaType.DOCUMENTARY
            ) {
                return media.title
            }
            return FolderSeriesNameParser.stripAllSeasonMarkers(media.title.trim())
                .ifBlank { media.title.trim() }
        }
    }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState
    
    // 当前选中的季数
    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason
    
    // 可用的季数列表
    private val _availableSeasons = MutableStateFlow<List<Int>>(listOf(1))
    val availableSeasons: StateFlow<List<Int>> = _availableSeasons
    
    // 所有剧集按季分组
    private var allEpisodesBySeason: Map<Int, List<EpisodeItem>> = emptyMap()
    
    // 当前媒体 ID
    private var currentMediaId: String? = null
    private var currentMediaType: MediaType = MediaType.MOVIE

    private val _manualEditState = MutableStateFlow(ManualMetadataEditState())
    val manualEditState: StateFlow<ManualMetadataEditState> = _manualEditState
    
    /**
     * 切换选中的季数
     */
    fun selectSeason(season: Int) {
        Log.d(TAG, "选择季数: $season")
        _selectedSeason.value = season
        
        // 更新UI状态中的剧集列表
        val currentState = _uiState.value
        if (currentState is DetailUiState.Success) {
            val filteredEpisodes = allEpisodesBySeason[season] ?: emptyList()
            Log.d(TAG, "筛选后的剧集数量: ${filteredEpisodes.size}")
            _uiState.value = currentState.copy(
                episodes = filteredEpisodes,
                media = currentState.media.copy(seasonCount = season)
            )
        }
    }

    fun loadMediaDetail(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            currentMediaId = null

            try {
                // 首先尝试从WebDavMediaDao获取数据
                var webDavMedia = webDavMediaDao.getById(mediaId)
                
                // 如果通过id获取不到数据，尝试通过标题搜索
                if (webDavMedia == null) {
                    val searchResults = webDavMediaDao.search(mediaId)
                    if (searchResults.isNotEmpty()) {
                        webDavMedia = searchResults.first()
                    }
                }
                
                if (webDavMedia != null) {
                    // 记录真实媒体ID（避免把标题等非ID写入观看历史）
                    currentMediaId = webDavMedia.id
                    currentMediaType = webDavMedia.type
                    val isWebDavEpisodic = webDavMedia.type == MediaType.TV_SHOW ||
                        webDavMedia.type == MediaType.VARIETY ||
                        webDavMedia.type == MediaType.ANIME ||
                        webDavMedia.type == MediaType.DOCUMENTARY
                    
                    // 转换为UI模型（综艺详情标题与首页卡片一致，不直接用 TMDB 里可能错误的「第八季」）
                    val displayTitle = varietyDisplayTitleForDetail(webDavMedia)
                    val mediaDetail = MediaDetail(
                        id = webDavMedia.id,
                        title = displayTitle,
                        originalTitle = webDavMedia.originalTitle,
                        overview = webDavMedia.overview,
                        posterUrl = webDavMedia.posterUrl,
                        backdropUrl = webDavMedia.backdropUrl,
                        rating = webDavMedia.rating,
                        year = webDavMedia.year?.toString(),
                        genres = webDavMedia.genres?.split(",")?.map { it.trim() } ?: emptyList(),
                        type = webDavMedia.type,
                        seasonCount = webDavMedia.seasonNumber ?: 1, // 使用从文件名中提取的真实季数
                        totalEpisodes = null,  // 总集数后续后台加载，避免进入详情页阻塞
                        path = webDavMedia.filePath
                    )

                    // 对于电视剧，获取同系列的其他剧集
                    val episodes: List<EpisodeItem>
                    val seasons: List<Int>
                    val initialSeason: Int
                    
                    if (isWebDavEpisodic) {
                        // 按剥离季标记后的剧名搜索同系列（综艺/纪录片多季合并详情，在页内切换季）
                        val rawSeries = webDavMediaDao.search(titleForSeriesSearch(webDavMedia))
                        val seriesEpisodes = rawSeries.filter {
                            it.libraryId == webDavMedia.libraryId && it.type == webDavMedia.type
                        }
                        Log.d(TAG, "找到同系列剧集: ${seriesEpisodes.size} 个")

                        // 先用本地信息快速构建剧集列表；同季同集多清晰度（如 4K/1080 目录）合并为一卡
                        val episodeDrafts = seriesEpisodes.map { episode ->
                            val parsed = episode.fileName?.let { FileNameParser.parse(it) }
                            val parsedEp = parsed?.episode
                            val episodeNumber = parsedEp ?: episode.episodeNumber ?: 1
                            val seasonNumber = episode.seasonNumber ?: 1
                            val parsedTitle = parsed?.title?.trim().orEmpty()
                            val fileNameTitle = episode.fileName.substringBeforeLast('.').trim()
                            val episodeTitle = resolveEpisodeDisplayTitle(
                                parsedTitle = parsedTitle,
                                fileNameTitle = fileNameTitle,
                                seriesTitle = webDavMedia.title,
                                episodeNumber = episodeNumber
                            )
                            val stillUrl = webDavMedia.posterUrl ?: webDavMedia.backdropUrl ?: episode.posterUrl
                            WebDavEpisodeDraft(
                                entity = episode,
                                episodeNumber = episodeNumber,
                                seasonNumber = seasonNumber,
                                episodeTitle = episodeTitle,
                                stillUrl = stillUrl,
                                duration = 0L
                            )
                        }
                        val allEpisodes = mergeWebDavEpisodesBySeasonEpisode(episodeDrafts)
                        
                        // 按季分组
                        allEpisodesBySeason = allEpisodes.groupBy { it.seasonNumber }
                        Log.d(TAG, "按季分组: ${allEpisodesBySeason.keys}")
                        
                        // 获取可用季数列表并排序
                        seasons = allEpisodesBySeason.keys.sorted()
                        _availableSeasons.value = seasons
                        
                        // 设置初始选中的季数（当前剧集的季数，或第一季）
                        initialSeason = webDavMedia.seasonNumber ?: seasons.firstOrNull() ?: 1
                        _selectedSeason.value = initialSeason
                        
                        // 先给所有分季剧集填充观看历史，再按季筛选，保证详情页/切季都能拿到正确进度
                        val episodesWithHistoryBySeason = allEpisodesBySeason.mapValues { (_, eps) ->
                            enrichEpisodesWithWatchHistory(eps, webDavMedia.id)
                        }
                        allEpisodesBySeason = episodesWithHistoryBySeason

                        // 筛选当前季的剧集
                        episodes = episodesWithHistoryBySeason[initialSeason] ?: emptyList()
                        Log.d(TAG, "当前季 $initialSeason 的剧集数量：${episodes.size}")
                    } else {
                        episodes = emptyList()
                        seasons = emptyList()
                        initialSeason = 1
                    }
                    
                    // 先快速显示页面，演职人员稍后加载
                    _uiState.value = DetailUiState.Success(
                        media = mediaDetail,
                        episodes = episodes,
                        cast = emptyList()
                    )

                    // 后台加载：总集数、单集标题/剧照/时长（尽量不阻塞 UI；失败则保留本地占位信息）
                    if (isWebDavEpisodic && !webDavMedia.tmdbId.isNullOrBlank()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val tmdbIdInt = webDavMedia.tmdbId?.toIntOrNull() ?: return@launch

                            // 1) 先读本地 tmdb_media；没有则 enqueue（后台慢慢补全封面/简介/总集数）
                            val cached = tmdbMediaDao.getByTmdbId(tmdbIdInt)
                            if (cached == null) {
                                tmdbMetadataSyncManager.enqueueMedia(tmdbIdInt, priority = 1)
                            } else {
                                withContext(Dispatchers.Main) {
                                    val currentState = _uiState.value
                                    if (currentState is DetailUiState.Success) {
                                        _uiState.value = currentState.copy(
                                            media = currentState.media.copy(
                                                overview = cached.overview ?: currentState.media.overview,
                                                posterUrl = cached.posterUrl ?: currentState.media.posterUrl,
                                                backdropUrl = cached.backdropUrl ?: currentState.media.backdropUrl,
                                                totalEpisodes = cached.episodeCount ?: currentState.media.totalEpisodes
                                            )
                                        )
                                    }
                                }
                            }

                            // 2) 当前季：优先读本地 tmdb_episode；没有则 enqueue（后台补全单集标题/剧照/时长）
                            val currentSeason = selectedSeason.value
                            val cachedEpisodes = tmdbEpisodeDao.getBySeason(tmdbIdInt, currentSeason)
                            if (cachedEpisodes.isEmpty()) {
                                tmdbMetadataSyncManager.enqueueSeasonEpisodes(tmdbIdInt, currentSeason, priority = 1)
                            } else {
                                val enriched = (allEpisodesBySeason[currentSeason] ?: emptyList()).map { ep ->
                                    val cachedEp = cachedEpisodes.firstOrNull { it.episodeNumber == ep.episodeNumber }
                                    if (cachedEp == null) ep else ep.copy(
                                        title = cachedEp.name ?: ep.title,
                                        stillUrl = cachedEp.stillUrl ?: ep.stillUrl,
                                        duration = (cachedEp.runtimeMinutes?.times(60_000L))?.takeIf { it > 0 } ?: ep.duration
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    val currentState = _uiState.value
                                    if (currentState is DetailUiState.Success) {
                                        _uiState.value = currentState.copy(episodes = enriched)
                                    }
                                }
                            }
                        }
                    }
                    
                    // 后台异步加载演职人员（使用TMDB API，增加超时和重试）
                    viewModelScope.launch {
                        val tmdbIdStr = webDavMedia.tmdbId
                        Log.d(TAG, "[Cast] 开始加载演职人员: tmdbId=$tmdbIdStr")
                        val cast = if (tmdbIdStr != null) {
                            try {
                                withTimeout(30000L) {  // 增加到30秒超时
                                    val tmdbIdInt = tmdbIdStr.toIntOrNull()
                                    if (tmdbIdInt != null) {
                                        Log.d(TAG, "[Cast] 调用TMDB getCastAndCrew: tmdbId=$tmdbIdInt")
                                        val result = metadataService.getCastAndCrew(
                                            tmdbId = tmdbIdInt,
                                            type = webDavMedia.type,
                                            actorLimit = 10,
                                            directorLimit = 5
                                        ).map { castMember ->
                                            CastItem(
                                                id = castMember.id,
                                                name = castMember.name,
                                                role = castMember.role,
                                                profileUrl = castMember.profileUrl
                                            )
                                        }
                                        Log.d(TAG, "[Cast] 成功加载 ${result.size} 位演职人员")
                                        result
                                    } else {
                                        Log.w(TAG, "[Cast] tmdbId格式错误: $tmdbIdStr")
                                        emptyList()
                                    }
                                }
                            } catch (e: TimeoutCancellationException) {
                                Log.w(TAG, "[Cast] 加载超时（30秒），请检查网络")
                                emptyList()
                            } catch (e: Exception) {
                                Log.e(TAG, "[Cast] 加载失败: ${e.message}", e)
                                emptyList()
                            }
                        } else {
                            Log.w(TAG, "[Cast] tmdbId为null")
                            emptyList()
                        }
                        
                        // 更新UI添加演职人员
                        val currentState = _uiState.value
                        if (currentState is DetailUiState.Success) {
                            if (cast.isNotEmpty()) {
                                Log.d(TAG, "[Cast] 更新UI，显示 ${cast.size} 位演职人员")
                                _uiState.value = currentState.copy(cast = cast)
                            } else {
                                Log.d(TAG, "[Cast] 演职人员为空，不更新UI")
                            }
                        }
                    }
                    return@launch
                }

                // 如果WebDavMediaDao中没有数据，尝试从MovieDao获取
                val movieEntity = movieDao.getMovieById(mediaId)

                // 如果数据库中没有数据，显示错误信息
                if (movieEntity == null) {
                    _uiState.value = DetailUiState.Error("未找到媒体信息")
                    return@launch
                }
                
                // MovieEntity 场景下，使用真实 movieId
                currentMediaId = movieEntity.id

                // 转换为UI模型
                val mediaDetail = MediaDetail(
                    id = movieEntity.id,
                    title = movieEntity.title,
                    originalTitle = movieEntity.originalTitle,
                    overview = movieEntity.overview,
                    posterUrl = movieEntity.posterUrl,
                    backdropUrl = movieEntity.backdropUrl,
                    rating = movieEntity.rating,
                    year = movieEntity.releaseDate?.take(4),
                    genres = movieEntity.genre?.split(",")?.map { it.trim() } ?: emptyList(),
                    type = movieEntity.type,
                    seasonCount = movieEntity.seasonCount,
                    totalEpisodes = null,  // MovieEntity路径暂不支持
                    path = movieEntity.quarkPath
                )

                // 获取剧集列表（电视剧 / 综艺 / 动漫 / 纪录片等多集类型）
                val episodes = if (movieEntity.type.isLocalEpisodicSeries()) {
                    episodeDao.getEpisodesByMovieId(mediaId).first().map { entity ->
                        EpisodeItem(
                            id = entity.id,
                            episodeNumber = entity.episodeNumber,
                            seasonNumber = entity.seasonNumber,
                            title = entity.title,
                            stillUrl = entity.stillUrl,
                            progress = entity.watchProgress,
                            duration = entity.duration ?: 0,
                            isWatched = entity.isWatched,
                            path = entity.quarkPath
                        )
                    }
                } else {
                    emptyList()
                }
                
                // 多集类型，按季分组
                if (movieEntity.type.isLocalEpisodicSeries() && episodes.isNotEmpty()) {
                    allEpisodesBySeason = episodes.groupBy { it.seasonNumber }
                    val seasons = allEpisodesBySeason.keys.sorted()
                    _availableSeasons.value = seasons
                    _selectedSeason.value = seasons.firstOrNull() ?: 1
                }

                // 获取演职人员列表（导演+演员）
                Log.d(TAG, "开始获取演职人员: tmdbId=${movieEntity.tmdbId}, type=${movieEntity.type}")
                val cast = if (movieEntity.tmdbId != null) {
                    Log.d(TAG, "调用getCastAndCrew: tmdbId=${movieEntity.tmdbId}")
                    metadataService.getCastAndCrew(
                        tmdbId = movieEntity.tmdbId,
                        type = movieEntity.type,
                        actorLimit = 10,
                        directorLimit = 5
                    ).map { castMember ->
                        CastItem(
                            id = castMember.id,
                            name = castMember.name,
                            role = castMember.role,
                            profileUrl = castMember.profileUrl
                        )
                    }
                } else {
                    Log.w(TAG, "tmdbId为null，无法获取演职人员")
                    emptyList()
                }
                Log.d(TAG, "演职人员获取完成: 总计 ${cast.size} 位")

                _uiState.value = DetailUiState.Success(
                    media = mediaDetail,
                    episodes = episodes,
                    cast = cast
                )

            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private suspend fun enrichEpisodesWithWatchHistory(
        episodes: List<EpisodeItem>,
        mediaId: String
    ): List<EpisodeItem> {
        return episodes.map { episode ->
            val candidateIds = buildList {
                add(episode.id)
                episode.qualityVariants.forEach { add(it.mediaId) }
            }.distinct()
            val histories = candidateIds.mapNotNull { resolveEpisodeWatchHistory(mediaId, it) }
            val history = histories.maxWithOrNull(
                compareBy({ it.lastWatchedAt }, { it.progress })
            )
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
    
    /**
     * 获取应该播放的起始位置（用于立即播放按钮）
     */
    suspend fun getResumePlaybackInfo(): ResumePlaybackInfo? {
        val mediaId = currentMediaId ?: return null
        val currentState = _uiState.value as? DetailUiState.Success ?: return null
        val media = currentState.media
        
        if (media.type == MediaType.MOVIE) {
            // 电影：直接返回电影本身的播放历史
            val history = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
            return if (history != null && history.progress > 0) {
                ResumePlaybackInfo(
                    videoUrl = media.path ?: "",
                    title = media.title,
                    episodeTitle = null,
                    mediaId = mediaId,
                    episodeId = null,
                    startPosition = history.progress
                )
            } else {
                // 没有观看历史，从头开始播放
                ResumePlaybackInfo(
                    videoUrl = media.path ?: "",
                    title = media.title,
                    episodeTitle = null,
                    mediaId = mediaId,
                    episodeId = null,
                    startPosition = 0
                )
            }
        } else {
            // 电视剧 / 综艺 / 动漫 / 纪录片：查找最近观看的剧集
            // 获取所有剧集
            val allEpisodes = allEpisodesBySeason.values.flatten()
            if (allEpisodes.isEmpty()) {
                // 未合并到分集表时（或仅单文件）：按当前条目路径播放，与电影一致
                val path = media.path?.takeIf { it.isNotBlank() } ?: return null
                val history = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
                val start = if (history != null && history.progress > 0) history.progress else 0L
                return ResumePlaybackInfo(
                    videoUrl = path,
                    title = media.title,
                    episodeTitle = null,
                    mediaId = mediaId,
                    episodeId = null,
                    startPosition = start
                )
            }

            // 1) 优先使用“该剧集（series）”维度的最新记录（movieId=mediaId）
            val latestSeriesHistory = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
            val targetFromSeriesHistory = latestSeriesHistory
                ?.episodeId
                ?.let { watchedEpisodeId ->
                    allEpisodes.find { ep ->
                        ep.id == watchedEpisodeId ||
                            ep.qualityVariants.any { it.mediaId == watchedEpisodeId }
                    }
                }

            if (latestSeriesHistory != null && targetFromSeriesHistory != null) {
                val watchedEpisodeId = latestSeriesHistory.episodeId
                val variantPath = targetFromSeriesHistory.qualityVariants
                    .find { it.mediaId == watchedEpisodeId }
                    ?.path
                val url = variantPath ?: targetFromSeriesHistory.path ?: ""
                val resumeEpisodeId = watchedEpisodeId.takeIf { id ->
                    targetFromSeriesHistory.id == id ||
                        targetFromSeriesHistory.qualityVariants.any { it.mediaId == id }
                } ?: targetFromSeriesHistory.id
                return ResumePlaybackInfo(
                    videoUrl = url,
                    title = media.title,
                    episodeTitle = "第${targetFromSeriesHistory.episodeNumber}集 ${targetFromSeriesHistory.title ?: ""}",
                    mediaId = mediaId,
                    episodeId = resumeEpisodeId,
                    startPosition = latestSeriesHistory.progress
                )
            }

            // 2) 兼容旧历史格式：在每集历史中找该剧的最后一次记录（含多清晰度任一变体 id）
            var latestLegacyHistory: WatchHistoryEntity? = null
            var targetEpisode: EpisodeItem? = null
            var legacyHistoryMediaId: String? = null
            for (episode in allEpisodes) {
                val ids = buildList {
                    add(episode.id)
                    episode.qualityVariants.forEach { add(it.mediaId) }
                }.distinct()
                for (eid in ids) {
                    val history = resolveEpisodeWatchHistory(mediaId, eid) ?: continue
                    if (latestLegacyHistory == null || history.lastWatchedAt > latestLegacyHistory.lastWatchedAt) {
                        latestLegacyHistory = history
                        targetEpisode = episode
                        legacyHistoryMediaId = eid
                    }
                }
            }

            return if (targetEpisode != null && latestLegacyHistory != null) {
                val resumePath = legacyHistoryMediaId?.let { hid ->
                    targetEpisode.qualityVariants.find { it.mediaId == hid }?.path
                } ?: targetEpisode.path ?: ""
                val resumeEpId = legacyHistoryMediaId ?: targetEpisode.id
                ResumePlaybackInfo(
                    videoUrl = resumePath,
                    title = media.title,
                    episodeTitle = "第${targetEpisode.episodeNumber}集 ${targetEpisode.title ?: ""}",
                    mediaId = mediaId,
                    episodeId = resumeEpId,
                    startPosition = latestLegacyHistory.progress
                )
            } else {
                // 没有任何观看历史，播放第一集
                val firstEpisode = allEpisodes.minWithOrNull(
                    compareBy<EpisodeItem>({ it.seasonNumber }, { it.episodeNumber })
                )
                if (firstEpisode != null) {
                    ResumePlaybackInfo(
                        videoUrl = firstEpisode.path ?: "",
                        title = media.title,
                        episodeTitle = "第${firstEpisode.episodeNumber}集 ${firstEpisode.title ?: ""}",
                        mediaId = mediaId,
                        episodeId = firstEpisode.id,
                        startPosition = 0
                    )
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * 获取指定剧集的播放信息（包含观看历史）
     */
    suspend fun getEpisodePlaybackInfo(episodeId: String): EpisodePlaybackInfo? {
        val mediaId = currentMediaId ?: return null
        val currentState = _uiState.value as? DetailUiState.Success ?: return null
        
        val episode = allEpisodesBySeason.values.flatten().find { ep ->
            ep.id == episodeId || ep.qualityVariants.any { it.mediaId == episodeId }
        } ?: return null

        val videoUrl = episode.qualityVariants.find { it.mediaId == episodeId }?.path
            ?: episode.path ?: ""

        val history = resolveEpisodeWatchHistory(mediaId, episodeId)
            ?: resolveEpisodeWatchHistory(mediaId, episode.id)

        return EpisodePlaybackInfo(
            videoUrl = videoUrl,
            title = currentState.media.title,
            episodeTitle = "第${episode.episodeNumber}集 ${episode.title ?: ""}",
            mediaId = mediaId,
            episodeId = episodeId,
            startPosition = history?.progress ?: 0
        )
    }

    /**
     * 兼容多种历史键格式：
     * 1) 新格式：movieId=系列ID, episodeId=单集ID
     * 2) 旧格式：movieId=单集ID, episodeId=null
     * 3) 过渡格式：按 episodeId 直接检索
     */
    private suspend fun resolveEpisodeWatchHistory(mediaId: String, episodeId: String): WatchHistoryEntity? {
        return watchHistoryDao.getWatchHistory(mediaId, episodeId)
            ?: watchHistoryDao.getLatestWatchHistoryByEpisodeId(episodeId)
            ?: watchHistoryDao.getLatestWatchHistoryByMovieId(episodeId)
    }

    fun refreshMetadata(mediaId: String) {
        viewModelScope.launch {
            try {
                val movieEntity = movieDao.getMovieById(mediaId) ?: return@launch

                // 如果TMDb ID存在，刷新元数据
                movieEntity.tmdbId?.let { tmdbId ->
                    val result = metadataService.getDetailsByTmdbId(tmdbId, movieEntity.type)

                    result.onSuccess { mediaItem ->
                        // 更新数据库
                        val updatedEntity = movieEntity.copy(
                            title = mediaItem.title,
                            originalTitle = mediaItem.originalTitle,
                            overview = mediaItem.overview,
                            posterUrl = mediaItem.posterUrl,
                            backdropUrl = mediaItem.backdropUrl,
                            rating = mediaItem.rating,
                            genre = mediaItem.genres.joinToString(","),
                            updatedAt = System.currentTimeMillis()
                        )
                        movieDao.updateMovie(updatedEntity)

                        // 重新加载
                        loadMediaDetail(mediaId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun prepareManualEdit(initialTitle: String, initialYear: String?) {
        _manualEditState.value = ManualMetadataEditState(
            query = buildString {
                append(initialTitle.trim())
                initialYear?.trim()?.takeIf { it.length == 4 }?.let {
                    append(" ")
                    append(it)
                }
            },
            candidates = emptyList(),
            isSearching = false,
            isApplying = false,
            errorMessage = null,
            lastAppliedTmdbId = null,
            successMessage = null
        )
    }

    fun updateManualQuery(query: String) {
        _manualEditState.value = _manualEditState.value.copy(query = query)
    }

    fun searchManualCandidates() {
        val query = _manualEditState.value.query.trim()
        if (query.isBlank()) {
            _manualEditState.value = _manualEditState.value.copy(errorMessage = "请输入要搜索的标题")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _manualEditState.value = _manualEditState.value.copy(
                isSearching = true,
                errorMessage = null,
                candidates = emptyList()
            )
            try {
                val year = Regex("""(19|20)\d{2}""").find(query)?.value?.toIntOrNull()
                val pureQuery = query.replace(Regex("""(19|20)\d{2}"""), "").trim().ifBlank { query }
                val episodic = currentMediaType == MediaType.TV_SHOW ||
                    currentMediaType == MediaType.VARIETY ||
                    currentMediaType == MediaType.ANIME ||
                    currentMediaType == MediaType.DOCUMENTARY

                val results = if (episodic) {
                    tmdbScraper.searchTvCandidates(pureQuery, year, limit = 12)
                } else {
                    tmdbScraper.searchMovieCandidates(pureQuery, year, limit = 12)
                }

                val candidates = results.mapNotNull { it.toCandidateOrNull() }
                _manualEditState.value = _manualEditState.value.copy(
                    isSearching = false,
                    candidates = candidates,
                    errorMessage = if (candidates.isEmpty()) "没有找到匹配结果，请换个关键词" else null
                )
            } catch (e: Exception) {
                _manualEditState.value = _manualEditState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "搜索失败"
                )
            }
        }
    }

    fun applyManualCandidate(candidate: ManualTmdbCandidate) {
        val mediaId = currentMediaId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _manualEditState.value = _manualEditState.value.copy(isApplying = true, errorMessage = null)
            try {
                val current = webDavMediaDao.getById(mediaId) ?: return@launch
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    tmdbId = candidate.tmdbId.toString(),
                    title = candidate.title.ifBlank { current.title },
                    originalTitle = candidate.originalTitle ?: current.originalTitle,
                    overview = candidate.overview ?: current.overview,
                    posterUrl = candidate.posterUrl ?: current.posterUrl,
                    backdropUrl = candidate.backdropUrl ?: current.backdropUrl,
                    year = candidate.year ?: current.year,
                    rating = candidate.rating ?: current.rating,
                    genres = candidate.genres.takeIf { it.isNotEmpty() }?.joinToString(",") ?: current.genres,
                    source = "tmdb",
                    scrapedAt = now,
                    updatedAt = now
                )
                webDavMediaDao.update(updated)

                // 同剧分集批量覆盖：手动修正后，确保所有分集条目的封面/简介/TMDB 关联一起更新，
                // 避免「最近播放」这类按 episodeId 取数的入口仍显示旧封面。
                val relatedIds = (
                    allEpisodesBySeason.values.flatten().flatMap { ep ->
                        listOf(ep.id) + ep.qualityVariants.map { it.mediaId }
                    } + mediaId
                    ).distinct()
                relatedIds.forEach { id ->
                    if (id == mediaId) return@forEach
                    val item = webDavMediaDao.getById(id) ?: return@forEach
                    if (item.libraryId != updated.libraryId || item.type != updated.type) return@forEach
                    webDavMediaDao.update(
                        item.copy(
                            tmdbId = candidate.tmdbId.toString(),
                            overview = candidate.overview ?: item.overview,
                            posterUrl = candidate.posterUrl ?: item.posterUrl,
                            backdropUrl = candidate.backdropUrl ?: item.backdropUrl,
                            year = candidate.year ?: item.year,
                            rating = candidate.rating ?: item.rating,
                            genres = candidate.genres.takeIf { it.isNotEmpty() }?.joinToString(",") ?: item.genres,
                            source = "tmdb",
                            scrapedAt = now,
                            updatedAt = now
                        )
                    )
                }

                tmdbMetadataSyncManager.enqueueMedia(candidate.tmdbId, priority = 1)
                if (updated.type != MediaType.MOVIE) {
                    // 手动修正后，立即拉取并落库单集信息，覆盖旧本地缓存，详情页可立刻显示正确封面/副标题
                    preloadEpisodeCacheForManualSelection(candidate.tmdbId)
                }

                _manualEditState.value = _manualEditState.value.copy(
                    isApplying = false,
                    lastAppliedTmdbId = candidate.tmdbId,
                    errorMessage = null,
                    successMessage = "已修正为：${candidate.title}${candidate.year?.let { " (${it})" } ?: ""}"
                )
                // 让首页最近播放立即重新组装，避免命中 5 分钟旧缓存
                watchHistoryService.clearCache()
                loadMediaDetail(mediaId)
            } catch (e: Exception) {
                _manualEditState.value = _manualEditState.value.copy(
                    isApplying = false,
                    errorMessage = e.message ?: "应用失败"
                )
            }
        }
    }

    fun clearManualEditAppliedFlag() {
        _manualEditState.value = _manualEditState.value.copy(lastAppliedTmdbId = null)
    }

    fun clearManualEditSuccessMessage() {
        _manualEditState.value = _manualEditState.value.copy(successMessage = null)
    }

    private fun resolveEpisodeDisplayTitle(
        parsedTitle: String,
        fileNameTitle: String,
        seriesTitle: String,
        episodeNumber: Int
    ): String {
        val normalizedSeries = seriesTitle.trim()
        val parsed = parsedTitle.trim()
        val genericEpRegex = Regex("""^第\s*\d+\s*集$""")

        if (parsed.isNotBlank() &&
            !genericEpRegex.matches(parsed) &&
            !parsed.equals(normalizedSeries, ignoreCase = true)
        ) {
            return parsed
        }

        // 从文件名里剥离剧名/年份/季集标记，尽量得到真实单集标题
        var cleaned = fileNameTitle
            .replace(normalizedSeries, "", ignoreCase = true)
            .replace(Regex("""\b(19|20)\d{2}\b"""), "")
            .replace(Regex("""\b[Ss]\d+[Ee]\d+\b"""), "")
            .replace(Regex("""第\s*\d+\s*集"""), "")
            .replace(Regex("""[._-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank()) {
            cleaned = "第${episodeNumber}集"
        }
        return cleaned
    }

    private data class WebDavEpisodeDraft(
        val entity: WebDavMediaEntity,
        val episodeNumber: Int,
        val seasonNumber: Int,
        val episodeTitle: String,
        val stillUrl: String?,
        val duration: Long
    )

    /** 同季同集多条 WebDAV 记录合并为一项，默认保留最高清晰度对应文件 */
    private fun mergeWebDavEpisodesBySeasonEpisode(drafts: List<WebDavEpisodeDraft>): List<EpisodeItem> {
        if (drafts.isEmpty()) return emptyList()
        return drafts
            .groupBy { it.seasonNumber to it.episodeNumber }
            .values
            .map { group ->
                val sorted = group.sortedByDescending {
                    VideoQualityDetector.rank(it.entity.filePath, it.entity.fileName)
                }
                val best = sorted.first()
                val variants = sorted.distinctBy { it.entity.id }.map {
                    EpisodeQualityVariant(
                        mediaId = it.entity.id,
                        label = VideoQualityDetector.label(it.entity.filePath, it.entity.fileName),
                        path = it.entity.filePath
                    )
                }
                EpisodeItem(
                    id = best.entity.id,
                    episodeNumber = best.episodeNumber,
                    seasonNumber = best.seasonNumber,
                    title = best.episodeTitle,
                    stillUrl = best.stillUrl,
                    progress = 0,
                    duration = best.duration,
                    isWatched = false,
                    path = best.entity.filePath,
                    qualityVariants = variants
                )
            }
    }

    private suspend fun preloadEpisodeCacheForManualSelection(tmdbId: Int) {
        val seasons = _availableSeasons.value
            .ifEmpty { listOf(_selectedSeason.value.coerceAtLeast(1)) }
            .distinct()
            .sorted()

        for (season in seasons) {
            val seasonEpisodes = runCatching {
                tmdbScraper.getTvSeasonEpisodes(tmdbId.toString(), season).orEmpty()
            }.getOrElse { emptyList() }

            if (seasonEpisodes.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val entities = seasonEpisodes.map { ep ->
                    TmdbEpisodeEntity(
                        tmdbId = tmdbId,
                        seasonNumber = season,
                        episodeNumber = ep.episodeNumber,
                        name = ep.name.takeIf { it.isNotBlank() },
                        overview = ep.overview?.takeIf { it.isNotBlank() },
                        stillUrl = ep.stillUrl,
                        airDate = ep.airDate,
                        runtimeMinutes = ep.runtime.takeIf { it > 0 },
                        updatedAt = now
                    )
                }
                tmdbEpisodeDao.upsertAll(entities)
            } else {
                tmdbMetadataSyncManager.enqueueSeasonEpisodes(tmdbId, season, priority = 1)
            }
        }
    }
}

data class ManualMetadataEditState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isApplying: Boolean = false,
    val candidates: List<ManualTmdbCandidate> = emptyList(),
    val errorMessage: String? = null,
    val lastAppliedTmdbId: Int? = null,
    val successMessage: String? = null
)

data class ManualTmdbCandidate(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val rating: Float?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>
)

private fun ScrapeResult.toCandidateOrNull(): ManualTmdbCandidate? {
    val parsedTmdbId = id.toIntOrNull() ?: return null
    return ManualTmdbCandidate(
        tmdbId = parsedTmdbId,
        title = title,
        originalTitle = originalTitle,
        year = year,
        rating = rating,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        genres = genres
    )
}

// 数据类：用于立即播放按钮的播放信息
data class ResumePlaybackInfo(
    val videoUrl: String,
    val title: String,
    val episodeTitle: String?,
    val mediaId: String,
    val episodeId: String?,
    val startPosition: Long
)

// 数据类：用于指定剧集播放的信息
data class EpisodePlaybackInfo(
    val videoUrl: String,
    val title: String,
    val episodeTitle: String?,
    val mediaId: String,
    val episodeId: String?,
    val startPosition: Long
)
