package com.lemon.yingshi.tv.domain.service

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerDataSourceFactory: PlayerDataSourceFactory,
    private val hlsPrefetchService: HlsPrefetchService
) {
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var videoQualityOptions: List<VideoQualityOption> = emptyList()
    private var selectedQualityIndex: Int = 0
    
    // 字幕和音轨信息
    private val _availableSubtitles = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableSubtitles: StateFlow<List<TrackInfo>> = _availableSubtitles.asStateFlow()
    
    private val _availableAudioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<TrackInfo>> = _availableAudioTracks.asStateFlow()
    
    private val _selectedSubtitleIndex = MutableStateFlow(-1) // -1表示无字幕
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex.asStateFlow()
    private val subtitleTrackOverrides = mutableMapOf<Int, TrackSelectionOverride>()
    
    private val _selectedAudioTrackIndex = MutableStateFlow(0)
    val selectedAudioTrackIndex: StateFlow<Int> = _selectedAudioTrackIndex.asStateFlow()
    private val audioTrackOverrides = mutableMapOf<Int, TrackSelectionOverride>()
    
    // 错误重试机制
    private var errorRetryCount = 0
    private val maxErrorRetries = 2
    private var lastErrorCode = 0
    private var currentMediaUrl: String? = null
    private var currentMediaTitle: String? = null
    private var currentMediaEpisodeTitle: String? = null
    private var currentMediaHeaders: Map<String, String> = emptyMap()
    private var currentSubtitles: List<SubtitleInfo> = emptyList()
    private var currentStartPosition: Long = 0
    private var autoSubtitleAppliedForCurrentMedia: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRetryRunnable: Runnable? = null

    @OptIn(UnstableApi::class)
    fun initializePlayer(): ExoPlayer {
        android.util.Log.d("PlayerService", "initializePlayer called, current exoPlayer=$exoPlayer")
        // 检查播放器是否存在且有效（未被释放）
        if (exoPlayer == null || exoPlayer!!.playbackState == Player.STATE_IDLE && exoPlayer!!.playerError != null) {
            // 如果播放器已被释放或无效，先清理
            if (exoPlayer != null) {
                android.util.Log.d("PlayerService", "Player is invalid, releasing and recreating...")
                try {
                    exoPlayer?.removeListener(playerListener)
                    exoPlayer?.release()
                } catch (e: Exception) {
                    android.util.Log.w("PlayerService", "Error releasing old player: ${e.message}")
                }
                exoPlayer = null
                trackSelector = null
            }
            val memoryProfile = buildPlaybackMemoryProfile(context)
            android.util.Log.i(
                "PlayerService",
                "Playback memory profile: maxVideo=${memoryProfile.maxVideoWidth}x${memoryProfile.maxVideoHeight}, " +
                    "maxBitrate=${memoryProfile.maxVideoBitrate}, " +
                    "buffers(min/max/start/rebuffer)=" +
                    "${memoryProfile.minBufferMs}/${memoryProfile.maxBufferMs}/" +
                    "${memoryProfile.bufferForPlaybackMs}/${memoryProfile.bufferForPlaybackAfterRebufferMs}"
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            // 创建TrackSelector以支持自定义轨道选择
            trackSelector = DefaultTrackSelector(context)
            val trackParams = DefaultTrackSelector.Parameters.Builder(context)
                // 低内存设备/模拟器限制峰值，降低 OOM 概率
                .setMaxVideoSize(memoryProfile.maxVideoWidth, memoryProfile.maxVideoHeight)
                .setMaxVideoBitrate(memoryProfile.maxVideoBitrate)
                // 不再强行偏好 H.265，避免高复杂度解码带来额外内存/CPU 压力
                .setForceHighestSupportedBitrate(false)
                // 允许选择任何支持的视频格式（包括软件解码器支持的格式）
                .setTunnelingEnabled(false) // 禁用隧道模式，确保可以使用软件解码器
                .build()
            trackSelector!!.parameters = trackParams
            
            // 配置 LoadControl 以优化缓冲
            // 对于软件解码（特别是模拟器），需要更大的缓冲区来保证流畅播放
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    memoryProfile.minBufferMs,
                    memoryProfile.maxBufferMs,
                    memoryProfile.bufferForPlaybackMs,
                    memoryProfile.bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true) // 优先保证时间阈值，而不是数据量
                .build()

            // 创建 BandwidthMeter 用于测量网速
            bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(2_000_000) // 初始估计 2Mbps
                .setSlidingWindowMaxWeight(2000) // 2秒滑动窗口
                .build()

            val mediaSourceFactory = playerDataSourceFactory.createMediaSourceFactory()

            // 创建渲染器工厂 - 关键修改：强制使用软件解码器
            // 在模拟器上，硬件解码器 (OMX.qcom) 通常是假的或不完整，导致黑屏
            // 解决方案：完全禁用硬件解码器，只使用 FFmpeg 扩展
            val renderersFactory = DefaultRenderersFactory(context).apply {
                // EXTENSION_RENDERER_MODE_PREFER = 2: 优先使用扩展解码器 (FFmpeg)
                // 这会告诉 ExoPlayer 优先选择 FFmpeg 而不是硬件解码器
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                
                // 启用解码器回退：确保硬件解码失败时使用 FFmpeg
                setEnableDecoderFallback(true)
                
                // 允许视频轨道切换时的缓冲时间
                setAllowedVideoJoiningTimeMs(5000)
            }
            android.util.Log.d("PlayerService", "RenderersFactory configured - FORCED SOFTWARE DECODING WITH FFMPEG")
            
            // 注意：由于硬件和软件解码器都不支持某些 HEVC 格式（如 hvc1.2.4.L153.90），
            // 即使启用了回退机制，也可能无法播放。
            // 如果问题仍然存在，建议考虑转码视频为设备支持的格式（如 H.264 或较低 profile 的 HEVC）

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter!!)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    addListener(playerListener)
                }
        }
        return exoPlayer!!
    }

    fun releasePlayer() {
        cancelPendingRetry()
        hlsPrefetchService.stop()
        playerDataSourceFactory.clearMediaCache()
        exoPlayer?.apply {
            playWhenReady = false
            pause()
            stop()
            clearMediaItems()
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        trackSelector = null
        bandwidthMeter = null
        _playerState.value = PlayerState()
    }

    fun getPlayer(): ExoPlayer? {
        android.util.Log.d("PlayerService", "getPlayer called, exoPlayer=$exoPlayer")
        return exoPlayer
    }
    
    /**
     * 获取当前网速（比特率），单位：bps
     */
    fun getCurrentBitrate(): Long {
        return bandwidthMeter?.bitrateEstimate ?: 0L
    }

    /**
     * 准备媒体播放，支持自定义HTTP头和字幕
     */
    @OptIn(UnstableApi::class)
    fun prepareMedia(
        url: String,
        title: String? = null,
        episodeTitle: String? = null,
        headers: Map<String, String> = emptyMap(),
        subtitles: List<SubtitleInfo> = emptyList(),
        startPositionMs: Long = 0L
    ) {
        android.util.Log.d("PlayerService", "prepareMedia: url=$url, headers=${headers.keys}, startPositionMs=$startPositionMs")
        
        // 保存当前媒体信息用于错误重试
        currentMediaUrl = url
        currentMediaTitle = title
        currentMediaEpisodeTitle = episodeTitle
        currentMediaHeaders = headers
        currentSubtitles = subtitles
        currentStartPosition = startPositionMs
        autoSubtitleAppliedForCurrentMedia = false
        _selectedSubtitleIndex.value = -1
        subtitleTrackOverrides.clear()
        // 重置错误重试计数（成功准备新媒体时重置）
        errorRetryCount = 0
        lastErrorCode = 0
        
        // 确保播放器已初始化且有效
        if (exoPlayer == null) {
            android.util.Log.d("PlayerService", "Player is null, initializing...")
            initializePlayer()
        } else {
            // 检查播放器是否有效（尝试访问其属性，如果抛出异常则说明已被释放）
            try {
                val state = exoPlayer!!.playbackState
                android.util.Log.d("PlayerService", "Player is valid, playbackState=$state")
            } catch (e: Exception) {
                android.util.Log.w("PlayerService", "Player is invalid, reinitializing: ${e.message}")
                exoPlayer = null
                trackSelector = null
                initializePlayer()
            }
        }
        
        if (headers.isNotEmpty()) {
            android.util.Log.d(
                "PlayerService",
                "Configuring HTTP headers: ${headers.keys}, values: ${headers.mapValues {
                    if (it.key.lowercase().contains("cookie") || it.key.lowercase().contains("authorization")) "***" else it.value
                }}"
            )
        }

        // 构建MediaItem
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(episodeTitle)
                    .build()
            )
        
        // 注意：不使用 ClippingConfiguration 设置起始位置，因为这会改变 duration 的计算
        // 起始位置将在播放器准备好后通过 seekTo 设置
        
        // 添加字幕
        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = subtitles.map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                    .setMimeType(subtitle.mimeType)
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.label)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }
        
        val mediaItem = mediaItemBuilder.build()

        val playbackUrl = normalizeLocalPlaybackUrl(url)
        if (!isLocalPlaybackUrl(playbackUrl)) {
            hlsPrefetchService.start(playbackUrl, headers, startPositionMs)
        }

        exoPlayer?.apply {
            val mediaSourceFactory = playerDataSourceFactory.createMediaSourceFactory(headers, playbackUrl)
            val mediaSource = mediaSourceFactory.createMediaSource(
                mediaItem.buildUpon().setUri(playbackUrl).build()
            )
            setMediaSource(mediaSource)
            prepare()
            if (startPositionMs > 0) {
                android.util.Log.d("PlayerService", "Seeking to start position: $startPositionMs ms")
                seekTo(startPositionMs)
            }
            playWhenReady = true
        }
    }

    fun preparePlaylist(
        urls: List<String>,
        titles: List<String>? = null,
        startIndex: Int = 0
    ) {
        val mediaItems = urls.mapIndexed { index, url ->
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles?.getOrNull(index))
                        .build()
                )
                .build()
        }

        exoPlayer?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            playWhenReady = true
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekToNext() {
        exoPlayer?.seekToNext()
    }

    fun seekToPrevious() {
        exoPlayer?.seekToPrevious()
    }

    fun seekForward(deltaMs: Long = 10000) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + deltaMs)
                .coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }

    fun seekBackward(deltaMs: Long = 10000) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - deltaMs)
                .coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun getAvailableSpeeds(): List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    fun getDuration(): Long = exoPlayer?.duration ?: 0

    fun getBufferedPosition(): Long = exoPlayer?.bufferedPosition ?: 0

    fun getCurrentMediaIndex(): Int = exoPlayer?.currentMediaItemIndex ?: 0

    fun getMediaCount(): Int = exoPlayer?.mediaItemCount ?: 0

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_IDLE -> PlayerState.Type.IDLE
                Player.STATE_BUFFERING -> PlayerState.Type.BUFFERING
                Player.STATE_READY -> PlayerState.Type.READY
                Player.STATE_ENDED -> PlayerState.Type.ENDED
                else -> PlayerState.Type.IDLE
            }
            _playerState.value = _playerState.value.copy(
                type = state,
                isPlaying = exoPlayer?.isPlaying == true
            )
            
            // 当播放器准备好时，更新可用轨道
            if (playbackState == Player.STATE_READY) {
                updateAvailableTracks()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("PlayerService", "Player error: ${error.message}", error)
            android.util.Log.e("PlayerService", "Error type: ${error.errorCode}, cause: ${error.cause?.message}")
            android.util.Log.e("PlayerService", "Error code details: ${error.errorCodeName}")
            
            // 如果是解码器错误，尝试记录详细信息
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
                android.util.Log.e("PlayerService", "Decoder error detected. This may indicate hardware decoder incompatibility.")
                android.util.Log.e("PlayerService", "Error should trigger fallback to software decoder if enabled.")
                
                // 检查错误原因
                val cause = error.cause
                if (cause != null) {
                    android.util.Log.e("PlayerService", "Error cause: ${cause.javaClass.simpleName}, message: ${cause.message}")
                    cause.printStackTrace()
                }
            }
            
            // 记录播放器当前状态
            exoPlayer?.let { player ->
                android.util.Log.e("PlayerService", "Player state at error: playbackState=${player.playbackState}, isPlaying=${player.isPlaying}")
                android.util.Log.e("PlayerService", "Current media item: ${player.currentMediaItem?.localConfiguration?.uri}")
            }
            
            // 检查是否应该自动重试
            val shouldAutoRetry = !isLocalPlaybackUrl(currentMediaUrl) &&
                errorRetryCount < maxErrorRetries &&
                (error.errorCode == lastErrorCode || lastErrorCode == 0) &&
                currentMediaUrl != null
            
            if (shouldAutoRetry) {
                errorRetryCount++
                lastErrorCode = error.errorCode
                android.util.Log.w("PlayerService", "播放错误，尝试自动重试 ($errorRetryCount/$maxErrorRetries)...")
                
                pendingRetryRunnable = Runnable { retryCurrentMedia() }
                mainHandler.postDelayed(pendingRetryRunnable!!, 1000)
                return
            }
            
            // 重置重试计数
            errorRetryCount = 0
            lastErrorCode = 0
            
            // 构建更详细的错误信息
            val errorMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "解码器查询失败"
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "视频解码失败"
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "网络错误"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                    if (isLocalPlaybackUrl(currentMediaUrl)) "离线视频格式异常，请删除后重新缓存" else "视频文件格式错误"
                }
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表格式错误"
                else -> {
                    // 对于未知错误，记录详细信息用于调试
                    android.util.Log.e("PlayerService", "Unknown error code: ${error.errorCode}, message: ${error.message}")
                    error.message ?: "播放器运行时错误"
                }
            }
            
            _playerState.value = _playerState.value.copy(
                type = PlayerState.Type.ERROR,
                error = errorMessage
            )
        }
        
        /**
         * 自动重试当前媒体
         */
        private fun retryCurrentMedia() {
            pendingRetryRunnable = null
            val url = currentMediaUrl ?: return
            if (exoPlayer == null) return
            android.util.Log.d("PlayerService", "自动重试播放: $url")
            
            try {
                // 清除错误状态
                _playerState.value = _playerState.value.copy(
                    type = PlayerState.Type.IDLE,
                    error = null
                )
                
                // 重新准备媒体
                prepareMedia(
                    url = url,
                    title = currentMediaTitle,
                    episodeTitle = currentMediaEpisodeTitle,
                    headers = currentMediaHeaders,
                    subtitles = currentSubtitles,
                    startPositionMs = currentStartPosition
                )
            } catch (e: Exception) {
                android.util.Log.e("PlayerService", "自动重试失败: ${e.message}")
                errorRetryCount = 0
                _playerState.value = _playerState.value.copy(
                    type = PlayerState.Type.ERROR,
                    error = "播放失败，请手动重试"
                )
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePositionInfo()
        }
        
        @OptIn(UnstableApi::class)
        override fun onTracksChanged(tracks: Tracks) {
            // 轨道变化时更新可用轨道
            android.util.Log.d("PlayerService", "Tracks changed: ${tracks.groups.size} track groups")
            
            // 检查视频轨道和渲染器信息
            var hasVideoTrack = false
            var hasSelectedVideoTrack = false
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                    hasVideoTrack = true
                    val isSelected = trackGroup.isSelected
                    hasSelectedVideoTrack = isSelected
                    val format = if (trackGroup.length > 0) trackGroup.getTrackFormat(0) else null
                    
                    android.util.Log.d("PlayerService", "Video track found: selected=$isSelected, format=${format?.codecs}, resolution=${format?.width}x${format?.height}")
                    
                    // 检查解码器信息
                    if (format != null) {
                        android.util.Log.d("PlayerService", "Video format details: mimeType=${format.sampleMimeType}, codec=${format.codecs}, colorSpace=${format.colorInfo}")
                    }
                }
            }
            
            if (!hasVideoTrack) {
                android.util.Log.w("PlayerService", "No video track found in media!")
            } else if (!hasSelectedVideoTrack) {
                android.util.Log.w("PlayerService", "Video track found but not selected! This may cause no video display.")
            } else {
                android.util.Log.d("PlayerService", "Video track is selected and should be rendering")
            }
            
            updateAvailableTracks()
        }
    }

    private fun updatePositionInfo() {
        exoPlayer?.let { player ->
            val position = player.currentPosition
            hlsPrefetchService.updatePlayhead(position)
            val prefetch = hlsPrefetchService.state.value
            _playerState.value = _playerState.value.copy(
                currentPosition = position,
                duration = player.duration.coerceAtLeast(0),
                bufferedPosition = player.bufferedPosition,
                prefetchProgress = prefetch.progress,
                prefetchActive = prefetch.active,
                prefetchCompletedSegments = prefetch.completedSegments,
                prefetchTotalSegments = prefetch.totalSegments,
                prefetchedEndPositionMs = prefetch.prefetchedEndPositionMs
            )
        }
    }

    fun refreshPrefetchState() {
        val prefetch = hlsPrefetchService.state.value
        _playerState.value = _playerState.value.copy(
            prefetchProgress = prefetch.progress,
            prefetchActive = prefetch.active,
            prefetchCompletedSegments = prefetch.completedSegments,
            prefetchTotalSegments = prefetch.totalSegments,
            prefetchedEndPositionMs = prefetch.prefetchedEndPositionMs
        )
    }

    fun updatePosition() {
        updatePositionInfo()
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun toggleMute() {
        exoPlayer?.let { player ->
            player.volume = if (player.volume > 0) 0f else 1f
        }
    }
    
    /**
     * 清除错误状态
     */
    fun clearError() {
        _playerState.value = _playerState.value.copy(
            error = null,
            type = if (exoPlayer != null && exoPlayer!!.playbackState != Player.STATE_IDLE) {
                PlayerState.Type.READY
            } else {
                PlayerState.Type.IDLE
            }
        )
    }

    fun isMuted(): Boolean = exoPlayer?.volume == 0f

    private fun cancelPendingRetry() {
        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRetryRunnable = null
        errorRetryCount = 0
        lastErrorCode = 0
    }
    
    /**
     * 选择字幕轨道
     * @param index 字幕索引，-1表示禁用字幕
     */
    @OptIn(UnstableApi::class)
    fun selectSubtitle(index: Int) {
        trackSelector?.let { selector ->
            val builder = selector.parameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, index < 0)

            val parameters = if (index >= 0 && index < _availableSubtitles.value.size) {
                val override = subtitleTrackOverrides[index]
                if (override != null) {
                    builder
                        .setPreferredTextLanguage(null)
                        .addOverride(override)
                        .build()
                } else {
                    builder
                        .setPreferredTextLanguage(_availableSubtitles.value[index].language)
                        .build()
                }
            } else {
                builder
                    .setPreferredTextLanguage(null)
                    .build()
            }
            
            selector.parameters = parameters
            _selectedSubtitleIndex.value = index
        }
    }
    
    /**
     * 选择音轨
     */
    @OptIn(UnstableApi::class)
    fun selectAudioTrack(index: Int) {
        trackSelector?.let { selector ->
            if (index >= 0 && index < _availableAudioTracks.value.size) {
                val override = audioTrackOverrides[index]
                val builder = selector.parameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)

                val parameters = if (override != null) {
                    android.util.Log.i(
                        "PlayerService",
                        "selectAudioTrack: force override index=$index, label=${_availableAudioTracks.value[index].label}, language=${_availableAudioTracks.value[index].language}"
                    )
                    builder
                        .setPreferredAudioLanguage(null)
                        .addOverride(override)
                        .build()
                } else {
                    // 兜底：若未构建到 override，退回旧逻辑按语言偏好选择
                    android.util.Log.w(
                        "PlayerService",
                        "selectAudioTrack: override missing for index=$index, fallback by language=${_availableAudioTracks.value[index].language}"
                    )
                    builder
                        .setPreferredAudioLanguage(_availableAudioTracks.value[index].language)
                        .build()
                }

                selector.parameters = parameters
                _selectedAudioTrackIndex.value = index
            }
        }
    }
    
    /**
     * 获取当前可用的字幕列表
     */
    @OptIn(UnstableApi::class)
    private fun updateAvailableTracks() {
        exoPlayer?.let { player ->
            val tracks = player.currentTracks
            
            // 更新字幕列表
            subtitleTrackOverrides.clear()
            val subtitles = mutableListOf<TrackInfo>()
            var subtitleIndex = 0
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val rawLabel = format.label?.trim().orEmpty()
                        val label = if (rawLabel.contains("[外挂]") || rawLabel.contains("[内嵌]")) {
                            rawLabel
                        } else {
                            val fallback = if (rawLabel.isNotBlank()) rawLabel else "字幕 ${subtitleIndex + 1}"
                            "[内嵌] $fallback"
                        }
                        subtitles.add(
                            TrackInfo(
                                index = subtitleIndex,
                                language = format.language ?: "unknown",
                                label = label
                            )
                        )
                        subtitleTrackOverrides[subtitleIndex] = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                        subtitleIndex++
                    }
                }
            }
            _availableSubtitles.value = subtitles
            maybeAutoEnableSubtitle(subtitles)
            
            // 更新音轨列表
            audioTrackOverrides.clear()
            val audioTracks = mutableListOf<TrackInfo>()
            var audioIndex = 0
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val language = format.language ?: "unknown"
                        val codec = format.sampleMimeType ?: format.codecs ?: "unknown"
                        val channelInfo = format.channelCount.takeIf { it > 0 }?.let { "${it}ch" } ?: ""
                        val sampleRateInfo = format.sampleRate.takeIf { it > 0 }?.let { "${it}Hz" } ?: ""
                        val extra = listOf(codec, channelInfo, sampleRateInfo)
                            .filter { it.isNotBlank() }
                            .joinToString(" / ")
                        val defaultLabel = "音轨 ${audioTracks.size + 1}"
                        val displayLabel = buildString {
                            append(format.label ?: defaultLabel)
                            if (extra.isNotBlank()) {
                                append("  ")
                                append(extra)
                            }
                        }
                        audioTracks.add(
                            TrackInfo(
                                index = audioIndex,
                                language = language,
                                label = displayLabel
                            )
                        )
                        audioTrackOverrides[audioIndex] = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                        audioIndex++
                    }
                }
            }
            _availableAudioTracks.value = audioTracks
        }
    }

    /**
     * 新媒体首次进入时自动打开一条字幕（优先中文），减少每次手动开启的操作。
     */
    private fun maybeAutoEnableSubtitle(subtitles: List<TrackInfo>) {
        if (autoSubtitleAppliedForCurrentMedia) return
        if (subtitles.isEmpty()) return

        val preferredIndex = subtitles.indexOfFirst { track ->
            val text = "${track.label} ${track.language}".lowercase()
            text.contains("zh") ||
                text.contains("chi") ||
                text.contains("中文") ||
                text.contains("简") ||
                text.contains("繁")
        }.let { if (it >= 0) it else 0 }

        android.util.Log.i(
            "PlayerService",
            "Auto enabling subtitle index=$preferredIndex, label=${subtitles[preferredIndex].label}"
        )
        autoSubtitleAppliedForCurrentMedia = true
        selectSubtitle(preferredIndex)
    }
}

