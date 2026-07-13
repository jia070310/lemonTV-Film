package com.lemon.yingshi.tv.ui.screens.recommended

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.ui.screens.home.HOME_RECOMMENDED_PAGE_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class RecommendedViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecommendedUiState>(RecommendedUiState.Loading)
    val uiState: StateFlow<RecommendedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** TV 推荐页：分页浏览（可深扫） */
    fun loadPage(page: Int) {
        viewModelScope.launch {
            if (_uiState.value !is RecommendedUiState.Success) {
                _uiState.value = RecommendedUiState.Loading
            }
            if (macCmsRepository.getServerUrl().isBlank()) {
                _uiState.value = RecommendedUiState.Error("请先在设置中配置 MacCMS 服务器")
                return@launch
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    macCmsRepository.fetchRecommendedVods(
                        level = RECOMMENDED_LEVEL,
                        page = page,
                        pageSize = HOME_RECOMMENDED_PAGE_SIZE,
                        fullScan = true
                    )
                }
                if (result.error != null && result.items.isEmpty()) {
                    _uiState.value = RecommendedUiState.Error(
                        MacCmsErrorMessages.fromMessage(result.error, "加载推荐失败")
                    )
                    return@launch
                }

                _uiState.value = if (result.items.isEmpty()) {
                    RecommendedUiState.Empty()
                } else {
                    RecommendedUiState.Success(
                        items = result.items,
                        total = result.total,
                        page = result.page,
                        pageCount = result.pageCount
                    )
                }
            } catch (e: Exception) {
                _uiState.value = RecommendedUiState.Error(
                    MacCmsErrorMessages.fromThrowable(e, "加载推荐失败")
                )
            }
        }
    }

    /** 手机推荐 Tab：快速探测，无数据立即显示空态，不无限扫描 */
    fun loadMobileFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                val current = _uiState.value
                if (current is RecommendedUiState.Success && current.items.isNotEmpty()) return@launch
                if (current is RecommendedUiState.Empty) return@launch
            }

            val showRefreshIndicator = forceRefresh || _uiState.value is RecommendedUiState.Success
            if (showRefreshIndicator) _isRefreshing.value = true
            try {
                if (macCmsRepository.getServerUrl().isBlank()) {
                    _uiState.value = RecommendedUiState.Error("请先在设置中配置 MacCMS 服务器")
                    return@launch
                }

                if (forceRefresh) {
                    withContext(Dispatchers.IO) {
                        macCmsRepository.invalidateRecommendedCache()
                    }
                }

                if (!forceRefresh) {
                    val cachedPage = withContext(Dispatchers.IO) {
                        macCmsRepository.peekRecommendedCache(RECOMMENDED_LEVEL)?.let { cached ->
                            sliceRecommendedPage(cached, page = 1)
                        }
                    }
                    cachedPage?.takeIf { it.items.isNotEmpty() }?.let { preview ->
                        _uiState.value = RecommendedUiState.Success(
                            items = preview.items,
                            total = preview.total,
                            page = preview.page,
                            pageCount = preview.pageCount
                        )
                    }
                }

                if (_uiState.value !is RecommendedUiState.Success) {
                    _uiState.value = RecommendedUiState.Loading
                }

                val firstPage = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(MOBILE_FETCH_TIMEOUT_MS) {
                        macCmsRepository.fetchRecommendedVods(
                            level = RECOMMENDED_LEVEL,
                            page = 1,
                            pageSize = MOBILE_PAGE_SIZE,
                            fullScan = false,
                            quickPreview = true
                        )
                    }
                }
                if (firstPage == null) {
                    if (_uiState.value !is RecommendedUiState.Success) {
                        _uiState.value = RecommendedUiState.Empty()
                    }
                    return@launch
                }
                if (firstPage.error != null && firstPage.items.isEmpty()) {
                    if (_uiState.value !is RecommendedUiState.Success) {
                        _uiState.value = RecommendedUiState.Error(
                            MacCmsErrorMessages.fromMessage(firstPage.error, "加载推荐失败")
                        )
                    }
                    return@launch
                }

                _uiState.value = if (firstPage.items.isEmpty()) {
                    RecommendedUiState.Empty()
                } else {
                    RecommendedUiState.Success(
                        items = firstPage.items,
                        total = firstPage.total,
                        page = firstPage.page,
                        pageCount = firstPage.pageCount
                    )
                }
            } catch (e: Exception) {
                if (_uiState.value !is RecommendedUiState.Success) {
                    _uiState.value = RecommendedUiState.Error(
                        MacCmsErrorMessages.fromThrowable(e, "加载推荐失败")
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? RecommendedUiState.Success ?: return
        if (_isLoadingMore.value || state.page >= state.pageCount) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                if (macCmsRepository.getServerUrl().isBlank()) return@launch
                val nextPage = state.page + 1
                val result = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(MOBILE_FETCH_TIMEOUT_MS) {
                        macCmsRepository.fetchRecommendedVods(
                            level = RECOMMENDED_LEVEL,
                            page = nextPage,
                            pageSize = MOBILE_PAGE_SIZE,
                            fullScan = false,
                            quickPreview = true
                        )
                    }
                } ?: return@launch
                if (result.items.isEmpty()) return@launch
                _uiState.value = state.copy(
                    items = state.items + result.items,
                    total = result.total,
                    page = result.page,
                    pageCount = result.pageCount
                )
            } catch (_: Exception) {
                // 加载更多失败不影响已展示内容
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** @deprecated 手机端请使用 [loadMobileFeed] */
    fun loadFullList(forceRefresh: Boolean = false) {
        loadMobileFeed(forceRefresh = forceRefresh)
    }

    fun goToPage(page: Int) {
        val state = _uiState.value as? RecommendedUiState.Success ?: return
        if (page < 1 || page > state.pageCount || page == state.page) return
        loadPage(page)
    }

    fun cacheVodForDetail(vod: MacCmsVodItem) {
        macCmsRepository.putCachedVodSnapshot(vod)
    }

    private data class RecommendedPageSlice(
        val items: List<MacCmsVodItem>,
        val total: Int,
        val page: Int,
        val pageCount: Int
    )

    private fun sliceRecommendedPage(
        all: List<MacCmsVodItem>,
        page: Int
    ): RecommendedPageSlice {
        val safePage = page.coerceAtLeast(1)
        val start = (safePage - 1) * MOBILE_PAGE_SIZE
        val pageItems = all.drop(start).take(MOBILE_PAGE_SIZE)
        val pageCount = if (all.isEmpty()) 1 else ((all.size + MOBILE_PAGE_SIZE - 1) / MOBILE_PAGE_SIZE)
        return RecommendedPageSlice(
            items = pageItems,
            total = all.size,
            page = safePage.coerceAtMost(pageCount),
            pageCount = pageCount
        )
    }

    companion object {
        const val RECOMMENDED_LEVEL = 9
        private const val MOBILE_PAGE_SIZE = HOME_RECOMMENDED_PAGE_SIZE
        private const val MOBILE_FETCH_TIMEOUT_MS = 12_000L
    }
}

sealed class RecommendedUiState {
    data object Loading : RecommendedUiState()
    data class Success(
        val items: List<MacCmsVodItem>,
        val total: Int,
        val page: Int,
        val pageCount: Int
    ) : RecommendedUiState()
    data class Empty(val message: String = NO_RECOMMEND_MESSAGE) : RecommendedUiState()

    data class Error(val message: String) : RecommendedUiState()

    companion object {
        const val NO_RECOMMEND_MESSAGE = "服务器无推荐影片"
    }
}
