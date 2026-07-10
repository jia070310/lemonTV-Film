package com.lemon.yingshi.tv.ui.screens.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.FilterCatalogContinuation
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport
import com.lemon.yingshi.tv.domain.model.MacCmsHomeNavCategory
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialTypeId: Int = savedStateHandle.get<Int>("typeId") ?: -1
    private val initialNavCategory: MacCmsHomeNavCategory? =
        savedStateHandle.get<String>("nav")
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> MacCmsHomeNavCategory.entries.find { it.name == name } }

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    private var catalogContinuation: FilterCatalogContinuation? = null
    private var catalogPool: List<MacCmsVodItem> = emptyList()
    private var catalogExhausted = false
    private var isLoadingMore = false
    private var loadGeneration = 0
    private var enrichGeneration = 0

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val configured = macCmsRepository.getServerUrl().isNotBlank()
            if (!configured) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConfigured = false,
                        error = "请先在设置中配置 MacCMS 服务器地址"
                    )
                }
                return@launch
            }

            try {
                val initialNav = when {
                    initialTypeId > 0 -> MacCmsTaxonomy.navCategoryForTypeId(initialTypeId)
                    initialNavCategory != null -> initialNavCategory
                    else -> null
                } ?: MacCmsHomeNavCategory.TV
                val initialSelectedTypeId = if (initialTypeId > 0) initialTypeId else 0
                val initialExpanded = when {
                    initialTypeId > 0 -> initialNav
                    initialNavCategory != null -> initialNav
                    else -> null
                }

                _uiState.update {
                    it.copy(
                        isConfigured = true,
                        expandedNavCategory = initialExpanded,
                        selectedNavCategory = initialNav,
                        selectedTypeId = initialSelectedTypeId,
                        isLoading = false
                    )
                }
                loadResults(reset = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = MacCmsErrorMessages.fromThrowable(e, "加载分类失败")
                    )
                }
            }
        }
    }

    fun selectNavCategory(category: MacCmsHomeNavCategory) {
        val state = _uiState.value
        val shouldCollapse = state.expandedNavCategory == category && state.selectedTypeId == 0
        _uiState.update {
            it.copy(
                expandedNavCategory = if (shouldCollapse) null else category,
                selectedNavCategory = category,
                selectedTypeId = 0,
                selectedArea = "",
                selectedLang = "",
                selectedYear = "",
                selectedPlot = "",
                selectedSort = MacCmsSortOption.LATEST
            )
        }
        if (!shouldCollapse) {
            loadResults(reset = true)
        }
    }

    fun selectSecondaryType(typeId: Int) {
        val nav = MacCmsTaxonomy.navCategoryForTypeId(typeId) ?: return
        if (_uiState.value.selectedNavCategory == nav && _uiState.value.selectedTypeId == typeId) return
        _uiState.update {
            it.copy(
                expandedNavCategory = nav,
                selectedNavCategory = nav,
                selectedTypeId = typeId,
                selectedArea = "",
                selectedLang = "",
                selectedYear = "",
                selectedPlot = "",
                selectedSort = MacCmsSortOption.LATEST
            )
        }
        loadResults(reset = true)
    }

    fun selectArea(area: String) {
        _uiState.update { it.copy(selectedArea = area) }
        loadResults(reset = true)
    }

    fun selectLang(lang: String) {
        _uiState.update { it.copy(selectedLang = lang) }
        loadResults(reset = true)
    }

    fun selectYear(year: String) {
        _uiState.update { it.copy(selectedYear = year) }
        loadResults(reset = true)
    }

    fun selectPlot(plot: String) {
        _uiState.update { it.copy(selectedPlot = plot) }
        loadResults(reset = true)
    }

    fun selectSort(sort: MacCmsSortOption) {
        _uiState.update { it.copy(selectedSort = sort) }
        loadResults(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (isLoadingMore || state.isLoading) return

        val displayed = state.items.size
        if (displayed < catalogPool.size) {
            appendNextPageFromPool()
            return
        }
        if (catalogExhausted) return
        fetchMoreCatalog(displayed + MacCmsFilterSupport.FILTER_UI_PAGE_SIZE)
    }

    private fun appendNextPageFromPool() {
        val state = _uiState.value
        val nextBatch = catalogPool
            .drop(state.items.size)
            .take(MacCmsFilterSupport.FILTER_UI_PAGE_SIZE)
        if (nextBatch.isEmpty()) return
        val newItems = state.items + nextBatch
        _uiState.update {
            it.copy(
                items = newItems,
                currentPage = uiPageCount(newItems.size),
                hasMoreResults = !catalogExhausted || newItems.size < catalogPool.size
            )
        }
        enrichDisplayItems(loadGeneration)
    }

    private fun loadResults(reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val typeIds = MacCmsTaxonomy.filterTypeIdsForSelection(
                state.selectedNavCategory,
                state.selectedTypeId
            )
            if (typeIds.isEmpty()) return@launch

            val generation = if (reset) ++loadGeneration else loadGeneration
            if (reset) {
                catalogContinuation = null
                catalogPool = emptyList()
                catalogExhausted = false
            }

            _uiState.update {
                it.copy(
                    isLoading = reset,
                    isLoadingMore = !reset,
                    items = if (reset) emptyList() else it.items,
                    totalCount = if (reset) 0 else it.totalCount,
                    error = null
                )
            }

            try {
                val filter = buildFilterState(state, typeIds)
                val target = if (reset) {
                    MacCmsFilterSupport.FILTER_INITIAL_TARGET
                } else {
                    catalogPool.size + MacCmsFilterSupport.FILTER_UI_PAGE_SIZE
                }
                val allowed = MacCmsTaxonomy.allowedTypeIds(state.selectedNavCategory)
                val result = macCmsRepository.advanceFilterCatalog(
                    filter = filter,
                    continuation = if (reset) null else catalogContinuation,
                    targetSortedCount = target,
                    allowedTypeIds = allowed
                )
                if (generation != loadGeneration) return@launch

                catalogContinuation = result.continuation
                catalogPool = result.sorted
                catalogExhausted = result.exhaustedAll

                val total = computeDisplayTotal(filter, result)
                val displayItems = if (reset) {
                    catalogPool.take(MacCmsFilterSupport.FILTER_UI_PAGE_SIZE)
                } else {
                    catalogPool.take(state.items.size + MacCmsFilterSupport.FILTER_UI_PAGE_SIZE)
                }
                val totalPages = uiPageCount(total.coerceAtLeast(displayItems.size))

                _uiState.update {
                    it.copy(
                        items = displayItems,
                        currentPage = uiPageCount(displayItems.size).coerceAtLeast(1),
                        totalPages = totalPages,
                        totalCount = total,
                        hasMoreResults = !catalogExhausted || displayItems.size < catalogPool.size,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null
                    )
                }
                enrichDisplayItems(generation)
            } catch (e: Exception) {
                if (generation != loadGeneration) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = MacCmsErrorMessages.fromThrowable(e, "加载失败")
                    )
                }
            }
            isLoadingMore = false
        }
    }

    private fun fetchMoreCatalog(target: Int) {
        viewModelScope.launch {
            if (isLoadingMore) return@launch
            isLoadingMore = true
            val state = _uiState.value
            val generation = loadGeneration
            _uiState.update { it.copy(isLoadingMore = true) }

            try {
                val typeIds = MacCmsTaxonomy.filterTypeIdsForSelection(
                    state.selectedNavCategory,
                    state.selectedTypeId
                )
                val filter = buildFilterState(state, typeIds)
                val allowed = MacCmsTaxonomy.allowedTypeIds(state.selectedNavCategory)
                val result = macCmsRepository.advanceFilterCatalog(
                    filter = filter,
                    continuation = catalogContinuation,
                    targetSortedCount = target,
                    allowedTypeIds = allowed
                )
                if (generation != loadGeneration) return@launch

                catalogContinuation = result.continuation
                catalogPool = result.sorted
                catalogExhausted = result.exhaustedAll

                val total = computeDisplayTotal(filter, result)
                val displayItems = catalogPool.take(
                    state.items.size + MacCmsFilterSupport.FILTER_UI_PAGE_SIZE
                )
                _uiState.update {
                    it.copy(
                        items = displayItems,
                        currentPage = uiPageCount(displayItems.size).coerceAtLeast(1),
                        totalPages = uiPageCount(total.coerceAtLeast(displayItems.size)),
                        totalCount = total,
                        hasMoreResults = !catalogExhausted || displayItems.size < catalogPool.size,
                        isLoadingMore = false
                    )
                }
                enrichDisplayItems(generation)
            } catch (e: Exception) {
                if (generation != loadGeneration) return@launch
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        error = MacCmsErrorMessages.fromThrowable(e, "加载更多失败")
                    )
                }
            }
            isLoadingMore = false
        }
    }

    private fun buildFilterState(
        state: FilterUiState,
        typeIds: List<Int>
    ): MacCmsFilterSupport.FilterState =
        MacCmsFilterSupport.FilterState(
            typeIds = typeIds,
            plot = state.selectedPlot,
            area = state.selectedArea,
            lang = state.selectedLang,
            year = state.selectedYear,
            sort = state.selectedSort
        )

    private fun computeDisplayTotal(
        filter: MacCmsFilterSupport.FilterState,
        result: com.lemon.yingshi.tv.data.repository.FilterCatalogResult
    ): Int =
        if (MacCmsFilterSupport.filterListTotalUsesClientCount(
                filter.plot, filter.area, filter.lang
            )
        ) {
            result.sorted.size
        } else {
            result.apiTotalSum.coerceAtLeast(result.sorted.size)
        }

    private fun uiPageCount(itemCount: Int): Int {
        val size = MacCmsFilterSupport.FILTER_UI_PAGE_SIZE
        return if (itemCount <= 0) 1 else (itemCount + size - 1) / size
    }

    /** ac=list 常缺封面/年份等，用 ac=detail 补全可见卡片信息 */
    private fun enrichDisplayItems(catalogGeneration: Int) {
        val enrichGen = ++enrichGeneration
        viewModelScope.launch {
            val snapshot = _uiState.value.items
            if (snapshot.isEmpty()) return@launch
            try {
                val enriched = macCmsRepository.enrichVodItemsForDisplay(snapshot)
                if (enrichGen != enrichGeneration || catalogGeneration != loadGeneration) return@launch
                _uiState.update { state ->
                    if (state.items.size != enriched.size ||
                        state.items.map { it.vodId } != enriched.map { it.vodId }
                    ) {
                        return@update state
                    }
                    state.copy(items = enriched)
                }
            } catch (_: Exception) {
                // 详情补全失败不影响列表展示
            }
        }
    }

    fun retry() = loadInitialData()
}

