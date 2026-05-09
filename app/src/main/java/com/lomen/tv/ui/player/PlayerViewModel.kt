package com.lomen.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.lomen.tv.data.local.database.dao.EpisodeDao
import com.lomen.tv.data.local.database.dao.MovieDao
import com.lomen.tv.data.local.database.dao.WebDavMediaDao
import com.lomen.tv.data.local.database.entity.SkipConfigEntity
import com.lomen.tv.data.local.database.entity.WebDavMediaEntity
import com.lomen.tv.data.scraper.FolderSeriesNameParser
import com.lomen.tv.data.repository.SkipConfigRepository
import com.lomen.tv.domain.model.MediaType
import com.lomen.tv.domain.model.isLocalEpisodicSeries
import com.lomen.tv.domain.service.MediaUrlResolver
import com.lomen.tv.domain.service.PlaybackStatsService
import com.lomen.tv.domain.service.PlayerService
import com.lomen.tv.domain.service.PlayerState
import com.lomen.tv.domain.service.SubtitleInfo
import com.lomen.tv.domain.service.TrackInfo
import com.lomen.tv.domain.service.WatchHistoryService
import com.lomen.tv.utils.FileNameParser
import com.lomen.tv.utils.VideoQualityDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerService: PlayerService,
    private val watchHistoryService: WatchHistoryService,
    private val playbackStatsService: PlaybackStatsService,
    private val mediaUrlResolver: MediaUrlResolver,
    private val episodeDao: EpisodeDao,
    private val movieDao: MovieDao,
    private val webDavMediaDao: WebDavMediaDao,
    private val skipConfigRepository: SkipConfigRepository,
    private val playerSettingsPreferences: com.lomen.tv.data.preferences.PlayerSettingsPreferences
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerService.playerState
    val availableSubtitles: StateFlow<List<TrackInfo>> = playerService.availableSubtitles
    val availableAudioTracks: StateFlow<List<TrackInfo>> = playerService.availableAudioTracks
    val selectedSubtitleIndex: StateFlow<Int> = playerService.selectedSubtitleIndex
    val selectedAudioTrackIndex: StateFlow<Int> = playerService.selectedAudioTrackIndex

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var saveProgressJob: Job? = null
    private var playbackStatsJob: Job? = null
    private var pendingPlaybackMs: Long = 0L

    private var currentMediaId: String? = null
    private var currentEpisodeId: String? = null
    @Volatile private var forceStartFromBeginning: Boolean = false

    fun initializePlayer() {
        playerService.initializePlayer()
        startPositionUpdates()
        startProgressSaving()
        startPlaybackStatsTracking()
    }

    fun releasePlayer() {
        // 停止自动保存，但先保存一次最终进度
        stopPlaybackStatsTracking()
        flushPlaybackStatsSync()
        stopProgressSaving()
        // 保存最终进度（使用 runBlocking 确保完成）
        saveWatchProgressSync()
        stopPositionUpdates()
        playerService.releasePlayer()
    }
    
    private fun saveWatchProgressSync() {
        val mediaId = currentMediaId ?: return
        
        // 检查记忆续播开关是否打开
        val rememberEnabled = kotlinx.coroutines.runBlocking {
            playerSettingsPreferences.rememberPlaybackPosition.first()
        }
        if (!rememberEnabled) {
            android.util.Log.d("PlayerViewModel", "Remember playback position is disabled, skipping sync save")
            return
        }
        
        val position = playerService.getCurrentPosition()
        val duration = playerService.getDuration()

        if (duration > 0) {
            // 使用 runBlocking 确保在 ViewModel 销毁前完成保存
            kotlinx.coroutines.runBlocking {
                try {
                    watchHistoryService.saveWatchProgress(
                        mediaId = mediaId,
                        episodeId = currentEpisodeId,
                        progress = position,
                        duration = duration
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error saving watch progress synchronously", e)
                }
            }
        }
    }

    private fun flushPlaybackStatsSync() {
        val delta = pendingPlaybackMs
        if (delta <= 0L) return
        pendingPlaybackMs = 0L
        kotlinx.coroutines.runBlocking {
            runCatching {
                playbackStatsService.addPlaybackTimeMs(delta)
            }.onFailure {
                android.util.Log.e("PlayerViewModel", "Error flushing playback stats synchronously", it)
            }
        }
    }

    fun setMediaInfo(mediaId: String, episodeId: String? = null) {
        android.util.Log.d("PlayerViewModel", "Setting media info: mediaId=$mediaId, episodeId=$episodeId")
        currentMediaId = mediaId
        currentEpisodeId = episodeId
    }
    
    /**
     * 立即保存观看进度（用于切换剧集时更新最近播放）
     */
    fun saveWatchProgressImmediately() {
        viewModelScope.launch {
            try {
                val mediaId = currentMediaId ?: return@launch
                
                // 检查记忆续播开关是否打开
                val rememberEnabled = playerSettingsPreferences.rememberPlaybackPosition.first()
                if (!rememberEnabled) {
                    android.util.Log.d("PlayerViewModel", "Remember playback position is disabled, skipping immediate save")
                    return@launch
                }
                
                val duration = playerService.getDuration()
                val position = playerService.getCurrentPosition()
                
                android.util.Log.d("PlayerViewModel", "Saving watch progress immediately: mediaId=$mediaId, episodeId=$currentEpisodeId, position=$position, duration=$duration")
                
                // 即使 position 为 0 也要保存，用于创建/更新观看历史记录
                if (duration > 0) {
                    watchHistoryService.saveWatchProgress(
                        mediaId = mediaId,
                        episodeId = currentEpisodeId,
                        progress = position,
                        duration = duration
                    )
                    android.util.Log.d("PlayerViewModel", "Watch progress saved successfully")
                } else {
                    android.util.Log.w("PlayerViewModel", "Duration is 0, cannot save watch progress yet")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error saving watch progress immediately", e)
            }
        }
    }

    /**
     * 选集面板切换集数前保存当前集进度（同页切换不会走 Activity 销毁逻辑，需主动落库）
     */
    suspend fun saveWatchProgressBeforeEpisodeSelectionSwitch() {
        val mediaId = currentMediaId ?: return
        val rememberEnabled = playerSettingsPreferences.rememberPlaybackPosition.first()
        if (!rememberEnabled) return
        val duration = playerService.getDuration()
        val position = playerService.getCurrentPosition()
        if (duration <= 0) return
        try {
            watchHistoryService.saveWatchProgress(
                mediaId = mediaId,
                episodeId = currentEpisodeId,
                progress = position,
                duration = duration
            )
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error saving watch progress before episode switch", e)
        }
    }

    /**
     * 选集中某一集时的续播位置（与详情页、下一集 [getPlaybackInfo] 使用的历史键一致）
     */
    suspend fun getResumeStartPositionForEpisodeSelection(selectedEpisodeId: String): Long {
        val mediaId = currentMediaId ?: return 0L
        val rememberEnabled = playerSettingsPreferences.rememberPlaybackPosition.first()
        if (!rememberEnabled) return 0L

        val movie = movieDao.getMovieById(mediaId)
        if (movie?.type?.isLocalEpisodicSeries() == true) {
            return watchHistoryService.getLastWatchPosition(mediaId, selectedEpisodeId)?.progress ?: 0L
        }

        watchHistoryService.getLastWatchPosition(mediaId, selectedEpisodeId)?.progress?.let { return it }
        watchHistoryService.getLastWatchPosition(selectedEpisodeId, selectedEpisodeId)?.progress?.let { return it }
        return watchHistoryService.getLastWatchPosition(selectedEpisodeId, null)?.progress ?: 0L
    }

    fun getPlayer(): Player? = playerService.getPlayer()

    /**
     * 准备媒体播放 - 支持真实URL解析和WebDAV认证
     */
    fun prepareMedia(videoPath: String, title: String?, episodeTitle: String?, startPosition: Long = 0L) {
        // 每次新开媒体先重置；若用户选择“从头开始”会在播放中重新置位
        forceStartFromBeginning = false
        viewModelScope.launch {
            try {
                _isLoadingMedia.value = true
                _loadError.value = null

                android.util.Log.i(
                    "PlayerCrashProbe",
                    "prepareMedia start: mediaId=$currentMediaId, episodeId=$currentEpisodeId, " +
                        "startPosition=$startPosition, title=$title, episodeTitle=$episodeTitle, " +
                        "videoPath=${videoPath.take(300)}"
                )
                
                // 解析真实播放URL
                val result = mediaUrlResolver.resolvePlaybackUrl(videoPath)
                
                if (result.isSuccess) {
                    val playbackInfo = result.getOrNull()!!
                    val headerKeys = playbackInfo.headers.keys.joinToString(",")
                    android.util.Log.i(
                        "PlayerCrashProbe",
                        "prepareMedia resolved: url=${playbackInfo.url.take(300)}, " +
                            "headers=[$headerKeys], subtitleCount=${playbackInfo.subtitles.size}"
                    )
                    
                    android.util.Log.d("PlayerViewModel", "Preparing media with startPosition=$startPosition ms")
                    
                    // 准备播放，直接传递起始位置
                    playerService.prepareMedia(
                        url = playbackInfo.url,
                        title = title,
                        episodeTitle = episodeTitle,
                        headers = playbackInfo.headers,
                        subtitles = playbackInfo.subtitles,
                        startPositionMs = startPosition
                    )
                    
                    // 如果指定了起始位置，等待播放器准备好后确保位置正确
                    if (startPosition > 0) {
                        android.util.Log.d("PlayerViewModel", "Waiting for player to be ready, then verifying start position: $startPosition ms")
                        // 先暂停播放，避免从头播放
                        playerService.pause()
                        
                        // 等待播放器准备好
                        var retries = 0
                        while (retries < 100) {
                            // 检查播放器是否被释放
                            val player = playerService.getPlayer()
                            if (player == null) {
                                android.util.Log.w("PlayerViewModel", "Player was released during preparation, reinitializing...")
                                // 重新初始化播放器并重新准备媒体
                                playerService.initializePlayer()
                                playerService.prepareMedia(
                                    url = playbackInfo.url,
                                    title = title,
                                    episodeTitle = episodeTitle,
                                    headers = playbackInfo.headers,
                                    subtitles = playbackInfo.subtitles,
                                    startPositionMs = startPosition
                                )
                                retries = 0 // 重置重试计数
                                delay(100) // 等待播放器初始化
                                continue
                            }
                            
                            val state = playerService.playerState.value
                            if (state.type == com.lomen.tv.domain.service.PlayerState.Type.READY && state.duration > 0) {
                                if (forceStartFromBeginning) {
                                    android.util.Log.d("PlayerViewModel", "Force restart enabled, play from beginning")
                                    playerService.pause()
                                    playerService.seekTo(0L)
                                    delay(120)
                                    playerService.play()
                                    forceStartFromBeginning = false
                                    break
                                }
                                // 播放器已准备好，先暂停，然后跳转到正确位置
                                playerService.pause()
                                val currentPos = playerService.getCurrentPosition()
                                android.util.Log.d("PlayerViewModel", "Player ready, current position: $currentPos, expected: $startPosition")
                                
                                // 如果位置不对（误差超过1秒），跳转到正确位置
                                if (kotlin.math.abs(currentPos - startPosition) > 1000) {
                                    android.util.Log.d("PlayerViewModel", "Position mismatch (diff: ${kotlin.math.abs(currentPos - startPosition)}ms), seeking to $startPosition")
                                    playerService.seekTo(startPosition)
                                    // 等待跳转完成
                                    delay(300)
                                } else {
                                    android.util.Log.d("PlayerViewModel", "Start position is correct")
                                }
                                
                                // 位置正确后，开始播放
                                playerService.play()
                                break
                            }
                            delay(50)
                            retries++
                        }
                        if (retries >= 100) {
                            android.util.Log.w("PlayerViewModel", "Player did not become ready in time")
                            // 再次检查播放器是否存在
                            val player = playerService.getPlayer()
                            if (player != null) {
                                // 即使超时，也尝试跳转并播放
                                if (forceStartFromBeginning) {
                                    playerService.seekTo(0L)
                                    forceStartFromBeginning = false
                                } else {
                                    playerService.seekTo(startPosition)
                                }
                                delay(200)
                                playerService.play()
                            } else {
                                android.util.Log.e("PlayerViewModel", "Player is null, cannot play")
                                _loadError.value = "播放器已释放，请重试"
                            }
                        }
                    } else {
                        // 如果没有指定起始位置，等待播放器准备好后自动播放
                        android.util.Log.d("PlayerViewModel", "No start position, waiting for player to be ready and auto-play")
                        var retries = 0
                        while (retries < 100) {
                            // 检查播放器是否被释放
                            val player = playerService.getPlayer()
                            if (player == null) {
                                android.util.Log.w("PlayerViewModel", "Player was released during preparation, reinitializing...")
                                // 重新初始化播放器并重新准备媒体
                                playerService.initializePlayer()
                                playerService.prepareMedia(
                                    url = playbackInfo.url,
                                    title = title,
                                    episodeTitle = episodeTitle,
                                    headers = playbackInfo.headers,
                                    subtitles = playbackInfo.subtitles,
                                    startPositionMs = startPosition
                                )
                                retries = 0 // 重置重试计数
                                delay(100) // 等待播放器初始化
                                continue
                            }
                            
                            val state = playerService.playerState.value
                            if (state.type == com.lomen.tv.domain.service.PlayerState.Type.READY) {
                                android.util.Log.d("PlayerViewModel", "Player ready, starting playback")
                                // 播放器已准备好，确保开始播放
                                if (!state.isPlaying) {
                                    playerService.play()
                                }
                                
                                // 播放器准备好后，保存观看历史（用于更新"最近播放"）
                                delay(1000) // 等待 duration 信息可用，并确保时间戳不同
                                saveWatchProgressImmediately()
                                
                                break
                            }
                            delay(50)
                            retries++
                        }
                        if (retries >= 100) {
                            android.util.Log.w("PlayerViewModel", "Player did not become ready in time, trying to play anyway")
                            // 再次检查播放器是否存在
                            val player = playerService.getPlayer()
                            if (player != null) {
                                playerService.play()
                            } else {
                                android.util.Log.e("PlayerViewModel", "Player is null, cannot play")
                                _loadError.value = "播放器已释放，请重试"
                            }
                        }
                    }
                    
                    _isLoadingMedia.value = false
                } else {
                    val resolveError = result.exceptionOrNull()
                    android.util.Log.e(
                        "PlayerCrashProbe",
                        "prepareMedia resolve failed: mediaId=$currentMediaId, episodeId=$currentEpisodeId, " +
                            "videoPath=${videoPath.take(300)}, error=${resolveError?.message}",
                        resolveError
                    )
                    _loadError.value = resolveError?.message ?: "加载失败"
                    _isLoadingMedia.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "PlayerCrashProbe",
                    "prepareMedia exception: mediaId=$currentMediaId, episodeId=$currentEpisodeId, " +
                        "startPosition=$startPosition, videoPath=${videoPath.take(300)}",
                    e
                )
                _loadError.value = e.message ?: "未知错误"
                _isLoadingMedia.value = false
            }
        }
    }

    fun play() {
        playerService.play()
    }

    fun pause() {
        playerService.pause()
    }

    fun togglePlayPause() {
        playerService.togglePlayPause()
    }

    fun seekTo(position: Long) {
        playerService.seekTo(position)
    }

    fun restartFromBeginning() {
        forceStartFromBeginning = true
        playerService.seekTo(0L)
        playerService.play()
    }

    fun seekForward(deltaMs: Long = 10000) {
        playerService.seekForward(deltaMs)
    }

    fun seekBackward(deltaMs: Long = 10000) {
        playerService.seekBackward(deltaMs)
    }

    private val _episodeNavigationMessage = MutableStateFlow<String?>(null)
    val episodeNavigationMessage: StateFlow<String?> = _episodeNavigationMessage.asStateFlow()
    
    private var onNavigateToEpisode: ((String, String?, String?, String?, String?, Long) -> Unit)? = null
    
    fun setEpisodeNavigationCallback(callback: (String, String?, String?, String?, String?, Long) -> Unit) {
        onNavigateToEpisode = callback
    }

    /**
     * 跳转到下一集
     */
    fun seekToNext() {
        val mediaId = currentMediaId ?: return
        val currentEpisodeId = currentEpisodeId
        
        android.util.Log.d("PlayerViewModel", "seekToNext: currentMediaId=$mediaId, currentEpisodeId=$currentEpisodeId")
        
        viewModelScope.launch {
            try {
                val nextEpisode = getNextEpisode(mediaId, currentEpisodeId)
                android.util.Log.d("PlayerViewModel", "seekToNext: nextEpisode=$nextEpisode")
                if (nextEpisode != null) {
                    // 有下一集，跳转
                    val playbackInfo = watchHistoryService.getPlaybackInfo(
                        nextEpisode.mediaId,
                        nextEpisode.episodeId
                    )
                    android.util.Log.d("PlayerViewModel", "seekToNext: playbackInfo=$playbackInfo")
                    if (playbackInfo != null && playbackInfo.videoPath != null) {
                        android.util.Log.d("PlayerViewModel", "seekToNext: 导航到下一集 videoPath=${playbackInfo.videoPath}")
                        onNavigateToEpisode?.invoke(
                            playbackInfo.videoPath,
                            playbackInfo.title,
                            playbackInfo.episodeTitle,
                            playbackInfo.mediaId,
                            playbackInfo.episodeId,
                            playbackInfo.startPosition
                        )
                    } else {
                        _episodeNavigationMessage.value = "无法加载下一集"
                        delay(3000)
                        _episodeNavigationMessage.value = null
                    }
                } else {
                    // 没有下一集，显示提示
                    _episodeNavigationMessage.value = "已是最后一集"
                    // 3秒后清除提示
                    delay(3000)
                    _episodeNavigationMessage.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error navigating to next episode", e)
                _episodeNavigationMessage.value = "跳转失败"
                delay(3000)
                _episodeNavigationMessage.value = null
            }
        }
    }

    /**
     * 跳转到上一集
     */
    fun seekToPrevious() {
        val mediaId = currentMediaId ?: return
        val currentEpisodeId = currentEpisodeId
        
        viewModelScope.launch {
            try {
                val previousEpisode = getPreviousEpisode(mediaId, currentEpisodeId)
                if (previousEpisode != null) {
                    // 有上一集，跳转
                    val playbackInfo = watchHistoryService.getPlaybackInfo(
                        previousEpisode.mediaId,
                        previousEpisode.episodeId
                    )
                    if (playbackInfo != null && playbackInfo.videoPath != null) {
                        onNavigateToEpisode?.invoke(
                            playbackInfo.videoPath,
                            playbackInfo.title,
                            playbackInfo.episodeTitle,
                            playbackInfo.mediaId,
                            playbackInfo.episodeId,
                            playbackInfo.startPosition
                        )
                    } else {
                        _episodeNavigationMessage.value = "无法加载上一集"
                        delay(3000)
                        _episodeNavigationMessage.value = null
                    }
                } else {
                    // 没有上一集，显示提示
                    _episodeNavigationMessage.value = "已是第一集"
                    // 3秒后清除提示
                    delay(3000)
                    _episodeNavigationMessage.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error navigating to previous episode", e)
                _episodeNavigationMessage.value = "跳转失败"
                delay(3000)
                _episodeNavigationMessage.value = null
            }
        }
    }
    
    /**
     * 解析当前播放上下文的 WebDAV 行：优先 [episodeId]（正在播放文件），否则 [mediaId]；
     * 优先取已标为分集类型的行，避免仅按 mediaId 落到错误类型导致选集为空。
     */
    private suspend fun loadWebDavForEpisodeContext(mediaId: String, episodeId: String?): WebDavMediaEntity? {
        val byMedia = webDavMediaDao.getById(mediaId)
        val byEpisode = episodeId?.takeIf { it.isNotBlank() }?.let { webDavMediaDao.getById(it) }
        val ordered = listOfNotNull(byEpisode, byMedia).distinctBy { it.id }
        return ordered.firstOrNull { it.type.isWebDavEpisodic() } ?: ordered.firstOrNull()
    }

    /** 文件名解析出的集号优先于库内（综艺等常被误标为 1） */
    private fun resolveWebDavEpisodeAndSeason(media: WebDavMediaEntity): Pair<Int?, Int> {
        val parsed = media.fileName?.let { FileNameParser.parse(it) }
        val ep = parsed?.episode ?: media.episodeNumber
        val season = parsed?.season ?: media.seasonNumber ?: 1
        return Pair(ep, season)
    }

    /** 同剧同季同集仅保留一条（最高清晰度），避免选集/上下集重复 */
    private fun dedupeWebDavEpisodesForSeries(
        anchor: WebDavMediaEntity,
        libraryMedia: List<WebDavMediaEntity>
    ): List<WebDavMediaEntity> {
        val grouped = libraryMedia
            .map { media ->
                val (episodeNum, seasonNum) = resolveWebDavEpisodeAndSeason(media)
                Triple(media, episodeNum, seasonNum)
            }
            .filter { (media, episodeNum, _) ->
                episodeNum != null &&
                    media.type.isWebDavEpisodic() &&
                    isSameWebDavSeries(anchor, media)
            }
            .groupBy { (_, ep, season) -> season to ep!! }
        return grouped.values
            .map { triples ->
                triples.maxByOrNull { (media, _, _) ->
                    VideoQualityDetector.rank(media.filePath, media.fileName)
                }!!.first
            }
            .sortedWith(
                compareBy<WebDavMediaEntity>(
                    { resolveWebDavEpisodeAndSeason(it).second },
                    { resolveWebDavEpisodeAndSeason(it).first ?: 0 }
                )
            )
    }
    
    /**
     * 获取下一集信息
     */
    private suspend fun getNextEpisode(mediaId: String, currentEpisodeId: String?): EpisodeInfo? {
        // 首先检查是否是电视剧
        val movie = movieDao.getMovieById(mediaId)
        val webDavMedia = if (movie == null) {
            loadWebDavForEpisodeContext(mediaId, currentEpisodeId)
        } else null
        
        // 非剧集类媒体无法按集跳转（本地库综艺/动漫/纪录片与电视剧相同结构）
        if ((movie == null || !movie.type.isLocalEpisodicSeries()) &&
            (webDavMedia == null || !webDavMedia.type.isWebDavEpisodic())
        ) {
            android.util.Log.d("PlayerViewModel", "Not episodic media, cannot navigate")
            return null
        }
        
        // 如果是 MovieEntity 类型的电视剧，从 EpisodeDao 获取剧集
        if (movie != null) {
            val episodes = episodeDao.getEpisodesByMovieId(mediaId).first()
            android.util.Log.d("PlayerViewModel", "Found ${episodes.size} episodes for movieId=$mediaId")
            
            if (episodes.isEmpty()) {
                android.util.Log.d("PlayerViewModel", "No episodes found")
                return null
            }
            
            // 排序所有剧集
            val sortedEpisodes = episodes.sortedWith(compareBy(
                { it.seasonNumber },
                { it.episodeNumber }
            ))
            
            android.util.Log.d("PlayerViewModel", "Sorted episodes: ${sortedEpisodes.map { "S${it.seasonNumber}E${it.episodeNumber}" }}")
            
            // 如果当前没有剧集ID，返回第一集
            if (currentEpisodeId == null) {
                val firstEpisode = sortedEpisodes.firstOrNull()
                android.util.Log.d("PlayerViewModel", "No current episode ID, returning first episode: ${firstEpisode?.id}")
                return firstEpisode?.let {
                    EpisodeInfo(
                        mediaId = mediaId,
                        episodeId = it.id,
                        seasonNumber = it.seasonNumber,
                        episodeNumber = it.episodeNumber
                    )
                }
            }
            
            // 找到当前剧集
            val currentEpisode = sortedEpisodes.find { it.id == currentEpisodeId }
            if (currentEpisode == null) {
                android.util.Log.w("PlayerViewModel", "Current episode not found: currentEpisodeId=$currentEpisodeId")
                android.util.Log.d("PlayerViewModel", "Available episode IDs: ${sortedEpisodes.map { it.id }}")
                return null
            }
            
            android.util.Log.d("PlayerViewModel", "Current episode: S${currentEpisode.seasonNumber}E${currentEpisode.episodeNumber}, index=${sortedEpisodes.indexOf(currentEpisode)}")
            
            // 找到下一集（同一季的下一集，或下一季的第一集）
            val currentIndex = sortedEpisodes.indexOf(currentEpisode)
            if (currentIndex < 0) {
                android.util.Log.w("PlayerViewModel", "Current episode index is negative")
                return null
            }
            
            if (currentIndex >= sortedEpisodes.size - 1) {
                android.util.Log.d("PlayerViewModel", "Already at last episode (index=$currentIndex, total=${sortedEpisodes.size})")
                return null // 已经是最后一集
            }
            
            val nextEpisode = sortedEpisodes[currentIndex + 1]
            android.util.Log.d("PlayerViewModel", "Next episode: S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber}")
            return EpisodeInfo(
                mediaId = mediaId,
                episodeId = nextEpisode.id,
                seasonNumber = nextEpisode.seasonNumber,
                episodeNumber = nextEpisode.episodeNumber
            )
        } else if (webDavMedia != null) {
            // WebDAV 媒体：获取同一电视剧的所有剧集
            val (currentEpisodeNumber, currentSeasonNumber) = resolveWebDavEpisodeAndSeason(webDavMedia)
            
            if (currentEpisodeNumber == null) {
                android.util.Log.d("PlayerViewModel", "WebDAV media has no episode number (mediaId=$mediaId, fileName=${webDavMedia.fileName})")
                return null // 不是剧集，无法导航
            }
            
            val allMedia = webDavMediaDao.getByLibraryId(webDavMedia.libraryId).first()
            val allEpisodes = dedupeWebDavEpisodesForSeries(webDavMedia, allMedia)

            android.util.Log.d("PlayerViewModel", "Found ${allEpisodes.size} WebDAV episodes for libraryId=${webDavMedia.libraryId}, tmdbId=${webDavMedia.tmdbId}")

            allEpisodes.forEachIndexed { index, media ->
                val (epNum, seasonNum) = resolveWebDavEpisodeAndSeason(media)
                android.util.Log.d("PlayerViewModel", "Episode[$index]: id=${media.id}, S${seasonNum}E${epNum}, fileName=${media.fileName}")
            }

            if (allEpisodes.isEmpty()) {
                android.util.Log.d("PlayerViewModel", "No WebDAV episodes found")
                return null
            }

            val targetId = currentEpisodeId ?: mediaId
            var currentIndex = allEpisodes.indexOfFirst { it.id == targetId }

            if (currentIndex < 0) {
                android.util.Log.d("PlayerViewModel", "Current targetId=$targetId not found by ID, trying to match by episode number")
                currentIndex = allEpisodes.indexOfFirst { media ->
                    val (otherEpisodeNum, otherSeasonNum) = resolveWebDavEpisodeAndSeason(media)
                    val matches = otherEpisodeNum == currentEpisodeNumber &&
                        otherSeasonNum == currentSeasonNumber
                    if (matches) {
                        android.util.Log.d("PlayerViewModel", "Found match: media.id=${media.id}, S${otherSeasonNum}E${otherEpisodeNum}")
                    }
                    matches
                }
            } else {
                android.util.Log.d("PlayerViewModel", "Found current episode by ID at index=$currentIndex (targetId=$targetId)")
            }

            android.util.Log.d("PlayerViewModel", "Current WebDAV episode: S${currentSeasonNumber}E${currentEpisodeNumber}, mediaId=$mediaId, episodeId=$currentEpisodeId, targetId=$targetId, index=$currentIndex, total=${allEpisodes.size}")

            if (currentIndex < 0) {
                android.util.Log.w("PlayerViewModel", "Current WebDAV episode not found")
                return null
            }

            if (currentIndex >= allEpisodes.size - 1) {
                android.util.Log.d("PlayerViewModel", "Already at last WebDAV episode (index=$currentIndex, total=${allEpisodes.size})")
                return null // 已经是最后一集
            }

            val nextEpisode = allEpisodes[currentIndex + 1]
            val (nextEpisodeNum, nextSeasonNum) = resolveWebDavEpisodeAndSeason(nextEpisode)
            
            android.util.Log.d("PlayerViewModel", "Next WebDAV episode: S${nextSeasonNum}E${nextEpisodeNum}")
            return EpisodeInfo(
                mediaId = nextEpisode.id,
                episodeId = nextEpisode.id, // WebDAV 媒体的 episodeId 就是 mediaId
                seasonNumber = nextSeasonNum,
                episodeNumber = nextEpisodeNum ?: 0
            )
        }
        
        return null
    }
    
    /**
     * 获取上一集信息
     */
    private suspend fun getPreviousEpisode(mediaId: String, currentEpisodeId: String?): EpisodeInfo? {
        // 首先检查是否是电视剧
        val movie = movieDao.getMovieById(mediaId)
        val webDavMedia = if (movie == null) {
            loadWebDavForEpisodeContext(mediaId, currentEpisodeId)
        } else null
        
        // 非剧集类媒体无法按集跳转（本地库综艺/动漫/纪录片与电视剧相同结构）
        if ((movie == null || !movie.type.isLocalEpisodicSeries()) &&
            (webDavMedia == null || !webDavMedia.type.isWebDavEpisodic())
        ) {
            android.util.Log.d("PlayerViewModel", "Not episodic media, cannot navigate")
            return null
        }
        
        // 如果是 MovieEntity 类型的电视剧，从 EpisodeDao 获取剧集
        if (movie != null) {
            val episodes = episodeDao.getEpisodesByMovieId(mediaId).first()
            android.util.Log.d("PlayerViewModel", "Found ${episodes.size} episodes for movieId=$mediaId")
            
            if (episodes.isEmpty()) {
                android.util.Log.d("PlayerViewModel", "No episodes found")
                return null
            }
            
            // 如果当前没有剧集ID，返回 null（已经是第一集）
            if (currentEpisodeId == null) {
                android.util.Log.d("PlayerViewModel", "No current episode ID, cannot go to previous")
                return null
            }
            
            // 排序所有剧集
            val sortedEpisodes = episodes.sortedWith(compareBy(
                { it.seasonNumber },
                { it.episodeNumber }
            ))
            
            android.util.Log.d("PlayerViewModel", "Sorted episodes: ${sortedEpisodes.map { "S${it.seasonNumber}E${it.episodeNumber}" }}")
            
            // 找到当前剧集
            val currentEpisode = sortedEpisodes.find { it.id == currentEpisodeId }
            if (currentEpisode == null) {
                android.util.Log.w("PlayerViewModel", "Current episode not found: currentEpisodeId=$currentEpisodeId")
                android.util.Log.d("PlayerViewModel", "Available episode IDs: ${sortedEpisodes.map { it.id }}")
                return null
            }
            
            android.util.Log.d("PlayerViewModel", "Current episode: S${currentEpisode.seasonNumber}E${currentEpisode.episodeNumber}, index=${sortedEpisodes.indexOf(currentEpisode)}")
            
            // 找到上一集（同一季的上一集，或上一季的最后一集）
            val currentIndex = sortedEpisodes.indexOf(currentEpisode)
            if (currentIndex <= 0) {
                android.util.Log.d("PlayerViewModel", "Already at first episode (index=$currentIndex)")
                return null // 已经是第一集
            }
            
            val previousEpisode = sortedEpisodes[currentIndex - 1]
            android.util.Log.d("PlayerViewModel", "Previous episode: S${previousEpisode.seasonNumber}E${previousEpisode.episodeNumber}")
            return EpisodeInfo(
                mediaId = mediaId,
                episodeId = previousEpisode.id,
                seasonNumber = previousEpisode.seasonNumber,
                episodeNumber = previousEpisode.episodeNumber
            )
        } else if (webDavMedia != null) {
            val (currentEpisodeNumber, currentSeasonNumber) = resolveWebDavEpisodeAndSeason(webDavMedia)
            
            if (currentEpisodeNumber == null) {
                android.util.Log.d("PlayerViewModel", "WebDAV media has no episode number (mediaId=$mediaId, fileName=${webDavMedia.fileName})")
                return null
            }
            
            val allMedia = webDavMediaDao.getByLibraryId(webDavMedia.libraryId).first()
            val allEpisodes = dedupeWebDavEpisodesForSeries(webDavMedia, allMedia)

            android.util.Log.d("PlayerViewModel", "Found ${allEpisodes.size} WebDAV episodes for libraryId=${webDavMedia.libraryId}, tmdbId=${webDavMedia.tmdbId}")

            allEpisodes.forEachIndexed { index, media ->
                val (epNum, seasonNum) = resolveWebDavEpisodeAndSeason(media)
                android.util.Log.d("PlayerViewModel", "Episode[$index]: id=${media.id}, S${seasonNum}E${epNum}, fileName=${media.fileName}")
            }

            if (allEpisodes.isEmpty()) {
                android.util.Log.d("PlayerViewModel", "No WebDAV episodes found")
                return null
            }

            val targetId = currentEpisodeId ?: mediaId
            var currentIndex = allEpisodes.indexOfFirst { it.id == targetId }

            if (currentIndex < 0) {
                android.util.Log.d("PlayerViewModel", "Current targetId=$targetId not found by ID, trying to match by episode number")
                currentIndex = allEpisodes.indexOfFirst { media ->
                    val (otherEpisodeNum, otherSeasonNum) = resolveWebDavEpisodeAndSeason(media)
                    val matches = otherEpisodeNum == currentEpisodeNumber &&
                        otherSeasonNum == currentSeasonNumber
                    if (matches) {
                        android.util.Log.d("PlayerViewModel", "Found match: media.id=${media.id}, S${otherSeasonNum}E${otherEpisodeNum}")
                    }
                    matches
                }
            } else {
                android.util.Log.d("PlayerViewModel", "Found current episode by ID at index=$currentIndex (targetId=$targetId)")
            }

            android.util.Log.d("PlayerViewModel", "Current WebDAV episode: S${currentSeasonNumber}E${currentEpisodeNumber}, mediaId=$mediaId, episodeId=$currentEpisodeId, targetId=$targetId, index=$currentIndex, total=${allEpisodes.size}")

            if (currentIndex < 0) {
                android.util.Log.w("PlayerViewModel", "Current WebDAV episode not found")
                return null
            }

            if (currentIndex <= 0) {
                android.util.Log.d("PlayerViewModel", "Already at first WebDAV episode (index=$currentIndex)")
                return null
            }

            val previousEpisode = allEpisodes[currentIndex - 1]
            val (prevEpisodeNum, prevSeasonNum) = resolveWebDavEpisodeAndSeason(previousEpisode)

            android.util.Log.d("PlayerViewModel", "Previous WebDAV episode: S${prevSeasonNum}E${prevEpisodeNum}")
            return EpisodeInfo(
                mediaId = previousEpisode.id,
                episodeId = previousEpisode.id,
                seasonNumber = prevSeasonNum,
                episodeNumber = prevEpisodeNum ?: 0
            )
        }
        
        return null
    }
    
    private data class EpisodeInfo(
        val mediaId: String,
        val episodeId: String?,  // 可空，WebDAV 媒体没有单独的 episodeId
        val seasonNumber: Int,
        val episodeNumber: Int
    )

    fun setPlaybackSpeed(speed: Float) {
        playerService.setPlaybackSpeed(speed)
    }

    fun getAvailableSpeeds(): List<Float> = playerService.getAvailableSpeeds()

    fun setVolume(volume: Float) {
        playerService.setVolume(volume)
    }

    fun toggleMute() {
        playerService.toggleMute()
    }

    fun isMuted(): Boolean = playerService.isMuted()
    
    fun selectSubtitle(index: Int) {
        playerService.selectSubtitle(index)
    }
    
    fun selectAudioTrack(index: Int) {
        playerService.selectAudioTrack(index)
    }
    
    /**
     * 清除播放器错误状态
     */
    fun clearError() {
        playerService.clearError()
    }
    
    /**
     * 解析播放 URL
     */
    suspend fun resolvePlaybackUrl(filePath: String): String {
        val result = mediaUrlResolver.resolvePlaybackUrl(filePath)
        return if (result.isSuccess) {
            result.getOrNull()?.url ?: filePath
        } else {
            filePath
        }
    }
    
    /**
     * 获取当前剧集的所有剧集列表
     */
    suspend fun getEpisodeList(): List<EpisodeListItem> {
        val mediaId = currentMediaId ?: return emptyList()
        
        return try {
            val movie = movieDao.getMovieById(mediaId)
            val webDavMedia = if (movie == null) {
                loadWebDavForEpisodeContext(mediaId, currentEpisodeId)
            } else null
            
            if (movie?.type?.isLocalEpisodicSeries() == true) {
                // MovieEntity：电视剧 / 综艺 / 动漫 / 纪录片剧集
                val episodes = episodeDao.getEpisodesByMovieId(mediaId).first()
                
                // 尝试从 TMDB 获取剧集信息（如果有 TMDB ID）
                val tmdbEpisodesMap = mutableMapOf<Pair<Int, Int>, com.lomen.tv.data.scraper.EpisodeInfo>()
                if (movie.tmdbId != null) {
                    try {
                        val tmdbScraper = com.lomen.tv.data.scraper.TmdbScraper.getInstance()
                        // 获取所有季的剧集信息
                        val seasons = episodes.map { it.seasonNumber }.distinct()
                        for (season in seasons) {
                            val tmdbEpisodes = tmdbScraper.getTvSeasonEpisodes(movie.tmdbId.toString(), season)
                            tmdbEpisodes?.forEach { ep ->
                                tmdbEpisodesMap[Pair(season, ep.episodeNumber)] = ep
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlayerViewModel", "Failed to get TMDB episode info", e)
                    }
                }
                
                episodes.map { episode ->
                    // 优先使用 TMDB 的副标题
                    val tmdbEpisode = tmdbEpisodesMap[Pair(episode.seasonNumber, episode.episodeNumber)]
                    val episodeTitle = tmdbEpisode?.name?.takeIf { it.isNotBlank() } 
                        ?: episode.title 
                        ?: ""
                    
                    EpisodeListItem(
                        id = episode.id,
                        episodeNumber = episode.episodeNumber,
                        seasonNumber = episode.seasonNumber,
                        title = episodeTitle,
                        stillUrl = episode.stillUrl,
                        path = episode.quarkPath
                    )
                }
            } else if (webDavMedia?.type?.isWebDavEpisodic() == true) {
                // WebDAV：电视剧 / 综艺 / 动漫等分集资源
                val allMedia = if (webDavMedia.libraryId != null) {
                    webDavMediaDao.getByLibraryId(webDavMedia.libraryId).first()
                } else {
                    emptyList()
                }

                val allEpisodes = dedupeWebDavEpisodesForSeries(webDavMedia, allMedia)

                // 尝试从 TMDB 获取剧集信息
                val tmdbEpisodesMap = mutableMapOf<Pair<Int, Int>, com.lomen.tv.data.scraper.EpisodeInfo>()
                if (webDavMedia.tmdbId != null) {
                    try {
                        val tmdbScraper = com.lomen.tv.data.scraper.TmdbScraper.getInstance()
                        val seasons = allEpisodes.map { resolveWebDavEpisodeAndSeason(it).second }.distinct()
                        for (season in seasons) {
                            val tmdbEpisodes = tmdbScraper.getTvSeasonEpisodes(webDavMedia.tmdbId, season)
                            tmdbEpisodes?.forEach { ep ->
                                tmdbEpisodesMap[Pair(season, ep.episodeNumber)] = ep
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlayerViewModel", "Failed to get TMDB episode info", e)
                    }
                }
                
                allEpisodes.map { media ->
                    val (episodeNum, seasonNum) = resolveWebDavEpisodeAndSeason(media)
                    val ep = episodeNum ?: 1
                    
                    val tmdbEpisode = tmdbEpisodesMap[Pair(seasonNum, ep)]
                    val episodeTitle = tmdbEpisode?.name?.takeIf { it.isNotBlank() }
                        ?: media.title
                        ?: ""
                    
                    EpisodeListItem(
                        id = media.id,
                        episodeNumber = ep,
                        seasonNumber = seasonNum,
                        title = episodeTitle,
                        stillUrl = media.posterUrl,
                        path = media.filePath
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error getting episode list", e)
            emptyList()
        }
    }
    
    /**
     * 获取当前集数的所有清晰度选项
     */
    suspend fun getQualityOptions(): List<QualityOption> {
        val mediaId = currentMediaId ?: return emptyList()
        val episodeId = currentEpisodeId
        
        return try {
            val movie = movieDao.getMovieById(mediaId)
            val webDavMedia = if (movie == null) {
                loadWebDavForEpisodeContext(mediaId, episodeId)
            } else null
            
            if (movie?.type?.isLocalEpisodicSeries() == true && episodeId != null) {
                // MovieEntity 分集类型，同一集可能有多个清晰度文件
                val episode = episodeDao.getEpisodeById(episodeId)
                if (episode != null) {
                    // 查找同名的其他剧集（不同清晰度）
                    val allEpisodes = episodeDao.getEpisodesByMovieId(mediaId).first()
                    val sameEpisodes = allEpisodes.filter { 
                        it.episodeNumber == episode.episodeNumber && 
                        it.seasonNumber == episode.seasonNumber &&
                        it.id != episodeId
                    }
                    
                    val options = mutableListOf<QualityOption>()
                    // 添加当前清晰度
                    options.add(QualityOption(
                        id = episode.id,
                        label = detectQualityLabel(episode.quarkPath ?: ""),
                        filePath = episode.quarkPath
                    ))
                    // 添加其他清晰度
                    sameEpisodes.forEach { ep ->
                        options.add(QualityOption(
                            id = ep.id,
                            label = detectQualityLabel(ep.quarkPath ?: ""),
                            filePath = ep.quarkPath
                        ))
                    }
                    options.sortedByDescending { q ->
                        VideoQualityDetector.rank(
                            q.filePath ?: "",
                            q.filePath?.substringAfterLast('/') ?: ""
                        )
                    }
                } else {
                    emptyList()
                }
            } else if (webDavMedia?.type?.isWebDavEpisodic() == true) {
                // WebDAV 分集资源：查找同一集的不同清晰度文件
                val currentEpisode = if (episodeId != null) {
                    webDavMediaDao.getById(episodeId)
                } else {
                    webDavMedia
                }
                
                if (currentEpisode != null) {
                    val (episodeNum, seasonNum) = resolveWebDavEpisodeAndSeason(currentEpisode)
                    if (episodeNum == null) {
                        emptyList()
                    } else {
                    
                    val allMedia = if (currentEpisode.libraryId != null) {
                        webDavMediaDao.getByLibraryId(currentEpisode.libraryId).first()
                    } else {
                        emptyList()
                    }
                    
                    val sameEpisodes = allMedia.filter { media ->
                        val (mediaEpisodeNum, mediaSeasonNum) = resolveWebDavEpisodeAndSeason(media)
                        media.type.isWebDavEpisodic() &&
                        mediaEpisodeNum == episodeNum &&
                        mediaSeasonNum == seasonNum &&
                        isSameWebDavSeries(currentEpisode, media)
                    }

                    sameEpisodes
                        .sortedByDescending { VideoQualityDetector.rank(it.filePath, it.fileName) }
                        .map { media ->
                            QualityOption(
                                id = media.id,
                                label = detectQualityLabel(media.filePath),
                                filePath = media.filePath
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error getting quality options", e)
            emptyList()
        }
    }
    
    /** 从完整路径检测清晰度（含目录名中的 4K / 1080 等） */
    private fun detectQualityLabel(fullPath: String): String {
        val fileName = fullPath.substringAfterLast('/', fullPath)
        val dir = if (fullPath.contains('/')) fullPath.substringBeforeLast('/') else ""
        return VideoQualityDetector.label(dir, fileName)
    }
    
    /**
     * 切换到指定清晰度
     */
    suspend fun switchQuality(qualityId: String): Boolean {
        val mediaId = currentMediaId ?: return false

        return try {
            // 同集换清晰度：从当前时间点续播（新版文件时长略短时由播放器自身钳制）
            val resumeMs = playerService.getCurrentPosition().coerceAtLeast(0L)

            val movie = movieDao.getMovieById(mediaId)
            val webDavMedia = if (movie == null) {
                loadWebDavForEpisodeContext(mediaId, currentEpisodeId)
            } else null

            if (movie?.type?.isLocalEpisodicSeries() == true) {
                // MovieEntity 分集类型
                val episode = episodeDao.getEpisodeById(qualityId)
                if (episode != null && episode.quarkPath != null) {
                    val videoPath = episode.quarkPath
                    prepareMedia(
                        videoPath = videoPath,
                        title = movie.title,
                        episodeTitle = episode.title,
                        startPosition = resumeMs
                    )
                    currentEpisodeId = qualityId
                    true
                } else {
                    false
                }
            } else if (webDavMedia?.type?.isWebDavEpisodic() == true) {
                // WebDAV 分集类型
                val media = webDavMediaDao.getById(qualityId)
                if (media != null && media.filePath != null) {
                    val videoPath = media.filePath
                    prepareMedia(
                        videoPath = videoPath,
                        title = webDavMedia.title,
                        episodeTitle = media.title,
                        startPosition = resumeMs
                    )
                    currentEpisodeId = qualityId
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error switching quality", e)
            false
        }
    }
    
    data class EpisodeListItem(
        val id: String,
        val episodeNumber: Int,
        val seasonNumber: Int,
        val title: String,
        val stillUrl: String?,
        val path: String?
    )
    
    data class QualityOption(
        val id: String,
        val label: String,
        val filePath: String?
    )

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                playerService.updatePosition()
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun startProgressSaving() {
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch {
            while (isActive) {
                delay(30000) // 每30秒保存一次进度
                saveWatchProgress()
            }
        }
    }

    private fun stopProgressSaving() {
        saveProgressJob?.cancel()
        saveProgressJob = null
    }

    private fun startPlaybackStatsTracking() {
        playbackStatsJob?.cancel()
        playbackStatsJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val state = playerService.playerState.value
                if (state.isPlaying && state.type == PlayerState.Type.READY) {
                    pendingPlaybackMs += 1000L
                    if (pendingPlaybackMs >= 5_000L) {
                        val delta = pendingPlaybackMs
                        pendingPlaybackMs = 0L
                        runCatching {
                            playbackStatsService.addPlaybackTimeMs(delta)
                        }.onFailure {
                            android.util.Log.e("PlayerViewModel", "Error flushing playback stats", it)
                            pendingPlaybackMs += delta
                        }
                    }
                } else if (pendingPlaybackMs > 0L) {
                    // 暂停/缓冲/停止时尽快落库，避免退出后看起来还是0分钟
                    val delta = pendingPlaybackMs
                    pendingPlaybackMs = 0L
                    runCatching {
                        playbackStatsService.addPlaybackTimeMs(delta)
                    }.onFailure {
                        android.util.Log.e("PlayerViewModel", "Error flushing playback stats on pause", it)
                        pendingPlaybackMs += delta
                    }
                }
            }
        }
    }

    private fun stopPlaybackStatsTracking() {
        playbackStatsJob?.cancel()
        playbackStatsJob = null
    }

    private fun saveWatchProgress() {
        val mediaId = currentMediaId
        if (mediaId == null) {
            android.util.Log.w("PlayerViewModel", "Cannot save watch progress: mediaId is null")
            return
        }
        
        // 检查记忆续播开关是否打开
        val rememberEnabled = kotlinx.coroutines.runBlocking {
            playerSettingsPreferences.rememberPlaybackPosition.first()
        }
        if (!rememberEnabled) {
            android.util.Log.d("PlayerViewModel", "Remember playback position is disabled, skipping save")
            return
        }
        
        val position = playerService.getCurrentPosition()
        val duration = playerService.getDuration()

        android.util.Log.d("PlayerViewModel", "Saving progress: mediaId=$mediaId, episodeId=$currentEpisodeId, position=$position, duration=$duration")

        if (duration > 0) {
            viewModelScope.launch {
                try {
                    watchHistoryService.saveWatchProgress(
                        mediaId = mediaId,
                        episodeId = currentEpisodeId,
                        progress = position,
                        duration = duration
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error saving watch progress", e)
                }
            }
        } else {
            android.util.Log.w("PlayerViewModel", "Cannot save watch progress: duration is 0")
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    // ==================== 跳过片头片尾功能 ====================
    
    private val _skipConfig = MutableStateFlow<SkipConfigEntity?>(null)
    val skipConfig: StateFlow<SkipConfigEntity?> = _skipConfig.asStateFlow()
    
    private var currentSeasonNumber: Int = 0
    private var hasSkippedIntro = false
    private var hasSkippedOutro = false
    private var currentSeriesMediaId: String? = null  // 系列级别的mediaId（用于电视剧）
    
    /**
     * 加载跳过配置
     */
    fun loadSkipConfig(mediaId: String, seasonNumber: Int = 0) {
        currentSeasonNumber = seasonNumber
        // 重置跳过状态（切换剧集时）
        resetSkipStatus()
        viewModelScope.launch {
            skipConfigRepository.getConfigFlow(mediaId, seasonNumber).collect { config ->
                _skipConfig.value = config
            }
        }
    }
    
    /**
     * 加载电视剧级别的跳过配置（对所有集数生效）
     */
    fun loadSkipConfigForSeries(mediaId: String, seasonNumber: Int = 0) {
        viewModelScope.launch {
            // 尝试获取媒体信息以确定系列ID
            val movie = movieDao.getMovieById(mediaId)
            val webDavMedia = if (movie == null) {
                loadWebDavForEpisodeContext(mediaId, currentEpisodeId)
            } else null
            
            // 确定系列ID：对于电视剧，使用tmdbId或基础标题
            val seriesMediaId = when {
                movie != null && movie.type.isLocalEpisodicSeries() -> {
                    // MovieEntity 多集系列，使用 mediaId
                    movie.id
                }
                webDavMedia != null && webDavMedia.type.isWebDavEpisodic() -> {
                    // WebDAV 分集系列：使用 tmdbId 或与详情页一致的剧名键
                    if (!webDavMedia.tmdbId.isNullOrBlank()) {
                        "tmdb_${webDavMedia.tmdbId}"
                    } else {
                        val baseTitle = webDavSeriesTitleKey(webDavMedia)
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        "series_${webDavMedia.libraryId}_${baseTitle}"
                    }
                }
                else -> {
                    // 电影或其他类型，使用原mediaId
                    mediaId
                }
            }
            
            android.util.Log.d("PlayerViewModel", "loadSkipConfigForSeries: original=$mediaId, series=$seriesMediaId")
            
            // 保存系列ID用于后续保存配置
            currentSeriesMediaId = seriesMediaId
            
            // 加载系列级别的配置
            loadSkipConfig(seriesMediaId, seasonNumber)
        }
    }
    
    /**
     * 获取当前跳过配置（如果没有则返回默认配置）
     */
    fun getCurrentSkipConfig(): SkipConfigEntity {
        return _skipConfig.value ?: SkipConfigEntity(
            mediaId = currentMediaId ?: "",
            seasonNumber = currentSeasonNumber
        )
    }
    
    /**
     * 保存跳过配置（使用系列级别的mediaId）
     */
    fun saveSkipConfig(config: SkipConfigEntity) {
        viewModelScope.launch {
            // 使用系列级别的mediaId（如果存在）
            val mediaId = currentSeriesMediaId ?: config.mediaId
            val configToSave = config.copy(
                mediaId = mediaId,
                seasonNumber = currentSeasonNumber
            )
            android.util.Log.d("PlayerViewModel", "saveSkipConfig: mediaId=$mediaId, introDuration=${config.introDuration}, outroDuration=${config.outroDuration}")
            skipConfigRepository.saveConfig(configToSave)
        }
    }
    
    /**
     * 更新片头时长
     */
    fun updateIntroDuration(duration: Long) {
        val mediaId = currentSeriesMediaId ?: currentMediaId ?: return
        viewModelScope.launch {
            skipConfigRepository.updateIntroDuration(mediaId, currentSeasonNumber, duration)
        }
    }
    
    /**
     * 更新片尾时长
     */
    fun updateOutroDuration(duration: Long) {
        val mediaId = currentSeriesMediaId ?: currentMediaId ?: return
        viewModelScope.launch {
            skipConfigRepository.updateOutroDuration(mediaId, currentSeasonNumber, duration)
        }
    }
    
    /**
     * 切换片头跳过开关
     */
    fun toggleSkipIntro() {
        val mediaId = currentSeriesMediaId ?: currentMediaId ?: return
        val currentConfig = _skipConfig.value
        val newEnabled = !(currentConfig?.skipIntroEnabled ?: true)
        viewModelScope.launch {
            skipConfigRepository.updateSkipIntroEnabled(mediaId, currentSeasonNumber, newEnabled)
        }
    }
    
    /**
     * 切换片尾跳过开关
     */
    fun toggleSkipOutro() {
        val mediaId = currentSeriesMediaId ?: currentMediaId ?: return
        val currentConfig = _skipConfig.value
        val newEnabled = !(currentConfig?.skipOutroEnabled ?: true)
        viewModelScope.launch {
            skipConfigRepository.updateSkipOutroEnabled(mediaId, currentSeasonNumber, newEnabled)
        }
    }
    
    /**
     * 重置为默认值
     */
    fun resetSkipConfigToDefault() {
        val mediaId = currentSeriesMediaId ?: currentMediaId ?: return
        viewModelScope.launch {
            skipConfigRepository.resetToDefault(mediaId, currentSeasonNumber)
        }
    }
    
    /**
     * 检查并自动跳过片头
     * 在播放开始时调用
     * @return 是否执行了跳过操作
     */
    fun checkAndSkipIntro(): Boolean {
        val config = _skipConfig.value
        android.util.Log.d("PlayerViewModel", "checkAndSkipIntro: config=$config, hasSkippedIntro=$hasSkippedIntro")
        if (config?.skipIntroEnabled == true && !hasSkippedIntro) {
            val introEnd = config.introDuration
            val currentPosition = playerService.getCurrentPosition()
            android.util.Log.d("PlayerViewModel", "checkAndSkipIntro: introEnd=$introEnd, currentPosition=$currentPosition")
            if (currentPosition < introEnd && introEnd > 0) {
                playerService.seekTo(introEnd)
                hasSkippedIntro = true
                android.util.Log.d("PlayerViewModel", "自动跳过片头到: $introEnd ms")
                return true
            }
        }
        return false
    }
    
    /**
     * 检查并自动跳过片尾
     * 在播放进度更新时调用
     */
    fun checkAndSkipOutro(): Boolean {
        val config = _skipConfig.value
        val duration = playerService.getDuration()
        
        if (config?.skipOutroEnabled == true && duration > 0 && !hasSkippedOutro) {
            val outroStart = duration - config.outroDuration
            val currentPosition = playerService.getCurrentPosition()
            
            // 当播放到片尾前5秒时，自动跳过到下一集
            if (currentPosition >= outroStart - 5000) {
                hasSkippedOutro = true
                android.util.Log.d("PlayerViewModel", "自动跳过片尾，准备播放下一集")
                seekToNext()
                return true
            }
        }
        return false
    }
    
    /**
     * 手动跳过片头
     */
    fun skipIntro() {
        val config = getCurrentSkipConfig()
        val introEnd = config.introDuration
        playerService.seekTo(introEnd)
        hasSkippedIntro = true
        android.util.Log.d("PlayerViewModel", "手动跳过片头到: $introEnd ms")
    }
    
    /**
     * 手动跳过片尾
     */
    fun skipOutro() {
        seekToNext()
        hasSkippedOutro = true
        android.util.Log.d("PlayerViewModel", "手动跳过片尾，播放下一集")
    }
    
    /**
     * 将当前时间设为片头结束时间
     */
    fun setCurrentPositionAsIntroEnd() {
        val position = playerService.getCurrentPosition()
        updateIntroDuration(position)
    }
    
    /**
     * 将当前时间设为片尾开始时间
     */
    fun setCurrentPositionAsOutroStart() {
        val duration = playerService.getDuration()
        val position = playerService.getCurrentPosition()
        if (duration > 0 && position > 0) {
            val outroDuration = duration - position
            updateOutroDuration(outroDuration)
        }
    }
    
    /**
     * 重置跳过状态（切换剧集时调用）
     */
    fun resetSkipStatus() {
        hasSkippedIntro = false
        hasSkippedOutro = false
    }

}

/** 与详情页一致：WebDAV 上按多集展示的媒体类型（含纪录片剧集） */
private fun MediaType.isWebDavEpisodic(): Boolean =
    this == MediaType.TV_SHOW ||
        this == MediaType.VARIETY ||
        this == MediaType.ANIME ||
        this == MediaType.DOCUMENTARY

private fun webDavSeriesTitleKey(media: WebDavMediaEntity): String =
    if (media.type.isWebDavEpisodic()) {
        FolderSeriesNameParser.stripAllSeasonMarkers(media.title.trim()).ifBlank { media.title.trim() }
    } else {
        media.title.replace(Regex("第\\d+集.*"), "").trim()
    }

private fun isSameWebDavSeries(anchor: WebDavMediaEntity, candidate: WebDavMediaEntity): Boolean {
    if (!anchor.type.isWebDavEpisodic() || !candidate.type.isWebDavEpisodic()) return false
    if (anchor.type != candidate.type) return false
    val keyA = webDavSeriesTitleKey(anchor)
    val keyB = webDavSeriesTitleKey(candidate)
    if (!anchor.tmdbId.isNullOrBlank()) {
        if (!candidate.tmdbId.isNullOrBlank()) {
            return anchor.tmdbId == candidate.tmdbId
        }
        // 锚点有 TMDB，部分分集尚未刮削到 tmdbId：用语义剧名对齐
        return keyA == keyB
    }
    if (!candidate.tmdbId.isNullOrBlank()) {
        return keyA == keyB
    }
    return keyA == keyB
}

