package com.lomen.tv.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.lomen.tv.domain.model.ResourceLibrary
import java.util.concurrent.TimeUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.Icon
import com.lomen.tv.data.preferences.TmdbApiPreferences
import com.lomen.tv.ui.components.InfoPillToast
import com.lomen.tv.R
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lomen.tv.ui.theme.BackgroundDark
import com.lomen.tv.ui.theme.GlassBackground
import com.lomen.tv.ui.theme.PrimaryYellow
import com.lomen.tv.ui.theme.DialogUiTokens
import com.lomen.tv.ui.theme.SurfaceDark
import com.lomen.tv.ui.theme.TextMuted
import com.lomen.tv.ui.theme.TextPrimary
import com.lomen.tv.ui.theme.TextSecondary
import com.lomen.tv.ui.DialogDimens
import com.lomen.tv.ui.screens.settings.TmdbApiSettingsDialog
import com.lomen.tv.ui.viewmodel.VersionUpdateViewModel
import com.lomen.tv.ui.components.VersionUpdateBadge
import android.view.MotionEvent


private const val HOME_SECTION_MAX_ITEMS = 10

private fun com.lomen.tv.data.local.database.entity.WebDavMediaEntity.activityAt(): Long =
    maxOf(createdAt, updatedAt)

private fun com.lomen.tv.ui.viewmodel.TvShowSeries.activityAt(): Long {
    val latestCreated = episodes.maxOfOrNull { it.createdAt } ?: 0L
    val latestUpdated = episodes.maxOfOrNull { it.updatedAt } ?: 0L
    return maxOf(latestCreated, latestUpdated)
}

// 首页排序：优先按最近刮削更新时间(updatedAt)，并用 createdAt / id 打破并列。
private fun List<com.lomen.tv.data.local.database.entity.WebDavMediaEntity>.sortedMediaForHome(): List<com.lomen.tv.data.local.database.entity.WebDavMediaEntity> =
    sortedWith(
        compareByDescending<com.lomen.tv.data.local.database.entity.WebDavMediaEntity> { it.updatedAt }
            .thenByDescending { it.createdAt }
            .thenByDescending { it.id }
    )

private fun com.lomen.tv.ui.viewmodel.TvShowSeries.latestUpdatedAt(): Long =
    episodes.maxOfOrNull { it.updatedAt } ?: 0L

private fun com.lomen.tv.ui.viewmodel.TvShowSeries.latestCreatedAt(): Long =
    episodes.maxOfOrNull { it.createdAt } ?: 0L

private fun List<com.lomen.tv.ui.viewmodel.TvShowSeries>.sortedSeriesForHome(): List<com.lomen.tv.ui.viewmodel.TvShowSeries> =
    sortedWith(
        compareByDescending<com.lomen.tv.ui.viewmodel.TvShowSeries> { it.latestUpdatedAt() }
            .thenByDescending { it.latestCreatedAt() }
            .thenByDescending { it.id }
    )

private fun FocusRequester.tryRequestFocus(): Boolean {
    return runCatching {
        requestFocus()
        true
    }.getOrElse { false }
}