data class FilterUiState(
    val isConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val treeCategories: List<MacCmsTaxonomy.FilterTreeCategory> = MacCmsTaxonomy.filterTreeCategories(),
    val expandedNavCategory: MacCmsHomeNavCategory? = null,
    val selectedNavCategory: MacCmsHomeNavCategory = MacCmsHomeNavCategory.TV,
    val selectedTypeId: Int = 0,
    val items: List<MacCmsVodItem> = emptyList(),
    val selectedArea: String = "",
    val selectedLang: String = "",
    val selectedYear: String = "",
    val selectedPlot: String = "",
    val selectedSort: MacCmsSortOption = MacCmsSortOption.LATEST,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val hasMoreResults: Boolean = false,
    val error: String? = null
) {
    private val filterOptions: MacCmsFilterSupport.FilterOptionRow
        get() = MacCmsFilterSupport.filterOptionsFor(selectedNavCategory)

    val plotOptions: List<String> get() = filterOptions.plot
    val areaOptions: List<String> get() = filterOptions.area
    val langOptions: List<String> get() = filterOptions.lang
    val yearOptions: List<String> get() = filterOptions.year
    val sortOptions: List<MacCmsSortOption> get() = filterOptions.sort

    val selectedCategoryName: String
        get() = if (selectedTypeId > 0) {
            MacCmsTaxonomy.secondaryLabel(selectedTypeId)
        } else {
            "${selectedNavCategory.label} · 全部"
        }

    val filterSummary: String
        get() {
            val parts = mutableListOf(selectedCategoryName)
            if (selectedPlot.isNotBlank()) parts.add(selectedPlot)
            if (selectedArea.isNotBlank()) parts.add(selectedArea)
            if (selectedYear.isNotBlank()) parts.add(selectedYear)
            if (selectedLang.isNotBlank()) parts.add(selectedLang)
            return parts.joinToString(" · ")
        }
}
