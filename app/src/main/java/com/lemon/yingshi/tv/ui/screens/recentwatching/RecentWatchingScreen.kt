package com.lemon.yingshi.tv.ui.screens.recentwatching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lemon.yingshi.tv.domain.service.WatchHistoryItem
import com.lemon.yingshi.tv.ui.screens.home.HomeViewModel
import com.lemon.yingshi.tv.ui.components.MediaCardFocusPlayIcon
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary

private const val RECENT_WATCHING_COLUMNS = 4

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecentWatchingScreen(
    onNavigateBack: () -> Unit,
    onPlayFromHistory: (WatchHistoryItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val recentWatchHistory by viewModel.recentWatchHistory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header with back button
            item {
                RecentWatchingHeader(
                    title = "最近播放 (${recentWatchHistory.size})",
                    onBackClick = onNavigateBack,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // Recent watching grid
            if (recentWatchHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无最近播放记录",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextMuted
                        )
                    }
                }
            } else {
                val columns = RECENT_WATCHING_COLUMNS
                val rowCount = (recentWatchHistory.size + columns - 1) / columns
                items(rowCount) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        for (column in 0 until columns) {
                            val index = rowIndex * columns + column
                            if (index < recentWatchHistory.size) {
                                val historyItem = recentWatchHistory[index]
                                RecentWatchingCard(
                                    historyItem = historyItem,
                                    onClick = { onPlayFromHistory(historyItem) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // 使用权重填充剩余空间，确保最后一行对齐
                                Spacer(modifier = Modifier.weight(1f))
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
private fun RecentWatchingHeader(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            colors = IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                focusedContainerColor = PrimaryYellow,
                focusedContentColor = BackgroundDark
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PrimaryYellow)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentWatchingCard(
    historyItem: WatchHistoryItem,
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

    // 优先展示接口保存的集名/版本名（HD国语、第01集 等）
    val displayTitle = historyItem.episodeTitle
        ?.takeIf { it.isNotBlank() }
        ?.let { "${historyItem.title} $it" }
        ?: historyItem.title

    val progressPercent = if (historyItem.duration > 0) {
        (historyItem.progress.toFloat() / historyItem.duration).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow.copy(alpha = 0.2f),
                pressedContainerColor = PrimaryYellow.copy(alpha = 0.2f)
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                )
            )
        ) {
            Box {
                val imageUrl = historyItem.coverImageModel()
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
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

                // Progress bar at bottom
                if (progressPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
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

                // Progress info at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "已看 $watchedTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    if (remainingTime.isNotEmpty()) {
                        Text(
                            text = remainingTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

