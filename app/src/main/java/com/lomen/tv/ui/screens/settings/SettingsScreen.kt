package com.lomen.tv.ui.screens.settings

import com.lomen.tv.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.lomen.tv.service.WebDavConfigServer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.lomen.tv.ui.theme.BackgroundDark
import com.lomen.tv.ui.theme.DialogUiTokens
import com.lomen.tv.ui.theme.GlassBackground
import androidx.compose.ui.graphics.Brush
import com.lomen.tv.ui.theme.PrimaryYellow
import com.lomen.tv.ui.theme.SurfaceDark
import com.lomen.tv.ui.theme.TextMuted
import com.lomen.tv.ui.theme.TextPrimary
import com.lomen.tv.ui.theme.TextSecondary
import com.lomen.tv.domain.model.ResourceLibrary
import com.lomen.tv.ui.viewmodel.VersionUpdateViewModel
import com.lomen.tv.domain.model.VersionInfo
import com.lomen.tv.domain.service.DownloadService
import com.lomen.tv.ui.components.VersionUpdateDialog
import com.lomen.tv.ui.components.DownloadProgressToast
import com.lomen.tv.ui.components.InfoPillToast
import java.util.UUID
import kotlinx.coroutines.launch
import com.lomen.tv.data.preferences.LiveSettingsPreferences
import com.lomen.tv.data.preferences.MediaClassificationPreferences
import com.lomen.tv.data.preferences.MediaClassificationStrategyHolder
import com.lomen.tv.data.preferences.TmdbApiPreferences
import com.lomen.tv.domain.model.MediaClassificationStrategy
import com.lomen.tv.ui.viewmodel.ResourceLibraryViewModel
import com.lomen.tv.ui.LocalCompactUiScale
import com.lomen.tv.ui.computeCompactUiScale
import com.lomen.tv.ui.DialogDimens
import com.lomen.tv.ui.scale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    sharedResourceLibraryViewModel: ResourceLibraryViewModel,
    sharedMediaSyncViewModel: com.lomen.tv.ui.viewmodel.MediaSyncViewModel,
    libraryToEdit: ResourceLibrary? = null
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showWebDavPostAddHint by remember { mutableStateOf(false) }
    var showEditWebDavDialog by remember { mutableStateOf(libraryToEdit != null) }
    var showGuangyaDialog by remember { mutableStateOf(false) }
    
    // 焦点管理
    val sidebarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    
    // 版本更新相关状态
    val versionUpdateViewModel: VersionUpdateViewModel = hiltViewModel()
    val versionInfo by versionUpdateViewModel.versionInfo.collectAsState()
    val hasUpdate by versionUpdateViewModel.hasUpdate.collectAsState()
    var showVersionUpdateDialog by remember { mutableStateOf(false) }
    
    // 首页设置对话框状态
    var showSortDialog by remember { mutableStateOf(false) }
    var showClassificationStrategyDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showForceRescrapeDialog by remember { mutableStateOf(false) }
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearPlaybackStatsDialog by remember { mutableStateOf(false) }
    var showScrapeRuleDialog by remember { mutableStateOf(false) }
    var showTmdbApiDialog by remember { mutableStateOf(false) }
    var showClearTmdbApiDialog by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showTmdbScanPill by remember { mutableStateOf(false) }
    
    // TMDB API 状态 - 提前声明以便在 UI 中使用
    val tmdbApiViewModel: com.lomen.tv.ui.viewmodel.TmdbApiViewModel = hiltViewModel()
    val hasCustomTmdbKey by tmdbApiViewModel.hasCustomApiKey.collectAsState(initial = false)
    val currentApiKey by tmdbApiViewModel.apiKey.collectAsState(initial = TmdbApiPreferences.DEFAULT_API_KEY)
    
    val categories = listOf(
        SettingCategory("资源管理", Icons.Default.CloudUpload),
        SettingCategory("首页设置", Icons.Default.Settings),
        SettingCategory("播放设置", Icons.Default.PlayArrow),
        SettingCategory("直播设置", Icons.Default.LiveTv),
        SettingCategory("关于应用", Icons.Default.Info)
    )

    val configuration = LocalConfiguration.current
    val compactScale = remember(configuration.screenHeightDp, configuration.screenWidthDp) {
        computeCompactUiScale(configuration.screenHeightDp, configuration.screenWidthDp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        CompositionLocalProvider(LocalCompactUiScale provides compactScale) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // 左侧导航栏
            SettingsSidebar(
                categories = categories,
                selectedIndex = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                onNavigateBack = onNavigateBack,
                hasUpdate = hasUpdate,
                sidebarFocusRequester = sidebarFocusRequester,
                contentFocusRequester = contentFocusRequester
            )

            // 右侧内容区
            SettingsContent(
                selectedCategory = selectedCategory,
                onShowWebDavDialog = { showWebDavDialog = true },
                onShowGuangyaDialog = { showGuangyaDialog = true },
                onShowSortDialog = { showSortDialog = true },
                onShowClassificationStrategyDialog = { showClassificationStrategyDialog = true },
                onShowClearCacheDialog = { showClearCacheDialog = true },
                onShowForceRescrapeDialog = { showForceRescrapeDialog = true },
                onShowClearWatchHistoryDialog = { showClearWatchHistoryDialog = true },
                onShowTmdbApiDialog = { showTmdbApiDialog = true },
                onShowClearTmdbApiDialog = { showClearTmdbApiDialog = true },
                onShowVersionUpdateDialog = { showVersionUpdateDialog = true },
                onShowClearPlaybackStatsDialog = { showClearPlaybackStatsDialog = true },
                onShowScrapeRuleDialog = { showScrapeRuleDialog = true },
                hasUpdate = hasUpdate,
                versionInfo = versionInfo,
                hasCustomTmdbKey = hasCustomTmdbKey,
                currentApiKey = currentApiKey,
                modifier = Modifier.weight(1f),
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
        }
        
        // WebDAV配置弹窗 - 添加新资源库（放在Box内部确保全屏覆盖）
        if (showWebDavDialog) {
            WebDavConfigDialog(
                onDismiss = { showWebDavDialog = false },
                onConfigReceived = { config ->
                    // 将WebDAV配置转换为ResourceLibrary并添加到ViewModel
                    val newLibrary = ResourceLibrary(
                        id = UUID.randomUUID().toString(),
                        name = "WebDAV-${config.host}",
                        type = ResourceLibrary.LibraryType.WEBDAV,
                        protocol = config.protocol,
                        host = config.host,
                        port = config.port,
                        path = config.path,
                        username = config.username,
                        password = config.password,
                        isActive = true
                    )
                    sharedResourceLibraryViewModel.addLibrary(newLibrary)
                    showWebDavDialog = false
                    showWebDavPostAddHint = true
                }
            )
        }

        // 光鸭云盘配置：官方网页二维码扫码登录 -> 目录选择 -> 回调入库
        if (showGuangyaDialog) {
            GuangyaLoginDialog(
                onDismiss = { showGuangyaDialog = false },
                onSuccess = { result ->
                    val picked = result.selectedPaths.firstOrNull().orEmpty()
                    val suffix = picked
                        .takeIf { it.isNotBlank() && it != "/" }
                        ?.trimEnd('/')
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                        ?.let { " - $it" }
                        .orEmpty()
                    val newLibrary = ResourceLibrary(
                        id = UUID.randomUUID().toString(),
                        name = "光鸭云盘$suffix",
                        type = ResourceLibrary.LibraryType.GUANGYA,
                        apiToken = result.accessToken,
                        apiRefreshToken = result.refreshToken,
                        apiTokenExpireAt = result.accessExpireAt,
                        guangyaLoginMode = result.guangyaLoginMode,
                        guangyaDeviceId = result.guangyaDeviceId,
                        selectedPaths = result.selectedPaths,
                        isActive = true
                    )
                    sharedResourceLibraryViewModel.addLibrary(newLibrary)
                    showGuangyaDialog = false
                    showSuccessMessage = "成功添加光鸭云盘资源库"
                }
            )
        }

        if (showWebDavPostAddHint) {
            WebDavServerAddedHintDialog(
                onDismiss = { showWebDavPostAddHint = false }
            )
        }

        // WebDAV编辑弹窗 - 编辑现有资源库（放在Box内部确保全屏覆盖）
        if (showEditWebDavDialog && libraryToEdit != null) {
            WebDavConfigDialog(
                onDismiss = { 
                    showEditWebDavDialog = false
                    // 如果用户取消编辑，返回上一页
                    onNavigateBack()
                },
                onConfigReceived = { config ->
                    // 更新现有资源库
                    val updatedLibrary = libraryToEdit.copy(
                        name = "WebDAV-${config.host}",
                        protocol = config.protocol,
                        host = config.host,
                        port = config.port,
                        path = config.path,
                        username = config.username,
                        password = config.password
                    )
                    sharedResourceLibraryViewModel.addLibrary(updatedLibrary)
                    showEditWebDavDialog = false
                    // 编辑完成后返回上一页
                    onNavigateBack()
                },
                existingLibrary = libraryToEdit
            )
        }
    }
    
    // 首页设置对话框
    val resourceLibraryViewModel: ResourceLibraryViewModel = sharedResourceLibraryViewModel
    val mediaSyncViewModel: com.lomen.tv.ui.viewmodel.MediaSyncViewModel = sharedMediaSyncViewModel
    val context = androidx.compose.ui.platform.LocalContext.current
    val sortPreferences = remember {
        com.lomen.tv.data.preferences.MediaTypeSortPreferences(context)
    }
    val classificationPreferences = remember {
        MediaClassificationPreferences(context)
    }
    val currentLibrary = resourceLibraryViewModel.getCurrentLibrary()
    
    // 对话框回调
    val onShowSortDialog = { showSortDialog = true }
    val onShowClearCacheDialog = { showClearCacheDialog = true }
    val onShowForceRescrapeDialog = { showForceRescrapeDialog = true }
    val onShowClearWatchHistoryDialog = { showClearWatchHistoryDialog = true }
    val onShowTmdbApiDialog = { showTmdbApiDialog = true }
    val onShowClearTmdbApiDialog = { showClearTmdbApiDialog = true }
    
    // 检查版本更新
    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = packageInfo.versionCode
        versionUpdateViewModel.checkForUpdates(currentVersionCode)
    }
    
    // 监听刮削状态
    val syncState by mediaSyncViewModel.syncState.collectAsState()
    val syncProgress by mediaSyncViewModel.syncProgress.collectAsState()
    
    // 刮削完成提示已改为全局提示（导航根部统一弹窗），这里仅处理错误提示。
    LaunchedEffect(syncState) {
        if (syncState is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Error) {
            val error = (syncState as com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Error).message
            if (error == TmdbApiPreferences.MSG_TMDB_REQUIRED_FOR_SCAN) {
                showTmdbScanPill = true
            } else {
                showSuccessMessage = "刮削失败: $error"
            }
            mediaSyncViewModel.resetState()
        }
    }
    
    // 成功提示
    if (showSuccessMessage != null) {
        SuccessToast(message = showSuccessMessage!!)
        androidx.compose.runtime.LaunchedEffect(showSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            showSuccessMessage = null
        }
    }

    if (showTmdbScanPill) {
        InfoPillToast(message = TmdbApiPreferences.MSG_TMDB_REQUIRED_FOR_SCAN)
        androidx.compose.runtime.LaunchedEffect(showTmdbScanPill) {
            delay(2500)
            showTmdbScanPill = false
        }
    }
    
    // 刮削进度提示
    when (syncState) {
        is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scanning -> {
            ScrapeProgressToast(
                message = "正在扫描文件...",
                progress = null
            )
        }
        is com.lomen.tv.ui.viewmodel.MediaSyncViewModel.SyncState.Scraping -> {
            val (current, total) = syncProgress
            ScrapeProgressToast(
                message = "正在刮削媒体信息...",
                progress = if (total > 0) (current to total) else null
            )
        }
        else -> { /* 不显示 */ }
    }
    
    // 媒体分类排序对话框
    if (showSortDialog) {
        MediaTypeSortDialog(
            sortPreferences = sortPreferences,
            onDismiss = { showSortDialog = false },
            onSuccess = {
                showSortDialog = false
                showSuccessMessage = "排序设置已保存"
            }
        )
    }

    // 媒体类型识别策略（结构优先 / 关键词优先）
    if (showClassificationStrategyDialog) {
        ClassificationStrategyDialog(
            classificationPreferences = classificationPreferences,
            onDismiss = { showClassificationStrategyDialog = false },
            onSuccess = {
                showClassificationStrategyDialog = false
                showSuccessMessage = "识别策略已保存"
            }
        )
    }
    
    // 强制全量刮削确认对话框
    if (showForceRescrapeDialog && currentLibrary != null) {
        ConfirmDialog(
            title = "强制全量刮削",
            message = "确定要重新刮削所有媒体文件吗？\n这将花费较长时间，建议在空闲时执行。",
            onConfirm = {
                mediaSyncViewModel.syncLibrary(currentLibrary)
                showForceRescrapeDialog = false
            },
            onDismiss = { showForceRescrapeDialog = false }
        )
    }
    
    // 清除缓存确认对话框
    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "清除缓存数据",
            message = "确定要清除所有媒体元数据缓存吗？\n服务器列表将被保留。",
            onConfirm = {
                resourceLibraryViewModel.clearAllCache()
                showClearCacheDialog = false
                showSuccessMessage = "缓存已清除"
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }
    
    // TMDB API 设置对话框
    if (showTmdbApiDialog) {
        TmdbApiSettingsDialog(
            onDismiss = { showTmdbApiDialog = false }
        )
    }
    
    // 清除 TMDB API Key 对话框
    if (showClearTmdbApiDialog) {
        ClearTmdbApiDialog(
            hasCustomKey = hasCustomTmdbKey && 
                          currentApiKey.isNotBlank() && 
                          currentApiKey != TmdbApiPreferences.DEFAULT_API_KEY,
            onConfirm = {
                tmdbApiViewModel.clearApiKey()
                showClearTmdbApiDialog = false
                showSuccessMessage = "TMDB API Key 已清除"
            },
            onDismiss = { showClearTmdbApiDialog = false }
        )
    }
    
    // 版本更新对话框
    if (showVersionUpdateDialog && versionInfo != null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val currentVersionInfo = versionInfo
        if (currentVersionInfo != null) {
            VersionUpdateDialog(
                versionInfo = currentVersionInfo,
                onUpdate = {
                    showVersionUpdateDialog = false
                    // 开始下载
                    versionUpdateViewModel.startDownloadProgress()
                    val downloadService = DownloadService(context)
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
        DownloadProgressToast(progress = downloadProgress)
    }
    
    // 清空最近播放记录确认对话框
    if (showClearWatchHistoryDialog) {
        val homeViewModel: com.lomen.tv.ui.screens.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val coroutineScope = rememberCoroutineScope()
        ConfirmDialog(
            title = "清空最近播放记录",
            message = "确定要清空所有最近播放记录吗？\n此操作不可恢复。",
            onConfirm = {
                coroutineScope.launch {
                    homeViewModel.watchHistoryService.clearAllWatchHistory()
                    showClearWatchHistoryDialog = false
                    showSuccessMessage = "最近播放记录已清空"
                }
            },
            onDismiss = { showClearWatchHistoryDialog = false }
        )
    }

    // 清零播放时长确认对话框
    if (showClearPlaybackStatsDialog) {
        val homeViewModel: com.lomen.tv.ui.screens.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val coroutineScope = rememberCoroutineScope()
        ConfirmDialog(
            title = "清零播放时长",
            message = "确定要清零应用统计中的总播放时长吗？\n此操作不可恢复。",
            onConfirm = {
                coroutineScope.launch {
                    homeViewModel.playbackStatsService.clearTotalPlaybackTime()
                    showClearPlaybackStatsDialog = false
                    showSuccessMessage = "总播放时长已清零"
                }
            },
            onDismiss = { showClearPlaybackStatsDialog = false }
        )
    }

    if (showScrapeRuleDialog) {
        ScrapeRuleDialog(
            onDismiss = { showScrapeRuleDialog = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SettingsSidebar(
    categories: List<SettingCategory>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    hasUpdate: Boolean,
    sidebarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester
) {
    val s = LocalCompactUiScale.current
    Column(
        modifier = Modifier
            .width(280.dp.scale(s))
            .fillMaxHeight()
            .background(SurfaceDark)
            .padding(24.dp.scale(s))
    ) {
        // 返回按钮和标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp.scale(s))
        ) {
            IconButton(
                onClick = onNavigateBack,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextPrimary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier.size(48.dp.scale(s))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(28.dp.scale(s))
                )
            }

            Spacer(modifier = Modifier.width(16.dp.scale(s)))

            // Logo
            Box(
                modifier = Modifier
                    .size(40.dp.scale(s))
                    .clip(RoundedCornerShape(12.dp.scale(s)))
                    .background(PrimaryYellow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BackgroundDark,
                    modifier = Modifier.size(24.dp.scale(s))
                )
            }

            Spacer(modifier = Modifier.width(12.dp.scale(s)))

            Text(
                text = "设置中心",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (MaterialTheme.typography.headlineMedium.fontSize.value * s + 2f).sp
                ),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp.scale(s)))

        // 导航菜单
        categories.forEachIndexed { index, category ->
            val isSelected = index == selectedIndex
            var isFocused by remember { mutableStateOf(false) }
            val itemFocusRequester = remember { FocusRequester() }
            Button(
                onClick = { onCategorySelected(index) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Color.Transparent else Color.Transparent,
                    contentColor = if (isSelected) PrimaryYellow else TextPrimary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp.scale(s))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp.scale(s))
                    .focusRequester(itemFocusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusProperties {
                        // 右键移动到右侧内容区
                        right = contentFocusRequester
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp.scale(s), vertical = 13.dp.scale(s))
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = when {
                            isFocused -> BackgroundDark
                            isSelected -> PrimaryYellow
                            else -> TextPrimary
                        },
                        modifier = Modifier.size(24.dp.scale(s))
                    )
                    Spacer(modifier = Modifier.width(16.dp.scale(s)))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * s + 2f).sp
                        ),
                        color = when {
                            isFocused -> BackgroundDark
                            isSelected -> PrimaryYellow
                            else -> TextPrimary
                        }
                    )
                    // 版本更新红点提示
                    if (index == 4 && hasUpdate) { // 4 是"关于应用"的索引
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(8.dp.scale(s))
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 系统时间 - 动态更新
        var currentTime by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                currentTime = sdf.format(Date())
                delay(60000) // 每分钟更新一次
            }
        }
        Text(
            text = "系统时间：$currentTime",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
            ),
            color = TextMuted,
            modifier = Modifier.padding(bottom = 16.dp.scale(s)) // 增加底部内边距，避免被截断
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SettingsContent(
    selectedCategory: Int,
    onShowWebDavDialog: () -> Unit,
    onShowGuangyaDialog: () -> Unit,
    onShowSortDialog: () -> Unit,
    onShowClassificationStrategyDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit,
    onShowForceRescrapeDialog: () -> Unit,
    onShowClearWatchHistoryDialog: () -> Unit,
    onShowTmdbApiDialog: () -> Unit,
    onShowClearTmdbApiDialog: () -> Unit,
    onShowVersionUpdateDialog: () -> Unit,
    onShowClearPlaybackStatsDialog: () -> Unit,
    onShowScrapeRuleDialog: () -> Unit,
    hasUpdate: Boolean,
    versionInfo: VersionInfo?,
    hasCustomTmdbKey: Boolean = false,
    currentApiKey: String = "",
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    val s = LocalCompactUiScale.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        SurfaceDark.copy(alpha = 0.5f)
                    )
                )
            )
            .padding(48.dp.scale(s))
            .focusRequester(contentFocusRequester)
            .focusProperties {
                // 左键返回到侧边栏
                left = sidebarFocusRequester
            }
    ) {
        androidx.tv.foundation.lazy.list.TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp.scale(s)),
            pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0.1f)
        ) {
            when (selectedCategory) {
                0 -> { // 资源管理
                    item {
                        SectionTitle(title = "资源导入", accentColor = PrimaryYellow)
                        Spacer(modifier = Modifier.height(16.dp.scale(s)))
                        ResourceImportSection(
                            onShowGuangyaDialog = onShowGuangyaDialog,
                            onShowWebDavDialog = onShowWebDavDialog
                        )
                    }
                }
                1 -> { // 首页设置
                    item {
                        SectionTitle(title = "首页设置", accentColor = PrimaryYellow)
                        Spacer(modifier = Modifier.height(16.dp.scale(s)))
                        HomeSettingsSection(
                            onShowSortDialog = onShowSortDialog,
                            onShowClassificationStrategyDialog = onShowClassificationStrategyDialog,
                            onShowClearCacheDialog = onShowClearCacheDialog,
                            onShowForceRescrapeDialog = onShowForceRescrapeDialog,
                            onShowTmdbApiDialog = onShowTmdbApiDialog,
                            onShowClearTmdbApiDialog = onShowClearTmdbApiDialog,
                            hasCustomTmdbKey = hasCustomTmdbKey,
                            currentApiKey = currentApiKey
                        )
                    }
                }
                2 -> { // 播放设置
                    item {
                        SectionTitle(title = "播放设置", accentColor = PrimaryYellow)
                        Spacer(modifier = Modifier.height(16.dp.scale(s)))
                        PlaybackSettingsSection(
                            onShowClearWatchHistoryDialog = onShowClearWatchHistoryDialog
                        )
                    }
                }
                3 -> { // 直播设置
                    item {
                        SectionTitle(title = "直播设置", accentColor = PrimaryYellow)
                        Spacer(modifier = Modifier.height(16.dp.scale(s)))
                        LiveSettingsSection()
                    }
                }
                4 -> { // 关于应用
                    item {
                        SectionTitle(title = "关于应用", accentColor = PrimaryYellow)
                        Spacer(modifier = Modifier.height(16.dp.scale(s)))
                        AboutSection(
                            hasUpdate = hasUpdate,
                            versionInfo = versionInfo,
                            onVersionUpdateClick = onShowVersionUpdateDialog,
                            onStatsClick = onShowClearPlaybackStatsDialog,
                            onScrapeRuleClick = onShowScrapeRuleDialog
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(title: String, accentColor: Color) {
    val s = LocalCompactUiScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp.scale(s))
                .height(24.dp.scale(s))
                .clip(RoundedCornerShape(2.dp.scale(s)))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(12.dp.scale(s)))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * s + 2f).sp
            ),
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResourceImportSection(
    onShowGuangyaDialog: () -> Unit,
    onShowWebDavDialog: () -> Unit
) {
    val s = LocalCompactUiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp.scale(s))
    ) {
        // 连通网盘
        SettingCard(
            icon = Icons.Default.CloudUpload,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF60a5fa),
            title = "添加光鸭云盘",
            subtitle = "支持二级文件夹扫描与极速预览",
            onClick = onShowGuangyaDialog,
            modifier = Modifier.weight(1f),
            isFirstItem = true
        )

        // 添加WebDAV网盘
        SettingCard(
            icon = Icons.Default.Folder,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF34d399),
            title = "添加WebDAV网盘",
            subtitle = "通过局域网或外部存储进行挂载",
            onClick = onShowWebDavDialog,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSettingsSection(
    onShowSortDialog: () -> Unit,
    onShowClassificationStrategyDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit,
    onShowForceRescrapeDialog: () -> Unit,
    onShowTmdbApiDialog: () -> Unit,
    onShowClearTmdbApiDialog: () -> Unit,
    hasCustomTmdbKey: Boolean = false,
    currentApiKey: String = ""
) {
    val resourceLibraryViewModel: com.lomen.tv.ui.viewmodel.ResourceLibraryViewModel = hiltViewModel()
    val currentLibrary = resourceLibraryViewModel.getCurrentLibrary()
    val context = LocalContext.current
    val classificationPrefs = remember { MediaClassificationPreferences(context) }
    val scanPrefs = remember { com.lomen.tv.data.preferences.LibraryScanPreferences(context) }
    val strategyLabel by classificationPrefs.classificationStrategy.collectAsState(
        initial = MediaClassificationPreferences.DEFAULT_STRATEGY
    )
    val scanConcurrency by scanPrefs.scanConcurrency.collectAsState(
        initial = com.lomen.tv.data.preferences.LibraryScanPreferences.DEFAULT_SCAN_CONCURRENCY
    )
    val coroutineScope = rememberCoroutineScope()
    var showScanConcurrencyDialog by remember { mutableStateOf(false) }
    val s = LocalCompactUiScale.current
    val gap = 16.dp.scale(s)
    val scanConcurrencyLabel = when (scanConcurrency) {
        6 -> "低（更稳，适合弱网络）"
        14 -> "高（更快，适合高性能服务器）"
        else -> "中（推荐）"
    }
    
    Column {
        // 媒体分类排序
        SettingCard(
            icon = Icons.Default.Settings,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF34d399),
            title = "媒体分类排序",
            subtitle = "自定义首页显示顺序（电视剧、动漫、电影、综艺等）",
            onClick = onShowSortDialog,
            modifier = Modifier.fillMaxWidth(),
            isFirstItem = true
        )
        
        Spacer(modifier = Modifier.height(gap))

        val strategySubtitle = when (strategyLabel) {
            MediaClassificationStrategy.SMART_BALANCED ->
                "当前：智能均衡（综合季集结构与类型词，优先减少误判，推荐）"
            MediaClassificationStrategy.KEYWORD_FIRST ->
                "当前：关键词优先（路径/文件名中的类型词优先于季集结构）"
            MediaClassificationStrategy.STRUCTURE_FIRST ->
                "当前：结构优先（季集、纯集数等结构优先于类型词）"
        }
        SettingCard(
            icon = Icons.Default.Speed,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFf472b6),
            title = "媒体类型识别策略",
            subtitle = strategySubtitle,
            onClick = onShowClassificationStrategyDialog,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(gap))
        
        // TMDB API 设置 - 显示当前状态
        // 判断是否有自定义API Key：有自定义Key且不等于默认值
        val isCustomKey = hasCustomTmdbKey && 
                          currentApiKey.isNotBlank() && 
                          currentApiKey != TmdbApiPreferences.DEFAULT_API_KEY
        
        val apiStatusText = if (isCustomKey) {
            // 显示API Key的前8位和后4位，中间用...代替
            val keyLength = currentApiKey.length
            if (keyLength > 12) {
                "${currentApiKey.take(8)}...${currentApiKey.takeLast(4)}"
            } else {
                currentApiKey
            }
        } else {
            "API为空 请尽快配置"
        }
        SettingCard(
            icon = Icons.Default.CloudUpload,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF3b82f6),
            title = "TMDB API 设置",
            subtitle = apiStatusText,
            onClick = onShowTmdbApiDialog,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(gap))
        
        // 强制全量刮削
        SettingCard(
            icon = Icons.Default.Refresh,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFa78bfa),
            title = "强制全量刮削",
            subtitle = if (currentLibrary != null) "重新刮削所有媒体文件（忽略缓存）" else "请先选择资源库",
            onClick = { if (currentLibrary != null) onShowForceRescrapeDialog() },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(gap))

        SettingCard(
            icon = Icons.Default.Speed,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF22c55e),
            title = "资源扫描并发",
            subtitle = "当前：$scanConcurrencyLabel（$scanConcurrency 线程）",
            onClick = { showScanConcurrencyDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(gap))
        
        // 清除缓存
        SettingCard(
            icon = Icons.Default.Refresh,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFfb923c),
            title = "清除缓存数据",
            subtitle = "清除所有媒体元数据缓存，保留服务器列表",
            onClick = onShowClearCacheDialog,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(gap))
        
        // 清除 TMDB API Key
        SettingCard(
            icon = Icons.Default.CloudUpload,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFef4444),
            title = "清除 TMDB API Key",
            subtitle = if (isCustomKey) "清除已配置的自定义 API Key" else "暂无自定义 API Key 数据",
            onClick = onShowClearTmdbApiDialog,
            modifier = Modifier.fillMaxWidth()
        )

        if (showScanConcurrencyDialog) {
            ScanConcurrencySelectDialog(
                options = com.lomen.tv.data.preferences.LibraryScanPreferences.AVAILABLE_CONCURRENCY_LEVELS,
                selectedConcurrency = scanConcurrency,
                getOptionLabel = { value ->
                    when (value) {
                        6 -> "低（更稳，适合弱网络）"
                        14 -> "高（更快，适合高性能服务器）"
                        else -> "中（推荐）"
                    }
                },
                onSelect = { selected ->
                    coroutineScope.launch {
                        scanPrefs.setScanConcurrency(selected)
                    }
                    showScanConcurrencyDialog = false
                },
                onDismiss = { showScanConcurrencyDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScanConcurrencySelectDialog(
    options: List<Int>,
    selectedConcurrency: Int,
    getOptionLabel: (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val itemFocusRequesters = remember(options) { List(options.size) { FocusRequester() } }
    val selectedIndex = options.indexOf(selectedConcurrency).let { if (it >= 0) it else 0 }
    LaunchedEffect(options, selectedConcurrency) {
        if (options.isNotEmpty()) {
            delay(100)
            itemFocusRequesters[selectedIndex].requestFocus()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(DialogUiTokens.ContainerColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "资源扫描并发",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            options.forEachIndexed { index, concurrency ->
                val isSelected = concurrency == selectedConcurrency
                var isFocused by remember(concurrency) { mutableStateOf(false) }

                Button(
                    onClick = { onSelect(concurrency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .focusRequester(itemFocusRequesters[index])
                        .onFocusChanged { isFocused = it.isFocused },
                    scale = ButtonDefaults.scale(
                        scale = 1.0f,
                        focusedScale = 1.02f,
                        pressedScale = 1.0f
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.25f) else SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "${getOptionLabel(concurrency)}（${concurrency}线程）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isFocused) Color.Black else if (isSelected) PrimaryYellow else TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackSettingsSection(
    onShowClearWatchHistoryDialog: () -> Unit = {}
) {
    val s = LocalCompactUiScale.current
    val playerSettingsPreferences = androidx.hilt.navigation.compose.hiltViewModel<com.lomen.tv.ui.viewmodel.PlayerSettingsViewModel>()
    val autoSkipEnabled by playerSettingsPreferences.autoSkipIntroOutro.collectAsState(initial = true)
    val rememberPlaybackEnabled by playerSettingsPreferences.rememberPlaybackPosition.collectAsState(initial = true)
    val seekDurationSeconds by playerSettingsPreferences.seekDurationSeconds.collectAsState(initial = 15)
    val coroutineScope = rememberCoroutineScope()
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    val seekOptions = remember { listOf(15, 20, 25, 30, 35) }
    
    Column {
        // 快进快退时长
        SettingListItem(
            title = "快进快退时长",
            subtitle = "设置遥控器左右键跳转的秒数",
            onClick = { showSeekDurationDialog = true },
            trailing = { itemFocused ->
                Text(
                    text = "${seekDurationSeconds}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (itemFocused) Color.Black else PrimaryYellow,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            isFirstItem = true
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 自动跳过片头片尾
        var autoSkipFocused by remember { mutableStateOf(false) }
        val autoSkipEnabledState by rememberUpdatedState(autoSkipEnabled)
        SettingListItem(
            title = "自动跳过片头片尾",
            subtitle = "智能识别影视内容，自动跳转至正片",
            onClick = {
                // 点击整行也切换开关
                val newValue = !autoSkipEnabledState
                android.util.Log.d("SettingsScreen", "Auto skip intro outro row clicked, new value: $newValue")
                coroutineScope.launch {
                    playerSettingsPreferences.setAutoSkipIntroOutro(newValue)
                }
            },
            trailing = { itemFocused ->
                Box(
                    modifier = Modifier
                        .border(
                            width = 2.dp,
                            color = if (itemFocused || autoSkipFocused) Color.White else Color(0xFF1a1a1e),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    Switch(
                        checked = autoSkipEnabled,
                        onCheckedChange = null, // TV 上使用 onClick 处理
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if ((itemFocused || autoSkipFocused) && autoSkipEnabled) {
                                Color(0xFFF59E0B) // 橙黄色 - 开启+聚焦
                            } else if (autoSkipEnabled) {
                                com.lomen.tv.ui.theme.SuccessGreen // 绿色 - 开启
                            } else {
                                Color(0xFF444444) // 深灰 - 关闭
                            },
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF444444) // 深灰 - 关闭
                        ),
                        modifier = Modifier.onFocusChanged { autoSkipFocused = it.isFocused }
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 记忆续播功能
        var rememberPlaybackFocused by remember { mutableStateOf(false) }
        val rememberPlaybackEnabledState by rememberUpdatedState(rememberPlaybackEnabled)
        SettingListItem(
            title = "记忆续播功能",
            subtitle = "自动记录播放进度，下次打开即刻续播",
            onClick = {
                // 点击整行也切换开关
                val newValue = !rememberPlaybackEnabledState
                android.util.Log.d("SettingsScreen", "Remember playback position row clicked, new value: $newValue")
                coroutineScope.launch {
                    playerSettingsPreferences.setRememberPlaybackPosition(newValue)
                }
            },
            trailing = { itemFocused ->
                Box(
                    modifier = Modifier
                        .border(
                            width = 2.dp,
                            color = if (itemFocused || rememberPlaybackFocused) Color.White else Color(0xFF1a1a1e),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    Switch(
                        checked = rememberPlaybackEnabled,
                        onCheckedChange = null, // TV 上使用 onClick 处理
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if ((itemFocused || rememberPlaybackFocused) && rememberPlaybackEnabled) {
                                Color(0xFFF59E0B) // 橙黄色 - 开启+聚焦
                            } else if (rememberPlaybackEnabled) {
                                com.lomen.tv.ui.theme.SuccessGreen // 绿色 - 开启
                            } else {
                                Color(0xFF444444) // 深灰 - 关闭
                            },
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF444444) // 深灰 - 关闭
                        ),
                        modifier = Modifier.onFocusChanged { rememberPlaybackFocused = it.isFocused }
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp.scale(s)))

        // 清空最近播放记录
        SettingCard(
            icon = Icons.Default.Refresh,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFef4444),
            title = "清空最近播放记录",
            subtitle = "清除所有最近播放记录，此操作不可恢复",
            onClick = onShowClearWatchHistoryDialog,
            modifier = Modifier.fillMaxWidth()
        )

        if (showSeekDurationDialog) {
            SeekDurationSelectDialog(
                options = seekOptions,
                selectedSeconds = seekDurationSeconds,
                onSelect = { seconds ->
                    coroutineScope.launch {
                        playerSettingsPreferences.setSeekDurationSeconds(seconds)
                    }
                    showSeekDurationDialog = false
                },
                onDismiss = { showSeekDurationDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekDurationSelectDialog(
    options: List<Int>,
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val itemFocusRequesters = remember(options) { List(options.size) { FocusRequester() } }
    val selectedIndex = options.indexOf(selectedSeconds).let { if (it >= 0) it else 0 }
    LaunchedEffect(options, selectedSeconds) {
        if (options.isNotEmpty()) {
            delay(100)
            itemFocusRequesters[selectedIndex].requestFocus()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(DialogUiTokens.ContainerColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "快进快退时长",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            options.forEachIndexed { index, seconds ->
                val isSelected = seconds == selectedSeconds
                var isFocused by remember(seconds) { mutableStateOf(false) }

                Button(
                    onClick = { onSelect(seconds) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .focusRequester(itemFocusRequesters[index])
                        .onFocusChanged { isFocused = it.isFocused },
                    scale = ButtonDefaults.scale(
                        scale = 1.0f,
                        focusedScale = 1.02f,
                        pressedScale = 1.0f
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.25f) else SurfaceDark,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "${seconds}s",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isFocused) Color.Black else if (isSelected) PrimaryYellow else TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutSection(
    hasUpdate: Boolean,
    versionInfo: VersionInfo?,
    onVersionUpdateClick: () -> Unit,
    onStatsClick: () -> Unit,
    onScrapeRuleClick: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val homeViewModel: com.lomen.tv.ui.screens.home.HomeViewModel = hiltViewModel()
    val totalPlayTime by homeViewModel.playbackStatsService.totalPlaybackTimeMs.collectAsState(initial = 0L)

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp.scale(s))
        ) {
            // 版本更新 - 从BuildConfig动态获取当前版本号
            val currentVersion = BuildConfig.VERSION_NAME
            val versionSubtitle = if (hasUpdate && versionInfo != null) {
                "当前版本: v$currentVersion | 最新版本: v${versionInfo.versionName}"
            } else {
                "当前版本: v$currentVersion (稳定版)"
            }
            InfoCard(
                icon = Icons.Default.Refresh,
                iconBackgroundColor = Color.White.copy(alpha = 0.4f),
                iconTint = Color(0xFFc084fc),
                title = "版本更新",
                subtitle = versionSubtitle,
                badge = if (hasUpdate) "New" else null,
                onClick = onVersionUpdateClick,
                modifier = Modifier.weight(1f),
                isFirstItem = true
            )

            // 应用统计 - 总播放时间（汇总全部观看记录 progress）
            val hours = totalPlayTime / (1000 * 60 * 60)
            val minutes = (totalPlayTime / (1000 * 60)) % 60
            val seconds = (totalPlayTime / 1000) % 60
            val timeText = when {
                hours > 0 -> "总播放时间: ${hours}小时${minutes}分钟"
                minutes > 0 -> "总播放时间: ${minutes}分${seconds}秒"
                seconds > 0 -> "总播放时间: ${seconds}秒"
                else -> "总播放时间: 0秒"
            }
            InfoCard(
                icon = Icons.Default.Timer,
                iconBackgroundColor = Color.White.copy(alpha = 0.4f),
                iconTint = TextMuted,
                title = "应用统计",
                subtitle = timeText,
                badge = null,
                onClick = onStatsClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp.scale(s)))

        SettingCard(
            icon = Icons.Default.Info,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF60a5fa),
            title = "刮削规则",
            subtitle = "查看支持的文件夹命名与文件名识别规则",
            onClick = onScrapeRuleClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp.scale(s)))

        // 应用信息
        var isFocused by remember { mutableStateOf(false) }
        Card(
            onClick = {},
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow
            ),
            scale = CardDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.02f,
                pressedScale = 1.0f
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Column(
                modifier = Modifier.padding(24.dp.scale(s))
            ) {
                Text(
                    text = "柠檬TV",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black else TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))
                Text(
                    text = "一款专为Android TV设计的视频播放器，支持WebDAV网盘资源导入、IPTV直播、智能跳过片头片尾、记忆续播等功能。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.8f) else TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))
                Text(
                    text = "GitHub: https://github.com/jia070310/lomenTV-VDS",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp.scale(s)))
                Text(
                    text = "© 2026 柠檬TV 版权所有",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else TextMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ScrapeRuleDialog(
    onDismiss: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val scrollState = rememberScrollState()
    var closeFocused by remember { mutableStateOf(false) }
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { keyEvent ->
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
                .width(900.dp.scale(s))
                .height(620.dp.scale(s))
                .padding(24.dp.scale(s))
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .focusProperties {
                    // 弹窗打开后禁止焦点跳出弹窗
                    exit = { FocusRequester.Cancel }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp.scale(s))
            ) {
                Text(
                    text = "刮削规则说明",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp.scale(s)))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text("1. 扫描范围：会递归扫描网盘目录中的视频文件。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("2. 文件夹结构优先：若目录中包含季/集结构（如 S01、Season 1、第1季），优先识别为剧集。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("3. 文件名识别季集：支持 S01E01、S1E2、第01集、E03、EP04 等常见命名。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("4. 类型关键词识别：文件夹或文件名包含 电影、剧场版、动漫、综艺、纪录片、演唱会 等关键词时，会辅助分类。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("5. NFO 优先：同目录存在 .nfo 时优先读取本地元数据（标题、简介、年份等）。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("6. 在线补全：本地信息不足时再请求 TMDB/豆瓣补全封面与详情。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("7. 增量规则：按文件指纹判断变更，未变化文件会跳过，已变化文件重新刮削。", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text("8. 流式展示：刮削每成功一批会立即入库，首页会按成功比例逐步显示。", color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(16.dp.scale(s)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        modifier = Modifier
                            .focusRequester(closeFocusRequester)
                            .onFocusChanged { closeFocused = it.isFocused }
                            .focusProperties {
                                // 当前弹窗只有一个可聚焦按钮，方向键保持在窗口内
                                up = closeFocusRequester
                                down = closeFocusRequester
                                left = closeFocusRequester
                                right = closeFocusRequester
                            }
                    ) {
                        Text(
                            text = "知道了",
                            color = if (closeFocused) BackgroundDark else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SettingCard(
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false
) {
    val s = LocalCompactUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // 第一个项目自动获取焦点
    LaunchedEffect(Unit) {
        if (isFirstItem) {
            focusRequester.requestFocus()
        }
    }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.02f,
            pressedScale = 1.0f
        ),
        modifier = modifier
            .height(88.dp.scale(s))
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp.scale(s), vertical = 13.dp.scale(s)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp.scale(s))
                    .clip(RoundedCornerShape(10.dp.scale(s)))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else iconTint,
                    modifier = Modifier.size(22.dp.scale(s))
                )
            }

            Spacer(modifier = Modifier.width(14.dp.scale(s)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp.scale(s)))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.8f) else TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SettingListItem(
    title: String,
    subtitle: String,
    trailing: @Composable (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    isFirstItem: Boolean = false
) {
    val s = LocalCompactUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // 第一个项目自动获取焦点
    LaunchedEffect(Unit) {
        if (isFirstItem) {
            focusRequester.requestFocus()
        }
    }
    
    Card(
        onClick = { onClick?.invoke() },
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.02f,
            pressedScale = 1.0f
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp.scale(s), vertical = 22.dp.scale(s)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black else TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp.scale(s)))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else TextMuted
                )
            }
            trailing(isFocused)
        }
    }
}

private val autoRefreshIntervalOptions = listOf(0, 1, 2, 4, 6, 12)

/** 定时刷新：右侧药丸按钮背景（与参考图一致，非高亮态） */
private val AutoRefreshTriggerPillIdleColor = Color(0xFF333333)

/** 与直播设置项 Switch 行内高度接近；下拉内文字与触发器一致 */
private val AutoRefreshTriggerTextSize = 14.sp
private val AutoRefreshTriggerLineHeight = 17.sp
private val AutoRefreshTriggerIconSize = 16.dp
/** TV Switch 轨道视觉约 32dp 高，药丸与之对齐 */
private val AutoRefreshTriggerHeight = 34.dp

private val AutoRefreshDropdownMenuWidth = 92.dp

/** 菜单内选项行高（紧凑，比例接近参考图） */
private val AutoRefreshDropdownItemHeight = 24.dp

/** 上拉菜单底边与下方药丸按钮顶之间的空隙（约一行高度，参照设计图） */
private val AutoRefreshMenuGapAboveTrigger = 26.dp

/** 上拉菜单相对锚点的额外平移（右正、下正）；勿用 remember(density) 缓存，否则改数值不生效 */
private val AutoRefreshPopupExtraOffsetX = 5.dp
private val AutoRefreshPopupExtraOffsetY = 5.dp

/** 菜单面板圆角（略大，接近参考图） */
private val AutoRefreshDropdownPanelShape = RoundedCornerShape(16.dp)

private val AutoRefreshPillShape = RoundedCornerShape(percent = 50)

/**
 * 定时刷新下拉：深色圆角面板；项为白字，焦点行黄底黑字（与参考图一致）。
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun AutoRefreshIntervalDropdownMenu(
    selectedHours: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val itemFocusRequesters = remember(autoRefreshIntervalOptions) {
        List(autoRefreshIntervalOptions.size) { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        val idx = autoRefreshIntervalOptions.indexOf(selectedHours).takeIf { it >= 0 } ?: 0
        delay(48)
        itemFocusRequesters.getOrNull(idx)?.requestFocus()
    }
    Box(
        modifier = Modifier
            .width(AutoRefreshDropdownMenuWidth)
            .shadow(8.dp, AutoRefreshDropdownPanelShape, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(AutoRefreshDropdownPanelShape)
            .background(Color(0xFF2A2A2A))
            .focusProperties {
                exit = { FocusRequester.Cancel }
            }
    ) {
        // 不滚动：六项一次完整展示；内边距让高亮胶囊略窄于面板（参照图）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            autoRefreshIntervalOptions.forEachIndexed { index, hours ->
                val isSelected = hours == selectedHours
                val label = if (hours == 0) "关闭" else "${hours}小时"
                val prev = itemFocusRequesters.getOrNull(index - 1)
                val next = itemFocusRequesters.getOrNull(index + 1)
                Button(
                    onClick = {
                        onSelect(hours)
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = PrimaryYellow,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = PrimaryYellow,
                        pressedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(shape = AutoRefreshPillShape),
                    modifier = Modifier
                        .focusRequester(itemFocusRequesters[index])
                        .focusProperties {
                            up = prev ?: FocusRequester.Cancel
                            down = next ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .fillMaxWidth()
                        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                        .height(AutoRefreshDropdownItemHeight)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Unspecified,
                            fontSize = AutoRefreshTriggerTextSize,
                            lineHeight = AutoRefreshTriggerLineHeight
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun InfoCard(
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false
) {
    val s = LocalCompactUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // 第一个项目自动获取焦点
    LaunchedEffect(Unit) {
        if (isFirstItem) {
            focusRequester.requestFocus()
        }
    }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = PrimaryYellow
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.02f,
            pressedScale = 1.0f
        ),
        modifier = modifier
            .height(100.dp.scale(s))
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp.scale(s)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp.scale(s))
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else iconTint,
                    modifier = Modifier.size(24.dp.scale(s))
                )
            }

            Spacer(modifier = Modifier.width(16.dp.scale(s)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black else TextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp.scale(s)))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else TextMuted
                )
            }

            badge?.let {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp.scale(s)))
                        .background(if (isFocused) Color.Black.copy(alpha = 0.2f) else PrimaryYellow.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp.scale(s), vertical = 4.dp.scale(s))
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (MaterialTheme.typography.labelSmall.fontSize.value * s + 2f).sp
                        ),
                        color = if (isFocused) Color.Black else PrimaryYellow,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsBottomBar(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassBackground)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上下键提示
            KeyHint(key = "↑↓", description = "切换选项")
            // OK键提示
            KeyHint(key = "OK", description = "确定")
            // 返回键提示
            KeyHint(key = "Back", description = "返回")
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyHint(key: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, TextMuted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

// Data class for setting categories
private data class SettingCategory(
    val name: String,
    val icon: ImageVector
)

/**
 * 确认对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
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
                    // 禁止焦点向外移动，实现焦点陷阱
                    canFocus = true
                }
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮 - 未选中时灰色，高亮时黄色底黑色字
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
                            .focusRequester(focusRequester)
                            .onFocusChanged { cancelButtonFocused = it.isFocused }
                            .focusProperties {
                                // 锁定左键和上键，防止光标移出窗口
                                left = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "取消",
                            color = if (cancelButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // 确定按钮 - 未选中时灰色，高亮时黄色底黑色字
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
                            .onFocusChanged { confirmButtonFocused = it.isFocused }
                            .focusProperties {
                                // 锁定上键，防止光标移出窗口
                                up = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "确定",
                            color = if (confirmButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                }
            }
        }
    }
    
    // 对话框显示时，默认将焦点给"取消"按钮（安全操作），避免误触"确定"
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 清除 TMDB API Key 对话框
 * 有数据时显示确认和取消按钮，无数据时只显示提示和取消按钮
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ClearTmdbApiDialog(
    hasCustomKey: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { keyEvent ->
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
                    canFocus = true
                    // 焦点陷阱：防止焦点从该对话框“跑出去”
                    exit = { FocusRequester.Cancel }
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(DialogDimens.CardPaddingInner)
                    .focusGroup()
            ) {
                Text(
                    text = "清除 TMDB API Key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (hasCustomKey) {
                        "确定要清除已配置的自定义 TMDB API Key 吗？\n清除后将使用默认 API Key。"
                    } else {
                        "暂无自定义 TMDB API Key 数据"
                    },
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
                            .focusRequester(focusRequester)
                            .onFocusChanged { cancelButtonFocused = it.isFocused }
                            .focusProperties {
                                left = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "取消",
                            color = if (cancelButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                    
                    // 有数据时才显示确定按钮
                    if (hasCustomKey) {
                        Spacer(modifier = Modifier.width(16.dp))
                        
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
                                .onFocusChanged { confirmButtonFocused = it.isFocused }
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = "确定",
                                color = if (confirmButtonFocused) BackgroundDark else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 对话框显示时聚焦到取消按钮
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 媒体分类排序对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun MediaTypeSortDialog(
    sortPreferences: com.lomen.tv.data.preferences.MediaTypeSortPreferences,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val currentOrder by sortPreferences.sortOrder.collectAsState(
        initial = com.lomen.tv.data.preferences.MediaTypeSortPreferences.DEFAULT_SORT_ORDER
    )
    var editableOrder by remember(currentOrder) { mutableStateOf(currentOrder) }
    
    val mediaTypeLabels = mapOf(
        com.lomen.tv.domain.model.MediaType.TV_SHOW to "电视剧",
        com.lomen.tv.domain.model.MediaType.ANIME to "动漫",
        com.lomen.tv.domain.model.MediaType.MOVIE to "电影",
        com.lomen.tv.domain.model.MediaType.VARIETY to "综艺",
        com.lomen.tv.domain.model.MediaType.CONCERT to "演唱会",
        com.lomen.tv.domain.model.MediaType.DOCUMENTARY to "纪录片",
        com.lomen.tv.domain.model.MediaType.OTHER to "其它"
    )
    
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
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
                .width(DialogDimens.CardWidthSort)
                .height(DialogDimens.CardHeightSort)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .focusProperties {
                    // 禁止焦点向外移动，实现焦点陷阱
                    canFocus = true
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DialogDimens.CardPaddingInner)
            ) {
                // 标题栏（固定）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "媒体分类排序",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    
                    // 恢复默认按钮 - 未选中时灰色，高亮时黄色底黑色字
                    var restoreButtonFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            editableOrder = com.lomen.tv.data.preferences.MediaTypeSortPreferences.DEFAULT_SORT_ORDER
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { restoreButtonFocused = it.isFocused }
                            .focusProperties {
                                // 锁定上键，防止光标移出窗口
                                up = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "恢复默认",
                            color = if (restoreButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 媒体类型列表（可滚动）
                androidx.tv.foundation.lazy.list.TvLazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(editableOrder.size) { index ->
                        val mediaType = editableOrder[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BackgroundDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryYellow
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = mediaTypeLabels[mediaType] ?: mediaType.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                            
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val newList = editableOrder.toMutableList()
                                            newList[index] = newList[index - 1].also { newList[index - 1] = newList[index] }
                                            editableOrder = newList
                                        },
                                        colors = IconButtonDefaults.colors(
                                            containerColor = Color.Transparent,
                                            contentColor = TextMuted,
                                            focusedContainerColor = PrimaryYellow,
                                            focusedContentColor = BackgroundDark
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "上移",
                                            modifier = Modifier.size(20.dp).rotate(-90f)
                                        )
                                    }
                                }
                                if (index < editableOrder.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val newList = editableOrder.toMutableList()
                                            newList[index] = newList[index + 1].also { newList[index + 1] = newList[index] }
                                            editableOrder = newList
                                        },
                                        colors = IconButtonDefaults.colors(
                                            containerColor = Color.Transparent,
                                            contentColor = TextMuted,
                                            focusedContainerColor = PrimaryYellow,
                                            focusedContentColor = BackgroundDark
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "下移",
                                            modifier = Modifier.size(20.dp).rotate(90f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮（固定）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮 - 未选中时灰色，高亮时黄色底黑色字
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
                            .onFocusChanged { cancelButtonFocused = it.isFocused }
                            .focusProperties {
                                // 锁定左键，防止光标移出窗口
                                left = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "取消",
                            color = if (cancelButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // 保存按钮 - 未选中时灰色，高亮时黄色底黑色字
                    var saveButtonFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                sortPreferences.saveSortOrder(editableOrder)
                                onSuccess()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        modifier = Modifier.onFocusChanged { saveButtonFocused = it.isFocused }
                    ) {
                        Text(
                            text = "保存",
                            color = if (saveButtonFocused) BackgroundDark else TextMuted
                        )
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ClassificationStrategyOptionRow(
    selected: MediaClassificationStrategy,
    strategy: MediaClassificationStrategy,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    onSelect: (MediaClassificationStrategy) -> Unit
) {
    val isSel = selected == strategy
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = { onSelect(strategy) },
        colors = CardDefaults.colors(
            // 非焦点时不使用整块黄底，避免“已选项”长期高亮干扰导航
            containerColor = Color.Transparent,
            contentColor = if (isSel) PrimaryYellow else TextPrimary,
            focusedContainerColor = PrimaryYellow,
            focusedContentColor = BackgroundDark
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.02f,
            pressedScale = 1.0f
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .focusProperties {
                this.up = up ?: FocusRequester.Cancel
                this.down = down ?: FocusRequester.Cancel
                this.left = left ?: FocusRequester.Cancel
                this.right = right ?: FocusRequester.Cancel
            }
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 14.dp, end = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (isSel) "$title  · 当前" else title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) BackgroundDark else if (isSel) PrimaryYellow else TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) BackgroundDark else TextMuted
            )
        }
    }
}

/**
 * 媒体类型识别策略：结构优先 / 关键词优先（刮削时区分电影/电视剧/综艺等）
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ClassificationStrategyDialog(
    classificationPreferences: MediaClassificationPreferences,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val currentStrategy by classificationPreferences.classificationStrategy.collectAsState(
        initial = MediaClassificationPreferences.DEFAULT_STRATEGY
    )
    var selected by remember(currentStrategy) { mutableStateOf(currentStrategy) }
    val coroutineScope = rememberCoroutineScope()
    val smartFocusRequester = remember { FocusRequester() }
    val structureFocusRequester = remember { FocusRequester() }
    val keywordFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { keyEvent ->
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
                .width(DialogDimens.CardWidthClassification)
                .height(DialogDimens.CardHeightClassification)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .focusProperties { canFocus = true }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "媒体类型识别策略",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "影响刮削时如何区分电影、电视剧、综艺、纪录片等。修改后建议执行「强制全量刮削」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(20.dp))

                ClassificationStrategyOptionRow(
                    selected = selected,
                    strategy = MediaClassificationStrategy.SMART_BALANCED,
                    title = "智能均衡（推荐）",
                    description = "综合季集结构与类型关键词进行判定：既避免把电影误判成剧集，也减少综艺/纪录片误归类。",
                    modifier = Modifier.focusRequester(smartFocusRequester),
                    up = saveFocusRequester,
                    down = structureFocusRequester,
                    onSelect = { selected = it }
                )
                ClassificationStrategyOptionRow(
                    selected = selected,
                    strategy = MediaClassificationStrategy.STRUCTURE_FIRST,
                    title = "结构优先",
                    description = "优先根据文件名中的季集（S01E01）、纯集数等结构判断，再参考「综艺、纪录片」等关键词。",
                    modifier = Modifier.focusRequester(structureFocusRequester),
                    up = smartFocusRequester,
                    down = keywordFocusRequester,
                    onSelect = { selected = it }
                )
                ClassificationStrategyOptionRow(
                    selected = selected,
                    strategy = MediaClassificationStrategy.KEYWORD_FIRST,
                    title = "关键词优先",
                    description = "优先根据路径/文件名中的类型词（综艺、纪录片、动漫等）判断，再参考季集结构。适合按类型分文件夹整理的资源。",
                    modifier = Modifier.focusRequester(keywordFocusRequester),
                    up = structureFocusRequester,
                    down = cancelFocusRequester,
                    onSelect = { selected = it }
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var cancelFocused by remember { mutableStateOf(false) }
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
                            .onFocusChanged { cancelFocused = it.isFocused }
                            .focusProperties {
                                up = keywordFocusRequester
                                down = smartFocusRequester
                                left = saveFocusRequester
                                right = saveFocusRequester
                            }
                    ) {
                        Text(
                            text = "取消",
                            color = if (cancelFocused) BackgroundDark else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    var saveFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                classificationPreferences.saveClassificationStrategy(selected)
                                MediaClassificationStrategyHolder.update(selected)
                                onSuccess()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        modifier = Modifier
                            .focusRequester(saveFocusRequester)
                            .onFocusChanged { saveFocused = it.isFocused }
                            .focusProperties {
                                up = keywordFocusRequester
                                down = smartFocusRequester
                                left = cancelFocusRequester
                                right = cancelFocusRequester
                            }
                    ) {
                        Text(
                            text = "保存",
                            color = if (saveFocused) BackgroundDark else TextMuted
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(currentStrategy) {
        when (currentStrategy) {
            MediaClassificationStrategy.SMART_BALANCED -> smartFocusRequester.requestFocus()
            MediaClassificationStrategy.STRUCTURE_FIRST -> structureFocusRequester.requestFocus()
            MediaClassificationStrategy.KEYWORD_FIRST -> keywordFocusRequester.requestFocus()
        }
    }
}

/**
 * 成功提示Toast
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SuccessToast(message: String) {
    com.lomen.tv.ui.components.WhitePillToast(
        message = message,
        icon = Icons.Default.Info,
        bottomPadding = 100.dp,
        backgroundAlpha = 0.75f,
        iconTint = Color.Black,
        textColor = Color.Black
    )
}

/**
 * 刮削进度提示Toast
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScrapeProgressToast(
    message: String,
    progress: Pair<Int, Int>? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(
                containerColor = Color(0xFF3b82f6),
                focusedContainerColor = Color(0xFF3b82f6)
            ),
            modifier = Modifier.padding(bottom = 100.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (progress != null) {
                        val (current, total) = progress
                        Text(
                            text = "$current / $total",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 直播设置区域 - 列表卡片样式
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveSettingsPreferences = remember { com.lomen.tv.data.preferences.LiveSettingsPreferences(context) }
    
    // 状态
    var showLiveSourceHistory by remember { mutableStateOf(false) }
    var showEpgHistory by remember { mutableStateOf(false) }
    var showUaHistory by remember { mutableStateOf(false) }
    var showWebConfigQr by remember { mutableStateOf(false) }
    var showLiveWebConfigSuccessHint by remember { mutableStateOf(false) }
    var showAutoRefreshMenu by remember { mutableStateOf(false) }
    var pendingLiveWebPushKind by remember { mutableStateOf(LiveWebPushSuccessKind.MIXED) }
    
    // 收集当前设置值
    val liveSourceUrl by liveSettingsPreferences.liveSourceUrl.collectAsState(initial = "")
    val epgUrl by liveSettingsPreferences.epgUrl.collectAsState(initial = "")
    val userAgent by liveSettingsPreferences.userAgent.collectAsState(initial = "")
    val channelChangeFlip by liveSettingsPreferences.channelChangeFlip.collectAsState(initial = false)
    val channelNoSelectEnable by liveSettingsPreferences.channelNoSelectEnable.collectAsState(initial = true)
    val epgEnable by liveSettingsPreferences.epgEnable.collectAsState(initial = true)
    val autoEnterLive by liveSettingsPreferences.autoEnterLive.collectAsState(initial = false)
    val bootStartup by liveSettingsPreferences.bootStartup.collectAsState(initial = false)
    val autoRefreshInterval by liveSettingsPreferences.autoRefreshInterval.collectAsState(
        initial = LiveSettingsPreferences.DEFAULT_AUTO_REFRESH_INTERVAL_HOURS
    )
    val liveSourceHistory by liveSettingsPreferences.liveSourceHistory.collectAsState(initial = emptySet())
    val epgUrlHistory by liveSettingsPreferences.epgUrlHistory.collectAsState(initial = emptySet())
    val userAgentHistory by liveSettingsPreferences.userAgentHistory.collectAsState(initial = emptySet())
    
    // 获取WiFi IP
    val wifiIpAddress = remember { getWifiIpAddress(context) }
    val serverUrl = "http://$wifiIpAddress:8893/live"
    
    // 启动 WebDav 配置服务器用于直播设置
    val webDavServer = remember { WebDavConfigServer.getInstance(context, 8893) }
    DisposableEffect(showWebConfigQr) {
        val open = showWebConfigQr
        if (open) {
            webDavServer.startServerWithLiveConfig(
                onWebDavConfig = { /* 在直播设置页面不处理 WebDAV 配置 */ },
                onLiveConfig = { config ->
                    scope.launch {
                        if (config.liveSourceUrl.isNotBlank()) {
                            liveSettingsPreferences.addLiveSourceToHistory(
                                config.liveSourceName.takeIf { it.isNotBlank() } ?: "自定义源",
                                config.liveSourceUrl
                            )
                        }
                        if (config.epgUrl.isNotBlank()) {
                            liveSettingsPreferences.addEpgUrlToHistory(config.epgUrl)
                        }
                        if (config.userAgent.isNotBlank()) {
                            liveSettingsPreferences.addUserAgentToHistory(config.userAgent)
                        }
                        showWebConfigQr = false
                        showLiveWebConfigSuccessHint = true
                    }
                }
            )
        }
        onDispose {
            if (open) {
                webDavServer.stopServer()
            }
        }
    }
    
    val liveSectionS = LocalCompactUiScale.current
    val liveCardGap = 16.dp.scale(liveSectionS)
    Column(
        verticalArrangement = Arrangement.spacedBy(liveCardGap)
    ) {
        // 直播源配置卡片
        LiveSettingListCard(
            icon = Icons.Default.LiveTv,
            iconBackgroundColor = Color(0xFFf59e0b).copy(alpha = 0.2f),
            iconTint = Color(0xFFf59e0b),
            title = "直播源配置",
            subtitle = if (liveSourceUrl.isBlank()) {
                "未配置，点击选择历史记录或扫码添加"
            } else {
                liveSourceUrl
            },
            onClick = { showLiveSourceHistory = true },
            isFirstItem = true
        )
        
        // 节目单配置卡片
        LiveSettingListCard(
            icon = Icons.Default.Timer,
            iconBackgroundColor = Color(0xFF60a5fa).copy(alpha = 0.2f),
            iconTint = Color(0xFF60a5fa),
            title = "节目单配置",
            subtitle = if (epgUrl.isBlank()) "未配置节目单地址" else epgUrl,
            onClick = { showEpgHistory = true }
        )
        
        // 自定义 User-Agent 卡片
        LiveSettingListCard(
            icon = Icons.Default.Settings,
            iconBackgroundColor = Color(0xFF34d399).copy(alpha = 0.2f),
            iconTint = Color(0xFF34d399),
            title = "自定义 User-Agent",
            subtitle = if (userAgent.isBlank()) "未配置，将使用默认 UA" else userAgent,
            onClick = { showUaHistory = true }
        )
        
        Spacer(modifier = Modifier.height(8.dp.scale(liveSectionS)))
        
        // 开关设置 - 使用卡片样式
        // 换台反转
        SettingListItem(
            title = "换台方向反转",
            subtitle = "上下键换台方向反转",
            trailing = { itemFocused ->
                Switch(
                    checked = channelChangeFlip,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.lomen.tv.ui.theme.SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF444444)
                    )
                )
            },
            onClick = {
                scope.launch { liveSettingsPreferences.setChannelChangeFlip(!channelChangeFlip) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp.scale(liveSectionS)))

        // 数字选台
        SettingListItem(
            title = "启用数字选台",
            subtitle = "使用数字键快速输入频道号",
            trailing = { itemFocused ->
                Switch(
                    checked = channelNoSelectEnable,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.lomen.tv.ui.theme.SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF444444)
                    )
                )
            },
            onClick = {
                scope.launch { liveSettingsPreferences.setChannelNoSelectEnable(!channelNoSelectEnable) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp.scale(liveSectionS)))

        // 启用节目单
        SettingListItem(
            title = "启用节目单",
            subtitle = "显示频道节目信息",
            trailing = { itemFocused ->
                Switch(
                    checked = epgEnable,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.lomen.tv.ui.theme.SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF444444)
                    )
                )
            },
            onClick = {
                scope.launch { liveSettingsPreferences.setEpgEnable(!epgEnable) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp.scale(liveSectionS)))

        // 打开APP直接进入直播界面
        SettingListItem(
            title = "打开直接进入直播",
            subtitle = "启动APP后自动进入直播界面",
            trailing = { itemFocused ->
                Switch(
                    checked = autoEnterLive,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.lomen.tv.ui.theme.SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF444444)
                    )
                )
            },
            onClick = {
                scope.launch { liveSettingsPreferences.setAutoEnterLive(!autoEnterLive) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp.scale(liveSectionS)))

        // 开机启动
        SettingListItem(
            title = "开机启动",
            subtitle = "设备开机后自动启动APP并进入直播",
            trailing = { itemFocused ->
                Switch(
                    checked = bootStartup,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.lomen.tv.ui.theme.SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF444444)
                    )
                )
            },
            onClick = {
                scope.launch { liveSettingsPreferences.setBootStartup(!bootStartup) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp.scale(liveSectionS)))

        // 定时刷新直播源：药丸按钮 + 下拉（样式见参考图）
        SettingListItem(
            title = "定时刷新直播源",
            subtitle = "每隔指定时间自动刷新直播源（当前：${if (autoRefreshInterval > 0) "${autoRefreshInterval}小时" else "已关闭"}）",
            trailing = { _ ->
                val intervalLabel =
                    if (autoRefreshInterval > 0) "${autoRefreshInterval}小时" else "关闭"
                val density = LocalDensity.current
                // BottomEnd：先整体上移「药丸高度 + 空隙」，再叠加额外平移（每次重算，避免 remember 只依赖 density 导致改 dp 不生效）
                val popupOffset = with(density) {
                    IntOffset(
                        x = AutoRefreshPopupExtraOffsetX.roundToPx(),
                        y = -(AutoRefreshTriggerHeight + AutoRefreshMenuGapAboveTrigger).roundToPx() +
                            AutoRefreshPopupExtraOffsetY.roundToPx()
                    )
                }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    Button(
                        onClick = { showAutoRefreshMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = AutoRefreshTriggerPillIdleColor,
                            contentColor = Color.White,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = Color.Black,
                            pressedContainerColor = PrimaryYellow,
                            pressedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(shape = AutoRefreshPillShape),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                            .height(AutoRefreshTriggerHeight)
                            .wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = intervalLabel,
                                fontSize = AutoRefreshTriggerTextSize,
                                lineHeight = AutoRefreshTriggerLineHeight,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择刷新间隔",
                                modifier = Modifier.size(AutoRefreshTriggerIconSize)
                            )
                        }
                    }
                    if (showAutoRefreshMenu) {
                        Popup(
                            alignment = Alignment.BottomEnd,
                            offset = popupOffset,
                            onDismissRequest = { showAutoRefreshMenu = false },
                            properties = PopupProperties(
                                focusable = true,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = false
                            )
                        ) {
                            AutoRefreshIntervalDropdownMenu(
                                selectedHours = autoRefreshInterval,
                                onSelect = { hours ->
                                    scope.launch {
                                        liveSettingsPreferences.setAutoRefreshInterval(hours)
                                    }
                                },
                                onDismiss = { showAutoRefreshMenu = false }
                            )
                        }
                    }
                }
            },
            onClick = { showAutoRefreshMenu = true }
        )
    }
    
    // 直播源历史列表弹窗
    val currentLiveSourceItem = liveSourceHistory.find { it.second == liveSourceUrl }
    HistoryListDialog(
        title = "历史直播源",
        showDialogProvider = { showLiveSourceHistory },
        onDismissRequest = { showLiveSourceHistory = false },
        blockDismissForOverlay = showWebConfigQr || showLiveWebConfigSuccessHint,
        items = liveSourceHistory.toList(),
        currentItem = currentLiveSourceItem,
        onSelected = { item ->
            val (name, url) = item
            scope.launch { 
                liveSettingsPreferences.setLiveSourceUrl(url)
                liveSettingsPreferences.addLiveSourceToHistory(name, url)
            }
            showLiveSourceHistory = false
        },
        onDeleted = { item ->
            val (_, url) = item
            scope.launch { liveSettingsPreferences.removeLiveSourceFromHistory(url) }
        },
        onAddNew = {
            pendingLiveWebPushKind = LiveWebPushSuccessKind.LIVE_SOURCE
            showWebConfigQr = true
        },
        itemContent = { item, isSelected, isFocused ->
            val (name, url) = item
            Pair(name, url)
        },
        isBuiltInItem = { item ->
            LiveSettingsPreferences.BUILT_IN_LIVE_SOURCES.any { it.second == item.second }
        },
        addNewText = "添加其他直播源"
    )
    
    // 节目单历史列表弹窗
    HistoryListDialog(
        title = "历史节目单",
        showDialogProvider = { showEpgHistory },
        onDismissRequest = { showEpgHistory = false },
        blockDismissForOverlay = showWebConfigQr || showLiveWebConfigSuccessHint,
        items = epgUrlHistory.toList(),
        currentItem = epgUrl.takeIf { it.isNotBlank() },
        onSelected = { url ->
            scope.launch { 
                liveSettingsPreferences.setEpgUrl(url)
                liveSettingsPreferences.addEpgUrlToHistory(url)
            }
            showEpgHistory = false
        },
        onDeleted = { url ->
            scope.launch { liveSettingsPreferences.removeEpgUrlFromHistory(url) }
        },
        onAddNew = {
            pendingLiveWebPushKind = LiveWebPushSuccessKind.PROGRAM_GUIDE
            showWebConfigQr = true
        },
        itemContent = { item, isSelected, isFocused ->
            Pair(
                if (item.contains("51zmt")) "默认节目单" else "自定义节目单",
                item
            )
        },
        isBuiltInItem = { item ->
            LiveSettingsPreferences.BUILT_IN_EPG_URLS.contains(item)
        },
        addNewText = "添加其他节目单"
    )
    
    // User-Agent 历史列表弹窗
    HistoryListDialog(
        title = "历史 User-Agent",
        showDialogProvider = { showUaHistory },
        onDismissRequest = { showUaHistory = false },
        blockDismissForOverlay = showWebConfigQr || showLiveWebConfigSuccessHint,
        items = userAgentHistory.toList(),
        currentItem = userAgent.takeIf { it.isNotBlank() },
        onSelected = { ua ->
            scope.launch { 
                liveSettingsPreferences.setUserAgent(ua)
                liveSettingsPreferences.addUserAgentToHistory(ua)
            }
            showUaHistory = false
        },
        onDeleted = { ua ->
            scope.launch { liveSettingsPreferences.removeUserAgentFromHistory(ua) }
        },
        onAddNew = {
            pendingLiveWebPushKind = LiveWebPushSuccessKind.USER_AGENT
            showWebConfigQr = true
        },
        itemContent = { item, isSelected, isFocused ->
            Pair(
                if (item.contains("ExoPlayer")) "默认UA" else "自定义UA",
                item
            )
        },
        isBuiltInItem = { item ->
            LiveSettingsPreferences.BUILT_IN_USER_AGENTS.contains(item)
        },
        addNewText = "添加其他 User-Agent"
    )
    
    // 网页配置二维码弹窗 - 在历史列表中通过"添加其他"触发
    QrCodeDialog(
        text = serverUrl,
        title = "网页配置",
        description = "使用手机扫描二维码访问网页配置界面\n可批量添加直播源、节目单和UA",
        showDialogProvider = { showWebConfigQr },
        onDismissRequest = { showWebConfigQr = false }
    )

    if (showLiveWebConfigSuccessHint) {
        LiveWebConfigSuccessHintDialog(
            onDismiss = { showLiveWebConfigSuccessHint = false },
            kind = pendingLiveWebPushKind
        )
    }
}

/**
 * 直播源配置对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun LiveSourceSettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(containerColor = DialogUiTokens.ContainerColor),
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "配置直播源",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "支持 M3U/M3U8 格式的直播源链接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // URL 输入框
                OutlinedTextField(
                    value = url,
                    onValueChange = { newValue: String -> url = newValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("请输入直播源 URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextMuted,
                        focusedBorderColor = PrimaryYellow,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮行
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted
                        )
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = { onSave(url) },
                        colors = ButtonDefaults.colors(
                            containerColor = PrimaryYellow,
                            contentColor = BackgroundDark
                        ),
                        enabled = url.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 节目单配置对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun EpgUrlSettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(containerColor = DialogUiTokens.ContainerColor),
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "配置节目单",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "支持 XMLTV 格式的 EPG 节目单链接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // URL 输入框
                OutlinedTextField(
                    value = url,
                    onValueChange = { newValue: String -> url = newValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("请输入 EPG 节目单 URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextMuted,
                        focusedBorderColor = PrimaryYellow,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮行
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted
                        )
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = { onSave(url) },
                        colors = ButtonDefaults.colors(
                            containerColor = PrimaryYellow,
                            contentColor = BackgroundDark
                        ),
                        enabled = url.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * User-Agent 配置对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun UserAgentSettingsDialog(
    currentUserAgent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var userAgent by remember { mutableStateOf(currentUserAgent) }
    val focusRequester = remember { FocusRequester() }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(containerColor = DialogUiTokens.ContainerColor),
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "配置 User-Agent",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "自定义播放器请求时使用的 User-Agent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // UA 输入框
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { newValue: String -> userAgent = newValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("请输入 User-Agent（留空使用默认）") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextMuted,
                        focusedBorderColor = PrimaryYellow,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮行
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted
                        )
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = { onSave(userAgent) },
                        colors = ButtonDefaults.colors(
                            containerColor = PrimaryYellow,
                            contentColor = BackgroundDark
                        )
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 网页配置提示对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WebConfigInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val wifiIpAddress = remember { getWifiIpAddress(context) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            onClick = {},
            colors = CardDefaults.colors(containerColor = DialogUiTokens.ContainerColor),
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .padding(DialogDimens.CardPaddingOuter)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "网页端配置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "请在电脑或手机浏览器中访问以下地址：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    onClick = {},
                    colors = CardDefaults.colors(containerColor = BackgroundDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "http://$wifiIpAddress:8893",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryYellow,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "网页端支持配置：\n• 直播源 URL\n• 节目单 URL\n• 自定义 User-Agent\n• WebDAV 网盘",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = PrimaryYellow,
                        contentColor = BackgroundDark
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("知道了")
                }
            }
        }
    }
}

/**
 * 获取 WiFi IP 地址
 */
private fun getWifiIpAddress(context: android.content.Context): String {
    // 方法1: 通过 WiFiManager 获取
    try {
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt != 0) {
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            if (ip != "0.0.0.0") return ip
        }
    } catch (e: Exception) {
        // 忽略错误，尝试其他方法
    }
    
    // 方法2: 通过网络接口获取
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    val ip = address.hostAddress
                    if (ip != null && !ip.startsWith("0.")) {
                        return ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        // 忽略错误
    }
    
    // 方法3: 返回常见局域网 IP 作为备选
    return "192.168.1.100"
}
