@file:OptIn(ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.screens.filter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
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
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport
import com.lemon.yingshi.tv.domain.model.MacCmsNavCategory
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import com.lemon.yingshi.tv.ui.components.InfoPillToast
import com.lemon.yingshi.tv.ui.components.VodPosterImage
import com.lemon.yingshi.tv.ui.components.VodPosterSkeleton
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.GlassBackground
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SIDEBAR_WIDTH = 168.dp
private const val FILTER_COLUMNS = 5
private const val LONG_PRESS_MS = 500L

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: FilterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sidebarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val firstChipFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val loadMoreFocusRequester = remember { FocusRequester() }
    val moveToContent: () -> Boolean = {
        firstChipFocusRequester.tryRequestFocus()
            || firstCardFocusRequester.tryRequestFocus()
            || contentFocusRequester.tryRequestFocus()
    }

    // 打开/回到筛选页时，默认焦点落在左上角：左侧分类栏第一项
    LaunchedEffect(Unit, uiState.treeCategories.size) {
        if (uiState.treeCategories.isEmpty()) return@LaunchedEffect
        delay(100)
        sidebarFocusRequester.tryRequestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when {
            !uiState.isConfigured && !uiState.isLoading -> {
                FilterEmptyState(
                    message = uiState.error ?: "请先在设置中配置 MacCMS 服务器",
                    onRetry = viewModel::retry,
                    onNavigateBack = onNavigateBack
                )
            }
            else -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    FilterTreeSidebar(
                        treeCategories = uiState.treeCategories,
                        expandedNavTypeId = uiState.expandedNavTypeId,
                        selectedNavTypeId = uiState.selectedNavTypeId,
                        selectedTypeId = uiState.selectedTypeId,
                        onNavCategoryClick = viewModel::selectNavCategory,
                        onSecondaryClick = viewModel::selectSecondaryType,
                        sidebarFocusRequester = sidebarFocusRequester,
                        onMoveToContent = moveToContent,
                        modifier = Modifier.width(SIDEBAR_WIDTH)
                    )

                    FilterScrollContent(
                        uiState = uiState,
                        onNavigateBack = onNavigateBack,
                        onAreaSelected = viewModel::selectArea,
                        onLangSelected = viewModel::selectLang,
                        onYearSelected = viewModel::selectYear,
                        onPlotSelected = viewModel::selectPlot,
                        onSortSelected = viewModel::selectSort,
                        onItemClick = { vod ->
                            viewModel.cacheVodForDetail(vod)
                            onNavigateToDetail(MacCmsIds.encode(vod.vodId))
                        },
                        onLoadMore = viewModel::loadMore,
                        onRetry = viewModel::retry,
                        sidebarFocusRequester = sidebarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                        firstChipFocusRequester = firstChipFocusRequester,
                        firstCardFocusRequester = firstCardFocusRequester,
                        loadMoreFocusRequester = loadMoreFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterTreeSidebar(
    treeCategories: List<MacCmsTaxonomy.FilterTreeCategory>,
    expandedNavTypeId: Int?,
    selectedNavTypeId: Int,
    selectedTypeId: Int,
    onNavCategoryClick: (MacCmsNavCategory) -> Unit,
    onSecondaryClick: (Int) -> Unit,
    sidebarFocusRequester: FocusRequester,
    onMoveToContent: () -> Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceDark)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "分类",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            pivotOffsets = PivotOffsets(parentFraction = 0.3f)
        ) {
            treeCategories.forEachIndexed { index, tree ->
                val isExpanded = expandedNavTypeId == tree.category.typeId
                val isMainSelected = selectedNavTypeId == tree.category.typeId && selectedTypeId == 0

                item(key = "main_${tree.category.typeId}") {
                    FilterSidebarItem(
                        text = tree.category.label,
                        isSelected = isMainSelected,
                        isChild = false,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.Default.KeyboardArrowRight
                                },
                                contentDescription = null,
                                tint = if (isMainSelected) PrimaryYellow else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = { onNavCategoryClick(tree.category) },
                        onMoveRight = onMoveToContent,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(sidebarFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                if (isExpanded) {
                    items(tree.children, key = { "child_${it.typeId}" }) { child ->
                        val isChildSelected = selectedTypeId == child.typeId
                        FilterSidebarItem(
                            text = child.label,
                            isSelected = isChildSelected,
                            isChild = true,
                            onClick = { onSecondaryClick(child.typeId) },
                            onMoveRight = onMoveToContent
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterSidebarItem(
    text: String,
    isSelected: Boolean,
    isChild: Boolean,
    onClick: () -> Unit,
    onMoveRight: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = PrimaryYellow
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (onMoveRight != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            // 优先按屏幕位置平移，找不到时再落到筛选条
                            if (!focusManager.moveFocus(FocusDirection.Right)) {
                                onMoveRight()
                            }
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isChild) 20.dp else 6.dp,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryYellow)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (isChild) {
                Spacer(modifier = Modifier.width(9.dp))
            }

            Text(
                text = text,
                style = if (isChild) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterScrollContent(
    uiState: FilterUiState,
    onNavigateBack: () -> Unit,
    onAreaSelected: (String) -> Unit,
    onLangSelected: (String) -> Unit,
    onYearSelected: (String) -> Unit,
    onPlotSelected: (String) -> Unit,
    onSortSelected: (MacCmsSortOption) -> Unit,
    onItemClick: (MacCmsVodItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    sidebarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    firstChipFocusRequester: FocusRequester,
    firstCardFocusRequester: FocusRequester,
    loadMoreFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val listState = rememberTvLazyListState()
    val scope = rememberCoroutineScope()
    var showScrollTopToast by remember { mutableStateOf(false) }
    val showScrollHint by remember {
        derivedStateOf {
            uiState.items.size > FILTER_COLUMNS && listState.firstVisibleItemIndex >= 2
        }
    }
    val scrollToTop: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            delay(120)
            firstChipFocusRequester.tryRequestFocus() || contentFocusRequester.tryRequestFocus()
            showScrollTopToast = true
        }
    }
    val cardLongPressUpModifier = rememberFilterLongPressUpModifier(
        enabled = { uiState.items.size > FILTER_COLUMNS },
        onScrollToTop = scrollToTop
    )

    LaunchedEffect(showScrollTopToast) {
        if (showScrollTopToast) {
            delay(1800)
            showScrollTopToast = false
        }
    }

    LaunchedEffect(uiState.isLoadingMore) {
        // 仅在用户触发「加载更多」时把焦点移到按钮，避免首屏加载完成后抢走默认焦点
        if (uiState.isLoadingMore) {
            loadMoreFocusRequester.tryRequestFocus()
        }
    }

    Box(
        modifier = modifier.focusRequester(contentFocusRequester)
    ) {
        TvLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            pivotOffsets = PivotOffsets(parentFraction = 0.12f)
        ) {
        item(key = "topbar") {
            FilterTopBar(
                summary = uiState.filterSummary,
                totalCount = uiState.totalCount,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                onNavigateBack = onNavigateBack,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }

        item(key = "chips") {
            FilterChipPanel(
                uiState = uiState,
                onAreaSelected = onAreaSelected,
                onLangSelected = onLangSelected,
                onYearSelected = onYearSelected,
                onPlotSelected = onPlotSelected,
                onSortSelected = onSortSelected,
                firstChipFocusRequester = firstChipFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }

        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                val skeletonCount = MacCmsFilterSupport.FILTER_UI_PAGE_SIZE
                val skeletonRows = (skeletonCount + FILTER_COLUMNS - 1) / FILTER_COLUMNS
                items(skeletonRows, key = { "skeleton_row_$it" }) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (col in 0 until FILTER_COLUMNS) {
                            val index = rowIndex * FILTER_COLUMNS + col
                            if (index < skeletonCount) {
                                FilterSkeletonCard(modifier = Modifier.weight(1f))
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            uiState.error != null && uiState.items.isEmpty() -> {
                item(key = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val errorMessage = uiState.error
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.colors(
                                    containerColor = SurfaceVariant,
                                    focusedContainerColor = PrimaryYellow,
                                    contentColor = TextPrimary,
                                    focusedContentColor = BackgroundDark
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重试")
                            }
                        }
                    }
                }
            }
            uiState.items.isEmpty() -> {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无符合条件的影片",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted
                        )
                    }
                }
            }
            else -> {
                val rowCount = (uiState.items.size + FILTER_COLUMNS - 1) / FILTER_COLUMNS
                items(rowCount, key = { "row_$it" }) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                            .then(cardLongPressUpModifier),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (col in 0 until FILTER_COLUMNS) {
                            val index = rowIndex * FILTER_COLUMNS + col
                            if (index < uiState.items.size) {
                                FilterVodCard(
                                    vod = uiState.items[index],
                                    onClick = { onItemClick(uiState.items[index]) },
                                    modifier = Modifier.weight(1f),
                                    cardModifier = Modifier
                                        .then(
                                            if (index == 0) {
                                                Modifier.focusRequester(firstCardFocusRequester)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (col == 0) {
                                                Modifier.onPreviewKeyEvent { event ->
                                                    if (
                                                        event.type == KeyEventType.KeyDown &&
                                                        event.key == Key.DirectionLeft
                                                    ) {
                                                        // 筛选条可能已滚出屏幕被回收，tryRequestFocus 安全降级到侧栏
                                                        firstChipFocusRequester.tryRequestFocus()
                                                            || sidebarFocusRequester.tryRequestFocus()
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
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

                if (uiState.hasMoreResults) {
                    item(key = "load_more") {
                        val loadMoreModifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .focusRequester(loadMoreFocusRequester)
                            .focusProperties {
                                down = FocusRequester.Cancel
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                    firstChipFocusRequester.tryRequestFocus()
                                        || sidebarFocusRequester.tryRequestFocus()
                                    true
                                } else {
                                    false
                                }
                            }

                        Box(
                            modifier = if (uiState.isLoadingMore) {
                                loadMoreModifier.focusable()
                            } else {
                                loadMoreModifier
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(
                                    color = PrimaryYellow,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Button(
                                    onClick = onLoadMore,
                                    colors = ButtonDefaults.colors(
                                        containerColor = SurfaceVariant,
                                        focusedContainerColor = PrimaryYellow,
                                        contentColor = TextPrimary,
                                        focusedContentColor = BackgroundDark
                                    )
                                ) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (showScrollHint) {
            FilterScrollToTopHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }

        if (showScrollTopToast) {
            InfoPillToast(
                message = "已返回顶部",
                bottomPadding = 72.dp
            )
        }
    }
}

@Composable
private fun rememberFilterLongPressUpModifier(
    enabled: () -> Boolean,
    onScrollToTop: () -> Unit
): Modifier {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var upJob by remember { mutableStateOf<Job?>(null) }
    var upLongTriggered by remember { mutableStateOf(false) }

    return Modifier.onPreviewKeyEvent { event ->
        if (!enabled() || event.key != Key.DirectionUp) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount != 0) return@onPreviewKeyEvent true
                upLongTriggered = false
                upJob?.cancel()
                upJob = scope.launch {
                    delay(LONG_PRESS_MS)
                    upLongTriggered = true
                    onScrollToTop()
                }
                true
            }
            KeyEventType.KeyUp -> {
                upJob?.cancel()
                upJob = null
                if (!upLongTriggered) {
                    focusManager.moveFocus(FocusDirection.Up)
                }
                upLongTriggered = false
                true
            }
            else -> false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterScrollToTopHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassBackground)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, PrimaryYellow.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "长按 ↑",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryYellow,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "返回顶部",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterSkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            VodPosterSkeleton(modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceVariant)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceVariant.copy(alpha = 0.7f))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterRefreshingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = PrimaryYellow)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "正在从服务器获取数据…",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterTopBar(
    summary: String,
    totalCount: Int,
    currentPage: Int,
    totalPages: Int,
    onNavigateBack: () -> Unit,
    sidebarFocusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack,
            colors = IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                focusedContainerColor = PrimaryYellow,
                focusedContentColor = BackgroundDark
            ),
            modifier = Modifier
                .focusProperties { left = sidebarFocusRequester }
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PrimaryYellow)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "筛选浏览",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (totalCount > 0) {
            Text(
                text = "共 $totalCount 部 · 第 $currentPage/$totalPages 页",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChipPanel(
    uiState: FilterUiState,
    onAreaSelected: (String) -> Unit,
    onLangSelected: (String) -> Unit,
    onYearSelected: (String) -> Unit,
    onPlotSelected: (String) -> Unit,
    onSortSelected: (MacCmsSortOption) -> Unit,
    firstChipFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        FilterChipRow(
            label = "剧情",
            options = listOf("") + uiState.plotOptions,
            selected = uiState.selectedPlot,
            onSelected = onPlotSelected,
            firstChipFocusRequester = firstChipFocusRequester,
            sidebarFocusRequester = sidebarFocusRequester
        )
        FilterChipRow("地区", listOf("") + uiState.areaOptions, uiState.selectedArea, onAreaSelected)
        FilterChipRow("语言", listOf("") + uiState.langOptions, uiState.selectedLang, onLangSelected)
        FilterChipRow("年份", listOf("") + uiState.yearOptions, uiState.selectedYear, onYearSelected)
        FilterChipRow(
            label = "排序",
            options = uiState.sortOptions.map { it.label },
            selected = uiState.selectedSort.label,
            onSelected = { label ->
                uiState.sortOptions.find { it.label == label }?.let(onSortSelected)
            },
            displayTransform = { if (it.isBlank()) "全部" else it }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    displayTransform: (String) -> String = { if (it.isBlank()) "全部" else it },
    firstChipFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(56.dp)
        )

        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(options) { option ->
                val isSelected = option == selected
                val displayText = displayTransform(option)
                val isFirstChip = firstChipFocusRequester != null && option == options.first()
                val chipRequester = firstChipFocusRequester
                Card(
                    onClick = { onSelected(option) },
                    colors = CardDefaults.colors(
                        containerColor = if (isSelected) PrimaryYellow else SurfaceVariant,
                        focusedContainerColor = PrimaryYellow
                    ),
                    scale = CardDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .height(36.dp)
                        .then(
                            if (isFirstChip && chipRequester != null) {
                                Modifier
                                    .focusRequester(chipRequester)
                                    .focusProperties {
                                        if (sidebarFocusRequester != null) {
                                            left = sidebarFocusRequester
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            sidebarFocusRequester != null &&
                                            event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionLeft
                                        ) {
                                            sidebarFocusRequester.tryRequestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) BackgroundDark else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterVodCard(
    vod: MacCmsVodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = cardModifier
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
            VodPosterImage(
                posterUrl = vod.vodPic,
                thumbUrl = vod.vodPicThumb,
                contentDescription = vod.vodName,
                modifier = Modifier.fillMaxSize(),
                showFocusPlayIcon = true,
                isFocused = isFocused,
                iconSize = 44.dp,
                crossfade = true,
                overlay = {
                    val remarks = vod.vodRemarks
                    if (!remarks.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(4.dp))
                                .background(PrimaryYellow.copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = remarks,
                                style = MaterialTheme.typography.labelSmall,
                                color = BackgroundDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val score = vod.vodScore?.toFloatOrNull()
                    if (score != null && score > 0f) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "%.1f".format(score),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10b981)
                            )
                        }
                    }

                    val vodYear = vod.vodYear
                    if (!vodYear.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = vodYear,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = vod.vodName,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = buildCardMeta(vod),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildCardMeta(vod: MacCmsVodItem): String {
    val parts = mutableListOf<String>()
    val year = vod.vodYear
    if (!year.isNullOrBlank()) parts.add(year)
    val genre = vod.vodClass?.split(Regex("[,，|]"))?.firstOrNull()?.trim()
        ?: vod.typeName?.takeIf { it.isNotBlank() }
    if (!genre.isNullOrBlank()) parts.add(genre)
    val area = vod.vodArea
    if (!area.isNullOrBlank()) parts.add(area)
    return parts.joinToString(" · ").ifBlank { "—" }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterEmptyState(
    message: String,
    onRetry: () -> Unit,
    onNavigateBack: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onNavigateBack != null) {
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceVariant,
                        focusedContainerColor = PrimaryYellow,
                        contentColor = TextPrimary,
                        focusedContentColor = BackgroundDark
                    )
                ) {
                    Text("返回")
                }
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.colors(
                    containerColor = SurfaceVariant,
                    focusedContainerColor = PrimaryYellow,
                    contentColor = TextPrimary,
                    focusedContentColor = BackgroundDark
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}
