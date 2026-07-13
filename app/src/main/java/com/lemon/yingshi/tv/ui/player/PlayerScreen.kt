@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.domain.service.PlayerState
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextMuted
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.view.MotionEvent
import android.view.View

private fun PlayerView.applyTransparentSubtitleStyle() {
    subtitleView?.apply {
        // 关闭字幕文件内的底色/窗口色（ASS 等常为黑底），改由 CaptionStyle 统一绘制
        setApplyEmbeddedStyles(false)
        setStyle(
            CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                /* typeface= */ null
            )
        )
    }
}

// 控制栏模式枚举
enum class ControlsMode {
    SEEK_OVERLAY,  // 快进退反馈态：执行左右快进退，不锁定其它控件
    NAV    // 导航模式：有焦点，可操作按钮，不能快进退
}

private enum class ControlFocusZone {
    NONE,
    TOP_BAR,
    PROGRESS,
    BOTTOM_BUTTONS
}

private const val CONTROLS_AUTO_HIDE_MS = 6000L
private const val PROGRESS_BUBBLE_FOCUS_SCALE = 1.12f
private const val PROGRESS_BUBBLE_IDLE_SCALE = 1.0f
private val PROGRESS_BUBBLE_FOCUS_GLOW = 12.dp
private val PROGRESS_BUBBLE_IDLE_GLOW = 0.dp
private val PROGRESS_BAR_HEIGHT = 6.dp
private val PROGRESS_BUBBLE_MIN_WIDTH = 88.dp
private val PROGRESS_BUBBLE_MAX_WIDTH = 170.dp
private const val FOCUS_ANIMATION_DURATION_MS = 170
private const val PLAYER_DEBUG_TAG = "PlayerScreenDebug"

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    androidx.media3.common.util.UnstableApi::class
)
@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String? = null,
    episodeTitle: String? = null,
    mediaId: String? = null,
    episodeId: String? = null,
    startPosition: Long = 0L,
    onBackPressed: () -> Unit,
    onRegisterGlobalKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val playerState by viewModel.playerState.collectAsState()
    val isLoadingMedia by viewModel.isLoadingMedia.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val episodeMessage by viewModel.episodeNavigationMessage.collectAsState()
    var showControls by remember { mutableStateOf(true) }

    // 续播提示：有起播位置时弹窗 3 秒（播放继续进行）
    var showResumePrompt by remember(startPosition) { mutableStateOf(startPosition > 0L) }
    LaunchedEffect(showResumePrompt) {
        if (showResumePrompt) {
            delay(8000)
            showResumePrompt = false
        }
    }
    
    // 当前播放的标题（可在切换剧集时更新）
    var currentTitle by remember { mutableStateOf(title) }
    var currentEpisodeTitle by remember { mutableStateOf(episodeTitle) }
    /** 同一次播放会话内切换集数后，与 ViewModel 同步，用于选集高亮 */
    var sessionEpisodeId by remember { mutableStateOf(episodeId) }
    LaunchedEffect(episodeId) { sessionEpisodeId = episodeId }
    
    // 对话框状态
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEpisodeListDialog by remember { mutableStateOf(false) }
    var showSkipConfigDialog by remember { mutableStateOf(false) }

    /** 任一模态打开时，勿自动隐藏控制栏 / 勿 clearFocus 抢播放器根焦点（否则会打断对话框） */
    val blockingOverlayDialogsOpen =
        showEpisodeListDialog || showSpeedDialog || showSkipConfigDialog

    // 倍速数据
    val availableSpeeds = remember { viewModel.getAvailableSpeeds() }
    val currentSpeed = remember { mutableStateOf(1.0f) }
    
    // 跳过片头片尾配置
    val skipConfig by viewModel.skipConfig.collectAsState()
    
    // 播放器设置
    val playerSettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel<com.lemon.yingshi.tv.ui.viewmodel.PlayerSettingsViewModel>()
    val seekDurationSeconds by playerSettingsViewModel.seekDurationSeconds.collectAsState(initial = 15)
    
    // 剧集列表
    var episodeList by remember { mutableStateOf<List<PlayerViewModel.EpisodeListItem>>(emptyList()) }
    
    // 显示错误信息（用 Snackbar 替代 Toast，更适合 TV）
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    LaunchedEffect(playerState.error, loadError) {
        val message = loadError ?: playerState.error
        if (message != null) {
            android.util.Log.e("PlayerScreen", "Player error: $message")
            errorMessage = message
            showErrorDialog = true
        }
    }
    
    // 监听播放完成状态，自动播放下一集
    LaunchedEffect(playerState.type) {
        if (playerState.type == PlayerState.Type.ENDED) {
            android.util.Log.d("PlayerScreen", "播放完成，截取封面后准备播放下一集")
            viewModel.captureCoverOnPlaybackEnded()
            kotlinx.coroutines.delay(1000)
            viewModel.seekToNext()
        }
    }
    val focusRequester = remember { FocusRequester() }
    
    // 控制栏模式：null=隐藏, SEEK=快进退模式(无焦点,按钮锁定), NAV=导航模式(有焦点,可操作按钮)
    var controlsMode by remember { mutableStateOf<ControlsMode?>(null) }
    
    // 自动隐藏控制栏的状态：记录最后一次交互时间
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 播放/暂停按钮焦点请求器
    val playPauseFocusRequester = remember { FocusRequester() }
    // 返回按钮焦点请求器
    val backButtonFocusRequester = remember { FocusRequester() }
    // 进度条焦点请求器
    val progressBarFocusRequester = remember { FocusRequester() }
    var requestPlayPauseFocusToken by remember { mutableStateOf(0) }
    var requestProgressFocusToken by remember { mutableStateOf(0) }
    var requestRootFocusRestoreToken by remember { mutableStateOf(0) }
    var navInitialFocusZone by remember { mutableStateOf(ControlFocusZone.BOTTOM_BUTTONS) }
    var focusedControlZone by remember { mutableStateOf(ControlFocusZone.NONE) }
    var rootHasFocus by remember { mutableStateOf(false) }
    
    fun debugLog(message: String) {
        if (com.lemon.yingshi.tv.BuildConfig.DEBUG) {
            android.util.Log.d(PLAYER_DEBUG_TAG, message)
        }
    }

    // 设置媒体信息、跳过配置、选集列表（同一 Effect，避免与下方 getEpisodeList 竞态导致选集恒空）
    LaunchedEffect(mediaId, episodeId) {
        if (mediaId != null) {
            viewModel.setMediaInfo(mediaId, episodeId)
            viewModel.loadSkipConfigForSeries(mediaId, seasonNumber = 0)
            episodeList = viewModel.getEpisodeList()
        }
    }

    val exitPlayback: () -> Unit = {
        viewModel.snapshotCoverBeforeExit()
        onBackPressed()
    }
    
    // 设置剧集导航回调
    LaunchedEffect(Unit) {
        viewModel.setEpisodeNavigationCallback { videoUrl, title, episodeTitle, mediaId, episodeId, startPosition ->
            // 启动新的播放器 Activity
            val intent = android.content.Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, title)
                putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episodeTitle)
                putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId)
                putExtra(PlayerActivity.EXTRA_EPISODE_ID, episodeId)
                putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition)
            }
            context.startActivity(intent)
            // 关闭当前播放器
            exitPlayback()
        }
    }
    
    // 显示提示消息
    LaunchedEffect(episodeMessage) {
        if (episodeMessage != null) {
            android.widget.Toast.makeText(context, episodeMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 初始化播放器
    LaunchedEffect(Unit) {
        android.util.Log.d("PlayerScreen", "Initializing player...")
        viewModel.initializePlayer()
        android.util.Log.d("PlayerScreen", "Player initialized, preparing media...")
        viewModel.prepareMedia(videoUrl, title, episodeTitle, startPosition)
        android.util.Log.d("PlayerScreen", "Media preparation started, startPosition=$startPosition")
        focusRequester.requestFocus()
    }

    // 注意：DisposableEffect 放在 AndroidView 之后，确保 onDispose 时 PlayerView 仍可截帧

    LaunchedEffect(playerState.isPlaying, skipConfig) {
        if (playerState.isPlaying && skipConfig?.skipIntroEnabled == true) {
            val skipped = viewModel.checkAndSkipIntro()
            if (skipped) {
                android.widget.Toast.makeText(context, "已跳过片头", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 监听播放进度，检查是否需要跳过片尾
    LaunchedEffect(playerState.currentPosition, skipConfig) {
        if (playerState.isPlaying && playerState.duration > 0 && skipConfig?.skipOutroEnabled == true) {
            val skipped = viewModel.checkAndSkipOutro()
            if (skipped) {
                android.widget.Toast.makeText(context, "已跳过片尾", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 注意：起始位置现在在 prepareMedia 时直接设置，不需要在这里再次跳转

    // 自动隐藏控制栏：从用户停止操作开始计时
    LaunchedEffect(showControls, lastInteractionTime, blockingOverlayDialogsOpen) {
        if (showControls && !blockingOverlayDialogsOpen) {
            // 等待6秒
            delay(CONTROLS_AUTO_HIDE_MS)
            // 检查是否已经超过6秒没有操作
            val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
            if (timeSinceLastInteraction >= CONTROLS_AUTO_HIDE_MS && !blockingOverlayDialogsOpen) {
                // 超过6秒没有操作，隐藏控制栏
                showControls = false
                controlsMode = null
                requestRootFocusRestoreToken++
                debugLog("auto-hide controls -> restoreRootFocus token=$requestRootFocusRestoreToken")
            }
        }
    }
    
    // 当控制栏显示时，根据模式处理焦点
    LaunchedEffect(showControls, controlsMode, requestPlayPauseFocusToken, requestProgressFocusToken) {
        debugLog(
            "focus-dispatch showControls=$showControls mode=$controlsMode " +
                "navInitial=$navInitialFocusZone reqPlay=$requestPlayPauseFocusToken reqProgress=$requestProgressFocusToken"
        )
        if (showControls) {
            delay(100) // 等待UI渲染
            when (controlsMode) {
                ControlsMode.NAV -> {
                    // 导航模式：按入口方向把焦点落到对应区域
                    when (navInitialFocusZone) {
                        ControlFocusZone.TOP_BAR,
                        ControlFocusZone.PROGRESS -> progressBarFocusRequester.requestFocus()
                        else -> playPauseFocusRequester.requestFocus()
                    }
                }
                ControlsMode.SEEK_OVERLAY -> {
                    // 快进退反馈态：不主动抢焦点，保留当前焦点
                }
                null -> {
                    // 默认情况
                    focusRequester.requestFocus()
                }
            }
        }
    }
    
    // 控制栏隐藏后，先清空旧焦点，再在退出动画后把焦点收回根容器
    LaunchedEffect(requestRootFocusRestoreToken, blockingOverlayDialogsOpen) {
        if (requestRootFocusRestoreToken > 0 && !blockingOverlayDialogsOpen) {
            debugLog("restore-root-focus token=$requestRootFocusRestoreToken clearFocus(force=true)")
            focusManager.clearFocus(force = true)
            // 控制栏隐藏时已经直接移除，不需要再等待淡出动画
            delay(16)
            focusRequester.requestFocus()
            debugLog("restore-root-focus requested to root box")
        }
    }
    
    LaunchedEffect(showControls, controlsMode, focusedControlZone, blockingOverlayDialogsOpen) {
        if (!showControls && focusedControlZone != ControlFocusZone.NONE) {
            focusedControlZone = ControlFocusZone.NONE
        }
        debugLog(
            "ui-state showControls=$showControls mode=$controlsMode " +
                "focusedZone=$focusedControlZone overlayDialog=$blockingOverlayDialogsOpen"
        )
    }

    // 兜底：控制栏隐藏时，持续保证根容器持有焦点，避免首按先用于“激活焦点”
    LaunchedEffect(showControls, blockingOverlayDialogsOpen, rootHasFocus) {
        if (!showControls && !blockingOverlayDialogsOpen && !rootHasFocus) {
            focusManager.clearFocus(force = true)
            focusRequester.requestFocus()
            debugLog("focus-guard request root focus because controls hidden")
        }
    }

    // 双击返回键处理
    var backPressedOnce by remember { mutableStateOf(false) }
    var backPressJob by remember { mutableStateOf<Job?>(null) }
    
    BackHandler {
        if (showEpisodeListDialog) {
            showEpisodeListDialog = false
        } else if (showSpeedDialog) {
            showSpeedDialog = false
        } else if (showSkipConfigDialog) {
            showSkipConfigDialog = false
        } else if (showControls) {
            showControls = false
            controlsMode = null
            requestRootFocusRestoreToken++
            debugLog("BackHandler hide controls -> restoreRootFocus token=$requestRootFocusRestoreToken")
        } else {
            if (backPressedOnce) {
                // 第二次按返回键，退出播放
                backPressJob?.cancel()
                exitPlayback()
            } else {
                // 第一次按返回键，显示提示
                backPressedOnce = true
                // 显示提示（使用Toast）
                android.widget.Toast.makeText(
                    context,
                    "再按一次返回键退出播放",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // 2秒后重置状态
                backPressJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        }
    }
    
    fun handleRemoteKeyDown(keyCode: Int, source: String): Boolean {
        debugLog(
            "key-down source=$source code=$keyCode showControls=$showControls " +
                "mode=$controlsMode zone=$focusedControlZone episodeDialog=$showEpisodeListDialog"
        )
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // controlsMode 初始为 null 时，焦点可能已在进度条/底部 pill，必须把 OK 交给焦点控件，
                // 否则会误触发暂停并把焦点强制拉回播放键（例如首次点底部功能按钮）
                val passOkToFocusedWidget = showControls && (
                    controlsMode == ControlsMode.NAV ||
                        (controlsMode == null && focusedControlZone != ControlFocusZone.NONE)
                    )
                if (passOkToFocusedWidget) {
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("pass-through key=OK/ENTER reason=letFocusedControlHandleClick source=$source")
                    false
                } else {
                    showControls = true
                    controlsMode = ControlsMode.NAV
                    navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                    requestPlayPauseFocusToken++
                    updateInteractionTime { lastInteractionTime = it }
                    viewModel.togglePlayPause()
                    debugLog("consume key=OK/ENTER action=togglePlayPause+showControls source=$source")
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (showControls &&
                    (focusedControlZone == ControlFocusZone.TOP_BAR ||
                        focusedControlZone == ControlFocusZone.BOTTOM_BUTTONS)
                ) {
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("pass-through key=LEFT reason=topOrBottomButtonZone source=$source")
                    false
                } else {
                    showControls = true
                    controlsMode = ControlsMode.SEEK_OVERLAY
                    updateInteractionTime { lastInteractionTime = it }
                    viewModel.seekBackward(seekDurationSeconds * 1000L)
                    debugLog("consume key=LEFT action=seekBackward source=$source")
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (showControls &&
                    (focusedControlZone == ControlFocusZone.TOP_BAR ||
                        focusedControlZone == ControlFocusZone.BOTTOM_BUTTONS)
                ) {
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("pass-through key=RIGHT reason=topOrBottomButtonZone source=$source")
                    false
                } else {
                    showControls = true
                    controlsMode = ControlsMode.SEEK_OVERLAY
                    updateInteractionTime { lastInteractionTime = it }
                    viewModel.seekForward(seekDurationSeconds * 1000L)
                    debugLog("consume key=RIGHT action=seekForward source=$source")
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!showControls) {
                    debugLog("consume key=UP action=blockedWhenControlsHidden source=$source")
                    true
                } else if (controlsMode == ControlsMode.SEEK_OVERLAY) {
                    controlsMode = ControlsMode.NAV
                    navInitialFocusZone = ControlFocusZone.PROGRESS
                    requestProgressFocusToken++
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("consume key=UP action=switchToNAV+focusProgress source=$source")
                    true
                } else {
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("pass-through key=UP reason=systemFocusMove source=$source")
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!showControls) {
                    showControls = true
                    controlsMode = ControlsMode.NAV
                    navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                    requestPlayPauseFocusToken++
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("consume key=DOWN action=showControls+focusPlayPause source=$source")
                    true
                } else if (controlsMode == ControlsMode.SEEK_OVERLAY) {
                    controlsMode = ControlsMode.NAV
                    navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                    requestPlayPauseFocusToken++
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("consume key=DOWN action=seekOverlayToNAV+focusPlayPause source=$source")
                    true
                } else {
                    updateInteractionTime { lastInteractionTime = it }
                    debugLog("pass-through key=DOWN reason=systemFocusMove source=$source")
                    false
                }
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_INFO -> {
                showControls = true
                controlsMode = ControlsMode.NAV
                navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                requestPlayPauseFocusToken++
                updateInteractionTime { lastInteractionTime = it }
                debugLog("consume key=MENU_GROUP action=showControls+focusPlayPause source=$source")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                showControls = true
                controlsMode = ControlsMode.NAV
                navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                requestPlayPauseFocusToken++
                updateInteractionTime { lastInteractionTime = it }
                viewModel.togglePlayPause()
                debugLog("consume key=MEDIA_PLAY_PAUSE action=togglePlayPause source=$source")
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                showControls = true
                controlsMode = ControlsMode.SEEK_OVERLAY
                updateInteractionTime { lastInteractionTime = it }
                viewModel.seekBackward(seekDurationSeconds * 1000L)
                debugLog("consume key=MEDIA_REWIND action=seekBackward source=$source")
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                showControls = true
                controlsMode = ControlsMode.SEEK_OVERLAY
                updateInteractionTime { lastInteractionTime = it }
                viewModel.seekForward(seekDurationSeconds * 1000L)
                debugLog("consume key=MEDIA_FAST_FORWARD action=seekForward source=$source")
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (showEpisodeListDialog) {
                    showEpisodeListDialog = false
                    debugLog("consume key=BACK action=closeEpisodeDialog source=$source")
                    true
                } else if (showSpeedDialog) {
                    showSpeedDialog = false
                    debugLog("consume key=BACK action=closeSpeedDialog source=$source")
                    true
                } else if (showSkipConfigDialog) {
                    showSkipConfigDialog = false
                    debugLog("consume key=BACK action=closeSkipDialog source=$source")
                    true
                } else if (showControls) {
                    showControls = false
                    controlsMode = null
                    requestRootFocusRestoreToken++
                    debugLog("consume key=BACK action=hideControls+restoreRootFocus source=$source")
                    true
                } else {
                    debugLog("pass-through key=BACK reason=delegateToBackHandler source=$source")
                    false
                }
            }
            else -> {
                debugLog("pass-through key=$keyCode reason=notHandled source=$source")
                false
            }
        }
    }
    
    DisposableEffect(onRegisterGlobalKeyHandler) {
        onRegisterGlobalKeyHandler { nativeEvent ->
            if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@onRegisterGlobalKeyHandler false
            handleRemoteKeyDown(nativeEvent.keyCode, source = "activity")
        }
        onDispose {
            onRegisterGlobalKeyHandler(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusProperties {
                // 控制栏显示时，根容器不参与焦点竞争，避免首按先落在根节点
                canFocus = !showControls
            }
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                rootHasFocus = focusState.hasFocus
                debugLog("root-focus hasFocus=${focusState.hasFocus}")
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                handleRemoteKeyDown(event.nativeKeyEvent.keyCode, source = "compose")
            }
    ) {
        val revealControlsFromPointer: () -> Unit = {
            if (!blockingOverlayDialogsOpen) {
                showControls = true
                controlsMode = ControlsMode.NAV
                navInitialFocusZone = ControlFocusZone.BOTTOM_BUTTONS
                requestPlayPauseFocusToken++
                updateInteractionTime { lastInteractionTime = it }
            }
        }

        // 视频播放器
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx)
                    .inflate(com.lemon.yingshi.tv.R.layout.player_view_texture, null) as PlayerView)
                    .apply {
                    // 让按键焦点留在 Compose 根容器，避免 PlayerView 抢焦点造成“首按仅激活”
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // 保持屏幕常亮
                    setKeepScreenOn(true)
                    applyTransparentSubtitleStyle()
                    // 设置播放器
                    player = viewModel.getPlayer()
                    viewModel.setPlayerViewForCoverCapture(this)
                    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                        debugLog("PlayerView focusChanged hasFocus=$hasFocus")
                    }
                    setOnClickListener {
                        debugLog("PlayerView onClick -> reveal controls")
                        // 原生 View 点击链路，兼容模拟器鼠标左键点击
                        revealControlsFromPointer()
                    }
                    setOnTouchListener { _, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_UP) {
                            debugLog("PlayerView touchUp -> reveal controls")
                            revealControlsFromPointer()
                            true
                        } else {
                            false
                        }
                    }
                    setOnGenericMotionListener { _, motionEvent ->
                        val isPrimaryMousePress =
                            motionEvent.action == MotionEvent.ACTION_BUTTON_PRESS &&
                                (motionEvent.buttonState and MotionEvent.BUTTON_PRIMARY) != 0
                        if (isPrimaryMousePress) {
                            debugLog("PlayerView primaryMousePress -> reveal controls")
                            revealControlsFromPointer()
                            true
                        } else {
                            false
                        }
                    }
                    android.util.Log.d("PlayerScreen", "PlayerView created, player=${player}")
                }
            },
            update = { playerView ->
                viewModel.setPlayerViewForCoverCapture(playerView)
                val currentPlayer = viewModel.getPlayer()
                if (playerView.player != currentPlayer) {
                    android.util.Log.d("PlayerScreen", "Updating PlayerView player: ${playerView.player} -> $currentPlayer")
                    playerView.player = currentPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(Unit) {
            onDispose {
                viewModel.releasePlayer()
                viewModel.setPlayerViewForCoverCapture(null)
            }
        }

        // 顶部信息栏（隐藏时直接移除，避免不可见控件残留焦点）
        if (showControls) {
            PlayerTopBar(
                title = currentTitle ?: "",
                episodeTitle = currentEpisodeTitle,
                onBackPressed = exitPlayback,
                backButtonFocusRequester = backButtonFocusRequester,
                progressBarFocusRequester = progressBarFocusRequester,
                onBackButtonFocusChanged = { isFocused ->
                    if (!showControls) {
                        focusedControlZone = ControlFocusZone.NONE
                    } else if (isFocused) {
                        focusedControlZone = ControlFocusZone.TOP_BAR
                    } else if (focusedControlZone == ControlFocusZone.TOP_BAR) {
                        focusedControlZone = ControlFocusZone.NONE
                    }
                }
            )
        }

        // 中央播放/暂停按钮（当暂停时显示）
        if (!playerState.isPlaying && playerState.type == PlayerState.Type.READY) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { viewModel.play() },
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        pressedContainerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        focusedContentColor = PrimaryYellow
                    ),
                    modifier = Modifier
                        .size(80.dp)
                        .focusProperties {
                            // 控制栏隐藏时，避免该按钮抢焦点显示成白色圆形
                            canFocus = showControls
                        }
                        .mousePrimaryClick { viewModel.play() }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }

        // 加载指示器（解析分享页 / 缓冲）
        if (isLoadingMedia || playerState.type == PlayerState.Type.BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoadingMedia) "正在解析播放地址..." else "加载中...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }

        // 底部控制栏（隐藏时直接移除，避免不可见控件残留焦点）
        if (showControls) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                PlayerControls(
                    playerState = playerState,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSeekTo = { position -> viewModel.seekTo(position) },
                    onSeekBackward = { viewModel.seekBackward() },
                    onSeekForward = { viewModel.seekForward() },
                    onSkipPrevious = { viewModel.seekToPrevious() },
                    onSkipNext = { viewModel.seekToNext() },
                    onUpdateInteractionTime = { lastInteractionTime = System.currentTimeMillis() },
                    playPauseFocusRequester = playPauseFocusRequester,
                    backButtonFocusRequester = backButtonFocusRequester,
                    progressBarFocusRequester = progressBarFocusRequester,
                    isLocked = false,
                    forceProgressBubbleHighlight = controlsMode == ControlsMode.SEEK_OVERLAY,
                    onProgressFocusChanged = { isFocused ->
                        if (!showControls) {
                            focusedControlZone = ControlFocusZone.NONE
                        } else if (isFocused) {
                            focusedControlZone = ControlFocusZone.PROGRESS
                        } else if (focusedControlZone == ControlFocusZone.PROGRESS) {
                            focusedControlZone = ControlFocusZone.NONE
                        }
                    },
                    onBottomButtonFocusChanged = { isFocused ->
                        if (!showControls) {
                            focusedControlZone = ControlFocusZone.NONE
                        } else if (isFocused) {
                            focusedControlZone = ControlFocusZone.BOTTOM_BUTTONS
                        } else if (focusedControlZone == ControlFocusZone.BOTTOM_BUTTONS) {
                            focusedControlZone = ControlFocusZone.NONE
                        }
                    },
                    currentSpeed = currentSpeed.value,
                    onShowSpeedDialog = { showSpeedDialog = true },
                    onShowEpisodeList = {
                        showEpisodeListDialog = true
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    hasEpisodes = episodeList.isNotEmpty(),
                    onShowSkipConfigDialog = { showSkipConfigDialog = true }
                )
            }
        }

        // 续播提示弹窗：继续/从头（不影响后台播放）
        if (showResumePrompt && startPosition > 0L) {
            ResumeOrRestartPromptDialog(
                startPositionMs = startPosition,
                onContinue = { showResumePrompt = false },
                onRestart = {
                    showResumePrompt = false
                    viewModel.restartFromBeginning()
                }
            )
        }

        // 选集窗口（Dialog 隔离焦点，避免光标跑到播放器控件）
        if (showEpisodeListDialog && episodeList.isNotEmpty()) {
            EpisodeListPanel(
                episodes = episodeList,
                currentEpisodeId = sessionEpisodeId,
                onDismiss = { showEpisodeListDialog = false },
                onSelectEpisode = { episode ->
                    showEpisodeListDialog = false
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.saveWatchProgressBeforeEpisodeSelectionSwitch()
                        val resumePositionMs = viewModel.getResumeStartPositionForEpisodeSelection(episode.id)
                        val resolvedUrl = viewModel.resolvePlaybackUrl(episode.path ?: "")
                        val newEpisodeTitle = if (episode.title.isNotEmpty()) {
                            "第${episode.episodeNumber}集 ${episode.title}"
                        } else {
                            "第${episode.episodeNumber}集"
                        }
                        viewModel.setMediaInfo(mediaId ?: "", episode.id)
                        sessionEpisodeId = episode.id
                        currentEpisodeTitle = newEpisodeTitle
                        viewModel.prepareMedia(resolvedUrl, currentTitle, newEpisodeTitle, resumePositionMs)
                    }
                },
                onUserInteraction = { lastInteractionTime = System.currentTimeMillis() }
            )
        }
        
        // 倍速选择对话框
        if (showSpeedDialog) {
            SpeedSelectionDialog(
                speeds = availableSpeeds,
                currentSpeed = currentSpeed.value,
                onDismiss = { showSpeedDialog = false },
                onSelect = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                    currentSpeed.value = speed
                    showSpeedDialog = false
                }
            )
        }
        
        // 跳过片头片尾设置对话框
        if (showSkipConfigDialog) {
            // 如果没有配置，创建一个初始值为0的配置
            val configToShow = skipConfig ?: com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity(
                mediaId = mediaId ?: "",
                seasonNumber = 0,
                introDuration = 0L,  // 新剧集默认片头为0
                outroDuration = 0L,  // 新剧集默认片尾为0
                skipIntroEnabled = true,
                skipOutroEnabled = true
            )
            SkipConfigDialog(
                show = showSkipConfigDialog,
                config = configToShow,
                scopeTitle = currentTitle ?: title ?: "",
                currentPosition = playerState.currentPosition,
                duration = playerState.duration,
                onDismiss = { showSkipConfigDialog = false },
                onSave = { config ->
                    viewModel.saveSkipConfig(config)
                    showSkipConfigDialog = false
                },
                onReset = {
                    viewModel.resetSkipConfigToDefault()
                },
                onSetCurrentAsIntroEnd = {
                    viewModel.setCurrentPositionAsIntroEnd()
                },
                onSetCurrentAsOutroStart = {
                    viewModel.setCurrentPositionAsOutroStart()
                }
            )
        }
        
        // 错误对话框
        if (showErrorDialog) {
            ErrorDialog(
                errorMessage = errorMessage,
                onDismiss = {
                    showErrorDialog = false
                    viewModel.clearError()
                },
                onRetry = {
                    showErrorDialog = false
                    viewModel.clearError()
                    viewModel.initializePlayer()
                    viewModel.prepareMedia(videoUrl, title, episodeTitle, startPosition)
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ResumeOrRestartPromptDialog(
    startPositionMs: Long,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ResumeOrRestartPromptContent(
                startPositionMs = startPositionMs,
                onContinue = onContinue,
                onRestart = onRestart,
                onClose = onContinue
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ResumeOrRestartPromptContent(
    startPositionMs: Long,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continueFocus = remember { FocusRequester() }
    val restartFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var continueFocused by remember { mutableStateOf(false) }
    var restartFocused by remember { mutableStateOf(false) }
    var closeFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        continueFocus.requestFocus()
    }

    Card(
        onClick = {}, // 仅用于承载样式；焦点由内部按钮接管
        colors = CardDefaults.colors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
        modifier = modifier
            .width(520.dp)
            .onPreviewKeyEvent { keyEvent ->
                // 锁定焦点在窗口内（方向键不外溢）
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionUp,
                    Key.DirectionDown -> true
                    else -> false
                }
            }
            .focusProperties {
                // 卡片本身不抢焦点，避免“焦点跑不见”
                canFocus = false
                exit = { FocusRequester.Cancel }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "检测到播放记录：${formatDuration(startPositionMs)}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .focusRequester(continueFocus)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = restartFocus
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .mousePrimaryClick { onContinue() }
                        .onFocusChanged { continueFocused = it.isFocused }
                ) {
                    Text(
                        text = "继续播放",
                        color = if (continueFocused) Color.Black else TextPrimary
                    )
                }
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .focusRequester(restartFocus)
                        .focusProperties {
                            left = continueFocus
                            right = closeFocus
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .mousePrimaryClick { onRestart() }
                        .onFocusChanged { restartFocused = it.isFocused }
                ) {
                    Text(
                        text = "从头开始",
                        color = if (restartFocused) Color.Black else TextPrimary
                    )
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .focusRequester(closeFocus)
                        .focusProperties {
                            left = restartFocus
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .mousePrimaryClick { onClose() }
                        .onFocusChanged { closeFocused = it.isFocused }
                ) {
                    Text(
                        text = "关闭",
                        color = if (closeFocused) Color.Black else TextPrimary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "8秒后自动关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

// 更新最后交互时间，用于自动隐藏控制栏计时
private fun updateInteractionTime(setTime: (Long) -> Unit) {
    setTime(System.currentTimeMillis())
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.mousePrimaryClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.pointerInteropFilter { motionEvent ->
    if (!enabled) return@pointerInteropFilter false
    when (motionEvent.action) {
        MotionEvent.ACTION_DOWN -> true
        MotionEvent.ACTION_UP -> {
            onClick()
            true
        }
        else -> false
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    title: String,
    episodeTitle: String?,
    onBackPressed: () -> Unit,
    backButtonFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester,
    onBackButtonFocusChanged: (Boolean) -> Unit
) {
    // 获取当前时间
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000) // 每分钟更新
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 左侧：返回按钮 + 标题信息
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isBackFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onBackPressed,
                    colors = IconButtonDefaults.colors(
                        containerColor = SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .focusRequester(backButtonFocusRequester)
                        .focusProperties {
                            down = progressBarFocusRequester
                        }
                        .mousePrimaryClick { onBackPressed() }
                        .onFocusChanged {
                            isBackFocused = it.isFocused
                            onBackButtonFocusChanged(it.isFocused)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isBackFocused) Color.Black else TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    // 主标题 + 集数标题
                    // 添加调试日志
                    LaunchedEffect(title, episodeTitle) {
                        android.util.Log.d("PlayerScreen", "Title: $title, EpisodeTitle: $episodeTitle")
                    }
                    val displayTitle = if (episodeTitle != null && episodeTitle.isNotBlank()) {
                        "$title $episodeTitle"
                    } else {
                        title ?: ""
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )

                    // "正在播放"标签
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = PrimaryYellow,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // 右侧：当前时间
            Text(
                text = currentTime,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun PlayerControls(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onUpdateInteractionTime: () -> Unit,
    playPauseFocusRequester: FocusRequester,
    backButtonFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester,
    isLocked: Boolean = false,
    forceProgressBubbleHighlight: Boolean = false,
    onProgressFocusChanged: (Boolean) -> Unit = {},
    onBottomButtonFocusChanged: (Boolean) -> Unit = {},
    currentSpeed: Float = 1.0f,
    onShowSpeedDialog: () -> Unit = {},
    onShowEpisodeList: () -> Unit = {},
    hasEpisodes: Boolean = false,
    onShowSkipConfigDialog: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .onFocusChanged { if (it.hasFocus) onUpdateInteractionTime() }
    ) {
        // 进度条区域（带时间提示气泡）
        val progress = if (playerState.duration > 0) {
            playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        } else 0f
        val bufferedProgress = if (playerState.duration > 0) {
            val prefetchEstimateMs = (playerState.prefetchProgress * playerState.duration).toLong()
            val bufferedMs = maxOf(playerState.bufferedPosition, prefetchEstimateMs)
            bufferedMs.toFloat() / playerState.duration.toFloat()
        } else {
            playerState.prefetchProgress
        }
        val progressInteractionSource = remember { MutableInteractionSource() }
        val isProgressFocused = progressInteractionSource.collectIsFocusedAsState().value
        val bubbleHighlighted = isProgressFocused || forceProgressBubbleHighlight
        val bubbleScale by animateFloatAsState(
            targetValue = if (bubbleHighlighted) PROGRESS_BUBBLE_FOCUS_SCALE else PROGRESS_BUBBLE_IDLE_SCALE,
            animationSpec = tween(durationMillis = FOCUS_ANIMATION_DURATION_MS),
            label = "progressBubbleScale"
        )
        val bubbleGlow by animateDpAsState(
            targetValue = if (bubbleHighlighted) PROGRESS_BUBBLE_FOCUS_GLOW else PROGRESS_BUBBLE_IDLE_GLOW,
            animationSpec = tween(durationMillis = FOCUS_ANIMATION_DURATION_MS),
            label = "progressBubbleGlow"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // 时间提示气泡 - 跟随播放进度并在两端做边界保护
            val density = androidx.compose.ui.platform.LocalDensity.current
            var bubbleWidthDp by remember { mutableStateOf(PROGRESS_BUBBLE_MIN_WIDTH) }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val safeProgress = progress.coerceIn(0f, 1f)
                val effectiveBubbleWidth = bubbleWidthDp.coerceIn(PROGRESS_BUBBLE_MIN_WIDTH, PROGRESS_BUBBLE_MAX_WIDTH)
                val maxBubbleX = (maxWidth - effectiveBubbleWidth).coerceAtLeast(0.dp)
                val bubbleX = ((maxWidth * safeProgress) - (effectiveBubbleWidth / 2)).coerceIn(0.dp, maxBubbleX)

                Box(
                    modifier = Modifier
                        .offset(
                            x = bubbleX,
                            y = (-8).dp
                        )
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                        }
                        .shadow(
                            elevation = bubbleGlow,
                            shape = RoundedCornerShape(6.dp),
                            ambientColor = if (bubbleHighlighted) Color.White.copy(alpha = 0.95f) else PrimaryYellow.copy(alpha = 0.55f),
                            spotColor = if (bubbleHighlighted) PrimaryYellow.copy(alpha = 0.98f) else PrimaryYellow.copy(alpha = 0.75f)
                        )
                        .widthIn(min = 0.dp, max = PROGRESS_BUBBLE_MAX_WIDTH)
                        .onSizeChanged { size ->
                            bubbleWidthDp = with(density) { size.width.toDp() }
                        }
                        .background(
                            color = if (bubbleHighlighted) Color(0xFFFFF176) else PrimaryYellow,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${formatDuration(playerState.currentPosition)} / ${formatDuration(playerState.duration)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 进度条（支持鼠标点击/拖动跳转）
        var progressBarWidthPx by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_BAR_HEIGHT)
                .focusRequester(progressBarFocusRequester)
                .focusProperties {
                    up = backButtonFocusRequester
                    down = playPauseFocusRequester
                }
                .focusable(interactionSource = progressInteractionSource)
                .onFocusChanged {
                    onProgressFocusChanged(it.hasFocus)
                    if (it.hasFocus) onUpdateInteractionTime()
                }
                .onSizeChanged { size ->
                    progressBarWidthPx = size.width.toFloat()
                }
                .pointerInteropFilter { motionEvent ->
                    if (playerState.duration <= 0 || progressBarWidthPx <= 0f) {
                        return@pointerInteropFilter false
                    }
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_UP -> {
                            val seekProgress = (motionEvent.x / progressBarWidthPx).coerceIn(0f, 1f)
                            onSeekTo((playerState.duration * seekProgress).toLong())
                            onUpdateInteractionTime()
                            true
                        }
                        else -> false
                    }
                }
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
        ) {
            // 预缓存/可播放缓冲（浅灰，在播放进度下层）
            if (bufferedProgress > progress) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                        .height(PROGRESS_BAR_HEIGHT)
                        .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                )
            }
            // 已播放进度（黄色）
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(PROGRESS_BAR_HEIGHT)
                    .background(PrimaryYellow, RoundedCornerShape(3.dp))
            )
        }

        if (playerState.prefetchActive && playerState.prefetchTotalSegments > 0) {
            Text(
                text = "预缓存 ${playerState.prefetchCompletedSegments}/${playerState.prefetchTotalSegments}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(if (playerState.prefetchActive) 12.dp else 24.dp))

        // 控制按钮栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：播放控制 + 时间显示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 上一集/播放/下一集按钮组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 上一集
                    var isSkipPreviousFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (!isLocked) {
                                onSkipPrevious()
                                onUpdateInteractionTime()
                            }
                        },
                        enabled = !isLocked,
                        colors = IconButtonDefaults.colors(
                            containerColor = if (isLocked) Color.White.copy(alpha = 0.05f) else SurfaceDark,
                            focusedContainerColor = PrimaryYellow,
                            contentColor = if (isLocked) Color.White.copy(alpha = 0.3f) else TextPrimary,
                            focusedContentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = !isLocked }
                            .mousePrimaryClick(enabled = !isLocked) {
                                onSkipPrevious()
                                onUpdateInteractionTime()
                            }
                            .onFocusChanged {
                                isSkipPreviousFocused = it.isFocused
                                onBottomButtonFocusChanged(it.isFocused)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (isLocked) Color.White.copy(alpha = 0.3f) else if (isSkipPreviousFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 播放/暂停按钮
                    var isPlayPauseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (!isLocked) {
                                onPlayPause()
                                onUpdateInteractionTime()
                            }
                        },
                        enabled = !isLocked,
                        colors = IconButtonDefaults.colors(
                            containerColor = if (isLocked) Color.White.copy(alpha = 0.05f) else SurfaceDark,
                            focusedContainerColor = PrimaryYellow,
                            contentColor = if (isLocked) Color.White.copy(alpha = 0.3f) else TextPrimary,
                            focusedContentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(playPauseFocusRequester)
                            .focusProperties {
                                canFocus = !isLocked
                                up = progressBarFocusRequester
                            }
                            .mousePrimaryClick(enabled = !isLocked) {
                                onPlayPause()
                                onUpdateInteractionTime()
                            }
                            .onFocusChanged {
                                isPlayPauseFocused = it.isFocused
                                onBottomButtonFocusChanged(it.isFocused)
                            }
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = if (isLocked) Color.White.copy(alpha = 0.3f) else if (isPlayPauseFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 下一集
                    var isSkipNextFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (!isLocked) {
                                onSkipNext()
                                onUpdateInteractionTime()
                            }
                        },
                        enabled = !isLocked,
                        colors = IconButtonDefaults.colors(
                            containerColor = if (isLocked) Color.White.copy(alpha = 0.05f) else SurfaceDark,
                            focusedContainerColor = PrimaryYellow,
                            contentColor = if (isLocked) Color.White.copy(alpha = 0.3f) else TextPrimary,
                            focusedContentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = !isLocked }
                            .mousePrimaryClick(enabled = !isLocked) {
                                onSkipNext()
                                onUpdateInteractionTime()
                            }
                            .onFocusChanged {
                                isSkipNextFocused = it.isFocused
                                onBottomButtonFocusChanged(it.isFocused)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (isLocked) Color.White.copy(alpha = 0.3f) else if (isSkipNextFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 时间显示
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(playerState.currentPosition),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        text = formatDuration(playerState.duration),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // 右侧：功能按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 倍速按钮
                ControlPillButton(
                    text = "${currentSpeed}X",
                    onClick = { 
                        if (!isLocked) {
                            onShowSpeedDialog()
                            onUpdateInteractionTime()
                        }
                    },
                    isLocked = isLocked,
                    onFocusChanged = onBottomButtonFocusChanged
                )

                ControlPillButton(
                    text = "选集",
                    onClick = {
                        if (!isLocked && hasEpisodes) {
                            onShowEpisodeList()
                            onUpdateInteractionTime()
                        }
                    },
                    isLocked = isLocked || !hasEpisodes,
                    onFocusChanged = onBottomButtonFocusChanged
                )

                // 跳过片头片尾按钮
                ControlPillButton(
                    text = "跳过",
                    onClick = { 
                        if (!isLocked) {
                            onShowSkipConfigDialog()
                            onUpdateInteractionTime()
                        }
                    },
                    isLocked = isLocked,
                    onFocusChanged = onBottomButtonFocusChanged
                )
            }
        }
    }
}

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeListPanel(
    episodes: List<PlayerViewModel.EpisodeListItem>,
    currentEpisodeId: String?,
    onDismiss: () -> Unit,
    onSelectEpisode: (PlayerViewModel.EpisodeListItem) -> Unit,
    onUserInteraction: () -> Unit
) {
    val sortedEpisodes = remember(episodes) { episodes.sortedBy { it.episodeNumber } }
    val currentIndex = sortedEpisodes.indexOfFirst { it.id == currentEpisodeId }
        .let { if (it >= 0) it else 0 }

    val gridColumns = 4
    val episodesPerPage = 20
    val gridSpacing = 8.dp
    val buttonWidth = 72.dp
    val buttonHeight = 40.dp
    val panelPadding = 20.dp
    val totalPages = max(1, (sortedEpisodes.size + episodesPerPage - 1) / episodesPerPage)
    val initialPage = (currentIndex / episodesPerPage).coerceIn(0, totalPages - 1)
    val hasPagination = totalPages > 1

    var currentPage by remember(sortedEpisodes) { mutableIntStateOf(initialPage) }
    val safePage = currentPage.coerceIn(0, totalPages - 1)

    val pageEpisodes = sortedEpisodes
        .drop(safePage * episodesPerPage)
        .take(episodesPerPage)
    val gridRowData = pageEpisodes.chunked(gridColumns)
    val pageStart = safePage * episodesPerPage
    val pageEnd = min(pageStart + episodesPerPage, sortedEpisodes.size)
    val focusIndexInPage = if (currentIndex in pageStart until pageEnd) {
        currentIndex - pageStart
    } else {
        0
    }.coerceIn(0, (pageEpisodes.size - 1).coerceAtLeast(0))

    val cellFocusRequesters = remember(safePage, pageEpisodes.size) {
        List(pageEpisodes.size) { FocusRequester() }
    }
    val prevPageFocusRequester = remember { FocusRequester() }
    val nextPageFocusRequester = remember { FocusRequester() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val initialFocusRequester = cellFocusRequesters.getOrNull(focusIndexInPage) ?: fallbackFocusRequester

    var lastInteractionAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val markInteraction = {
        lastInteractionAt = System.currentTimeMillis()
        onUserInteraction()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            val inactiveFor = System.currentTimeMillis() - lastInteractionAt
            if (inactiveFor >= 5000) {
                onDismiss()
                break
            }
        }
    }

    LaunchedEffect(sortedEpisodes, currentEpisodeId) {
        currentPage = initialPage
    }

    LaunchedEffect(safePage, sortedEpisodes, currentEpisodeId, focusIndexInPage, pageEpisodes.size) {
        if (sortedEpisodes.isNotEmpty() && pageEpisodes.isNotEmpty()) {
            delay(120)
            cellFocusRequesters.getOrNull(focusIndexInPage)?.tryRequestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    exit = { FocusRequester.Cancel }
                }
                .onPreviewKeyEvent { keyEvent ->
                    markInteraction()
                    if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 74.dp)
                    .width(360.dp)
                    .wrapContentHeight()
                    .background(DialogUiTokens.ContainerColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                    .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                    .padding(panelPadding)
                    .focusGroup()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (hasPagination) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "播放列表",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (hasPagination) {
                        Text(
                            text = "${safePage + 1}/$totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }

                if (hasPagination) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                markInteraction()
                                currentPage = (safePage - 1).coerceAtLeast(0)
                            },
                            enabled = safePage > 0,
                            colors = ButtonDefaults.colors(
                                containerColor = SurfaceDark,
                                focusedContainerColor = PrimaryYellow,
                                contentColor = TextPrimary,
                                focusedContentColor = Color.Black,
                                disabledContainerColor = SurfaceDark.copy(alpha = 0.4f),
                                disabledContentColor = TextMuted
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
                            modifier = Modifier
                                .focusRequester(prevPageFocusRequester)
                                .focusProperties {
                                    left = prevPageFocusRequester
                                    right = nextPageFocusRequester
                                    up = prevPageFocusRequester
                                    down = initialFocusRequester
                                }
                        ) {
                            Text(text = "上一页")
                        }

                        Button(
                            onClick = {
                                markInteraction()
                                currentPage = (safePage + 1).coerceAtMost(totalPages - 1)
                            },
                            enabled = safePage < totalPages - 1,
                            colors = ButtonDefaults.colors(
                                containerColor = SurfaceDark,
                                focusedContainerColor = PrimaryYellow,
                                contentColor = TextPrimary,
                                focusedContentColor = Color.Black,
                                disabledContainerColor = SurfaceDark.copy(alpha = 0.4f),
                                disabledContentColor = TextMuted
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
                            modifier = Modifier
                                .focusRequester(nextPageFocusRequester)
                                .focusProperties {
                                    left = prevPageFocusRequester
                                    right = nextPageFocusRequester
                                    up = nextPageFocusRequester
                                    down = initialFocusRequester
                                }
                        ) {
                            Text(text = "下一页")
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    gridRowData.forEachIndexed { rowIndex, rowEpisodes ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                        ) {
                            rowEpisodes.forEachIndexed { colIndex, episode ->
                                val cellIndex = rowIndex * gridColumns + colIndex
                                val isSelected = episode.id == currentEpisodeId
                                val isFirstRow = rowIndex == 0
                                val isLastRow = rowIndex == gridRowData.lastIndex
                                val isFirstCol = colIndex == 0
                                val isLastCol = colIndex == rowEpisodes.lastIndex
                                val cellFocusRequester = cellFocusRequesters[cellIndex]
                                val upTarget = when {
                                    !isFirstRow -> {
                                        val upIndex = cellIndex - gridColumns
                                        if (upIndex in cellFocusRequesters.indices) {
                                            cellFocusRequesters[upIndex]
                                        } else {
                                            cellFocusRequester
                                        }
                                    }
                                    hasPagination -> prevPageFocusRequester
                                    else -> cellFocusRequester
                                }
                                val downTarget = if (!isLastRow) {
                                    val downIndex = cellIndex + gridColumns
                                    if (downIndex in cellFocusRequesters.indices) {
                                        cellFocusRequesters[downIndex]
                                    } else {
                                        cellFocusRequester
                                    }
                                } else {
                                    cellFocusRequester
                                }

                                EpisodeListPageButton(
                                    episodeNumber = episode.episodeNumber,
                                    isSelected = isSelected,
                                    buttonWidth = buttonWidth,
                                    buttonHeight = buttonHeight,
                                    modifier = Modifier
                                        .focusRequester(cellFocusRequester)
                                        .focusProperties {
                                            left = if (isFirstCol) cellFocusRequester else cellFocusRequesters[cellIndex - 1]
                                            right = if (isLastCol) cellFocusRequester else cellFocusRequesters[cellIndex + 1]
                                            up = upTarget
                                            down = downTarget
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> isFirstCol
                                                Key.DirectionRight -> isLastCol
                                                Key.DirectionUp -> isFirstRow && !hasPagination
                                                Key.DirectionDown -> isLastRow
                                                else -> false
                                            }
                                        },
                                    onFocus = markInteraction,
                                    onClick = {
                                        markInteraction()
                                        onSelectEpisode(episode)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeListPageButton(
    episodeNumber: Int,
    isSelected: Boolean,
    buttonWidth: Dp,
    buttonHeight: Dp,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val highlighted = isSelected || isFocused

    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) PrimaryYellow else SurfaceDark.copy(alpha = 0.6f),
            contentColor = if (isSelected) BackgroundDark else TextPrimary,
            focusedContainerColor = PrimaryYellow,
            focusedContentColor = BackgroundDark,
            pressedContainerColor = PrimaryYellow,
            pressedContentColor = BackgroundDark
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier
            .size(width = buttonWidth, height = buttonHeight)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocus()
            }
            .mousePrimaryClick { onClick() }
    ) {
        Text(
            text = episodeNumber.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (highlighted) BackgroundDark else TextPrimary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlPillButton(
    text: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    isLocked: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        enabled = !isLocked,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = if (isLocked) Color.White.copy(alpha = 0.05f) else SurfaceDark,
            focusedContainerColor = PrimaryYellow,
            contentColor = if (isLocked) Color.White.copy(alpha = 0.3f) else TextPrimary,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            disabledContentColor = Color.White.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusProperties { canFocus = !isLocked }
            .mousePrimaryClick(enabled = !isLocked) { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            color = if (isLocked) Color.White.copy(alpha = 0.3f) else if (isFocused) Color.Black else TextPrimary,
            maxLines = 1
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"

    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// 倍速选择对话框
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SpeedSelectionDialog(
    speeds: List<Float>,
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit
) {
    val focusRequesters = remember(speeds.size) { List(speeds.size) { FocusRequester() } }
    val targetIndex = speeds.indexOfFirst { it == currentSpeed }.let { if (it >= 0) it else 0 }
    LaunchedEffect(speeds.size, currentSpeed) {
        if (speeds.isNotEmpty()) {
            delay(100)
            focusRequesters[targetIndex].requestFocus()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(DialogUiTokens.ContainerColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .padding(24.dp)
        ) {
            Text(
                text = "选择播放倍速",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            speeds.forEachIndexed { index, speed ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = speed == currentSpeed
                Button(
                    onClick = { onSelect(speed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(focusRequesters[index])
                        .mousePrimaryClick { onSelect(speed) }
                        .onFocusChanged { isFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "${speed}X",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFocused) Color.Black else TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        // 自动聚焦到重试按钮
        delay(100)
        retryFocusRequester.requestFocus()
    }
    
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .width(500.dp)
                .wrapContentHeight()
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .focusable()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 错误图标
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 标题
                Text(
                    text = "播放错误",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 错误信息
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
                
                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    // 重试按钮
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .width(120.dp)
                            .focusRequester(retryFocusRequester)
                            .mousePrimaryClick { onRetry() },
                        colors = ButtonDefaults.colors(
                            containerColor = PrimaryYellow,
                            contentColor = Color.Black,
                            focusedContainerColor = PrimaryYellow.copy(alpha = 0.8f),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "重试",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(120.dp)
                            .focusRequester(focusRequester)
                            .mousePrimaryClick { onDismiss() },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Gray.copy(alpha = 0.3f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.Gray.copy(alpha = 0.5f),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
