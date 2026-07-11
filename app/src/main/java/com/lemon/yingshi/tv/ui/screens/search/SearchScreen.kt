@file:OptIn(ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lemon.yingshi.tv.ui.components.MediaCardFocusPlayIcon
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.GlassBackground
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val SEARCH_GRID_COLUMNS = 5

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    val inputFocusRequester = remember { FocusRequester() }
    val firstHistoryFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val fallbackContentFocusRequester = remember { FocusRequester() }
    val firstContentFocusRequester = when (uiState) {
        is SearchUiState.Success -> firstResultFocusRequester
        else -> if (searchHistory.isNotEmpty()) firstHistoryFocusRequester else fallbackContentFocusRequester
    }

    LaunchedEffect(Unit) {
        delay(150)
        inputFocusRequester.tryRequestFocus()
    }

    BackHandler(onBack = onNavigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = { viewModel.search() },
                onClear = viewModel::clearSearch,
                onNavigateBack = onNavigateBack,
                inputFocusRequester = inputFocusRequester,
                firstContentFocusRequester = firstContentFocusRequester
            )

            when (val state = uiState) {
                is SearchUiState.Initial -> {
                    SearchInitialContent(
                        searchHistory = searchHistory,
                        onHistoryClick = { query ->
                            viewModel.onSearchQueryChange(query)
                            viewModel.search()
                        },
                        onClearHistory = viewModel::clearHistory,
                        firstHistoryFocusRequester = firstHistoryFocusRequester,
                        inputFocusRequester = inputFocusRequester
                    )
                }
                is SearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryYellow)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "搜索中…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }
                }
                is SearchUiState.Success -> {
                    SearchResultsGrid(
                        results = state.results,
                        total = state.total,
                        page = state.page,
                        pageCount = state.pageCount,
                        onItemClick = onNavigateToDetail,
                        onPrevPage = { viewModel.goToPage(state.page - 1) },
                        onNextPage = { viewModel.goToPage(state.page + 1) },
                        firstResultFocusRequester = firstResultFocusRequester,
                        inputFocusRequester = inputFocusRequester
                    )
                }
                is SearchUiState.Empty -> {
                    SearchMessageState(
                        message = "未找到「$searchQuery」相关结果",
                        hint = "请尝试更换关键词后重新搜索"
                    )
                }
                is SearchUiState.Error -> {
                    SearchMessageState(
                        message = state.message,
                        hint = "请检查网络或 MacCMS 服务器配置"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit,
    inputFocusRequester: FocusRequester,
    firstContentFocusRequester: FocusRequester
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputFocused by remember { mutableStateOf(false) }

    fun moveToContentBelow(): Boolean {
        keyboardController?.hide()
        return firstContentFocusRequester.tryRequestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "搜索影片",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
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
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceDark)
                    .border(
                        width = if (inputFocused) 2.dp else 0.dp,
                        color = if (inputFocused) PrimaryYellow else Color.Transparent,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .focusRequester(inputFocusRequester)
                    .onFocusChanged { focusState ->
                        inputFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            keyboardController?.show()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.DirectionCenter -> {
                                onSearch()
                                true
                            }
                            Key.DirectionDown -> moveToContentBelow()
                            else -> false
                        }
                    }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        cursorBrush = SolidColor(PrimaryYellow),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索电影、电视剧、综艺、动漫…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextMuted
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            colors = IconButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextMuted,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = onSearch,
                colors = IconButtonDefaults.colors(
                    containerColor = SurfaceVariant,
                    contentColor = PrimaryYellow,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = "输入关键词后按确认键搜索，下键可进入历史或结果",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(start = 52.dp, top = 10.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchInitialContent(
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    firstHistoryFocusRequester: FocusRequester,
    inputFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp)
    ) {
        if (searchHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "搜索历史",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = TextMuted,
                        focusedContainerColor = PrimaryYellow,
                        focusedContentColor = BackgroundDark
                    )
                ) {
                    Text("清除历史")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TvLazyRow(
                contentPadding = PaddingValues(end = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                pivotOffsets = PivotOffsets(parentFraction = 0.15f)
            ) {
                items(searchHistory, key = { it }) { history ->
                    val index = searchHistory.indexOf(history)
                    SearchHistoryChip(
                        query = history,
                        onClick = { onHistoryClick(history) },
                        modifier = Modifier
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(firstHistoryFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .focusProperties { up = inputFocusRequester }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "输入影片名称开始搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "支持搜索电影、电视剧、综艺、动漫等内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchHistoryChip(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.colors(
            containerColor = SurfaceVariant,
            focusedContainerColor = PrimaryYellow,
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.85f)
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultsGrid(
    results: List<SearchResultItem>,
    total: Int,
    page: Int,
    pageCount: Int,
    onItemClick: (String) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    firstResultFocusRequester: FocusRequester,
    inputFocusRequester: FocusRequester
) {
    val rowCount = (results.size + SEARCH_GRID_COLUMNS - 1) / SEARCH_GRID_COLUMNS

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.12f)
    ) {
        item {
            Text(
                text = "共找到 $total 个结果 · 第 $page/$pageCount 页",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        items(rowCount, key = { "search_row_$it" }) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (col in 0 until SEARCH_GRID_COLUMNS) {
                    val index = rowIndex * SEARCH_GRID_COLUMNS + col
                    if (index < results.size) {
                        SearchResultGridCard(
                            result = results[index],
                            onClick = { onItemClick(results[index].id) },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == 0) {
                                        Modifier
                                            .focusRequester(firstResultFocusRequester)
                                            .focusProperties { up = inputFocusRequester }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (pageCount > 1) {
            item {
                SearchPaginationBar(
                    page = page,
                    pageCount = pageCount,
                    onPrevPage = onPrevPage,
                    onNextPage = onNextPage,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultGridCard(
    result: SearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box {
                if (!result.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(result.posterUrl)
                            .crossfade(false)
                            .build(),
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (!isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                MediaCardFocusPlayIcon(isFocused = isFocused, iconSize = 48.dp)
                if (!result.remarks.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryYellow.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = result.remarks,
                            style = MaterialTheme.typography.labelSmall,
                            color = BackgroundDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(
            result.year?.takeIf { it.isNotBlank() },
            result.rating?.takeIf { it > 0f }?.let { "%.1f".format(it) }
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SearchPaginationBar(
    page: Int,
    pageCount: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrevPage,
            enabled = page > 1,
            colors = ButtonDefaults.colors(
                containerColor = SurfaceVariant,
                focusedContainerColor = PrimaryYellow,
                contentColor = TextPrimary,
                focusedContentColor = BackgroundDark,
                disabledContainerColor = SurfaceVariant.copy(alpha = 0.4f),
                disabledContentColor = TextMuted
            )
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("上一页")
        }

        Text(
            text = "$page / $pageCount",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Button(
            onClick = onNextPage,
            enabled = page < pageCount,
            colors = ButtonDefaults.colors(
                containerColor = SurfaceVariant,
                focusedContainerColor = PrimaryYellow,
                contentColor = TextPrimary,
                focusedContentColor = BackgroundDark,
                disabledContainerColor = SurfaceVariant.copy(alpha = 0.4f),
                disabledContentColor = TextMuted
            )
        ) {
            Text("下一页")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchMessageState(
    message: String,
    hint: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(GlassBackground)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

data class SearchResultItem(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val rating: Float?,
    val year: String?,
    val remarks: String? = null,
    val genres: List<String>,
    val type: com.lemon.yingshi.tv.domain.model.MediaType
)