private data class PlaybackMemoryProfile(
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    val maxVideoBitrate: Int,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

private fun buildPlaybackMemoryProfile(context: Context): PlaybackMemoryProfile {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryClassMb = activityManager?.memoryClass ?: 256
    val lowRamDevice = activityManager?.isLowRamDevice == true
    val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)

    val constrained = lowRamDevice || isEmulator || memoryClassMb <= 256 || maxHeapMb <= 256
    return if (constrained) {
        // 更保守：优先稳定，避免播放线程 OOM
        PlaybackMemoryProfile(
            maxVideoWidth = 1920,
            maxVideoHeight = 1080,
            maxVideoBitrate = 8_000_000,
            minBufferMs = 10_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 2_500
        )
    } else {
        PlaybackMemoryProfile(
            maxVideoWidth = 3840,
            maxVideoHeight = 2160,
            maxVideoBitrate = 20_000_000,
            minBufferMs = 15_000,
            maxBufferMs = 45_000,
            bufferForPlaybackMs = 2_000,
            bufferForPlaybackAfterRebufferMs = 3_000
        )
    }
}

data class PlayerState(
    val type: PlayerState.Type = PlayerState.Type.IDLE,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val prefetchProgress: Float = 0f,
    val prefetchActive: Boolean = false,
    val prefetchCompletedSegments: Int = 0,
    val prefetchTotalSegments: Int = 0,
    /** 预缓存窗口已覆盖到的媒体时间点（毫秒），用于进度条缓冲显示 */
    val prefetchedEndPositionMs: Long = 0L,
    val error: String? = null
) {
    enum class Type {
        IDLE,
        BUFFERING,
        READY,
        PLAYING,
        ENDED,
        ERROR
    }
}

data class VideoQualityOption(
    val height: Int,
    val label: String,
    val bitrate: Long
)

data class TrackInfo(
    val index: Int,
    val language: String,
    val label: String
)

data class SubtitleInfo(
    val url: String,
    val mimeType: String,
    val language: String,
    val label: String
)
