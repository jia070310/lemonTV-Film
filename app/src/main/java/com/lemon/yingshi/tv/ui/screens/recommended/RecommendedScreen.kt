package com.lemon.yingshi.tv.ui.screens.recommended

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.ui.screens.home.HOME_RECOMMENDED_COLUMNS
import com.lemon.yingshi.tv.ui.screens.home.MacCmsVodCard
import com.lemon.yingshi.tv.ui.screens.search.SearchPaginationBar
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecommendedScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: RecommendedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (val state = uiState) {
            RecommendedUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryYellow)
                }
            }

            is RecommendedUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    RecommendedHeader(
                        title = "最新推荐",
                        total = 0,
                        onBackClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMuted
                    )
                }
            }

            RecommendedUiState.Empty -> {
                TvLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        RecommendedHeader(
                            title = "最新推荐",
                            total = 0,
                            onBackClick = onNavigateBack,
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无推荐内容",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            is RecommendedUiState.Success -> {
                val rows = state.items.chunked(HOME_RECOMMENDED_COLUMNS)
                TvLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.12f)
                ) {
                    item {
                        RecommendedHeader(
                            title = "最新推荐",
                            total = state.total,
                            onBackClick = onNavigateBack,
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }

                    if (state.total > 0) {
                        item {
                            Text(
                                text = if (state.pageCount > 1) {
                                    "共 ${state.total} 部 · 第 ${state.page}/${state.pageCount} 页"
                                } else {
                                    "共 ${state.total} 部"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(rows.size, key = { "recommended_row_$it" }) { rowIndex ->
                        RecommendedScrollRow(
                            items = rows[rowIndex],
                            onItemClick = { vod -> onNavigateToDetail(MacCmsIds.encode(vod.vodId)) },
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    if (state.pageCount > 1) {
                        item {
                            SearchPaginationBar(
                                page = state.page,
                                pageCount = state.pageCount,
                                onPrevPage = { viewModel.goToPage(state.page - 1) },
                                onNextPage = { viewModel.goToPage(state.page + 1) },
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecommendedScrollRow(
    items: List<com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem>,
    onItemClick: (com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem) -> Unit,
    modifier: Modifier = Modifier
) {
    TvLazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 48.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.2f)
    ) {
        items(items.size, key = { index -> "rec_${items[index].vodId}" }) { index ->
            val vod = items[index]
            MacCmsVodCard(
                vod = vod,
                onClick = { onItemClick(vod) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecommendedHeader(
    title: String,
    total: Int,
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
                .background(Color(0xFFf97316))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = if (total > 0) "$title ($total)" else title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
    }
}
