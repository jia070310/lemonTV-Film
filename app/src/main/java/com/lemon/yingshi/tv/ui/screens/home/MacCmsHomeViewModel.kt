package com.lemon.yingshi.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MacCmsHomeSectionRef
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class MacCmsHomeSection(
    val sectionKey: String,
    val typeName: String,
    val typeId: Int,
    /** 首页顶级分类栏目跳转筛选页时使用；二级子类栏目为 null */
    val navTypeId: Int? = null,
    val items: List<MacCmsVodItem>,
    val total: Int,
    val error: String? = null
)

data class MacCmsHomeUiState(
    val isLoading: Boolean = false,
    val isLoadingSections: Boolean = false,
    val isConfigured: Boolean = false,
    val sections: List<MacCmsHomeSection> = emptyList(),
    val recommendedItems: List<MacCmsVodItem> = emptyList(),
    val recommendedTotal: Int = 0,
    val error: String? = null
)

@HiltViewModel
class MacCmsHomeViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository,
    private val categorySortPreferences: MacCmsCategorySortPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacCmsHomeUiState(isLoading = true))
    val uiState: StateFlow<MacCmsHomeUiState> = _uiState.asStateFlow()

    private var homeLoadGeneration = 0
    private var sectionEnrichGeneration = 0

    init {
        loadHome()
        observeHomeConfigChanges()
        observeServerUrlChanges()
    }

    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val generation = ++homeLoadGeneration
            val hasCachedData = _uiState.value.sections.isNotEmpty() ||
                _uiState.value.recommendedItems.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hasCachedData,
                    isLoadingSections = false,
                    error = null
                )
            }
            if (forceRefresh) {
                macCmsRepository.invalidateRecommendedCache()
            }
            if (macCmsRepository.getServerUrl().isBlank()) {
                _uiState.update {
                    MacCmsHomeUiState(
                        isLoading = false,
                        isConfigured = false,
                        error = "请先在设置中配置 MacCMS 服务器"
                    )
                }
                return@launch
            }

            try {
                val taxonomy = macCmsRepository.fetchTaxonomy(forceRefresh = forceRefresh)
                val savedOrder = categorySortPreferences.sectionOrder.first()
                val savedVisible = categorySortPreferences.visibleSectionKeys.first()
                val visibilityConfigured = categorySortPreferences.visibilityConfigured.first()
                val sectionRefs = taxonomy.buildHomeSectionRefs(
                    savedOrder = savedOrder,
                    savedVisible = savedVisible,
                    visibilityConfigured = visibilityConfigured
                )
                val sectionOrder = sectionRefs.map { it.sectionKey }
                val sectionConfigError = if (sectionRefs.isEmpty()) {
                    "请在设置中开启至少一个首页分类"
                } else {
                    null
                }

                if (forceRefresh) {
                    _uiState.update {
                        it.copy(
                            sections = emptyList(),
                            recommendedItems = emptyList(),
                            recommendedTotal = 0
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConfigured = true,
                        isLoadingSections = sectionRefs.isNotEmpty(),
                        error = sectionConfigError
                    )
                }

                launch {
                    try {
                        val recommendedResult = macCmsRepository.fetchRecommendedVods(
                            level = 9,
                            homePreviewLimit = HOME_RECOMMENDED_HOME_ITEMS,
                            fullScan = true
                        )
                        if (generation != homeLoadGeneration) return@launch
                        _uiState.update { state ->
                            state.copy(
                                recommendedItems = recommendedResult.items,
                                recommendedTotal = recommendedResult.total,
                                error = when {
                                    state.sections.isNotEmpty() ||
                                        recommendedResult.items.isNotEmpty() -> null
                                    state.error != null -> state.error
                                    else -> sectionConfigError
                                }
                            )
                        }
                    } catch (_: Exception) {
                        // 推荐区失败不阻塞首页分类
                    }
                }

                if (sectionRefs.isEmpty()) {
                    return@launch
                }

                val enrichGen = ++sectionEnrichGeneration
                val completed = AtomicInteger(0)
                val totalSections = sectionRefs.size

                sectionRefs.forEach { ref ->
                    launch {
                        try {
                            val result = when (ref) {
                                is MacCmsHomeSectionRef.Main ->
                                    macCmsRepository.fetchLatestForNavCategory(
                                        category = ref.category,
                                        taxonomy = taxonomy,
                                        limit = HOME_MACCMS_MAX_ITEMS,
                                        enrichDetail = false
                                    )
                                is MacCmsHomeSectionRef.Secondary ->
                                    macCmsRepository.fetchLatestForType(
                                        typeId = ref.typeId,
                                        limit = HOME_MACCMS_MAX_ITEMS,
                                        enrichDetail = false
                                    )
                            }
                            if (generation != homeLoadGeneration) return@launch

                            val section = buildSection(ref, result)
                            if (section != null) {
                                upsertSection(section, sectionOrder)
                                enrichSectionStaged(
                                    sectionKey = section.sectionKey,
                                    items = section.items,
                                    enrichGeneration = enrichGen,
                                    homeGeneration = generation
                                )
                            }
                        } catch (_: Exception) {
                            // 单个栏目失败不影响其它栏目
                        } finally {
                            if (completed.incrementAndGet() == totalSections &&
                                generation == homeLoadGeneration
                            ) {
                                _uiState.update { state ->
                                    val visibleSections = state.sections.filter { it.items.isNotEmpty() }
                                    state.copy(
                                        isLoadingSections = false,
                                        error = when {
                                            visibleSections.isNotEmpty() ||
                                                state.recommendedItems.isNotEmpty() -> null
                                            state.error != null -> state.error
                                            else -> sectionConfigError
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (generation != homeLoadGeneration) return@launch
                _uiState.update {
                    MacCmsHomeUiState(
                        isLoading = false,
                        isConfigured = true,
                        error = MacCmsErrorMessages.fromThrowable(e, "加载首页失败")
                    )
                }
            }
        }
    }

    private fun observeHomeConfigChanges() {
        viewModelScope.launch {
            categorySortPreferences.homeConfigChanges
                .drop(1)
                .collect { loadHome(forceRefresh = true) }
        }
    }

    private fun observeServerUrlChanges() {
        viewModelScope.launch {
            macCmsRepository.serverUrl
                .drop(1)
                .collect { loadHome(forceRefresh = true) }
        }
    }

    private fun buildSection(
        ref: MacCmsHomeSectionRef,
        result: com.lemon.yingshi.tv.data.repository.MacCmsCategoryFetchResult
    ): MacCmsHomeSection? {
        val filterTypeId = when (ref) {
            is MacCmsHomeSectionRef.Main -> 0
            is MacCmsHomeSectionRef.Secondary -> ref.typeId
        }
        if (result.items.isEmpty()) {
            return if (result.error != null) {
                MacCmsHomeSection(
                    sectionKey = ref.sectionKey,
                    typeName = ref.displayName,
                    typeId = filterTypeId,
                    navTypeId = ref.navTypeId,
                    items = emptyList(),
                    total = 0,
                    error = result.error
                )
            } else {
                null
            }
        }
        return MacCmsHomeSection(
            sectionKey = ref.sectionKey,
            typeName = ref.displayName,
            typeId = filterTypeId,
            navTypeId = ref.navTypeId,
            items = result.items,
            total = result.total
        )
    }

    private fun upsertSection(section: MacCmsHomeSection, sectionOrder: List<String>) {
        _uiState.update { state ->
            val updated = state.sections
                .filter { it.sectionKey != section.sectionKey } + section
            val ordered = updated.sortedBy { key ->
                sectionOrder.indexOf(key.sectionKey).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            val visibleSections = ordered.filter { it.items.isNotEmpty() }
            state.copy(
                sections = visibleSections,
                error = when {
                    visibleSections.isNotEmpty() || state.recommendedItems.isNotEmpty() -> null
                    section.error != null -> section.error
                    else -> state.error
                }
            )
        }
    }

    /** 先补全首屏可见卡片封面，再后台补全其余卡片 */
    private fun enrichSectionStaged(
        sectionKey: String,
        items: List<MacCmsVodItem>,
        enrichGeneration: Int,
        homeGeneration: Int
    ) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val visibleCount = HOME_VISIBLE_ENRICH_ITEMS.coerceAtMost(items.size)
            try {
                val enrichedVisible = macCmsRepository.enrichVodItemsForDisplay(items.take(visibleCount))
                mergeSectionEnriched(sectionKey, enrichedVisible, enrichGeneration, homeGeneration)

                if (items.size > visibleCount) {
                    val enrichedRest = macCmsRepository.enrichVodItemsForDisplay(items.drop(visibleCount))
                    mergeSectionEnriched(
                        sectionKey,
                        enrichedVisible + enrichedRest,
                        enrichGeneration,
                        homeGeneration
                    )
                }
            } catch (_: Exception) {
                // 详情补全失败不影响列表展示
            }
        }
    }

    private fun mergeSectionEnriched(
        sectionKey: String,
        enriched: List<MacCmsVodItem>,
        enrichGeneration: Int,
        homeGeneration: Int
    ) {
        if (enrichGeneration != sectionEnrichGeneration || homeGeneration != homeLoadGeneration) return
        val enrichedMap = enriched.associateBy { it.vodId }
        _uiState.update { state ->
            val section = state.sections.find { it.sectionKey == sectionKey } ?: return@update state
            val mergedItems = section.items.map { enrichedMap[it.vodId] ?: it }
            state.copy(
                sections = state.sections.map {
                    if (it.sectionKey == sectionKey) it.copy(items = mergedItems) else it
                }
            )
        }
    }
}
