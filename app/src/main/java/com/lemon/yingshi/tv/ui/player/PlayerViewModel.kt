package com.lemon.yingshi.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.data.repository.MacCmsPlayerHelper
import com.lemon.yingshi.tv.data.repository.SkipConfigRepository
import com.lemon.yingshi.tv.domain.service.MediaUrlResolver
import com.lemon.yingshi.tv.domain.service.PlaybackStatsService
import com.lemon.yingshi.tv.domain.service.PlayerService
import com.lemon.yingshi.tv.domain.service.PlayerState
import com.lemon.yingshi.tv.domain.service.SubtitleInfo
import com.lemon.yingshi.tv.domain.service.TrackInfo
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import com.lemon.yingshi.tv.domain.service.WatchHistoryCoverStore
import com.lemon.yingshi.tv.utils.WatchHistoryCoverCapture
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerService: PlayerService,
    private val watchHistoryService: WatchHistoryService,
    private val playbackStatsService: PlaybackStatsService,
    private val mediaUrlResolver: MediaUrlResolver,
    private val macCmsPlayerHelper: MacCmsPlayerHelper,
    private val skipConfigRepository: SkipConfigRepository,
    private val coverStore: WatchHistoryCoverStore,
    private val playerSettingsPreferences: com.lemon.yingshi.tv.data.preferences.PlayerSettingsPreferences
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
    private var currentTitle: String? = null
    private var currentEpisodeTitle: String? = null
    private var currentVideoUrl: String? = null
    @Volatile private var forceStartFromBeginning: Boolean = false
    @Volatile private var playerReleased = false
    private var playerViewRef: WeakReference<PlayerView>? = null
    private var pendingExitCover: android.graphics.Bitmap? = null

    fun setPlayerViewForCoverCapture(playerView: PlayerView?) {
        playerViewRef = playerView?.let { WeakReference(it) }
    }

    /** 用户按返回退出前调用：在 PlayerView 销毁前截取当前画面 */
    fun snapshotCoverBeforeExit() {
        if (playerReleased) return
        pendingExitCover?.recycle()
        pendingExitCover = null

        val position = playerService.getCurrentPosition()
        val bitmap = captureCoverBitmap(position)
        pendingExitCover = bitmap
        android.util.Log.d(
            "PlayerViewModel",
            "snapshotCoverBeforeExit: captured=${bitmap != null}, position=$position"
        )
    }

    private fun captureCoverBitmap(positionMs: Long): android.graphics.Bitmap? {
        val playerView = playerViewRef?.get()
        var bitmap = playerView?.let { WatchHistoryCoverCapture.captureFrame(it) }
        if (bitmap != null && !WatchHistoryCoverCapture.isLikelyBlank(bitmap)) {
            return bitmap
        }
        bitmap?.recycle()
        val url = currentVideoUrl
        if (!url.isNullOrBlank()) {
            return WatchHistoryCoverCapture.captureFrameFromVideo(url, positionMs)
        }
        return null
    }

    /**
     * 播放自动结束时：在最后几分钟随机 seek 后截帧，用作播放记录封面。
     */
    suspend fun captureCoverOnPlaybackEnded() {
        captureWatchHistoryCover(CoverCaptureReason.PLAYBACK_ENDED)
    }

    private enum class CoverCaptureReason {
        /** 用户按返回退出：截取当前画面 */
        EXIT_BACK,
        /** 自动播完：截取最后几分钟内的随机画面 */
        PLAYBACK_ENDED
    }

    private suspend fun captureWatchHistoryCover(reason: CoverCaptureReason): Boolean {
        val mediaId = currentMediaId ?: return false
        val episodeId = currentEpisodeId

        if (!isRememberPlaybackEnabled()) {
            android.util.Log.d("PlayerViewModel", "Remember playback disabled, skip cover capture")
            return false
        }

        val duration = playerService.getDuration()
        if (duration <= 0) return false

        when (reason) {
            CoverCaptureReason.EXIT_BACK -> persistWatchProgressSnapshot()
            CoverCaptureReason.PLAYBACK_ENDED -> saveCompletedProgressSnapshot()
        }

        val capturePosition = if (reason == CoverCaptureReason.PLAYBACK_ENDED) {
            val targetPosition = randomPositionInLastMinutes(duration)
            withContext(Dispatchers.Main) {
                playerService.seekTo(targetPosition)
            }
            delay(FRAME_SETTLE_MS)
            targetPosition
        } else {
            playerService.getCurrentPosition()
        }

        val bitmap = withContext(Dispatchers.Main) {
            captureCoverBitmap(capturePosition)
        } ?: return false

        val coverPath = try {
            coverStore.saveCover(mediaId, episodeId, bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        } ?: return false

        watchHistoryService.updateLocalCover(mediaId, episodeId, coverPath)
        android.util.Log.d(
            "PlayerViewModel",
            "Saved watch history cover ($reason): $coverPath"
        )
        return true
    }

    private suspend fun isRememberPlaybackEnabled(): Boolean =
        playerSettingsPreferences.rememberPlaybackPosition.first()

    private suspend fun persistWatchProgressSnapshot() {
        val mediaId = currentMediaId ?: return
        val position = playerService.getCurrentPosition()
        val duration = playerService.getDuration()
        if (duration <= 0) return
        watchHistoryService.saveWatchProgress(
            mediaId = mediaId,
            episodeId = currentEpisodeId,
            progress = position,
            duration = duration,
            title = currentTitle,
            episodeTitle = currentEpisodeTitle,
            videoUrl = currentVideoUrl
        )
    }

    private suspend fun saveCompletedProgressSnapshot() {
        val mediaId = currentMediaId ?: return
        val duration = playerService.getDuration()
        if (duration <= 0) return
        watchHistoryService.saveWatchProgress(
            mediaId = mediaId,
            episodeId = currentEpisodeId,
            progress = duration,
            duration = duration,
            title = currentTitle,
            episodeTitle = currentEpisodeTitle,
            videoUrl = currentVideoUrl
        )
    }

    private fun randomPositionInLastMinutes(durationMs: Long): Long {
        val windowStart = (durationMs - COVER_LAST_MINUTES_MS).coerceAtLeast(0L)
        val windowEnd = (durationMs - 1_000L).coerceAtLeast(0L)
        if (windowEnd <= windowStart) {
            return (durationMs * 0.85).toLong().coerceIn(0L, windowEnd.coerceAtLeast(0L))
        }
        return (windowStart..windowEnd).random()
    }

    fun initializePlayer() {
        playerService.initializePlayer()
        startPositionUpdates()
        startProgressSaving()
        startPlaybackStatsTracking()
    }

    fun releasePlayer() {
        if (playerReleased) return
        playerReleased = true

        stopPlaybackStatsTracking()
        flushPlaybackStatsSync()
        stopProgressSaving()
        stopPositionUpdates()

        val mediaId = currentMediaId
        val episodeId = currentEpisodeId
        val position = playerService.getCurrentPosition()
        val duration = playerService.getDuration()
        val title = currentTitle
        val episodeTitle = currentEpisodeTitle
        val videoUrl = currentVideoUrl

        // 必须在释放播放器前截帧；优先使用退出前已缓存的画面
        var bitmap = pendingExitCover?.also { pendingExitCover = null }
        if (bitmap == null || WatchHistoryCoverCapture.isLikelyBlank(bitmap)) {
            bitmap?.recycle()
            bitmap = captureCoverBitmap(position)
        }

        playerService.releasePlayer()

        if (mediaId == null || duration <= 0) {
            bitmap?.recycle()
            return
        }

        runBlocking(Dispatchers.IO) {
            try {
                if (!playerSettingsPreferences.rememberPlaybackPosition.first()) return@runBlocking

                watchHistoryService.saveWatchProgress(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progress = position,
                    duration = duration,
                    title = title,
                    episodeTitle = episodeTitle,
                    videoUrl = videoUrl
                )

                if (bitmap != null) {
                    try {
                        val path = coverStore.saveCover(mediaId, episodeId, bitmap)
                        if (path != null) {
                            watchHistoryService.updateLocalCover(mediaId, episodeId, path)
                            android.util.Log.d(
                                "PlayerViewModel",
                                "Saved watch history cover on exit: $path"
                            )
                        }
                    } finally {
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                } else {
                    android.util.Log.w("PlayerViewModel", "No cover bitmap captured on exit")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error saving cover on exit", e)
                bitmap?.recycle()
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
        if (currentEpisodeId != episodeId) {
            resetSkipStatus()
        }
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
                        duration = duration,
                        title = currentTitle,
                        episodeTitle = currentEpisodeTitle,
                        videoUrl = currentVideoUrl
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
        return watchHistoryService.getLastWatchPosition(mediaId, selectedEpisodeId)?.progress ?: 0L
    }

    fun getPlayer(): Player? = playerService.getPlayer()

    /**
     * 准备媒体播放 - 支持真实URL解析和WebDAV认证
     */
    fun prepareMedia(videoPath: String, title: String?, episodeTitle: String?, startPosition: Long = 0L) {
        currentTitle = title
        currentEpisodeTitle = episodeTitle
        currentVideoUrl = videoPath
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
                            if (state.type == com.lemon.yingshi.tv.domain.service.PlayerState.Type.READY && state.duration > 0) {
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
                            if (state.type == com.lemon.yingshi.tv.domain.service.PlayerState.Type.READY) {
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
        viewModelScope.launch {
            try {
                navigateToAdjacentEpisodeWithFeedback(direction = 1)
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error navigating to next episode", e)
                showEpisodeNavigationMessage("跳转失败")
            }
        }
    }

    /**
     * 跳转到上一集
     */
    fun seekToPrevious() {
        viewModelScope.launch {
            try {
                navigateToAdjacentEpisodeWithFeedback(direction = -1)
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error navigating to previous episode", e)
                showEpisodeNavigationMessage("跳转失败")
            }
        }
    }

    private suspend fun navigateToAdjacentEpisodeWithFeedback(direction: Int): Boolean {
        val mediaId = currentMediaId
        if (mediaId == null) {
            showEpisodeNavigationMessage(
                if (direction > 0) "无法加载下一集" else "无法加载上一集"
            )
            return false
        }
        if (!MacCmsIds.isMacCmsId(mediaId)) {
            showEpisodeNavigationMessage(
                if (direction > 0) "无法加载下一集" else "无法加载上一集"
            )
            return false
        }

        val episode = macCmsPlayerHelper.getAdjacentEpisode(mediaId, currentEpisodeId, direction)
        android.util.Log.d(
            "PlayerViewModel",
            "navigateToAdjacentEpisode: direction=$direction, currentEpisodeId=$currentEpisodeId, episode=$episode"
        )
        if (episode == null) {
            showEpisodeNavigationMessage(
                if (direction > 0) "已是最后一集" else "已是第一集"
            )
            return false
        }

        val rememberEnabled = playerSettingsPreferences.rememberPlaybackPosition.first()
        val start = if (rememberEnabled) {
            watchHistoryService.getLastWatchPosition(mediaId, episode.id)?.progress ?: 0L
        } else {
            0L
        }
        val callback = onNavigateToEpisode
        if (callback == null) {
            showEpisodeNavigationMessage(
                if (direction > 0) "无法加载下一集" else "无法加载上一集"
            )
            return false
        }

        callback.invoke(
            episode.path,
            currentTitle ?: "",
            buildEpisodeTitle(episode.episodeNumber, episode.title),
            mediaId,
            episode.id,
            start
        )
        return true
    }

    private suspend fun showEpisodeNavigationMessage(message: String) {
        _episodeNavigationMessage.value = message
        delay(3000)
        _episodeNavigationMessage.value = null
    }

    private fun buildEpisodeTitle(episodeNumber: Int, title: String): String {
        return if (title.isNotBlank()) {
            "第${episodeNumber}集 $title"
        } else {
            "第${episodeNumber}集"
        }
    }

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
        if (!MacCmsIds.isMacCmsId(mediaId)) return emptyList()
        return try {
            macCmsPlayerHelper.loadEpisodes(mediaId, currentEpisodeId).map { episode ->
                EpisodeListItem(
                    id = episode.id,
                    episodeNumber = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    title = episode.title,
                    stillUrl = null,
                    path = episode.path
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error getting episode list", e)
            emptyList()
        }
    }

    /**
     * 获取当前集数的所有清晰度选项
     */
    suspend fun getQualityOptions(): List<QualityOption> = emptyList()

    /**
     * 切换到指定清晰度
     */
    suspend fun switchQuality(qualityId: String): Boolean = false

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
                        duration = duration,
                        title = currentTitle,
                        episodeTitle = currentEpisodeTitle,
                        videoUrl = currentVideoUrl
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
        releasePlayer()
        super.onCleared()
    }

    companion object {
        private const val COVER_LAST_MINUTES_MS = 3 * 60 * 1000L
        private const val FRAME_SETTLE_MS = 500L
        private const val DEFAULT_OUTRO_DURATION_MS = 120_000L
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
        currentSeriesMediaId = mediaId
        loadSkipConfig(mediaId, seasonNumber)
    }
    
    /**
     * 获取当前跳过配置（如果没有则返回默认配置）
     */
    fun getCurrentSkipConfig(): SkipConfigEntity {
        return _skipConfig.value ?: SkipConfigEntity(
            mediaId = currentSeriesMediaId ?: currentMediaId ?: "",
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
        val config = _skipConfig.value ?: return false
        val duration = playerService.getDuration()
        if (!config.skipOutroEnabled || duration <= 0 || hasSkippedOutro) {
            return false
        }

        val outroDuration = config.outroDuration.takeIf { it > 0 } ?: DEFAULT_OUTRO_DURATION_MS
        val outroStart = (duration - outroDuration).coerceAtLeast(0L)
        val currentPosition = playerService.getCurrentPosition()

        if (currentPosition >= outroStart) {
            android.util.Log.d(
                "PlayerViewModel",
                "自动跳过片尾: outroStart=$outroStart, currentPosition=$currentPosition, outroDuration=$outroDuration"
            )
            skipOutro(autoTriggered = true)
            return true
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
    fun skipOutro(autoTriggered: Boolean = false) {
        hasSkippedOutro = true
        viewModelScope.launch {
            val navigated = navigateToAdjacentEpisodeWithFeedback(direction = 1)
            if (!navigated) {
                hasSkippedOutro = false
                if (autoTriggered) {
                    android.util.Log.w("PlayerViewModel", "自动跳过片尾失败，已恢复检测状态")
                }
            } else {
                android.util.Log.d("PlayerViewModel", "跳过片尾，播放下一集")
            }
        }
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