private fun requestFirstAvailableFocus(vararg requesters: FocusRequester?): Boolean {
    requesters.forEach { requester ->
        if (requester != null && requester.tryRequestFocus()) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.mousePrimaryClick(onClick: () -> Unit): Modifier =
    this.pointerInteropFilter { motionEvent ->
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
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (com.lomen.tv.domain.model.MediaType) -> Unit = {},
    onNavigateToRecentWatching: () -> Unit = {},
    onNavigateToLive: () -> Unit = {},
    onPlayFromHistory: (com.lomen.tv.domain.service.WatchHistoryItem) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    resourceLibraryViewModel: com.lomen.tv.ui.viewmodel.ResourceLibraryViewModel = hiltViewModel(),
    mediaSyncViewModel: com.lomen.tv.ui.viewmodel.MediaSyncViewModel = hiltViewModel(),
    versionUpdateViewModel: VersionUpdateViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val bottomBarFirstTabFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var focusedColumnIndex by remember { mutableIntStateOf(0) } // 0/1/2 对应第一/二/三列

    // 获取最近播放记录
    val recentWatchHistory by viewModel.recentWatchHistory.collectAsState()
    
    // 获取通知
    val currentNotification by viewModel.currentNotification.collectAsState()
    
    // 每次显示首页时刷新通知
    LaunchedEffect(Unit) {
        android.util.Log.d("HomeScreen", "Refreshing notifications on resume")
        viewModel.refreshNotifications()
    }
    
    // 调试日志 - 通知状态
    LaunchedEffect(currentNotification) {
        android.util.Log.d("HomeScreen", "Current notification: ${currentNotification?.message ?: "null"}")
    }
    
    // 调试日志
    LaunchedEffect(recentWatchHistory) {
        android.util.Log.d("HomeScreen", "Recent watch history updated: ${recentWatchHistory.size} items")
        recentWatchHistory.forEach { item ->
            android.util.Log.d("HomeScreen", "  - ${item.title} (${item.mediaId})")
        }
    }

    // 获取WebDAV媒体数据
    val tvShowsRaw by resourceLibraryViewModel.tvShows.collectAsState()
    val animeRaw by resourceLibraryViewModel.anime.collectAsState()
    val moviesRaw by resourceLibraryViewModel.movies.collectAsState()
    val varietyRaw by resourceLibraryViewModel.variety.collectAsState()
    val concertsRaw by resourceLibraryViewModel.concerts.collectAsState()
    val documentariesRaw by resourceLibraryViewModel.documentaries.collectAsState()
    val othersRaw by resourceLibraryViewModel.others.collectAsState()
    val currentLibrary by resourceLibraryViewModel.currentLibraryId.collectAsState()
    val mediaSortOrder by resourceLibraryViewModel.mediaSortOrder.collectAsState()

    // 首页按“最近刮削优先”排序：第一张始终优先展示最新写入刮削结果。
    val movies = moviesRaw.sortedMediaForHome()
    val variety = varietyRaw.sortedSeriesForHome()
    val concerts = concertsRaw.sortedMediaForHome()
    val documentaries = documentariesRaw.sortedMediaForHome()
    val others = othersRaw.sortedMediaForHome()
    val tvShows = tvShowsRaw.sortedSeriesForHome()
    val anime = animeRaw.sortedSeriesForHome()
    val rowSpecs = remember(
        recentWatchHistory,
        mediaSortOrder,
        tvShows,
        anime,
        movies,
        variety,
        concerts,
        documentaries,
        others
    ) {
        buildList {
            if (recentWatchHistory.isNotEmpty()) {
                val displayCount = recentWatchHistory.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                add(displayCount + if (recentWatchHistory.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
            }
            mediaSortOrder.forEach { mediaType ->
                when (mediaType) {
                    com.lomen.tv.domain.model.MediaType.TV_SHOW -> {
                        if (tvShows.isNotEmpty()) {
                            val displayCount = tvShows.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (tvShows.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.ANIME -> {
                        if (anime.isNotEmpty()) {
                            val displayCount = anime.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (anime.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.MOVIE -> {
                        if (movies.isNotEmpty()) {
                            val displayCount = movies.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (movies.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.VARIETY -> {
                        if (variety.isNotEmpty()) {
                            val displayCount = variety.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (variety.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.CONCERT -> {
                        if (concerts.isNotEmpty()) {
                            val displayCount = concerts.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (concerts.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.DOCUMENTARY -> {
                        if (documentaries.isNotEmpty()) {
                            val displayCount = documentaries.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (documentaries.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.OTHER -> {
                        if (others.isNotEmpty()) {
                            val displayCount = others.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (others.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                }
            }
        }
    }
    val rowFocusRequesters = remember(rowSpecs) {
        rowSpecs.map { itemCount -> List(itemCount) { FocusRequester() } }
    }
    val mediaRowSpecs = remember(mediaSortOrder, tvShows, anime, movies, variety, concerts, documentaries, others) {
        buildList {
            mediaSortOrder.forEach { mediaType ->
                when (mediaType) {
                    com.lomen.tv.domain.model.MediaType.TV_SHOW -> {
                        if (tvShows.isNotEmpty()) {
                            val displayCount = tvShows.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (tvShows.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.ANIME -> {
                        if (anime.isNotEmpty()) {
                            val displayCount = anime.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (anime.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.MOVIE -> {
                        if (movies.isNotEmpty()) {
                            val displayCount = movies.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (movies.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.VARIETY -> {
                        if (variety.isNotEmpty()) {
                            val displayCount = variety.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (variety.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.CONCERT -> {
                        if (concerts.isNotEmpty()) {
                            val displayCount = concerts.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (concerts.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.DOCUMENTARY -> {
                        if (documentaries.isNotEmpty()) {
                            val displayCount = documentaries.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (documentaries.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.OTHER -> {
                        if (others.isNotEmpty()) {
                            val displayCount = others.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                            add(displayCount + if (others.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
                        }
                    }
                }
            }
        }
    }
    val mediaRowFocusRequesters = remember(mediaRowSpecs) {
        mediaRowSpecs.map { itemCount -> List(itemCount) { FocusRequester() } }
    }
    val headerFocusRequesters = remember { List(3) { FocusRequester() } }

    LaunchedEffect(Unit) {
        delay(100)
        headerFocusRequesters[0].tryRequestFocus()
    }

    // 获取同步状态
    val syncState by mediaSyncViewModel.syncState.collectAsState()
    val syncProgress by mediaSyncViewModel.syncProgress.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showTmdbScanPill by remember { mutableStateOf(false) }

    // 刮削过程中媒体列表会频繁流式更新：如果每次更新都强制横向滚动归零，会造成明显闪烁。
    // 这里仅在“进入同步中”时触发一次“横向列表回到起点”的信号。
    val isSyncing =
        syncState is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scanning ||
            syncState is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scraping
    var homeRowResetNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(isSyncing) {
        if (isSyncing) homeRowResetNonce++
    }

    LaunchedEffect(syncState) {
        android.util.Log.d("HomeScreen", "Sync state changed: $syncState, progress: $syncProgress")
        if (syncState is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Error) {
            val msg = (syncState as com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Error).message
            if (msg == TmdbApiPreferences.MSG_TMDB_REQUIRED_FOR_SCAN) {
                showTmdbScanPill = true
            }
            mediaSyncViewModel.resetState()
        }
    }

    // TMDB API 配置检测
    val tmdbApiViewModel: com.lomen.tv.ui.viewmodel.TmdbApiViewModel = hiltViewModel()
    val isTmdbApiConfigured by tmdbApiViewModel.isApiKeyConfigured.collectAsState(initial = true)
    var showTmdbApiRequiredDialog by remember { mutableStateOf(false) }
    var showTmdbApiSettingsDialog by remember { mutableStateOf(false) }
    
    // 版本更新检查
    val hasUpdate by versionUpdateViewModel.hasUpdate.collectAsState()
    val versionInfo by versionUpdateViewModel.versionInfo.collectAsState()
    var showVersionUpdateDialog by remember { mutableStateOf(false) }
    val versionUpdateBadgeFocusRequester = remember { FocusRequester() }
    var showTopVersionBadge by remember(versionInfo?.versionName, hasUpdate) {
        mutableStateOf(true)
    }

    // 启动时检测 TMDB API 配置
    LaunchedEffect(Unit) {
        // 延迟检测，确保界面已加载
        delay(500)
        if (!isTmdbApiConfigured) {
            android.util.Log.d("HomeScreen", "TMDB API not configured, showing required dialog")
            showTmdbApiRequiredDialog = true
        }
        
        // 检查版本更新
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = packageInfo.versionCode
        versionUpdateViewModel.checkForUpdates(currentVersionCode)
    }

    // 双击返回键处理
    var backPressedOnce by remember { mutableStateOf(false) }
    var backPressJob by remember { mutableStateOf<Job?>(null) }

    BackHandler {
        if (backPressedOnce) {
            // 第二次按返回键，退出APP
            backPressJob?.cancel()
            // 退出APP
            (context as? android.app.Activity)?.finish()
        } else {
            // 第一次按返回键，显示提示
            backPressedOnce = true
            // 显示提示（使用Toast）
            android.widget.Toast.makeText(
                context,
                "再按一次退出APP",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            // 2秒后重置状态
            backPressJob = CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Menu && keyEvent.type == KeyEventType.KeyUp) {
                    bottomBarFirstTabFocusRequester.tryRequestFocus()
                    true
                } else {
                    false
                }
            }
    ) {
        val showVersionBadgeOverlay =
            hasUpdate && versionInfo != null && showTopVersionBadge

        TvLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester),
            contentPadding = PaddingValues(bottom = 100.dp),
            pivotOffsets = PivotOffsets(parentFraction = 0.1f)
        ) {
            var rowIndexCursor = 0
            var mediaRowIndexCursor = 0
            // Header
            item {
                HomeHeader(
                    onSearchClick = onNavigateToSearch,
                    onRefreshClick = {
                        // 触发增量刷剥
                        val library = resourceLibraryViewModel.getCurrentLibrary()
                        if (library != null) {
                            mediaSyncViewModel.syncLibraryIncremental(library)
                        }
                    },
                    syncState = syncState,
                    syncProgress = syncProgress,
                    notification = currentNotification,
                    firstRowFocusRequesters = rowFocusRequesters.firstOrNull(),
                    headerFocusRequesters = headerFocusRequesters,
                    fallbackContentFocusRequester = contentFocusRequester,
                    bottomNavigationFirstTabFocusRequester = bottomBarFirstTabFocusRequester,
                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                    showVersionUpdateBadge = showVersionBadgeOverlay,
                    versionUpdateBadgeFocusRequester = versionUpdateBadgeFocusRequester
                )
            }

            // 最近播放栏目 - 只有数据时才显示
            if (recentWatchHistory.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "最近播放",
                        accentColor = PrimaryYellow
                    )
                }
                item {
                    RecentWatchingRow(
                        watchHistoryItems = recentWatchHistory,
                        onItemClick = onPlayFromHistory,
                        showMore = recentWatchHistory.size > HOME_SECTION_MAX_ITEMS,
                        onMoreClick = onNavigateToRecentWatching,
                        currentRowFocusRequesters = rowFocusRequesters.getOrNull(rowIndexCursor),
                        headerFocusRequesters = headerFocusRequesters,
                        isFirstContentRow = rowIndexCursor == 0,
                        onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                        showTopVersionBadge = showVersionBadgeOverlay,
                        topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester
                    )
                }
                rowIndexCursor++
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }

            // 按自定义顺序显示各个媒体类型
            mediaSortOrder.forEach { mediaType ->
                when (mediaType) {
                    com.lomen.tv.domain.model.MediaType.TV_SHOW -> {
                        if (tvShows.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "电视剧 (${tvShows.size}部)",
                                    accentColor = Color(0xFF10b981)
                                )
                            }
                            item {
                                WebDavTvShowsRow(
                                    tvShows = tvShows,
                                    onItemClick = onNavigateToDetail,
                                    showMore = tvShows.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多电视剧",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.TV_SHOW) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.ANIME -> {
                        if (anime.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "动漫 (${anime.size}部)",
                                    accentColor = Color(0xFFf59e0b)
                                )
                            }
                            item {
                                WebDavTvShowsRow(
                                    tvShows = anime,
                                    onItemClick = onNavigateToDetail,
                                    showMore = anime.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多动漫",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.ANIME) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.MOVIE -> {
                        if (movies.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "电影 (${movies.size})",
                                    accentColor = PrimaryYellow
                                )
                            }
                            item {
                                WebDavMoviesRow(
                                    movies = movies,
                                    onItemClick = onNavigateToDetail,
                                    showMore = movies.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多电影",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.MOVIE) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.VARIETY -> {
                        if (variety.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "综艺 (${variety.size}部)",
                                    accentColor = Color(0xFFec4899)
                                )
                            }
                            item {
                                WebDavTvShowsRow(
                                    tvShows = variety,
                                    onItemClick = onNavigateToDetail,
                                    showMore = variety.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多综艺",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.VARIETY) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.CONCERT -> {
                        if (concerts.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "演唱会 (${concerts.size})",
                                    accentColor = Color(0xFFa855f7)
                                )
                            }
                            item {
                                WebDavMoviesRow(
                                    movies = concerts,
                                    onItemClick = onNavigateToDetail,
                                    showMore = concerts.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多演唱会",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.CONCERT) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.DOCUMENTARY -> {
                        if (documentaries.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "纪录片 (${documentaries.size})",
                                    accentColor = Color(0xFF3b82f6)
                                )
                            }
                            item {
                                WebDavMoviesRow(
                                    movies = documentaries,
                                    onItemClick = onNavigateToDetail,
                                    showMore = documentaries.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多纪录片",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.DOCUMENTARY) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                    com.lomen.tv.domain.model.MediaType.OTHER -> {
                        if (others.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = "其它 (${others.size})",
                                    accentColor = Color(0xFF6b7280)
                                )
                            }
                            item {
                                WebDavMoviesRow(
                                    movies = others,
                                    onItemClick = onNavigateToDetail,
                                    showMore = others.size > HOME_SECTION_MAX_ITEMS,
                                    moreLabel = "更多其它",
                                    onMoreClick = { onNavigateToCategory(com.lomen.tv.domain.model.MediaType.OTHER) },
                                    currentRowFocusRequesters = mediaRowFocusRequesters.getOrNull(mediaRowIndexCursor),
                                    headerFocusRequesters = headerFocusRequesters,
                                    isFirstContentRow = rowIndexCursor == 0,
                                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 2) },
                                    showTopVersionBadge = showVersionBadgeOverlay,
                                    topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester,
                                    resetToStartNonce = homeRowResetNonce,
                                    pinStartWhileSyncing = isSyncing
                                )
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                            rowIndexCursor++
                            mediaRowIndexCursor++
                        }
                    }
                }
            }

            // 如果没有WebDAV数据，显示提示
            if (currentLibrary == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无媒体资源",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击下方资源库按钮添加WebDAV服务器",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }


        }

        if (showVersionBadgeOverlay) {
            val ver = versionInfo!!
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                VersionUpdateBadge(
                    versionName = ver.versionName,
                    onClick = {
                        showTopVersionBadge = false
                        showVersionUpdateDialog = true
                    },
                    onDismiss = { showTopVersionBadge = false },
                    focusRequester = versionUpdateBadgeFocusRequester,
                    headerFocusRequesters = headerFocusRequesters,
                    focusedHeaderColumnIndex = focusedColumnIndex,
                    modifier = Modifier
                )
            }
        }

        // Bottom Navigation - Fixed at bottom
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                when (index) {
                    0 -> { /* Home - already here */ }
                    1 -> onNavigateToLive() // 导航到直播页面
                    2 -> { /* Hot - not implemented yet */ }
                    3 -> onNavigateToLibrary() // 导航到资源库页面
                    4 -> onNavigateToSettings()
                }
            },
            onExitNavigation = {
                // 按上键退出导航栏，将焦点返回到内容区域
                requestFirstAvailableFocus(
                    mediaRowFocusRequesters.lastOrNull()?.getOrNull(0),
                    rowFocusRequesters.firstOrNull()?.getOrNull(0),
                    contentFocusRequester
                )
            },
            hasUpdate = hasUpdate,
            firstTabFocusRequester = bottomBarFirstTabFocusRequester,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // TMDB API 必需提示对话框
        if (showTmdbApiRequiredDialog) {
            TmdbApiRequiredDialog(
                onConfirm = {
                    android.util.Log.d("HomeScreen", "TmdbApiRequiredDialog onConfirm clicked")
                    showTmdbApiRequiredDialog = false
                    // 延迟打开设置对话框，确保状态更新完成
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(100)
                        showTmdbApiSettingsDialog = true
                        android.util.Log.d("HomeScreen", "Opening TmdbApiSettingsDialog")
                    }
                },
                onDismiss = {
                    // 用户取消后，下次启动还会提示
                    showTmdbApiRequiredDialog = false
                }
            )
        }

        // TMDB API 设置对话框
        if (showTmdbApiSettingsDialog) {
            TmdbApiSettingsDialog(
                onDismiss = { showTmdbApiSettingsDialog = false }
            )
        }
        
        // 版本更新对话框
        if (showVersionUpdateDialog && versionInfo != null) {
            val scope = rememberCoroutineScope()
            val currentVersionInfo = versionInfo
            if (currentVersionInfo != null) {
                com.lomen.tv.ui.components.VersionUpdateDialog(
                    versionInfo = currentVersionInfo,
                    onUpdate = {
                        showVersionUpdateDialog = false
                        // 开始下载
                        versionUpdateViewModel.startDownloadProgress()
                        val downloadService = com.lomen.tv.domain.service.DownloadService(context)
                        scope.launch {
                            downloadService.downloadApk(
                                versionInfo = currentVersionInfo,
                                onProgress = { progress ->
                                    versionUpdateViewModel.updateDownloadProgress(progress)
                                },
                                onComplete = { apkFile ->
                                    versionUpdateViewModel.completeDownload()
                                    if (apkFile != null) {
                                        downloadService.installApk(apkFile)
                                    }
                                }
                            )
                        }
                    },
                    onCancel = {
                        showVersionUpdateDialog = false
                    }
                )
            }
        }
        
        // 下载进度提示
        val isDownloading by versionUpdateViewModel.isDownloading.collectAsState()
        val downloadProgress by versionUpdateViewModel.downloadProgress.collectAsState()
        if (isDownloading) {
            com.lomen.tv.ui.components.DownloadProgressToast(progress = downloadProgress)
        }

        if (showTmdbScanPill) {
            InfoPillToast(message = TmdbApiPreferences.MSG_TMDB_REQUIRED_FOR_SCAN)
            LaunchedEffect(showTmdbScanPill) {
                delay(2500)
                showTmdbScanPill = false
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeHeader(
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    syncState: com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState = com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Idle,
    syncProgress: Pair<Int, Int> = 0 to 0,
    notification: com.lomen.tv.domain.model.Notification? = null,
    firstRowFocusRequesters: List<FocusRequester>? = null,
    headerFocusRequesters: List<FocusRequester>,
    fallbackContentFocusRequester: FocusRequester,
    bottomNavigationFirstTabFocusRequester: FocusRequester,
    onFocusedColumnChanged: (Int) -> Unit,
    showVersionUpdateBadge: Boolean = false,
    versionUpdateBadgeFocusRequester: FocusRequester? = null
) {
    val hasContentRowBelow = !firstRowFocusRequesters.isNullOrEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo and Navigation
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo - Using complete launcher icon with rounded corners
            val context = androidx.compose.ui.platform.LocalContext.current
            val drawable = context.getDrawable(R.mipmap.ic_launcher)
            drawable?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(),
                    contentDescription = "Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "柠檬TV",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            // 通知栏（靠近左边）
            if (notification != null) {
                Spacer(modifier = Modifier.width(24.dp))
                com.lomen.tv.ui.components.NotificationBar(
                    notification = notification,
                    modifier = Modifier.width(520.dp)
                )
            }

        }

        // 右侧：Search, Refresh, Sync Status and User
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 同步状态显示
            when (syncState) {
                is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scanning -> {
                    SyncStatusIndicator("扫描中...", syncProgress)
                }
                is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scraping -> {
                    SyncStatusIndicator("刮削中 ${syncProgress.first}/${syncProgress.second}", syncProgress)
                }
                else -> {}
            }
            
            // 刷新按钮（增量刮削）
            IconButton(
                onClick = onRefreshClick,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .focusRequester(headerFocusRequesters[0])
                    .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(0) }
                    .focusProperties {
                        if (showVersionUpdateBadge && versionUpdateBadgeFocusRequester != null) {
                            left = versionUpdateBadgeFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        when {
                            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                if (hasContentRowBelow) {
                                    requestFirstAvailableFocus(
                                        firstRowFocusRequesters?.getOrNull(0),
                                        fallbackContentFocusRequester
                                    )
                                } else {
                                    bottomNavigationFirstTabFocusRequester.tryRequestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "增量刮削",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 搜索按钮
            IconButton(
                onClick = onSearchClick,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .focusRequester(headerFocusRequesters[1])
                    .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(1) }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown) {
                            if (hasContentRowBelow) {
                                requestFirstAvailableFocus(
                                    firstRowFocusRequesters?.getOrNull(0),
                                    fallbackContentFocusRequester
                                )
                            } else {
                                bottomNavigationFirstTabFocusRequester.tryRequestFocus()
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User Avatar
            IconButton(
                onClick = { /* User profile action */ },
                colors = IconButtonDefaults.colors(
                    containerColor = SurfaceDark,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .focusRequester(headerFocusRequesters[2])
                    .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(2) }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown) {
                            if (hasContentRowBelow) {
                                requestFirstAvailableFocus(
                                    firstRowFocusRequesters?.getOrNull(0),
                                    fallbackContentFocusRequester
                                )
                            } else {
                                bottomNavigationFirstTabFocusRequester.tryRequestFocus()
                            }
                        } else {
                            false
                        }
                    }
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MoreMediaCard(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 160.dp,
    height: androidx.compose.ui.unit.Dp = 240.dp,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(height)
            .mousePrimaryClick { onClick() },
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.04f,
            pressedScale = 0.97f
        ),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow,
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.8f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = Color.White.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = PrimaryYellow
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 同步状态指示器
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SyncStatusIndicator(
    text: String,
    progress: Pair<Int, Int>
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        // 进度条
        if (progress.second > 0) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp * (progress.first.toFloat() / progress.second.coerceAtLeast(1)))
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryYellow)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = PrimaryYellow
        )
        
        Spacer(modifier = Modifier.width(12.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(
    title: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentWatchingRow(
    watchHistoryItems: List<com.lomen.tv.domain.service.WatchHistoryItem>,
    onItemClick: (com.lomen.tv.domain.service.WatchHistoryItem) -> Unit,
    showMore: Boolean = false,
    onMoreClick: () -> Unit = {},
    currentRowFocusRequesters: List<FocusRequester>?,
    headerFocusRequesters: List<FocusRequester>,
    isFirstContentRow: Boolean,
    onFocusedColumnChanged: (Int) -> Unit,
    showTopVersionBadge: Boolean = false,
    topVersionBadgeFocusRequester: FocusRequester? = null
) {
    val displayCount = watchHistoryItems.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
    val totalItems = displayCount + if (showMore) 1 else 0

    TvLazyRow(
        contentPadding = PaddingValues(start = 48.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.2f)
    ) {
        items(
            count = totalItems,
            key = { index ->
                if (index < displayCount) {
                    watchHistoryItems[index].id
                } else {
                    "more_recent_watching"
                }
            }
        ) { index ->
            if (index < displayCount) {
                val historyItem = watchHistoryItems[index]
                RecentCard(
                    historyItem = historyItem,
                    onClick = { onItemClick(historyItem) },
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            } else {
                MoreMediaCard(
                    label = "更多",
                    onClick = onMoreClick,
                    width = 320.dp,
                    height = 180.dp,
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentCard(
    historyItem: com.lomen.tv.domain.service.WatchHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // 格式化时间显示
    val watchedTime = formatDuration(historyItem.progress)
    val remainingTime = if (historyItem.duration > 0) {
        val remaining = historyItem.duration - historyItem.progress
        if (remaining > 0) "剩余 ${formatDuration(remaining)}" else "已看完"
    } else ""
    
    // 构建标题：如果有剧集信息，只显示集数，不显示副标题
    val displayTitle = if (historyItem.episodeNumber != null) {
        "${historyItem.title} 第${historyItem.episodeNumber}集"
    } else {
        historyItem.title
    }
    
    val progressPercent = if (historyItem.duration > 0) {
        (historyItem.progress.toFloat() / historyItem.duration).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = onClick,
        modifier = modifier
            .width(320.dp)
            .height(180.dp)
            .mousePrimaryClick { onClick() }
            .onFocusChanged { isFocused = it.isFocused },
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.04f,
            pressedScale = 0.97f
        ),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow.copy(alpha = 0.2f),
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = Color.White.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box {
            val imageUrl = historyItem.backdropUrl ?: historyItem.posterUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageUrl)
                        .size(640, 360)
                        .crossfade(false)
                        .build(),
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = TextMuted
                    )
                }
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Play Button Overlay - 只在聚焦时显示
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0f))
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已看 $watchedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    if (remainingTime.isNotEmpty()) {
                        Text(
                            text = remainingTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                if (progressPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPercent)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(PrimaryYellow)
                        )
                    }
                }
            }
        }
    }
}

// 格式化时长（毫秒转 MM:SS 或 HH:MM:SS）
private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvShowsGrid(
    onItemClick: (String) -> Unit
) {
    val tvShows = listOf(
        TvShow("1", "狂飙", "9.2", "共39集 | 犯罪悬疑", "https://picsum.photos/300/400?random=10"),
        TvShow("2", "三体", "8.7", "共30集 | 科幻", "https://picsum.photos/300/400?random=11"),
        TvShow("3", "漫长的季节", "9.4", "共12集 | 悬疑", "https://picsum.photos/300/400?random=12"),
        TvShow("4", "繁花", "8.5", "共30集 | 剧情", "https://picsum.photos/300/400?random=13"),
        TvShow("5", "庆余年2", "7.2", "共36集 | 古装", "https://picsum.photos/300/400?random=14"),
        TvShow("6", "隐秘的角落", "8.8", "共12集 | 悬疑", "https://picsum.photos/300/400?random=15")
    )

    TvLazyRow(
        contentPadding = PaddingValues(start = 48.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.2f)
    ) {
        items(
            count = tvShows.size,
            key = { index -> tvShows[index].id }
        ) { index ->
            val show = tvShows[index]
            TvShowCard(
                show = show,
                onClick = { onItemClick(show.id) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvShowCard(
    show: TvShow,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.width(160.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(160.dp)
                .height(213.dp)
                .mousePrimaryClick { onClick() }
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow,
                pressedContainerColor = PrimaryYellow.copy(alpha = 0.8f)
            ),
            scale = CardDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.08f,
                pressedScale = 1.0f
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 2.dp, color = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(show.imageUrl)
                        .size(320, 480)
                        .crossfade(false)
                        .build(),
                    contentDescription = show.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Rating badge
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = show.rating,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF10b981)
                    )
                }

                // Play Button Overlay - 只在聚焦时显示
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0f))
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = show.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = show.info,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConcertsRow(
    onItemClick: (String) -> Unit
) {
    val concerts = listOf(
        Concert("1", "周杰伦 嘉年华演唱会", "世界巡回", "https://picsum.photos/288/360?random=20"),
        Concert("2", "五月天 好好好想见到你", "线上演唱会", "https://picsum.photos/288/360?random=21"),
        Concert("3", "陈奕迅 Fear and Dreams", "香港红馆", "https://picsum.photos/288/360?random=22"),
        Concert("4", "张学友 60+ 演唱会", "经典重现", "https://picsum.photos/288/360?random=23")
    )

    TvLazyRow(
        contentPadding = PaddingValues(start = 48.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.2f)
    ) {
        items(
            count = concerts.size,
            key = { index -> concerts[index].id }
        ) { index ->
            val concert = concerts[index]
            ConcertCard(
                concert = concert,
                onClick = { onItemClick(concert.id) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConcertCard(
    concert: Concert,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(250.dp)
            .mousePrimaryClick { onClick() }
            .onFocusChanged { isFocused = it.isFocused },
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.04f,
            pressedScale = 0.97f
        ),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow.copy(alpha = 0.2f),
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = PrimaryYellow.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(concert.imageUrl)
                    .size(400, 500)
                    .crossfade(false)
                    .build(),
                contentDescription = concert.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Glass card overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBackground)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = concert.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = concert.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFec4899)
                    )
                }
            }

            // Play Button Overlay - 只在聚焦时显示
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0f))
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onExitNavigation: () -> Unit,
    hasUpdate: Boolean,
    firstTabFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem("首页", Icons.Default.Home),
        NavItem("直播", Icons.Default.LiveTv),
        NavItem("热门", Icons.Default.LocalFireDepartment),
        NavItem("资源库", Icons.Default.VideoLibrary),
        NavItem("设置", Icons.Default.Settings)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.6f))
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedTab
                val isSettingsTab = index == 4
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        IconButton(
                            onClick = { onTabSelected(index) },
                            colors = IconButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = if (isSelected) PrimaryYellow else BackgroundDark,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            ),
                            modifier = Modifier
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstTabFocusRequester)
                                    else Modifier
                                )
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                        onExitNavigation()
                                        true
                                    } else {
                                        false
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        // 红点提示
                        if (isSettingsTab && hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                        }
                    }

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) PrimaryYellow else BackgroundDark
                    )
                }
            }
        }
    }
}

// WebDAV Movies Row
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WebDavMoviesRow(
    movies: List<com.lomen.tv.data.local.database.entity.WebDavMediaEntity>,
    onItemClick: (String) -> Unit,
    showMore: Boolean,
    moreLabel: String,
    onMoreClick: () -> Unit,
    currentRowFocusRequesters: List<FocusRequester>?,
    headerFocusRequesters: List<FocusRequester>,
    isFirstContentRow: Boolean,
    onFocusedColumnChanged: (Int) -> Unit,
    showTopVersionBadge: Boolean = false,
    topVersionBadgeFocusRequester: FocusRequester? = null,
    resetToStartNonce: Int = 0,
    pinStartWhileSyncing: Boolean = false
) {
    val displayCount = movies.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
    val totalItems = displayCount + if (showMore) 1 else 0

    // 锁住横向滚动偏移：刮削过程中数据库刷新导致列表重排时，
    // 避免 TvLazyRow 自动调整 scroll，从而把“第一张卡片”视觉位置挪走。
    val listState = rememberTvLazyListState(0)
    var handledResetNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(resetToStartNonce, displayCount) {
        // 每轮刮削只处理一次；若触发时还没有数据，等有数据后再执行。
        if (resetToStartNonce > handledResetNonce && displayCount > 0) {
            listState.scrollToItem(0)
            handledResetNonce = resetToStartNonce
        }
    }
    LaunchedEffect(pinStartWhileSyncing) {
        if (!pinStartWhileSyncing) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (firstIndex, firstOffset) ->
                // 刮削中把首位锚定在第1张卡片，防止系统焦点/重排把首卡挤出屏幕左侧
                if (firstIndex != 0 || firstOffset != 0) {
                    listState.scrollToItem(0)
                }
            }
    }

    val rowModifier =
        if (pinStartWhileSyncing) {
            Modifier
                .padding(start = 48.dp)
                .clipToBounds()
        } else {
            Modifier
        }
    val rowContentPadding =
        if (pinStartWhileSyncing) PaddingValues(start = 0.dp, end = 120.dp)
        else PaddingValues(start = 48.dp, end = 120.dp)

    TvLazyRow(
        modifier = rowModifier,
        state = listState,
        contentPadding = rowContentPadding,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = if (pinStartWhileSyncing) 0f else 0.2f)
    ) {
        items(
            count = totalItems,
            key = { index ->
                if (index < displayCount) {
                    movies[index].id
                } else {
                    "more_$moreLabel"
                }
            }
        ) { index ->
            if (index < displayCount) {
                val movie = movies[index]
                WebDavMediaCard(
                    media = movie,
                    onClick = { onItemClick(movie.id) },
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            } else {
                MoreMediaCard(
                    label = moreLabel,
                    onClick = onMoreClick,
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            }
        }
    }
}

// WebDAV TV Shows Row
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WebDavTvShowsRow(
    tvShows: List<com.lomen.tv.ui.viewmodel.TvShowSeries>,
    onItemClick: (String) -> Unit,
    showMore: Boolean,
    moreLabel: String,
    onMoreClick: () -> Unit,
    currentRowFocusRequesters: List<FocusRequester>?,
    headerFocusRequesters: List<FocusRequester>,
    isFirstContentRow: Boolean,
    onFocusedColumnChanged: (Int) -> Unit,
    showTopVersionBadge: Boolean = false,
    topVersionBadgeFocusRequester: FocusRequester? = null,
    resetToStartNonce: Int = 0,
    pinStartWhileSyncing: Boolean = false
) {
    val displayCount = tvShows.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
    val totalItems = displayCount + if (showMore) 1 else 0

    // 同步锁住横向滚动偏移：避免列表刷新时，第一张卡片的视觉位置被自动挪动。
    val listState = rememberTvLazyListState(0)
    var handledResetNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(resetToStartNonce, displayCount) {
        // 每轮刮削只处理一次；若触发时还没有数据，等有数据后再执行。
        if (resetToStartNonce > handledResetNonce && displayCount > 0) {
            listState.scrollToItem(0)
            handledResetNonce = resetToStartNonce
        }
    }
    LaunchedEffect(pinStartWhileSyncing) {
        if (!pinStartWhileSyncing) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (firstIndex, firstOffset) ->
                // 刮削中把首位锚定在第1张卡片，防止系统焦点/重排把首卡挤出屏幕左侧
                if (firstIndex != 0 || firstOffset != 0) {
                    listState.scrollToItem(0)
                }
            }
    }

    val rowModifier =
        if (pinStartWhileSyncing) {
            Modifier
                .padding(start = 48.dp)
                .clipToBounds()
        } else {
            Modifier
        }
    val rowContentPadding =
        if (pinStartWhileSyncing) PaddingValues(start = 0.dp, end = 120.dp)
        else PaddingValues(start = 48.dp, end = 120.dp)

    TvLazyRow(
        modifier = rowModifier,
        state = listState,
        contentPadding = rowContentPadding,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = if (pinStartWhileSyncing) 0f else 0.2f)
    ) {
        items(
            count = totalItems,
            key = { index ->
                if (index < displayCount) {
                    tvShows[index].id
                } else {
                    "more_$moreLabel"
                }
            }
        ) { index ->
            if (index < displayCount) {
                val series = tvShows[index]
                TvShowSeriesCard(
                    series = series,
                    onClick = { onItemClick(series.id) },
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            } else {
                MoreMediaCard(
                    label = moreLabel,
                    onClick = onMoreClick,
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters
                                ?.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up =
                                    if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                        topVersionBadgeFocusRequester
                                    } else {
                                        headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                    }
                            }
                        }
                )
            }
        }
    }
}

// WebDAV Media Card
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WebDavMediaCard(
    media: com.lomen.tv.data.local.database.entity.WebDavMediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.width(160.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = onClick,
            modifier = modifier
                .width(160.dp)
                .height(240.dp)
                .mousePrimaryClick { onClick() }
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.04f,
                pressedScale = 0.97f
            ),
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = SurfaceDark,  // 选中时保持背景不变，避免遮盖标题
                pressedContainerColor = PrimaryYellow.copy(alpha = 0.8f)
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = PrimaryYellow),  // 选中时显示黄色边框
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box {
                // 海报图片
                if (media.posterUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(media.posterUrl)
                            // 控制图片尺寸，避免加载超大原图；这里按卡片大小的 2x 估算
                            .size(320, 480)
                            .crossfade(false)
                            .build(),
                        contentDescription = media.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 默认占位图
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = TextMuted
                        )
                    }
                }

                // 评分标签
                if (media.rating != null && media.rating > 0) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "%.1f".format(media.rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF10b981)
                        )
                    }
                }

                // 年份标签
                if (media.year != null) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = media.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                // Play Button Overlay - 只在聚焦时显示
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0f))
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))  // 增加间距，避免选中边框遮盖标题

        Text(
            text = media.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 剧集系列卡片
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvShowSeriesCard(
    series: com.lomen.tv.ui.viewmodel.TvShowSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.width(160.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = onClick,
            modifier = modifier
                .width(160.dp)
                .height(240.dp)
                .mousePrimaryClick { onClick() }
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.04f,
                pressedScale = 0.97f
            ),
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = SurfaceDark,  // 选中时保持背景不变，避免遮盖标题
                pressedContainerColor = PrimaryYellow.copy(alpha = 0.8f)
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = PrimaryYellow),  // 选中时显示黄色边框
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box {
                // 海报图片
                if (series.posterUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(series.posterUrl)
                            .size(320, 480)
                            .crossfade(false)
                            .build(),
                        contentDescription = series.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 默认占位图
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = TextMuted
                        )
                    }
                }

                // 评分标签
                if (series.rating != null && series.rating > 0) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "%.1f".format(series.rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF10b981)
                        )
                    }
                }

                // 年份标签
                if (series.year != null) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = series.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                // 集数标签
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PrimaryYellow.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${series.episodeCount}集",
                        style = MaterialTheme.typography.labelSmall,
                        color = BackgroundDark
                    )
                }

                // Play Button Overlay - 只在聚焦时显示
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0f))
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))  // 增加间距，避免选中边框遮盖标题

        Text(
            text = series.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Data classes
private data class RecentItem(
    val id: String,
    val title: String,
    val watchedTime: String,
    val remainingTime: String,
    val progress: Float,
    val imageUrl: String
)

private data class TvShow(
    val id: String,
    val title: String,
    val rating: String,
    val info: String,
    val imageUrl: String
)

private data class Concert(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String
)

private data class NavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * TMDB API 必需提示对话框
 * 当用户未配置 API Key 时显示，引导用户进行配置
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TmdbApiRequiredDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { keyEvent ->
                // 处理返回键
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .clickable(
                onClick = { },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(
                containerColor = DialogUiTokens.ContainerColor,
                focusedContainerColor = DialogUiTokens.ContainerColor
            ),
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .focusProperties {
                    // 弹窗卡片本身不抢焦点，仅允许内部按钮获得焦点，避免焦点逃逸到弹窗外
                    canFocus = false
                }
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "需要配置 TMDB API",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "为了获取媒体元数据（海报、简介、评分等），您需要配置 TMDB API Key。\n\n您可以在 themoviedb.org 免费申请 API Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮
                    var cancelButtonFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .onFocusChanged { cancelButtonFocused = it.isFocused }
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = confirmFocusRequester
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "稍后再说",
                            color = if (cancelButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // 确定按钮 - 去配置
                    var confirmButtonFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        modifier = Modifier
                            .focusRequester(confirmFocusRequester)
                            .onFocusChanged { confirmButtonFocused = it.isFocused }
                            .focusProperties {
                                left = cancelFocusRequester
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "去配置",
                            color = if (confirmButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                }
            }
        }
    }
    
    // 默认焦点给"去配置"按钮（引导用户配置）
    LaunchedEffect(Unit) {
        confirmFocusRequester.requestFocus()
    }
}
