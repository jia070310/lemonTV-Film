package com.lemon.yingshi.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.domain.model.MediaType
import com.lemon.yingshi.tv.ui.components.VodPosterImage
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

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

    LaunchedEffect(mediaId) {
        viewModel.loadMediaDetail(mediaId)
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
                val isMacCms = MacCmsIds.isMacCmsId(state.media.id)
                DetailContent(
                    media = state.media,
                    episodes = state.episodes,
                    playSources = state.playSources,
                    selectedPlaySourceIndex = state.selectedPlaySourceIndex,
                    isLoadingPlayInfo = state.isLoadingPlayInfo,
                    isMacCms = isMacCms,
                    onPlaySourceSelected = { viewModel.selectPlaySource(it) },
                    onNavigateBack = onNavigateBack,
                    onPlayClick = onPlayClick,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailContent(
    media: MediaDetail,
    episodes: List<EpisodeItem>,
    playSources: List<String>,
    selectedPlaySourceIndex: Int,
    isLoadingPlayInfo: Boolean,
    isMacCms: Boolean,
    onPlaySourceSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onPlayClick: (videoUrl: String, title: String, episodeTitle: String?, mediaId: String, episodeId: String?, startPosition: Long) -> Unit,
    viewModel: DetailViewModel
) {
    var focusedEpisodePath by remember(episodes) {
        mutableStateOf(
            if (media.type.usesEpisodeDetailLayout() && episodes.isNotEmpty()) {
                episodes.sortedBy { it.episodeNumber }.firstOrNull()?.path
            } else {
                media.path
            }
        )
    }
    var selectedEpisodeId by remember(episodes) {
        mutableStateOf(episodes.sortedBy { it.episodeNumber }.firstOrNull()?.id)
    }
    val playButtonFocusRequester = remember { FocusRequester() }
    val lineSectionFocusRequester = remember { FocusRequester() }
    val lastLineFocusRequester = remember { FocusRequester() }
    val prevPageFocusRequester = remember { FocusRequester() }
    val nextPageFocusRequester = remember { FocusRequester() }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val episodeColumns = max(
        1,
        ((screenWidth - 96.dp + 12.dp) / (72.dp + 12.dp)).toInt()
    )
    val hasEpisodePagination = episodes.size > episodeColumns * 4

    LaunchedEffect(episodes, selectedPlaySourceIndex) {
        selectedEpisodeId = episodes.sortedBy { it.episodeNumber }.firstOrNull()?.id
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            HeroSection(
                media = media,
                episodes = episodes,
                isLoadingPlayInfo = isLoadingPlayInfo,
                playButtonFocusRequester = playButtonFocusRequester,
                lineSectionFocusRequester = lineSectionFocusRequester,
                onNavigateBack = onNavigateBack,
                onPlayClick = {
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
                },
            )
        }

        if (isMacCms && isLoadingPlayInfo) {
            item {
                PlayInfoLoadingSection(
                    playButtonFocusRequester = playButtonFocusRequester
                )
            }
        }

        if (isMacCms && playSources.isNotEmpty()) {
            item {
                PlaySourceSection(
                    playSources = playSources,
                    selectedIndex = selectedPlaySourceIndex,
                    focusRequester = lineSectionFocusRequester,
                    lastLineFocusRequester = lastLineFocusRequester,
                    prevPageFocusRequester = prevPageFocusRequester,
                    linkPaginationFocus = hasEpisodePagination,
                    onSourceSelected = onPlaySourceSelected
                )
            }
        }

        if (media.type.usesEpisodeDetailLayout() && episodes.isNotEmpty()) {
            item {
                EpisodesGridSection(
                    episodes = episodes,
                    totalEpisodes = media.totalEpisodes,
                    selectedEpisodeId = selectedEpisodeId,
                    hasPlaySourcesAbove = isMacCms && playSources.isNotEmpty(),
                    lineSectionFocusRequester = lineSectionFocusRequester,
                    lastLineFocusRequester = lastLineFocusRequester,
                    prevPageFocusRequester = prevPageFocusRequester,
                    nextPageFocusRequester = nextPageFocusRequester,
                    playSourceCount = playSources.size,
                    onEpisodeFocus = { episode ->
                        focusedEpisodePath = episode.path
                    },
                    onEpisodeClick = { episode ->
                        selectedEpisodeId = episode.id
                        CoroutineScope(Dispatchers.Main).launch {
                            val playbackInfo = viewModel.getEpisodePlaybackInfo(episode.id)
                            if (playbackInfo != null) {
                                val episodeTitleText = episode.title?.trim() ?: ""
                                val displayEpisodeTitle = if (episodeTitleText == media.title || episodeTitleText.isEmpty()) {
                                    "第${episode.episodeNumber}集"
                                } else {
                                    "第${episode.episodeNumber}集 $episodeTitleText"
                                }
                                onPlayClick(
                                    playbackInfo.videoUrl,
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
        }

        if (!isMacCms) {
            item {
                PathInformationSection(
                    mediaPath = if (media.type.usesEpisodeDetailLayout()) {
                        focusedEpisodePath
                    } else {
                        media.path
                    }
                )
            }
        }

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
    isLoadingPlayInfo: Boolean,
    playButtonFocusRequester: FocusRequester,
    lineSectionFocusRequester: FocusRequester,
    onNavigateBack: () -> Unit,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        VodPosterImage(
            posterUrl = media.backdropUrl ?: media.posterUrl,
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            crossfade = true
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
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Director / Actors / Release Date
                MediaMetaInfoSection(media = media)

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onPlayClick,
                        enabled = !isLoadingPlayInfo,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceDark.copy(alpha = 0.8f),
                            contentColor = TextPrimary,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = Color.Black,
                            pressedContainerColor = PrimaryYellow,
                            pressedContentColor = Color.Black,
                            disabledContainerColor = SurfaceDark.copy(alpha = 0.5f),
                            disabledContentColor = TextMuted
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                        modifier = Modifier
                            .focusRequester(playButtonFocusRequester)
                            .focusProperties {
                                down = lineSectionFocusRequester
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLoadingPlayInfo) "加载播放源..." else "立即播放",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.Unspecified
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayInfoLoadingSection(
    playButtonFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp)
            .focusProperties {
                up = playButtonFocusRequester
            }
    ) {
        Text(
            text = "播放源",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = PrimaryYellow,
                strokeWidth = 2.dp
            )
            Text(
                text = "正在加载播放源与选集…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaMetaInfoSection(media: MediaDetail) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        media.director?.let { director ->
            MetaInfoRow(label = "导演", value = director)
        }
        media.actors?.let { actors ->
            MetaInfoRow(label = "演员", value = actors)
        }
        media.releaseDate?.let { date ->
            MetaInfoRow(label = "上映时间", value = date)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun PlaySourceSection(
    playSources: List<String>,
    selectedIndex: Int,
    focusRequester: FocusRequester,
    lastLineFocusRequester: FocusRequester,
    prevPageFocusRequester: FocusRequester,
    linkPaginationFocus: Boolean,
    onSourceSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp)
    ) {
        Text(
            text = "线路选择",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playSources.size) { index ->
                val isLast = index == playSources.lastIndex
                val isSingleSource = playSources.size == 1
                PlaySourceChip(
                    name = playSources[index],
                    isActiveSource = index == selectedIndex,
                    modifier = Modifier
                        .then(
                            when {
                                isSingleSource -> Modifier.focusRequester(focusRequester)
                                index == 0 -> Modifier.focusRequester(focusRequester)
                                isLast -> Modifier.focusRequester(lastLineFocusRequester)
                                else -> Modifier
                            }
                        )
                        .focusProperties {
                            if ((isLast || isSingleSource) && linkPaginationFocus) {
                                right = prevPageFocusRequester
                            }
                        },
                    onClick = { onSourceSelected(index) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaySourceChip(
    name: String,
    isActiveSource: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (isActiveSource) {
                SurfaceVariant.copy(alpha = 0.95f)
            } else {
                SurfaceDark.copy(alpha = 0.8f)
            },
            contentColor = TextPrimary,
            focusedContainerColor = PrimaryYellow,
            focusedContentColor = Color.Black,
            pressedContainerColor = PrimaryYellow,
            pressedContentColor = Color.Black
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isActiveSource) {
                    androidx.compose.ui.text.font.FontWeight.Bold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                },
                color = Color.Unspecified
            )
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesGridSection(
    episodes: List<EpisodeItem>,
    totalEpisodes: Int?,
    selectedEpisodeId: String?,
    hasPlaySourcesAbove: Boolean,
    lineSectionFocusRequester: FocusRequester,
    lastLineFocusRequester: FocusRequester,
    prevPageFocusRequester: FocusRequester,
    nextPageFocusRequester: FocusRequester,
    playSourceCount: Int,
    onEpisodeFocus: (EpisodeItem) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    val sortedEpisodes = remember(episodes) { episodes.sortedBy { it.episodeNumber } }
    val gridRows = 4
    val gridSpacing = 12.dp
    val buttonWidth = 72.dp
    val buttonHeight = 48.dp
    val firstEpisodeFocusRequester = remember { FocusRequester() }
    var refocusEpisodesAfterPageChange by remember { mutableStateOf(false) }

    val latestEpisode = if (sortedEpisodes.isEmpty()) 0 else maxOf(
        sortedEpisodes.maxOfOrNull { it.episodeNumber } ?: 0,
        sortedEpisodes.size
    )
    val totalCount = totalEpisodes ?: latestEpisode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = max(
                1,
                ((maxWidth + gridSpacing) / (buttonWidth + gridSpacing)).toInt()
            )
            val episodesPerPage = columns * gridRows
            val totalPages = max(1, (sortedEpisodes.size + episodesPerPage - 1) / episodesPerPage)
            var currentPage by remember(sortedEpisodes, columns) { mutableIntStateOf(0) }

            LaunchedEffect(sortedEpisodes) {
                currentPage = 0
            }

            val safePage = currentPage.coerceIn(0, totalPages - 1)

            LaunchedEffect(safePage) {
                if (refocusEpisodesAfterPageChange) {
                    delay(48)
                    runCatching { firstEpisodeFocusRequester.requestFocus() }
                    refocusEpisodesAfterPageChange = false
                }
            }

            val pageEpisodes = sortedEpisodes
                .drop(safePage * episodesPerPage)
                .take(episodesPerPage)
            val gridRowData = pageEpisodes.chunked(columns)

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "选集",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "更新至${latestEpisode}/${totalCount}集",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    if (totalPages > 1) {
                        EpisodePaginationBar(
                            page = safePage + 1,
                            pageCount = totalPages,
                            prevPageFocusRequester = prevPageFocusRequester,
                            nextPageFocusRequester = nextPageFocusRequester,
                            lastLineFocusRequester = lastLineFocusRequester,
                            firstEpisodeFocusRequester = firstEpisodeFocusRequester,
                            lineSectionFocusRequester = lineSectionFocusRequester,
                            playSourceCount = playSourceCount,
                            onPrevPage = {
                                refocusEpisodesAfterPageChange = true
                                currentPage = (safePage - 1).coerceAtLeast(0)
                            },
                            onNextPage = {
                                refocusEpisodesAfterPageChange = true
                                currentPage = (safePage + 1).coerceAtMost(totalPages - 1)
                            }
                        )
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
                                val isSelected = episode.id == selectedEpisodeId
                                EpisodeNumberButton(
                                    episodeNumber = episode.episodeNumber,
                                    isSelected = isSelected,
                                    buttonWidth = buttonWidth,
                                    buttonHeight = buttonHeight,
                                    modifier = Modifier
                                        .then(
                                            if (rowIndex == 0 && colIndex == 0) {
                                                Modifier.focusRequester(firstEpisodeFocusRequester)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .focusProperties {
                                            if (colIndex == 0) {
                                                left = FocusRequester.Cancel
                                            }
                                            if (rowIndex == 0 && colIndex == 0 && hasPlaySourcesAbove) {
                                                up = lineSectionFocusRequester
                                            }
                                        },
                                    onFocus = { onEpisodeFocus(episode) },
                                    onClick = { onEpisodeClick(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun EpisodePaginationBar(
    page: Int,
    pageCount: Int,
    prevPageFocusRequester: FocusRequester,
    nextPageFocusRequester: FocusRequester,
    lastLineFocusRequester: FocusRequester,
    firstEpisodeFocusRequester: FocusRequester,
    lineSectionFocusRequester: FocusRequester,
    playSourceCount: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canFocusPrev = page > 1
    val canFocusNext = page < pageCount
    val lineFocusTarget = if (playSourceCount <= 1) {
        lineSectionFocusRequester
    } else {
        lastLineFocusRequester
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrevPage,
            enabled = canFocusPrev,
            colors = ButtonDefaults.colors(
                containerColor = SurfaceVariant,
                focusedContainerColor = PrimaryYellow,
                contentColor = TextPrimary,
                focusedContentColor = BackgroundDark,
                disabledContainerColor = SurfaceVariant.copy(alpha = 0.4f),
                disabledContentColor = TextMuted
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
            modifier = Modifier
                .focusRequester(prevPageFocusRequester)
                .focusProperties {
                    canFocus = pageCount > 1
                    left = lineFocusTarget
                    right = nextPageFocusRequester
                    down = firstEpisodeFocusRequester
                }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("上一页")
        }

        Text(
            text = "$page / $pageCount",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Button(
            onClick = onNextPage,
            enabled = canFocusNext,
            colors = ButtonDefaults.colors(
                containerColor = SurfaceVariant,
                focusedContainerColor = PrimaryYellow,
                contentColor = TextPrimary,
                focusedContentColor = BackgroundDark,
                disabledContainerColor = SurfaceVariant.copy(alpha = 0.4f),
                disabledContentColor = TextMuted
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
            modifier = Modifier
                .focusRequester(nextPageFocusRequester)
                .focusProperties {
                    canFocus = canFocusNext
                    left = prevPageFocusRequester
                    down = firstEpisodeFocusRequester
                }
        ) {
            Text("下一页")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeNumberButton(
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
            contentColor = if (isSelected) BackgroundDark else TextSecondary,
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
    ) {
        Text(
            text = episodeNumber.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (highlighted) {
                    androidx.compose.ui.text.font.FontWeight.Bold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                }
            ),
            color = if (highlighted) BackgroundDark else TextSecondary
        )
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
    val totalEpisodes: Int? = null,
    val path: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val releaseDate: String? = null
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
        val cast: List<CastItem> = emptyList(),
        val playSources: List<String> = emptyList(),
        val selectedPlaySourceIndex: Int = 0,
        val isLoadingPlayInfo: Boolean = false
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
