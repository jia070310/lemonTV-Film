package com.lemon.yingshi.tv.ui.screens.settings

import com.lemon.yingshi.tv.BuildConfig
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.GlassBackground
import androidx.compose.ui.graphics.Brush
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.theme.TvSelectableTokens
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel
import com.lemon.yingshi.tv.ui.viewmodel.StorageSettingsViewModel
import com.lemon.yingshi.tv.ui.viewmodel.VersionUpdateViewModel
import com.lemon.yingshi.tv.util.StorageFormatter
import com.lemon.yingshi.tv.domain.model.VersionInfo
import com.lemon.yingshi.tv.domain.service.DownloadService
import com.lemon.yingshi.tv.ui.components.VersionUpdateDialog
import com.lemon.yingshi.tv.ui.components.DownloadProgressToast
import kotlinx.coroutines.launch
import com.lemon.yingshi.tv.ui.LocalCompactUiScale
import com.lemon.yingshi.tv.ui.computeCompactUiScale
import com.lemon.yingshi.tv.ui.DialogDimens
import com.lemon.yingshi.tv.ui.scale

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    
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
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearPlaybackStatsDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showHideCategoriesDialog by remember { mutableStateOf(false) }
    var showClearPrivacyDialog by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf<String?>(null) }
    
    val categories = listOf(
        SettingCategory("资源管理", Icons.Default.CloudUpload),
        SettingCategory("首页设置", Icons.Default.Settings),
        SettingCategory("隐私设置", Icons.Default.VisibilityOff),
        SettingCategory("播放设置", Icons.Default.PlayArrow),
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
                onShowSortDialog = { showSortDialog = true },
                onShowClearCacheDialog = { showClearCacheDialog = true },
                onShowClearWatchHistoryDialog = { showClearWatchHistoryDialog = true },
                onShowVersionUpdateDialog = { showVersionUpdateDialog = true },
                onShowClearPlaybackStatsDialog = { showClearPlaybackStatsDialog = true },
                onShowKeywordsDialog = { showKeywordsDialog = true },
                onShowHideCategoriesDialog = { showHideCategoriesDialog = true },
                onShowClearPrivacyDialog = { showClearPrivacyDialog = true },
                onShowSuccessMessage = { showSuccessMessage = it },
                hasUpdate = hasUpdate,
                versionInfo = versionInfo,
                modifier = Modifier.weight(1f),
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
        }
    }
    
    // 首页设置对话框
    val context = androidx.compose.ui.platform.LocalContext.current
    val sortPreferences = remember {
        com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences(context)
    }
    val coroutineScope = rememberCoroutineScope()
    
    // 检查版本更新
    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = packageInfo.versionCode
        versionUpdateViewModel.checkForUpdates(currentVersionCode)
    }
    
    // 成功提示
    if (showSuccessMessage != null) {
        SuccessToast(message = showSuccessMessage!!)
        androidx.compose.runtime.LaunchedEffect(showSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            showSuccessMessage = null
        }
    }

    // 首页设置 - 分类排序
    val macCmsConfigViewModel: MacCmsConfigViewModel = hiltViewModel()
    if (showSortDialog) {
        MacCmsCategorySortDialog(
            sortPreferences = sortPreferences,
            macCmsRepository = macCmsConfigViewModel.macCmsRepository,
            onDismiss = { showSortDialog = false },
            onSuccess = {
                showSortDialog = false
                showSuccessMessage = "首页分类设置已保存"
            }
        )
    }

    // 清除缓存确认对话框
    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "清除缓存数据",
            message = "确定要重置首页分类显示与排序设置吗？\n返回首页点击刷新即可重新加载。",
            onConfirm = {
                coroutineScope.launch {
                    sortPreferences.clearHomeCategoryCache()
                }
                showClearCacheDialog = false
                showSuccessMessage = "缓存已清除"
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showKeywordsDialog) {
        PrivacyKeywordsDialog(
            onDismiss = { showKeywordsDialog = false },
            onSuccess = {
                showKeywordsDialog = false
                showSuccessMessage = "敏感关键词已保存"
            }
        )
    }

    if (showHideCategoriesDialog) {
        PrivacyHideCategoriesDialog(
            onDismiss = { showHideCategoriesDialog = false },
            onSuccess = {
                showHideCategoriesDialog = false
                showSuccessMessage = "隐藏分类已保存"
            }
        )
    }

    if (showClearPrivacyDialog) {
        val privacyViewModel: com.lemon.yingshi.tv.ui.viewmodel.PrivacySettingsViewModel =
            hiltViewModel()
        ConfirmDialog(
            title = "清空隐私设置",
            message = "确定清除全部敏感关键词与手动隐藏分类吗？",
            onConfirm = {
                privacyViewModel.clearAll()
                showClearPrivacyDialog = false
                showSuccessMessage = "隐私设置已清空"
            },
            onDismiss = { showClearPrivacyDialog = false }
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
        val homeViewModel: com.lemon.yingshi.tv.ui.screens.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
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
        val homeViewModel: com.lemon.yingshi.tv.ui.screens.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
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
            val itemFocusRequester = remember(index) { FocusRequester() }
            val buttonFocusRequester = if (isSelected) sidebarFocusRequester else itemFocusRequester
            Button(
                onClick = { onCategorySelected(index) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) TvSelectableTokens.selectedContainerColor else Color.Transparent,
                    contentColor = TvSelectableTokens.selectedContentColor,
                    focusedContainerColor = TvSelectableTokens.focusedContainerColor,
                    focusedContentColor = TvSelectableTokens.focusedContentColor
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp.scale(s))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp.scale(s))
                    .focusRequester(buttonFocusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusProperties {
                        // 右键移动到右侧内容区
                        right = contentFocusRequester
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            contentFocusRequester.tryRequestFocus()
                        } else {
                            false
                        }
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
                    if (category.name == "关于应用" && hasUpdate) {
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
    onShowSortDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit,
    onShowClearWatchHistoryDialog: () -> Unit,
    onShowVersionUpdateDialog: () -> Unit,
    onShowClearPlaybackStatsDialog: () -> Unit,
    onShowKeywordsDialog: () -> Unit,
    onShowHideCategoriesDialog: () -> Unit,
    onShowClearPrivacyDialog: () -> Unit,
    onShowSuccessMessage: (String) -> Unit,
    hasUpdate: Boolean,
    versionInfo: VersionInfo?,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    val s = LocalCompactUiScale.current

    val scrollState = rememberScrollState()

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == 0) {
            delay(100)
            contentFocusRequester.tryRequestFocus()
        }
    }

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
            .then(
                if (selectedCategory != 0) {
                    Modifier
                        .focusRequester(contentFocusRequester)
                        .focusProperties {
                            // 左键返回到侧边栏
                            left = sidebarFocusRequester
                        }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp.scale(s))
        ) {
            when (selectedCategory) {
                0 -> { // 资源管理
                    SectionTitle(title = "资源管理", accentColor = PrimaryYellow)
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    MacCmsConfigSection(
                        contentFocusRequester = contentFocusRequester
                    )
                }
                1 -> { // 首页设置
                    SectionTitle(title = "首页设置", accentColor = PrimaryYellow)
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    MacCmsHomeSettingsSection(
                        onShowSortDialog = onShowSortDialog,
                        onShowClearCacheDialog = onShowClearCacheDialog
                    )
                }
                2 -> { // 隐私设置
                    SectionTitle(title = "隐私设置", accentColor = PrimaryYellow)
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    PrivacySettingsSection(
                        onShowKeywordsDialog = onShowKeywordsDialog,
                        onShowHideCategoriesDialog = onShowHideCategoriesDialog,
                        onShowClearPrivacyDialog = onShowClearPrivacyDialog
                    )
                }
                3 -> { // 播放设置
                    SectionTitle(title = "播放设置", accentColor = PrimaryYellow)
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    PlaybackSettingsSection(
                        onShowClearWatchHistoryDialog = onShowClearWatchHistoryDialog,
                        onShowSuccessMessage = onShowSuccessMessage
                    )
                }
                4 -> { // 关于应用
                    SectionTitle(title = "关于应用", accentColor = PrimaryYellow)
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    AboutSection(
                        hasUpdate = hasUpdate,
                        versionInfo = versionInfo,
                        onVersionUpdateClick = onShowVersionUpdateDialog,
                        onStatsClick = onShowClearPlaybackStatsDialog
                    )
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
private fun MacCmsHomeSettingsSection(
    onShowSortDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val gap = 16.dp.scale(s)

    Column {
        SettingCard(
            icon = Icons.Default.Settings,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF34d399),
            title = "首页分类设置",
            subtitle = "设置首页显示的分类、排序与二级类目开关",
            onClick = onShowSortDialog,
            modifier = Modifier.fillMaxWidth(),
            isFirstItem = true
        )

        Spacer(modifier = Modifier.height(gap))

        SettingCard(
            icon = Icons.Default.Refresh,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFfb923c),
            title = "清除缓存数据",
            subtitle = "重置首页分类显示与排序设置",
            onClick = onShowClearCacheDialog,
            modifier = Modifier.fillMaxWidth()
        )
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
                        containerColor = if (isSelected) TvSelectableTokens.selectedContainerColor else SurfaceDark,
                        focusedContainerColor = TvSelectableTokens.focusedContainerColor,
                        contentColor = TvSelectableTokens.selectedContentColor,
                        focusedContentColor = TvSelectableTokens.focusedContentColor
                    )
                ) {
                    Text(
                        text = "${getOptionLabel(concurrency)}（${concurrency}线程）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvSelectableTokens.contentColor(isFocused)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackSettingsSection(
    onShowClearWatchHistoryDialog: () -> Unit = {},
    onShowSuccessMessage: (String) -> Unit = {}
) {
    val s = LocalCompactUiScale.current
    val playerSettingsPreferences = androidx.hilt.navigation.compose.hiltViewModel<com.lemon.yingshi.tv.ui.viewmodel.PlayerSettingsViewModel>()
    val storageViewModel: StorageSettingsViewModel = hiltViewModel()
    val rememberPlaybackEnabled by playerSettingsPreferences.rememberPlaybackPosition.collectAsState(initial = true)
    val seekDurationSeconds by playerSettingsPreferences.seekDurationSeconds.collectAsState(initial = 15)
    val coroutineScope = rememberCoroutineScope()
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    val seekOptions = remember { listOf(15, 20, 25, 30, 35) }

    var cacheSizes by remember { mutableStateOf(storageViewModel.readCacheSizes()) }
    var showClearPlaybackCacheDialog by remember { mutableStateOf(false) }
    var showClearCoverCacheDialog by remember { mutableStateOf(false) }
    var showClearHomeFeedCacheDialog by remember { mutableStateOf(false) }
    var showClearAllCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSizes = storageViewModel.readCacheSizes()
    }
    
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
                                com.lemon.yingshi.tv.ui.theme.SuccessGreen // 绿色 - 开启
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

        SectionTitle(title = "存储管理", accentColor = PrimaryYellow)
        Spacer(modifier = Modifier.height(12.dp.scale(s)))

        Text(
            text = "合计占用 ${StorageFormatter.format(cacheSizes.totalBytes)} · 退出播放后自动清理播放缓存",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp.scale(s))
        )

        SettingCard(
            icon = Icons.Default.PlayArrow,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF60a5fa),
            title = "播放缓存",
            subtitle = "当前 ${StorageFormatter.format(cacheSizes.playbackBytes)}，仅预加载播放头前方约 90 秒",
            onClick = { showClearPlaybackCacheDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp.scale(s)))

        SettingCard(
            icon = Icons.Default.CloudUpload,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFa78bfa),
            title = "封面缓存",
            subtitle = "当前 ${StorageFormatter.format(cacheSizes.coverBytes)} / 上限 ${StorageFormatter.format(cacheSizes.coverMaxBytes)}，满额自动覆盖最旧",
            onClick = { showClearCoverCacheDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp.scale(s)))

        SettingCard(
            icon = Icons.Default.Storage,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF34d399),
            title = "首页数据缓存",
            subtitle = "当前 ${StorageFormatter.format(cacheSizes.homeFeedBytes)} / 上限 ${StorageFormatter.format(cacheSizes.homeFeedMaxBytes)}，满额自动覆盖最旧",
            onClick = { showClearHomeFeedCacheDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp.scale(s)))

        SettingCard(
            icon = Icons.Default.Refresh,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFef4444),
            title = "一键清除全部缓存",
            subtitle = "清除播放、封面与首页数据缓存",
            onClick = { showClearAllCacheDialog = true },
            modifier = Modifier.fillMaxWidth()
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

        if (showClearPlaybackCacheDialog) {
            ConfirmDialog(
                title = "清除播放缓存",
                message = "确定清除播放预加载缓存？退出播放时也会自动清理。",
                onConfirm = {
                    storageViewModel.clearPlaybackCache()
                    cacheSizes = storageViewModel.readCacheSizes()
                    showClearPlaybackCacheDialog = false
                    onShowSuccessMessage("播放缓存已清除")
                },
                onDismiss = { showClearPlaybackCacheDialog = false }
            )
        }

        if (showClearCoverCacheDialog) {
            ConfirmDialog(
                title = "清除封面缓存",
                message = "确定清除海报与观看历史封面缓存？",
                onConfirm = {
                    storageViewModel.clearCoverCache()
                    cacheSizes = storageViewModel.readCacheSizes()
                    showClearCoverCacheDialog = false
                    onShowSuccessMessage("封面缓存已清除")
                },
                onDismiss = { showClearCoverCacheDialog = false }
            )
        }

        if (showClearHomeFeedCacheDialog) {
            ConfirmDialog(
                title = "清除首页数据缓存",
                message = "确定清除首页栏目数据缓存？返回首页将重新加载。",
                onConfirm = {
                    storageViewModel.clearHomeFeedCache()
                    cacheSizes = storageViewModel.readCacheSizes()
                    showClearHomeFeedCacheDialog = false
                    onShowSuccessMessage("首页数据缓存已清除")
                },
                onDismiss = { showClearHomeFeedCacheDialog = false }
            )
        }

        if (showClearAllCacheDialog) {
            ConfirmDialog(
                title = "清除全部缓存",
                message = "确定清除播放、封面与首页数据缓存？",
                onConfirm = {
                    storageViewModel.clearAllCaches()
                    cacheSizes = storageViewModel.readCacheSizes()
                    showClearAllCacheDialog = false
                    onShowSuccessMessage("全部缓存已清除")
                },
                onDismiss = { showClearAllCacheDialog = false }
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
                        containerColor = if (isSelected) TvSelectableTokens.selectedContainerColor else SurfaceDark,
                        focusedContainerColor = TvSelectableTokens.focusedContainerColor,
                        contentColor = TvSelectableTokens.selectedContentColor,
                        focusedContentColor = TvSelectableTokens.focusedContentColor
                    )
                ) {
                    Text(
                        text = "${seconds}s",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvSelectableTokens.contentColor(isFocused)
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
    onStatsClick: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val homeViewModel: com.lemon.yingshi.tv.ui.screens.home.HomeViewModel = hiltViewModel()
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
                    text = "柠檬影视TV",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black else TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))
                Text(
                    text = "一款专为 Android TV 设计的 MacCMS 影视客户端，支持首页分类、筛选搜索、智能跳过片头片尾、记忆续播等功能。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.8f) else TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))
                Text(
                    text = "GitHub: https://github.com/jia070310/lemonTV-Film",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * s + 2f).sp
                    ),
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp.scale(s)))
                Text(
                    text = "© 2026 柠檬影视TV 版权所有",
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
internal fun SettingCard(
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
                        containerColor = if (isSelected) TvSelectableTokens.selectedContainerColor else Color.Transparent,
                        contentColor = TvSelectableTokens.selectedContentColor,
                        focusedContainerColor = TvSelectableTokens.focusedContainerColor,
                        focusedContentColor = TvSelectableTokens.focusedContentColor,
                        pressedContainerColor = TvSelectableTokens.focusedContainerColor,
                        pressedContentColor = TvSelectableTokens.focusedContentColor
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
internal fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
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
                },
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
                    .border(
                        DialogUiTokens.BorderWidth,
                        DialogUiTokens.BorderColor,
                        RoundedCornerShape(DialogUiTokens.CornerRadius)
                    )
                    .focusProperties {
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
    }

    // 对话框显示时，默认将焦点给"取消"按钮（安全操作），避免误触"确定"
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 成功提示Toast
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SuccessToast(message: String) {
    com.lemon.yingshi.tv.ui.components.WhitePillToast(
        message = message,
        icon = Icons.Default.Info,
        bottomPadding = 100.dp,
        backgroundAlpha = 0.75f,
        iconTint = Color.Black,
        textColor = Color.Black
    )
}
