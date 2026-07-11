package com.lemon.yingshi.tv.ui.screens.recommended

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.ui.screens.home.HOME_RECOMMENDED_PAGE_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendedViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecommendedUiState>(RecommendedUiState.Loading)
    val uiState: StateFlow<RecommendedUiState> = _uiState.asStateFlow()

    init {
        loadPage(1)
    }

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
                val result = macCmsRepository.fetchRecommendedVods(
                    level = 9,
                    page = page,
                    pageSize = HOME_RECOMMENDED_PAGE_SIZE,
                    fullScan = true
                )
                if (result.error != null && result.items.isEmpty()) {
                    _uiState.value = RecommendedUiState.Error(
                        MacCmsErrorMessages.fromMessage(result.error, "加载推荐失败")
                    )
                    return@launch
                }

                _uiState.value = if (result.items.isEmpty()) {
                    RecommendedUiState.Empty
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

    fun goToPage(page: Int) {
        val state = _uiState.value as? RecommendedUiState.Success ?: return
        if (page < 1 || page > state.pageCount || page == state.page) return
        loadPage(page)
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
    data object Empty : RecommendedUiState()
    data class Error(val message: String) : RecommendedUiState()
}
