package com.lemon.yingshi.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.SearchHistoryPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.mapMacCmsTypeName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SEARCH_PAGE_SIZE = 30

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository,
    private val searchHistoryPreferences: SearchHistoryPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            searchHistoryPreferences.history.collect { history ->
                _searchHistory.value = history
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _uiState.value = SearchUiState.Initial
        }
    }

    fun search(page: Int = 1) {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            if (page == 1) {
                addToHistory(query)
            }

            if (macCmsRepository.getServerUrl().isBlank()) {
                _uiState.value = SearchUiState.Error("请先在设置中配置 MacCMS 服务器")
                return@launch
            }

            try {
                val response = macCmsRepository.searchVod(
                    keyword = query,
                    page = page,
                    pageSize = SEARCH_PAGE_SIZE
                )
                if (response.code != 1) {
                    _uiState.value = SearchUiState.Error(
                        MacCmsErrorMessages.fromMessage(response.msg, "搜索失败")
                    )
                    return@launch
                }

                val enriched = macCmsRepository.enrichVodItemsForDisplay(response.list)
                val results = enriched.map { it.toSearchResultItem() }
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(
                        results = results,
                        total = response.total,
                        page = response.page.coerceAtLeast(1),
                        pageCount = response.pagecount.coerceAtLeast(1)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(
                    MacCmsErrorMessages.fromThrowable(e, "搜索失败")
                )
            }
        }
    }

    fun goToPage(page: Int) {
        val state = _uiState.value as? SearchUiState.Success ?: return
        if (page < 1 || page > state.pageCount || page == state.page) return
        search(page)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Initial
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryPreferences.clear()
        }
    }

    private fun addToHistory(query: String) {
        viewModelScope.launch {
            searchHistoryPreferences.addQuery(query)
        }
    }

    private fun MacCmsVodItem.toSearchResultItem(): SearchResultItem {
        val score = vodScore?.trim()?.toFloatOrNull()
        return SearchResultItem(
            id = MacCmsIds.encode(vodId),
            title = vodName,
            originalTitle = null,
            overview = vodBlurb?.takeIf { it.isNotBlank() } ?: vodContent,
            posterUrl = vodPic,
            rating = score?.takeIf { it > 0f },
            year = vodYear?.takeIf { it.isNotBlank() },
            remarks = vodRemarks?.takeIf { it.isNotBlank() },
            genres = emptyList(),
            type = mapMacCmsTypeName(typeName)
        )
    }
}

sealed class SearchUiState {
    data object Initial : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(
        val results: List<SearchResultItem>,
        val total: Int = 0,
        val page: Int = 1,
        val pageCount: Int = 1
    ) : SearchUiState()
    data object Empty : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
