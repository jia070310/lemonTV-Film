package com.lomen.tv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lomen.tv.domain.model.MediaType
import com.lomen.tv.ui.components.InfoPillToast
import com.lomen.tv.ui.theme.BackgroundDark
import com.lomen.tv.ui.theme.GlassBackground
import com.lomen.tv.ui.theme.PrimaryYellow
import com.lomen.tv.ui.theme.RatingGold
import com.lomen.tv.ui.theme.SurfaceDark
import com.lomen.tv.ui.theme.TextMuted
import com.lomen.tv.ui.theme.TextPrimary
import com.lomen.tv.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun MediaType.usesEpisodeDetailLayout(): Boolean =
    this == MediaType.TV_SHOW ||
        this == MediaType.VARIETY ||
        this == MediaType.ANIME ||
        this == MediaType.DOCUMENTARY

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    mediaId: String,
    onNavigateBack: () -> Unit,
    onPlayClick: (videoUrl: String, title: String, episodeTitle: String?, mediaId: String, episodeId: String?, startPosition: Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val availableSeasons by viewModel.availableSeasons.collectAsState()
    val manualEditState by viewModel.manualEditState.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        viewModel.loadMediaDetail(mediaId)
    }
    LaunchedEffect(manualEditState.lastAppliedTmdbId) {
        if (manualEditState.lastAppliedTmdbId != null) {
            showManualDialog = false
            viewModel.clearManualEditAppliedFlag()
        }
    }
    LaunchedEffect(manualEditState.successMessage) {
        if (manualEditState.successMessage != null) {
            delay(2200)
            viewModel.clearManualEditSuccessMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载中...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            }
            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载失败: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
            is DetailUiState.Success -> {
                DetailContent(
                    media = state.media,
                    episodes = state.episodes,
                    cast = state.cast,
                    selectedSeason = selectedSeason,
                    availableSeasons = availableSeasons,
                    onSeasonSelected = { viewModel.selectSeason(it) },
                    onManualEditClick = {
                        showManualDialog = true
                        viewModel.prepareManualEdit(state.media.title, state.media.year)
                    },
                    onNavigateBack = onNavigateBack,
                    onPlayClick = onPlayClick,
                    viewModel = viewModel
                )
            }
        }

        if (showManualDialog) {
            ManualMetadataEditDialog(
                state = manualEditState,
                onDismiss = { showManualDialog = false },
                onQueryChange = viewModel::updateManualQuery,
                onSearch = viewModel::searchManualCandidates,
                onApply = viewModel::applyManualCandidate
            )
        }
        manualEditState.successMessage?.let { msg ->
            InfoPillToast(message = msg)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailContent(
    media: MediaDetail,
    episodes: List<EpisodeItem>,
    cast: List<CastItem>,
    selectedSeason: Int,
    availableSeasons: List<Int>,
    onSeasonSelected: (Int) -> Unit,
    onManualEditClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onPlayClick: (videoUrl: String, title: String, episodeTitle: String?, mediaId: String, episodeId: String?, startPosition: Long) -> Unit,
    viewModel: DetailViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (media.type.usesEpisodeDetailLayout()) {
        listOf("剧集", "简介", "演职人员")
    } else {
        listOf("简介", "演职人员")
    }
    val tabFocusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    val activeTabFocusRequester = tabFocusRequesters.getOrElse(selectedTab) { tabFocusRequesters.first() }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Hero Section with Backdrop
        item {
            HeroSection(
                media = media,
                episodes = episodes,
                selectedSeason = selectedSeason,
                availableSeasons = availableSeasons,
                currentTabFocusRequester = activeTabFocusRequester,
                onSeasonSelected = onSeasonSelected,
                onManualEditClick = onManualEditClick,
                onNavigateBack = onNavigateBack,
                onPlayClick = {
                    // 立即播放按钮：根据观看历史决定播放位置
                    CoroutineScope(Dispatchers.Main).launch {
                        val playbackInfo = viewModel.getResumePlaybackInfo()
                        if (playbackInfo != null) {
                            onPlayClick(
                                playbackInfo.videoUrl,
                                playbackInfo.title,
                                playbackInfo.episodeTitle,
                                playbackInfo.mediaId,
                                playbackInfo.episodeId,
                                playbackInfo.startPosition
                            )
                        }
                    }
                }
            )
        }

        // Tabs
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
                containerColor = Color.Transparent,
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    tabPositions.getOrNull(selectedTab)?.let {
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = it,
                            activeColor = PrimaryYellow,
                            inactiveColor = Color.White,
                            doesTabRowHaveFocus = doesTabRowHaveFocus
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onFocus = { selectedTab = index },
                        modifier = Modifier.focusRequester(tabFocusRequesters[index])
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedTab == index) BackgroundDark else TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Content based on selected tab
        when (selectedTab) {
            0 -> {
                if (media.type.usesEpisodeDetailLayout()) {
                    // Episodes
                    item {
                        EpisodesSection(
                            episodes = episodes,
                            totalEpisodes = media.totalEpisodes,
                            tabFocusRequester = activeTabFocusRequester,
                            onEpisodeClick = { episode ->
                                // 点击剧集时，获取该集的观看历史
                                CoroutineScope(Dispatchers.Main).launch {
                                    val playbackInfo = viewModel.getEpisodePlaybackInfo(episode.id)
                                    if (playbackInfo != null) {
                                        val videoUrl = playbackInfo.videoUrl
                                        // 确保集数信息正确显示
                                        val episodeTitleText = episode.title?.trim() ?: ""
                                        val displayEpisodeTitle = if (episodeTitleText == media.title || episodeTitleText.isEmpty()) {
                                            "第${episode.episodeNumber}集"
                                        } else {
                                            "第${episode.episodeNumber}集 $episodeTitleText"
                                        }
                                        android.util.Log.d("DetailScreen", "Playing episode: title=$displayEpisodeTitle, media.title=${media.title}, episode.title=${episode.title}, startPosition=${playbackInfo.startPosition}")
                                        onPlayClick(
                                            videoUrl,
                                            media.title,
                                            displayEpisodeTitle,
                                            playbackInfo.mediaId,
                                            playbackInfo.episodeId,
                                            playbackInfo.startPosition
                                        )
                                    }
                                }
                            }
                        )
                    }
                } else {
                    // Overview for movie
                    item {
                        OverviewSection(overview = media.overview)
                    }
                }
            }
            1 -> {
                if (media.type.usesEpisodeDetailLayout()) {
                    // Overview for TV show
                    item {
                        OverviewSection(overview = media.overview)
                    }
                } else {
                    // Cast for movie
                    item {
                        CastSection(
                            cast = cast,
                            tabFocusRequester = activeTabFocusRequester
                        )
                    }
                }
            }
            2 -> {
                // Cast for TV show
                item {
                    CastSection(
                        cast = cast,
                        tabFocusRequester = activeTabFocusRequester
                    )
                }
            }
        }

        // Path Information Section
        item {
            PathInformationSection(
                mediaPath = media.path
            )
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroSection(
    media: MediaDetail,
    episodes: List<EpisodeItem>,
    selectedSeason: Int,
    availableSeasons: List<Int>,
    currentTabFocusRequester: FocusRequester,
    onSeasonSelected: (Int) -> Unit,
    onManualEditClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onPlayClick: () -> Unit
) {
    // 季数下拉菜单状态
    var showSeasonDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val seasonTriggerFocusRequester = remember { FocusRequester() }
    val dismissSeasonMenu: () -> Unit = {
        showSeasonDropdown = false
        scope.launch {
            delay(32)
            seasonTriggerFocusRequester.requestFocus()
        }
    }
    BackHandler(enabled = showSeasonDropdown) {
        dismissSeasonMenu()
    }

    // Backdrop Section with Back Button overlay
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        // Backdrop Image
        AsyncImage(
            model = media.backdropUrl ?: media.posterUrl,
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundDark.copy(alpha = 0.95f),
                            BackgroundDark.copy(alpha = 0.4f),
                            BackgroundDark.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // Back Button - 直接叠加在图片上层
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            colors = androidx.tv.material3.IconButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = TextPrimary,
                focusedContainerColor = PrimaryYellow,
                focusedContentColor = BackgroundDark
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(28.dp)
            )
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 60.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(600.dp)
            ) {
                // Title
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Rating and Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    media.rating?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF10b981))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "%.1f".format(it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    media.year?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Text(
                        text = media.genres.take(3).joinToString(" / "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    if (media.type.usesEpisodeDetailLayout()) {
                    // 最大集号（集号缺失或全被误标为 1 时，用本季条数兜底）
                    val latestEpisode = if (episodes.isEmpty()) 0 else maxOf(
                        episodes.maxOfOrNull { it.episodeNumber } ?: 0,
                        episodes.size
                    )
                    val episodeCount = media.totalEpisodes ?: latestEpisode
                        Text(
                            text = "共${episodeCount}集",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }

                // Overview
                Text(
                    text = media.overview ?: "暂无简介",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceDark.copy(alpha = 0.8f),
                            contentColor = TextPrimary,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = Color.Black,
                            pressedContainerColor = PrimaryYellow,
                            pressedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "立即播放",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.Unspecified
                            )
                        )
                    }

                    if (media.type.usesEpisodeDetailLayout() && availableSeasons.size > 1) {
                        // 多季显示下拉选择器
                        Box {
                            Button(
                                onClick = { showSeasonDropdown = !showSeasonDropdown },
                                colors = ButtonDefaults.colors(
                                    containerColor = SurfaceDark.copy(alpha = 0.8f),
                                    contentColor = TextPrimary,
                                    focusedContainerColor = PrimaryYellow,
                                    focusedContentColor = Color.Black,
                                    pressedContainerColor = PrimaryYellow,
                                    pressedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                                modifier = Modifier
                                    .focusRequester(seasonTriggerFocusRequester)
                                    .focusProperties {
                                        down = currentTabFocusRequester
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "第${selectedSeason}季",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.Unspecified
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "选择季数",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // 下拉菜单
                            if (showSeasonDropdown) {
                                SeasonDropdownMenu(
                                    availableSeasons = availableSeasons,
                                    selectedSeason = selectedSeason,
                                    onSeasonSelected = onSeasonSelected,
                                    onDismissMenu = dismissSeasonMenu
                                )
                            }
                        }
                    } else if (media.type.usesEpisodeDetailLayout()) {
                        // 单季显示固定标签
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.colors(
                                containerColor = SurfaceDark.copy(alpha = 0.8f),
                                contentColor = TextPrimary,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = Color.Black,
                                pressedContainerColor = PrimaryYellow,
                                pressedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                            modifier = Modifier
                                .focusProperties {
                                    down = currentTabFocusRequester
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "第${selectedSeason}季",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.Unspecified
                                )
                            )
                        }
                    }
                    Button(
                        onClick = onManualEditClick,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceDark.copy(alpha = 0.8f),
                            contentColor = TextPrimary,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = Color.Black,
                            pressedContainerColor = PrimaryYellow,
                            pressedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "手动修正",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.Unspecified
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ManualMetadataEditDialog(
    state: ManualMetadataEditState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onApply: (ManualTmdbCandidate) -> Unit
) {
    val queryFocusRequester = remember { FocusRequester() }
    val searchBtnFocusRequester = remember { FocusRequester() }
    var queryFieldValue by remember { mutableStateOf(TextFieldValue(state.query)) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 仅在外部文本真的变化时同步，避免用户编辑时光标被重置到开头
    LaunchedEffect(state.query) {
        if (state.query != queryFieldValue.text) {
            queryFieldValue = TextFieldValue(
                text = state.query,
                selection = TextRange(state.query.length)
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        searchBtnFocusRequester.requestFocus()
    }

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
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                onClick = {},
                colors = CardDefaults.colors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth(0.375f)
                    .fillMaxHeight(0.8f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        // 锁定焦点在弹窗内部，避免跳到背景页面
                        exit = { FocusRequester.Cancel }
                    }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("手动修正刮削", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BackgroundDark.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = queryFieldValue,
                                onValueChange = { newValue ->
                                    queryFieldValue = newValue
                                    onQueryChange(newValue.text)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(queryFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            queryFieldValue = queryFieldValue.copy(
                                                selection = TextRange(queryFieldValue.text.length)
                                            )
                                            keyboardController?.show()
                                        }
                                    }
                                    .focusProperties {
                                        right = searchBtnFocusRequester
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (
                                            keyEvent.type == KeyEventType.KeyUp &&
                                            keyEvent.key == Key.DirectionRight &&
                                            queryFieldValue.selection.end >= queryFieldValue.text.length
                                        ) {
                                            searchBtnFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                                cursorBrush = SolidColor(PrimaryYellow),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                decorationBox = { inner ->
                                    if (queryFieldValue.text.isBlank()) {
                                        Text("输入剧名+年份，例如 八千里路云和月 2026", color = TextMuted)
                                    }
                                    inner()
                                }
                            )
                        }
                        Button(
                            onClick = onSearch,
                            colors = ButtonDefaults.colors(
                                containerColor = SurfaceDark.copy(alpha = 0.8f),
                                contentColor = TextPrimary,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = Color.Black,
                                pressedContainerColor = PrimaryYellow,
                                pressedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .focusRequester(searchBtnFocusRequester)
                                .focusProperties {
                                    left = queryFocusRequester
                                }
                        ) {
                            Text(
                                text = if (state.isSearching) "搜索中..." else "搜索",
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color.Unspecified)
                            )
                        }
                    }
                    state.errorMessage?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.candidates) { item ->
                            var isFocused by remember { mutableStateOf(false) }
                            Card(
                                onClick = { onApply(item) },
                                colors = CardDefaults.colors(
                                    containerColor = BackgroundDark.copy(alpha = 0.5f),
                                    focusedContainerColor = PrimaryYellow,
                                    pressedContainerColor = PrimaryYellow
                                ),
                                scale = CardDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.02f,
                                    pressedScale = 1.0f
                                ),
                                modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${item.title} (${item.year ?: "--"})  TMDB:${item.tmdbId}",
                                        color = if (isFocused) Color.Black else TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    item.originalTitle?.takeIf { it.isNotBlank() && it != item.title }?.let {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = it,
                                            color = if (isFocused) Color.Black.copy(alpha = 0.82f) else TextMuted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.overview ?: "暂无简介",
                                        color = if (isFocused) Color.Black.copy(alpha = 0.82f) else TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (state.isApplying) "正在应用..." else "点击应用此结果",
                                        color = if (isFocused) Color.Black else PrimaryYellow,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
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
private fun EpisodesSection(
    episodes: List<EpisodeItem>,
    totalEpisodes: Int?,  // 总集数
    tabFocusRequester: FocusRequester,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 32.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "剧集播放",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            val latestEpisode = if (episodes.isEmpty()) 0 else maxOf(
                episodes.maxOfOrNull { it.episodeNumber } ?: 0,
                episodes.size
            )
            val episodeText = if (totalEpisodes != null) {
                "更新至 ${latestEpisode} 集 / 全集 $totalEpisodes 集"
            } else {
                "更新至 ${latestEpisode} 集 / 全集"
            }
            Text(
                text = episodeText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        // Episodes Horizontal List - sorted by episode number
        val sortedEpisodes = episodes.sortedBy { it.episodeNumber }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 64.dp, top = 16.dp, bottom = 16.dp)
        ) {
            items(sortedEpisodes) {
                EpisodeCard(
                    episode = it,
                    tabFocusRequester = tabFocusRequester,
                    onClick = { onEpisodeClick(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    tabFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.width(300.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(300.dp)
                .height(168.dp)
                .focusProperties {
                    up = tabFocusRequester
                }
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow.copy(alpha = 0.2f),
                pressedContainerColor = PrimaryYellow.copy(alpha = 0.2f)
            ),
            scale = CardDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.05f,
                pressedScale = 1.0f
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 2.dp, color = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Thumbnail
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )

                // 播放进度条（有观看记录才显示）
                val progressRatio = remember(episode.progress, episode.duration) {
                    if (episode.duration > 0) {
                        (episode.progress.toFloat() / episode.duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                }
                if (progressRatio > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "已看 ${formatWatchedTime(episode.progress)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .fillMaxHeight()
                                .background(PrimaryYellow)
                        )
                    }
                }

                // Duration
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // 显示真实时长（如果有）或默认时长
                    val durationText = if (episode.duration > 0) {
                        val minutes = episode.duration / 60000
                        "${minutes}分钟"
                    } else {
                        "--:--"
                    }
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
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

        // 单集信息：第一行显示集数，第二行显示标题
        Text(
            text = "第${episode.episodeNumber}集",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .width(300.dp)
        )
        Text(
            text = episode.title ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 2.dp)
                .width(300.dp)
        )
    }
}

private fun formatWatchedTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OverviewSection(overview: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Section Header
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            Text(
                text = "简介",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        // Overview Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 4.dp)
        ) {
            Text(
                text = overview ?: "暂无简介",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastSection(
    cast: List<CastItem>,
    tabFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Section Header
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            Text(
                text = "演职人员",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        // Cast Grid
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(cast) {
                CastCard(
                    castItem = it,
                    tabFocusRequester = tabFocusRequester
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastCard(
    castItem: CastItem,
    tabFocusRequester: FocusRequester
) {
    var isFocused by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    Card(
        onClick = {},
        modifier = Modifier
            .width(96.dp)
            .focusProperties {
                up = tabFocusRequester
            }
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = PrimaryYellow.copy(alpha = 0.2f),
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.4f)
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f,
            pressedScale = 1.0f
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(96.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .padding(4.dp)
            ) {
            AsyncImage(
                model = castItem.profileUrl,
                contentDescription = castItem.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = castItem.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) BackgroundDark else TextPrimary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        castItem.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) BackgroundDark else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        }
    }
}

/**
 * 季数下拉选择菜单：打开后焦点落在当前季项上，方向键限制在菜单内，返回键关闭并回到选季按钮。
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SeasonDropdownMenu(
    availableSeasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onDismissMenu: () -> Unit
) {
    val itemHeight = 56.dp
    val maxVisibleItems = 4
    val menuHeight = if (availableSeasons.size <= maxVisibleItems) {
        itemHeight * availableSeasons.size + 16.dp
    } else {
        itemHeight * maxVisibleItems + 16.dp
    }

    val itemFocusRequesters = remember(availableSeasons) {
        List(availableSeasons.size) { FocusRequester() }
    }

    LaunchedEffect(Unit) {
        val idx = availableSeasons.indexOf(selectedSeason).takeIf { it >= 0 } ?: 0
        delay(48)
        itemFocusRequesters.getOrNull(idx)?.requestFocus()
    }

    Box(
        modifier = Modifier
            .padding(top = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .width(140.dp)
            .height(menuHeight)
            .focusProperties {
                exit = { FocusRequester.Cancel }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .focusGroup()
        ) {
            availableSeasons.forEachIndexed { index, season ->
                val isSelected = season == selectedSeason
                val prev = itemFocusRequesters.getOrNull(index - 1)
                val next = itemFocusRequesters.getOrNull(index + 1)
                Button(
                    onClick = {
                        onSeasonSelected(season)
                        onDismissMenu()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (isSelected) PrimaryYellow else TextPrimary,
                        focusedContainerColor = PrimaryYellow,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = PrimaryYellow,
                        pressedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .focusRequester(itemFocusRequesters[index])
                        .focusProperties {
                            up = prev ?: FocusRequester.Cancel
                            down = next ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "第${season}季",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Unspecified
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// Data classes for UI
data class MediaDetail(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Float?,
    val year: String?,
    val genres: List<String>,
    val type: MediaType,
    val seasonCount: Int = 0,
    val totalEpisodes: Int? = null,  // 总集数
    val path: String? = null
)

/** 同集不同清晰度文件（如 4K / 1080 两套目录），用于详情合并与播放器切换 */
data class EpisodeQualityVariant(
    val mediaId: String,
    val label: String,
    val path: String
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: Int,
    val seasonNumber: Int = 1,  // 新增季数字段
    val title: String?,
    val stillUrl: String?,
    val progress: Long = 0,
    val duration: Long = 0,
    val isWatched: Boolean = false,
    val path: String? = null,
    /** 多清晰度时含全部版本（已按清晰度从高到低排序）；单版本可为空 */
    val qualityVariants: List<EpisodeQualityVariant> = emptyList()
)

/**
 * 演职人员数据类
 */
data class CastItem(
    val id: Int,
    val name: String,
    val role: String?,  // 导演/角色名称
    val profileUrl: String?
)

// UI State
sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val media: MediaDetail,
        val episodes: List<EpisodeItem>,
        val cast: List<CastItem>
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PathInformationSection(
    mediaPath: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        // Section Header
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            Text(
                text = "文件路径",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        // Path Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .padding(16.dp)
        ) {
            Text(
                text = mediaPath ?: "暂无路径信息",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
