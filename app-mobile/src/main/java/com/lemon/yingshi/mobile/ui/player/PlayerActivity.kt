package com.lemon.yingshi.mobile.ui.player

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityPlayerBinding
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import com.lemon.yingshi.tv.data.preferences.PlayerSettingsPreferences
import com.lemon.yingshi.tv.domain.service.PlayerState
import com.lemon.yingshi.tv.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    @Inject lateinit var playerSettingsPreferences: PlayerSettingsPreferences

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private var controlsVisible = true
    private var hideControlsJob: Job? = null
    private var userSeeking = false
    private var seekDurationMs = 15_000L
    private var currentSpeed = 1.0f
    private var episodeList: List<PlayerViewModel.EpisodeListItem> = emptyList()
    private var sessionEpisodeId: String? = null
    private var startPositionMs = 0L
    private var resumeDialogShown = false
    private var currentTitle: String? = null
    private var currentEpisodeTitle: String? = null
    private var lastShownError: String? = null

    private var speedDialog: android.app.Dialog? = null
    private var episodeDialog: android.app.Dialog? = null
    private var skipDialog: android.app.Dialog? = null
    private var gesturePreviewPosition: Long? = null
    private var seekGestureHelper: PlayerSeekGestureHelper? = null
    private var isExiting = false
    private var videoSizeListener: Player.Listener? = null
    private var orientationListenerPlayer: Player? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE)
        val episodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE)
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
        sessionEpisodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        viewModel.setPosterUrl(intent.getStringExtra(EXTRA_POSTER_URL))

        setupPlayerView()
        currentTitle = title
        currentEpisodeTitle = episodeTitle
        updateTitleDisplay(currentTitle, currentEpisodeTitle)
        bindControls()
        setupSeekGestures()
        setupBackNavigation()

        if (mediaId != null) {
            viewModel.setMediaInfo(mediaId, sessionEpisodeId)
            viewModel.loadSkipConfigForSeries(mediaId, seasonNumber = 0)
            lifecycleScope.launch {
                episodeList = viewModel.getEpisodeList()
                binding.episodeButton.isVisible = episodeList.isNotEmpty()
            }
        }

        viewModel.setEpisodeNavigationCallback { url, newTitle, newEpisodeTitle, newMediaId, newEpisodeId, start ->
            val resolvedMediaId = newMediaId ?: return@setEpisodeNavigationCallback
            currentTitle = newTitle
            currentEpisodeTitle = newEpisodeTitle
            updateTitleDisplay(currentTitle, currentEpisodeTitle)
            sessionEpisodeId = newEpisodeId
            viewModel.setMediaInfo(resolvedMediaId, newEpisodeId)
            viewModel.prepareMedia(url, newTitle, newEpisodeTitle, start)
        }

        viewModel.initializePlayer()
        viewModel.prepareMedia(videoUrl, title, episodeTitle, startPositionMs)
        binding.playerView.player = viewModel.getPlayer()
        setupVideoSizeOrientationListener()
        viewModel.setPlayerViewForCoverCapture(binding.playerView)

        observeState(videoUrl, title, episodeTitle)
        updateClock()
        showControlsTemporarily()

        if (startPositionMs > 0L && !resumeDialogShown) {
            resumeDialogShown = true
            PlayerDialogs.showResumeDialog(
                context = this,
                startPositionMs = startPositionMs,
                onContinue = { },
                onRestart = { viewModel.restartFromBeginning() }
            )
        }
    }

    private fun setupPlayerView() {
        binding.playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = false
            subtitleView?.apply {
                setApplyEmbeddedStyles(false)
                setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                )
            }
        }
    }

    private fun bindControls() {
        binding.playerBack.setOnClickListener { exitPlayback() }
        binding.playPauseButton.setOnClickListener {
            viewModel.togglePlayPause()
            showControlsTemporarily()
        }
        binding.centerPlayButton.setOnClickListener {
            viewModel.play()
            showControlsTemporarily()
        }
        binding.skipPreviousButton.setOnClickListener {
            viewModel.seekToPrevious()
            showControlsTemporarily()
        }
        binding.skipNextButton.setOnClickListener {
            viewModel.seekToNext()
            showControlsTemporarily()
        }
        binding.speedButton.setOnClickListener { showSpeedPicker() }
        binding.episodeButton.setOnClickListener { showEpisodePicker() }
        binding.skipButton.setOnClickListener { showSkipPicker() }

        binding.progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) userSeeking = true
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                hideControlsJob?.cancel()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = viewModel.playerState.value.duration
                if (duration > 0) {
                    val target = (binding.progressBar.progress / 1000f * duration).toLong()
                    viewModel.seekTo(target)
                }
                userSeeking = false
                showControlsTemporarily()
            }
        })
    }

    private fun setupVideoSizeOrientationListener() {
        val player = viewModel.getPlayer() ?: run {
            detachVideoSizeOrientationListener()
            return
        }
        if (player === orientationListenerPlayer) return

        detachVideoSizeOrientationListener()
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                applyOrientationForVideoSize(videoSize)
            }
        }
        videoSizeListener = listener
        orientationListenerPlayer = player
        player.addListener(listener)
        applyOrientationForVideoSize(player.videoSize)
    }

    private fun detachVideoSizeOrientationListener() {
        orientationListenerPlayer?.let { player ->
            videoSizeListener?.let { player.removeListener(it) }
        }
        videoSizeListener = null
        orientationListenerPlayer = null
    }

    private fun applyOrientationForVideoSize(videoSize: VideoSize) {
        val width = videoSize.width
        val height = videoSize.height
        if (width <= 0 || height <= 0) return

        val rotation = videoSize.unappliedRotationDegrees
        val effectiveWidth: Int
        val effectiveHeight: Int
        if (rotation == 90 || rotation == 270) {
            effectiveWidth = height
            effectiveHeight = width
        } else {
            effectiveWidth = width
            effectiveHeight = height
        }

        val targetOrientation = if (effectiveHeight > effectiveWidth) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (requestedOrientation != targetOrientation) {
            requestedOrientation = targetOrientation
        }
    }

    private fun setupSeekGestures() {
        seekGestureHelper = PlayerSeekGestureHelper(
            context = this,
            targetView = binding.root,
            isTouchOnControls = { event -> isTouchOnControlArea(event.y) },
            getDurationMs = { viewModel.playerState.value.duration },
            getCurrentPositionMs = {
                gesturePreviewPosition ?: viewModel.playerState.value.currentPosition
            },
            getSeekStepMs = { seekDurationMs },
            onSeekPreview = { previewMs, deltaMs ->
                gesturePreviewPosition = previewMs
                userSeeking = true
                showSeekFeedback(deltaMs, previewMs)
                updateProgressUi(
                    viewModel.playerState.value,
                    positionOverride = previewMs
                )
            },
            onSeekCommit = { positionMs ->
                gesturePreviewPosition = null
                userSeeking = false
                hideSeekFeedback()
                viewModel.seekTo(positionMs)
                showControlsTemporarily()
                updateProgressUi(viewModel.playerState.value)
            },
            onSeekCancel = {
                gesturePreviewPosition = null
                userSeeking = false
                hideSeekFeedback()
                updateProgressUi(viewModel.playerState.value)
            },
            onSingleTap = { toggleControls() },
            onInteraction = { showControlsTemporarily() }
        ).also { it.attach() }
    }

    private fun isTouchOnControlArea(y: Float): Boolean {
        if (!controlsVisible) return false
        val topGuard = 80f * resources.displayMetrics.density
        val bottomGuard = 170f * resources.displayMetrics.density
        val height = binding.root.height
        if (height <= 0) return false
        return y < topGuard || y > height - bottomGuard
    }

    private fun showSeekFeedback(deltaMs: Long, previewMs: Long) {
        val duration = viewModel.playerState.value.duration
        binding.seekFeedbackOverlay.isVisible = true
        val sign = if (deltaMs >= 0) "+" else "-"
        binding.seekDeltaText.text = "$sign${PlayerTimeFormatter.format(kotlin.math.abs(deltaMs))}"
        binding.seekTargetText.text =
            "${PlayerTimeFormatter.format(previewMs)} / ${PlayerTimeFormatter.format(duration)}"
    }

    private fun hideSeekFeedback() {
        binding.seekFeedbackOverlay.isVisible = false
    }

    private fun observeState(videoUrl: String, title: String?, episodeTitle: String?) {
        lifecycleScope.launch {
            playerSettingsPreferences.seekDurationSeconds.collect { seconds ->
                seekDurationMs = seconds * 1000L
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.isLoadingMedia, viewModel.playerState) { loading, state ->
                    loading || state.type == PlayerState.Type.BUFFERING
                }.collect { buffering ->
                    binding.bufferingContainer.isVisible = buffering
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    val playing = state.isPlaying && state.type == PlayerState.Type.READY
                    binding.playPauseButton.setImageResource(
                        if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play
                    )
                    binding.centerPlayButton.isVisible = !playing && state.type == PlayerState.Type.READY
                    updateProgressUi(state)

                    if (state.type == PlayerState.Type.ENDED) {
                        viewModel.captureCoverOnPlaybackEnded()
                        delay(800)
                        viewModel.seekToNext()
                    }
                    if (state.isPlaying) {
                        if (viewModel.checkAndSkipIntro()) {
                            Toast.makeText(this@PlayerActivity, R.string.player_skip_intro, Toast.LENGTH_SHORT).show()
                        }
                        if (viewModel.checkAndSkipOutro()) {
                            Toast.makeText(this@PlayerActivity, R.string.player_skip_outro, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    val error = state.error
                    if (error != null && error != lastShownError) {
                        lastShownError = error
                        PlayerDialogs.showErrorDialog(
                            context = this@PlayerActivity,
                            message = error,
                            onRetry = {
                                lastShownError = null
                                viewModel.clearError()
                                viewModel.initializePlayer()
                                viewModel.prepareMedia(videoUrl, title, episodeTitle, startPositionMs)
                            }
                        )
                    } else if (error == null) {
                        lastShownError = null
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.episodeNavigationMessage.collect { message ->
                    message?.let {
                        Toast.makeText(this@PlayerActivity, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect {
                    if (isExiting) return@collect
                    val player = viewModel.getPlayer()
                    if (binding.playerView.player != player) {
                        binding.playerView.player = player
                    }
                    setupVideoSizeOrientationListener()
                }
            }
        }
    }

    private fun updateProgressUi(state: PlayerState, positionOverride: Long? = gesturePreviewPosition) {
        val duration = state.duration
        if (duration <= 0) return

        val currentPosition = positionOverride ?: state.currentPosition
        val progress = (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
        val prefetchMs = (state.prefetchProgress * duration).toLong()
        val bufferedMs = maxOf(state.bufferedPosition, prefetchMs)
        val buffered = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)

        binding.progressBubble.text =
            "${PlayerTimeFormatter.format(currentPosition)} / ${PlayerTimeFormatter.format(duration)}"

        if (state.prefetchActive && state.prefetchTotalSegments > 0) {
            binding.prefetchText.isVisible = true
            binding.prefetchText.text = getString(
                R.string.player_prefetch,
                state.prefetchCompletedSegments,
                state.prefetchTotalSegments
            )
        } else {
            binding.prefetchText.isVisible = false
        }

        val trackWidth = binding.progressTrackContainer.width
        if (trackWidth <= 0) {
            binding.progressTrackContainer.post { updateProgressUi(state) }
            return
        }

        binding.progressPlayed.layoutParams = binding.progressPlayed.layoutParams.apply {
            width = (trackWidth * progress).toInt()
        }
        binding.progressBuffered.layoutParams = binding.progressBuffered.layoutParams.apply {
            width = (trackWidth * buffered).toInt()
        }

        binding.progressBubble.post {
            val bubbleWidth = binding.progressBubble.width
            val maxX = (trackWidth - bubbleWidth).coerceAtLeast(0)
            binding.progressBubble.translationX =
                (trackWidth * progress - bubbleWidth / 2f).coerceIn(0f, maxX.toFloat())
        }

        if (!userSeeking) {
            binding.progressBar.progress = (progress * 1000).toInt().coerceIn(0, 1000)
        }
    }

    private fun updateTitleDisplay(title: String?, episodeTitle: String?) {
        val display = if (!episodeTitle.isNullOrBlank()) {
            "${title.orEmpty()} $episodeTitle"
        } else {
            title.orEmpty()
        }
        binding.playerTitle.text = display
    }

    private fun updateClock() {
        lifecycleScope.launch {
            while (true) {
                binding.playerClock.text =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(60_000)
            }
        }
    }

    private fun showSpeedPicker() {
        speedDialog?.dismiss()
        speedDialog = PlayerDialogs.showSpeedDialog(
            context = this,
            speeds = viewModel.getAvailableSpeeds(),
            currentSpeed = currentSpeed
        ) { speed ->
            viewModel.setPlaybackSpeed(speed)
            currentSpeed = speed
            binding.speedButton.text = "${speed}X"
            speedDialog?.dismiss()
            showControlsTemporarily()
        }
    }

    private fun showEpisodePicker() {
        if (episodeList.isEmpty()) return
        episodeDialog?.dismiss()
        episodeDialog = PlayerDialogs.showEpisodeListDialog(
            context = this,
            episodes = episodeList,
            currentEpisodeId = sessionEpisodeId
        ) { episode ->
            episodeDialog?.dismiss()
            lifecycleScope.launch {
                viewModel.saveWatchProgressBeforeEpisodeSelectionSwitch()
                val resumePosition = viewModel.getResumeStartPositionForEpisodeSelection(episode.id)
                val resolvedUrl = viewModel.resolvePlaybackUrl(episode.path.orEmpty())
                val newEpisodeTitle = if (episode.title.isNotEmpty()) {
                    "第${episode.episodeNumber}集 ${episode.title}"
                } else {
                    "第${episode.episodeNumber}集"
                }
                sessionEpisodeId = episode.id
                currentEpisodeTitle = newEpisodeTitle
                updateTitleDisplay(currentTitle, currentEpisodeTitle)
                viewModel.setMediaInfo(intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty(), episode.id)
                viewModel.prepareMedia(resolvedUrl, currentTitle, newEpisodeTitle, resumePosition)
            }
            showControlsTemporarily()
        }
    }

    private fun showSkipPicker() {
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: return
        skipDialog?.dismiss()
        lifecycleScope.launch {
            val config = viewModel.skipConfig.first() ?: SkipConfigEntity(
                mediaId = mediaId,
                seasonNumber = 0
            )
            val duration = viewModel.playerState.value.duration
            val scopeTitle = binding.playerTitle.text.toString()
            skipDialog = PlayerDialogs.showSkipConfigDialog(
                context = this@PlayerActivity,
                config = config,
                scopeTitle = scopeTitle,
                durationMs = duration,
                onSave = { viewModel.saveSkipConfig(it) },
                onReset = { viewModel.resetSkipConfigToDefault() },
                onSetIntroEnd = {
                    viewModel.playerState.value.currentPosition
                },
                onSetOutroStart = {
                    val state = viewModel.playerState.value
                    if (state.duration > 0 && state.currentPosition > 0) {
                        state.duration - state.currentPosition
                    } else {
                        0L
                    }
                }
            )
        }
        showControlsTemporarily()
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible
        binding.controlsOverlay.isVisible = controlsVisible
        if (controlsVisible) showControlsTemporarily()
    }

    private fun showControlsTemporarily() {
        controlsVisible = true
        binding.controlsOverlay.isVisible = true
        hideControlsJob?.cancel()
        hideControlsJob = lifecycleScope.launch {
            delay(6_000)
            controlsVisible = false
            binding.controlsOverlay.isVisible = false
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitPlayback()
            }
        })
    }

    private fun exitPlayback() {
        if (isExiting || isFinishing) return
        isExiting = true
        try {
            runCatching { viewModel.snapshotCoverBeforeExit() }
        } finally {
            stopPlaybackAndRelease()
            finish()
        }
    }

    private fun stopPlaybackAndRelease() {
        isExiting = true
        detachVideoSizeOrientationListener()
        viewModel.pause()
        binding.playerView.player = null
        viewModel.setPlayerViewForCoverCapture(null)
        viewModel.releasePlayer()
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations && !isExiting) {
            viewModel.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        stopPlaybackAndRelease()
    }

    override fun onDestroy() {
        seekGestureHelper?.detach()
        speedDialog?.dismiss()
        episodeDialog?.dismiss()
        skipDialog?.dismiss()
        stopPlaybackAndRelease()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_MEDIA_ID = "extra_media_id"
        const val EXTRA_EPISODE_ID = "extra_episode_id"
        const val EXTRA_START_POSITION = "extra_start_position"
        const val EXTRA_POSTER_URL = "extra_poster_url"
    }
}
