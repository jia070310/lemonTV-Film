package com.lemon.yingshi.tv.ui.screens.home

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
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
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.ui.components.InfoPillToast
import com.lemon.yingshi.tv.R
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lemon.yingshi.tv.ui.components.MediaCardFocusPlayIcon
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.GlassBackground
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.DialogDimens
import com.lemon.yingshi.tv.ui.viewmodel.VersionUpdateViewModel
import com.lemon.yingshi.tv.ui.components.VersionUpdateBadge
import android.view.MotionEvent


private const val HOME_SECTION_MAX_ITEMS = 10

private fun FocusRequester.tryRequestFocus(): Boolean {
    return runCatching {
        requestFocus()
        true
    }.getOrElse { false }
}

private fun navigateUpToVersionBadge(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    showBadge: Boolean,
    badgeFocusRequester: FocusRequester?
): Boolean {
    if (keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown &&
        showBadge && badgeFocusRequester != null
    ) {
        badgeFocusRequester.tryRequestFocus()
        return true
    }
    return false
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
    onNavigateToSettings: () -> Unit,
    onNavigateToRecentWatching: () -> Unit = {},
    onNavigateToRecommended: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToFilter: (typeId: Int, navTypeId: Int) -> Unit = { _, _ -> },
    onPlayFromHistory: (com.lemon.yingshi.tv.domain.service.WatchHistoryItem) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    macCmsHomeViewModel: MacCmsHomeViewModel = hiltViewModel(),
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

    // MacCMS 首页数据
    val macCmsHomeState by macCmsHomeViewModel.uiState.collectAsState()
    val macCmsSections = macCmsHomeState.sections
    val recommendedItems = macCmsHomeState.recommendedItems

    val rowSpecs = remember(recentWatchHistory, recommendedItems, macCmsSections) {
        buildList {
            if (recentWatchHistory.isNotEmpty()) {
                val displayCount = recentWatchHistory.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                add(displayCount + if (recentWatchHistory.size > HOME_SECTION_MAX_ITEMS) 1 else 0)
            }
            if (recommendedItems.isNotEmpty()) {
                val displayCount = recommendedItems.size.coerceAtMost(HOME_RECOMMENDED_HOME_ITEMS)
                val firstRowCount = minOf(HOME_RECOMMENDED_COLUMNS, displayCount)
                if (firstRowCount > 0) add(firstRowCount)
                val secondRowCount = (displayCount - HOME_RECOMMENDED_COLUMNS).coerceAtLeast(0) + 1
                add(secondRowCount)
            }
            macCmsSections.forEach { section ->
                val displayCount = section.items.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
                add(displayCount + if (section.items.isNotEmpty()) 1 else 0)
            }
        }
    }
    val rowFocusRequesters = remember(rowSpecs) {
        rowSpecs.map { itemCount -> List(itemCount) { FocusRequester() } }
    }
    val macCmsRowFocusRequesters = remember(macCmsSections) {
        macCmsSections.map { section ->
            val displayCount = section.items.size.coerceAtMost(HOME_SECTION_MAX_ITEMS)
            val totalItems = displayCount + if (section.items.isNotEmpty()) 1 else 0
            List(totalItems) { FocusRequester() }
        }
    }
    val headerFocusRequesters = remember { List(4) { FocusRequester() } }

    LaunchedEffect(Unit) {
        delay(100)
        headerFocusRequesters[0].tryRequestFocus()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadScope = rememberCoroutineScope()

    // 版本更新检查
    val hasUpdate by versionUpdateViewModel.hasUpdate.collectAsState()
    val versionInfo by versionUpdateViewModel.versionInfo.collectAsState()
    var showVersionUpdateDialog by remember { mutableStateOf(false) }
    val versionUpdateBadgeFocusRequester = remember { FocusRequester() }
    var showTopVersionBadge by remember(versionInfo?.versionName, hasUpdate) {
        mutableStateOf(true)
    }

    val startVersionDownload: () -> Unit = startVersionDownload@{
        val currentVersionInfo = versionInfo ?: return@startVersionDownload
        showTopVersionBadge = false
        showVersionUpdateDialog = false
        versionUpdateViewModel.startDownloadProgress()
        val downloadService = com.lemon.yingshi.tv.domain.service.DownloadService(context)
        downloadScope.launch {
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
    }

    // 启动时检查版本更新
    LaunchedEffect(Unit) {
        delay(500)
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
            // Header
            item {
                HomeHeader(
                    onSearchClick = onNavigateToSearch,
                    onFavoritesClick = onNavigateToFavorites,
                    onRefreshClick = { macCmsHomeViewModel.loadHome(forceRefresh = true) },
                    notification = currentNotification,
                    firstRowFocusRequesters = rowFocusRequesters.firstOrNull(),
                    headerFocusRequesters = headerFocusRequesters,
                    fallbackContentFocusRequester = contentFocusRequester,
                    bottomNavigationFirstTabFocusRequester = bottomBarFirstTabFocusRequester,
                    onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 3) },
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
                        onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 3) },
                        showTopVersionBadge = showVersionBadgeOverlay,
                        topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester
                    )
                }
                rowIndexCursor++
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }

            // 最新推荐栏目 - 推荐等级 9，两行六列
            if (recommendedItems.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "最新推荐",
                        accentColor = Color(0xFFf97316)
                    )
                }
                item {
                    RecommendedVodSection(
                        items = recommendedItems,
                        onItemClick = { vod ->
                            macCmsHomeViewModel.cacheVodForDetail(vod)
                            onNavigateToDetail(MacCmsIds.encode(vod.vodId))
                        },
                        onMoreClick = onNavigateToRecommended,
                        rowFocusRequesters = rowFocusRequesters,
                        firstRowIndex = rowIndexCursor,
                        headerFocusRequesters = headerFocusRequesters,
                        isFirstContentRow = rowIndexCursor == 0,
                        onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 3) },
                        showTopVersionBadge = showVersionBadgeOverlay,
                        topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                rowIndexCursor += if (recommendedItems.size.coerceAtMost(HOME_RECOMMENDED_HOME_ITEMS)
                        .coerceAtMost(HOME_RECOMMENDED_COLUMNS) > 0
                ) {
                    2
                } else {
                    1
                }
            }

            // MacCMS 分类内容行（分块加载：有内容先展示，仅在全空时显示加载圈）
            if (!macCmsHomeState.isConfigured) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = macCmsHomeState.error ?: "请先在设置中配置 MacCMS 服务器",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                macCmsSections.forEachIndexed { sectionIndex, section ->
                    item {
                        SectionTitle(
                            title = "${section.typeName} (${section.total})",
                            accentColor = accentColorForCategory(section.typeName)
                        )
                    }
                    item {
                        MacCmsVodRow(
                            items = section.items,
                            onItemClick = { vod ->
                                macCmsHomeViewModel.cacheVodForDetail(vod)
                                onNavigateToDetail(MacCmsIds.encode(vod.vodId))
                            },
                            showMore = section.items.isNotEmpty(),
                            moreLabel = "更多",
                            onMoreClick = {
                                onNavigateToFilter(section.typeId, section.navTypeId ?: -1)
                            },
                            currentRowFocusRequesters = macCmsRowFocusRequesters.getOrNull(sectionIndex),
                            headerFocusRequesters = headerFocusRequesters,
                            isFirstContentRow = rowIndexCursor == 0,
                            onFocusedColumnChanged = { focusedColumnIndex = it.coerceIn(0, 3) },
                            showTopVersionBadge = showVersionBadgeOverlay,
                            topVersionBadgeFocusRequester = versionUpdateBadgeFocusRequester
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                    rowIndexCursor++
                }
                if (macCmsSections.isEmpty() && recommendedItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (macCmsHomeState.isLoading || macCmsHomeState.isLoadingSections) {
                                CircularProgressIndicator(color = PrimaryYellow)
                            } else {
                                Text(
                                    text = macCmsHomeState.error ?: "暂无内容",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextMuted
                                )
                            }
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
                    onClick = startVersionDownload,
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
                    1 -> onNavigateToFilter(-1, -1)
                    2 -> onNavigateToSettings()
                }
            },
            onExitNavigation = {
                // 按上键退出导航栏，将焦点返回到内容区域
                requestFirstAvailableFocus(
                    macCmsRowFocusRequesters.lastOrNull()?.getOrNull(0),
                    rowFocusRequesters.firstOrNull()?.getOrNull(0),
                    contentFocusRequester
                )
            },
            hasUpdate = hasUpdate,
            firstTabFocusRequester = bottomBarFirstTabFocusRequester,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 版本更新对话框
        if (showVersionUpdateDialog && versionInfo != null) {
            val currentVersionInfo = versionInfo
            if (currentVersionInfo != null) {
                com.lemon.yingshi.tv.ui.components.VersionUpdateDialog(
                    versionInfo = currentVersionInfo,
                    onUpdate = startVersionDownload,
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
            com.lemon.yingshi.tv.ui.components.DownloadProgressToast(progress = downloadProgress)
        }

    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeHeader(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onRefreshClick: () -> Unit,
    notification: com.lemon.yingshi.tv.domain.model.Notification? = null,
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
                text = "柠檬影视TV",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            // 通知栏（靠近左边）
            if (notification != null) {
                Spacer(modifier = Modifier.width(24.dp))
                com.lemon.yingshi.tv.ui.components.NotificationBar(
                    notification = notification,
                    modifier = Modifier.width(520.dp)
                )
            }

        }

        // 右侧：Search, Refresh, Sync Status and User
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 刷新按钮
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
                    .onPreviewKeyEvent { keyEvent ->
                        when {
                            navigateUpToVersionBadge(
                                keyEvent,
                                showVersionUpdateBadge,
                                versionUpdateBadgeFocusRequester
                            ) -> true
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
                        when {
                            navigateUpToVersionBadge(
                                keyEvent,
                                showVersionUpdateBadge,
                                versionUpdateBadgeFocusRequester
                            ) -> true
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp -> {
                                onSearchClick()
                                true
                            }
                            keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                                onSearchClick()
                                true
                            }
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
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 收藏按钮
            IconButton(
                onClick = onFavoritesClick,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .focusRequester(headerFocusRequesters[2])
                    .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(2) }
                    .onPreviewKeyEvent { keyEvent ->
                        when {
                            navigateUpToVersionBadge(
                                keyEvent,
                                showVersionUpdateBadge,
                                versionUpdateBadgeFocusRequester
                            ) -> true
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp -> {
                                onFavoritesClick()
                                true
                            }
                            keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                                onFavoritesClick()
                                true
                            }
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
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "收藏",
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
                    .focusRequester(headerFocusRequesters[3])
                    .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(3) }
                    .onPreviewKeyEvent { keyEvent ->
                        when {
                            navigateUpToVersionBadge(
                                keyEvent,
                                showVersionUpdateBadge,
                                versionUpdateBadgeFocusRequester
                            ) -> true
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
fun MoreMediaCard(
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
    watchHistoryItems: List<com.lemon.yingshi.tv.domain.service.WatchHistoryItem>,
    onItemClick: (com.lemon.yingshi.tv.domain.service.WatchHistoryItem) -> Unit,
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
                                up = if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
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
                                up = if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
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
    historyItem: com.lemon.yingshi.tv.domain.service.WatchHistoryItem,
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
            val imageUrl = historyItem.coverImageModel()
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
            MediaCardFocusPlayIcon(isFocused = isFocused, iconSize = 56.dp)

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
        NavItem("筛选", Icons.Default.FilterList),
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
                val isSettingsTab = index == 2
                
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

private data class NavItem(
    val label: String,
    val icon: ImageVector
)

