package com.lemon.yingshi.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.cache.HomeFeedDiskCache
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MacCmsHomeSectionRef
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

data class MacCmsHomeSection(
    val sectionKey: String,
    val typeName: String,
    val typeId: Int,
    /** 首页顶级分类栏目跳转筛选页时使用；二级子类栏目为 null */
    val navTypeId: Int? = null,
    val items: List<MacCmsVodItem>,
    val total: Int,
    val error: String? = null,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = items.isNotEmpty()
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
    private val categorySortPreferences: MacCmsCategorySortPreferences,
    private val homeFeedDiskCache: HomeFeedDiskCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacCmsHomeUiState(isLoading = true))
    val uiState: StateFlow<MacCmsHomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var homeLoadGeneration = 0
    private val sectionEnrichTokens = ConcurrentHashMap<String, Int>()
    private var cachedTaxonomy: MacCmsTaxonomy? = null
    private var cachedSectionRefs: List<MacCmsHomeSectionRef> = emptyList()
    private var cachedSectionOrder: List<String> = emptyList()
    private val loadedSectionKeys = ConcurrentHashMap.newKeySet<String>()
    private val loadingSectionKeys = ConcurrentHashMap.newKeySet<String>()
    private val sectionLoadSemaphore = Semaphore(MAX_CONCURRENT_SECTION_LOADS)

    init {
        loadHome()
        observeHomeConfigChanges()
        observeServerUrlChanges()
    }

    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val generation = ++homeLoadGeneration
            if (forceRefresh) _isRefreshing.value = true
            loadedSectionKeys.clear()
            loadingSectionKeys.clear()
            sectionEnrichTokens.clear()

            val hasCachedData = _uiState.value.sections.any { it.isLoaded } ||
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

            val serverUrl = macCmsRepository.getServerUrl()
            if (serverUrl.isBlank()) {
                _uiState.update {
                    MacCmsHomeUiState(
                        isLoading = false,
                        isConfigured = false,
                        error = "请先在设置中配置 MacCMS 服务器"
                    )
                }
                finishRefresh()
                return@launch
            }

            if (!forceRefresh) {
                homeFeedDiskCache.read(serverUrl)?.let { cached ->
                    if (generation != homeLoadGeneration) return@launch
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConfigured = true,
                            recommendedItems = homeFeedDiskCache.toRecommendedItems(cached.recommended),
                            recommendedTotal = cached.recommended.size,
                            sections = cached.sections.map { section ->
                                homeFeedDiskCache.toHomeSection(section)
                            },
                            error = null
                        )
                    }
                    schedulePosterEnrich(generation)
                }
            }

            try {
                val taxonomy = withContext(Dispatchers.IO) {
                    macCmsRepository.fetchTaxonomy(forceRefresh = forceRefresh)
                }
                if (generation != homeLoadGeneration) return@launch
                cachedTaxonomy = taxonomy

                val savedOrder = categorySortPreferences.sectionOrder.first()
                val savedVisible = categorySortPreferences.visibleSectionKeys.first()
                val visibilityConfigured = categorySortPreferences.visibilityConfigured.first()
                val sectionRefs = taxonomy.buildHomeSectionRefs(
                    savedOrder = savedOrder,
                    savedVisible = savedVisible,
                    visibilityConfigured = visibilityConfigured
                )
                cachedSectionRefs = sectionRefs
                cachedSectionOrder = sectionRefs.map { it.sectionKey }

                val sectionConfigError = if (sectionRefs.isEmpty()) {
                    "请在设置中开启至少一个首页分类"
                } else {
                    null
                }

                if (forceRefresh) {
                    _uiState.update {
                        it.copy(
                            recommendedItems = emptyList(),
                            recommendedTotal = 0
                        )
                    }
                }

                val placeholders = sectionRefs.map { ref -> placeholderSection(ref) }
                _uiState.update { state ->
                    val mergedSections = mergePlaceholders(state.sections, placeholders)
                    state.copy(
                        isLoading = false,
                        isConfigured = true,
                        isLoadingSections = sectionRefs.isNotEmpty(),
                        sections = mergedSections,
                        error = sectionConfigError
                    )
                }
                schedulePosterEnrich(generation)

                launch {
                    try {
                        val currentRecommended = _uiState.value.recommendedItems
                        if (currentRecommended.isNotEmpty() && sectionNeedsPosterEnrich(currentRecommended)) {
                            val enriched = withContext(Dispatchers.IO) {
                                macCmsRepository.enrichVodItemsForDisplay(currentRecommended)
                            }
                            if (generation != homeLoadGeneration) return@launch
                            _uiState.update { it.copy(recommendedItems = enriched) }
                        }
                    } catch (_: Exception) {
                        // 推荐区封面补全失败不阻塞
                    }
                }

                launch {
                    try {
                        val preview = withContext(Dispatchers.IO) {
                            macCmsRepository.fetchRecommendedVods(
                                level = RECOMMENDED_LEVEL,
                                homePreviewLimit = HOME_RECOMMENDED_HOME_ITEMS,
                                fullScan = false
                            )
                        }
                        if (generation != homeLoadGeneration) return@launch
                        if (preview.items.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    recommendedItems = preview.items,
                                    recommendedTotal = preview.total,
                                    error = clearErrorIfHasContent(state, sectionConfigError)
                                )
                            }
                            if (sectionNeedsPosterEnrich(preview.items)) {
                                val enriched = withContext(Dispatchers.IO) {
                                    macCmsRepository.enrichVodItemsForDisplay(preview.items)
                                }
                                if (generation != homeLoadGeneration) return@launch
                                _uiState.update { state ->
                                    state.copy(recommendedItems = enriched.take(HOME_RECOMMENDED_HOME_ITEMS))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // 推荐区失败不阻塞首页分类
                    }
                }

                if (sectionRefs.isEmpty()) {
                    finishRefresh()
                    return@launch
                }

                val initialKeys = sectionRefs
                    .take(HOME_INITIAL_LOAD_SECTIONS)
                    .map { it.sectionKey }
                startSectionLoads(initialKeys, generation, sectionConfigError)
            } catch (e: Exception) {
                if (generation != homeLoadGeneration) return@launch
                _uiState.update {
                    MacCmsHomeUiState(
                        isLoading = false,
                        isConfigured = true,
                        error = MacCmsErrorMessages.fromThrowable(e, "加载首页失败")
                    )
                }
                finishRefresh()
            }
        }
    }

    /** 滑动停顿时由 UI 调用：只加载屏幕内及邻近栏目 */
    fun ensureSectionsLoaded(sectionKeys: List<String>) {
        if (sectionKeys.isEmpty()) return
        startSectionLoads(sectionKeys, homeLoadGeneration, _uiState.value.error)
    }

    private fun startSectionLoads(
        sectionKeys: List<String>,
        generation: Int,
        sectionConfigError: String?
    ) {
        val pending = sectionKeys.filter { key ->
            key !in loadedSectionKeys && key !in loadingSectionKeys
        }
        if (pending.isEmpty()) return

        pending.forEach { key ->
            viewModelScope.launch {
                loadSection(key, generation, sectionConfigError)
            }
        }
    }

    private suspend fun loadSection(
        sectionKey: String,
        generation: Int,
        sectionConfigError: String?
    ) {
        if (!loadingSectionKeys.add(sectionKey)) return
        markSectionLoading(sectionKey, loading = true)

        val taxonomy = cachedTaxonomy
        val ref = cachedSectionRefs.find { it.sectionKey == sectionKey }
        if (taxonomy == null || ref == null) {
            loadingSectionKeys.remove(sectionKey)
            markSectionLoading(sectionKey, loading = false)
            return
        }

        try {
            sectionLoadSemaphore.acquire()
            val result = withContext(Dispatchers.IO) {
                when (ref) {
                    is MacCmsHomeSectionRef.Main ->
                        macCmsRepository.fetchLatestForNavCategory(
                            category = ref.category,
                            taxonomy = taxonomy,
                            limit = HOME_MACCMS_MAX_ITEMS,
                            enrichDetail = true
                        )
                    is MacCmsHomeSectionRef.Secondary ->
                        macCmsRepository.fetchLatestForType(
                            typeId = ref.typeId,
                            limit = HOME_MACCMS_MAX_ITEMS,
                            enrichDetail = true
                        )
                }
            }
            if (generation != homeLoadGeneration) return

            val section = buildSection(ref, result)
            if (section != null) {
                loadedSectionKeys.add(sectionKey)
                upsertSection(section.copy(isLoaded = true, isLoading = false), cachedSectionOrder)
                persistHomeCache()
                if (sectionNeedsPosterEnrich(section.items)) {
                    enrichSectionStaged(
                        sectionKey = section.sectionKey,
                        items = section.items,
                        homeGeneration = generation
                    )
                }
            } else {
                removeEmptyPlaceholder(sectionKey)
            }
        } catch (_: Exception) {
            markSectionLoading(sectionKey, loading = false)
        } finally {
            loadingSectionKeys.remove(sectionKey)
            sectionLoadSemaphore.release()
            if (generation == homeLoadGeneration) {
                _uiState.update { state ->
                    val visibleSections = state.sections.filter { it.isLoaded && it.items.isNotEmpty() }
                    state.copy(
                        isLoadingSections = loadingSectionKeys.isNotEmpty(),
                        error = clearErrorIfHasContent(state, sectionConfigError)
                            ?: if (visibleSections.isEmpty()) sectionConfigError else null
                    )
                }
                if (loadingSectionKeys.isEmpty()) {
                    finishRefresh()
                }
            }
        }
    }

    private fun markSectionLoading(sectionKey: String, loading: Boolean) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.sectionKey != sectionKey) section
                    else section.copy(isLoading = loading)
                }
            )
        }
    }

    private fun removeEmptyPlaceholder(sectionKey: String) {
        loadedSectionKeys.add(sectionKey)
        _uiState.update { state ->
            state.copy(sections = state.sections.filter { it.sectionKey != sectionKey })
        }
    }

    private fun mergePlaceholders(
        existing: List<MacCmsHomeSection>,
        placeholders: List<MacCmsHomeSection>
    ): List<MacCmsHomeSection> {
        if (existing.isEmpty()) return placeholders
        val existingByKey = existing.associateBy { it.sectionKey }
        return placeholders.map { placeholder ->
            val cached = existingByKey[placeholder.sectionKey]
            if (cached != null && cached.isLoaded && cached.items.isNotEmpty()) {
                cached.copy(isLoading = false)
            } else {
                placeholder
            }
        }
    }

    private fun placeholderSection(ref: MacCmsHomeSectionRef): MacCmsHomeSection {
        val filterTypeId = when (ref) {
            is MacCmsHomeSectionRef.Main -> 0
            is MacCmsHomeSectionRef.Secondary -> ref.typeId
        }
        return MacCmsHomeSection(
            sectionKey = ref.sectionKey,
            typeName = ref.displayName,
            typeId = filterTypeId,
            navTypeId = ref.navTypeId,
            items = emptyList(),
            total = 0,
            isLoading = false,
            isLoaded = false
        )
    }

    private fun clearErrorIfHasContent(
        state: MacCmsHomeUiState,
        sectionConfigError: String?
    ): String? =
        when {
            state.sections.any { it.isLoaded && it.items.isNotEmpty() } ||
                state.recommendedItems.isNotEmpty() -> null
            state.error != null -> state.error
            else -> sectionConfigError
        }

    private fun persistHomeCache() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            val serverUrl = macCmsRepository.getServerUrl()
            if (serverUrl.isBlank()) return@launch
            homeFeedDiskCache.write(
                serverUrl = serverUrl,
                recommended = state.recommendedItems,
                sections = state.sections
            )
        }
    }

    private fun finishRefresh() {
        _isRefreshing.value = false
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
                    error = result.error,
                    isLoaded = true,
                    isLoading = false
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
            total = result.total,
            isLoaded = true,
            isLoading = false
        )
    }

    private fun upsertSection(section: MacCmsHomeSection, sectionOrder: List<String>) {
        _uiState.update { state ->
            val existing = state.sections.find { it.sectionKey == section.sectionKey }
            val mergedSection = if (existing != null) {
                section.copy(items = mergeItemsPreservingPosters(existing.items, section.items))
            } else {
                section
            }
            val updated = state.sections
                .filter { it.sectionKey != mergedSection.sectionKey } + mergedSection
            val ordered = updated.sortedBy { key ->
                sectionOrder.indexOf(key.sectionKey).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            val visibleSections = ordered.filter { it.isLoaded && it.items.isNotEmpty() || !it.isLoaded }
            state.copy(
                sections = visibleSections,
                error = when {
                    visibleSections.any { it.isLoaded && it.items.isNotEmpty() } ||
                        state.recommendedItems.isNotEmpty() -> null
                    section.error != null -> section.error
                    else -> state.error
                }
            )
        }
    }

    private fun schedulePosterEnrich(generation: Int) {
        _uiState.value.sections.forEach { section ->
            if (section.items.isNotEmpty() && sectionNeedsPosterEnrich(section.items)) {
                enrichSectionStaged(section.sectionKey, section.items, generation)
            }
        }
    }

    private fun sectionNeedsPosterEnrich(items: List<MacCmsVodItem>): Boolean =
        items.any { item ->
            item.vodPic.isNullOrBlank() && item.vodPicThumb.isNullOrBlank()
        }

    /** 先补全可见卡片封面，再后台补全其余（每栏目独立 token，避免并行 enrich 互相覆盖） */
    private fun enrichSectionStaged(
        sectionKey: String,
        items: List<MacCmsVodItem>,
        homeGeneration: Int
    ) {
        val token = sectionEnrichTokens.merge(sectionKey, 1) { _, old -> old + 1 }!!
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            try {
                val enrichedAll = withContext(Dispatchers.IO) {
                    macCmsRepository.enrichVodItemsForDisplay(items)
                }
                mergeSectionEnriched(sectionKey, enrichedAll, token, homeGeneration)
                persistHomeCache()
            } catch (_: Exception) {
                // 详情补全失败不影响列表展示
            }
        }
    }

    private fun mergeItemsPreservingPosters(
        oldItems: List<MacCmsVodItem>,
        newItems: List<MacCmsVodItem>
    ): List<MacCmsVodItem> {
        if (oldItems.isEmpty()) return newItems
        val oldMap = oldItems.associateBy { it.vodId }
        return newItems.map { item ->
            val prev = oldMap[item.vodId] ?: return@map item
            item.copy(
                vodPic = item.vodPic?.takeIf { it.isNotBlank() } ?: prev.vodPic,
                vodPicThumb = item.vodPicThumb?.takeIf { it.isNotBlank() } ?: prev.vodPicThumb,
                vodPicSlide = item.vodPicSlide?.takeIf { it.isNotBlank() } ?: prev.vodPicSlide
            )
        }
    }

    private fun mergeSectionEnriched(
        sectionKey: String,
        enriched: List<MacCmsVodItem>,
        token: Int,
        homeGeneration: Int
    ) {
        if (sectionEnrichTokens[sectionKey] != token || homeGeneration != homeLoadGeneration) return
        val enrichedMap = enriched.associateBy { it.vodId }
        _uiState.update { state ->
            val section = state.sections.find { it.sectionKey == sectionKey } ?: return@update state
            val mergedItems = mergeItemsPreservingPosters(
                oldItems = section.items,
                newItems = section.items.map { enrichedMap[it.vodId] ?: it }
            )
            state.copy(
                sections = state.sections.map {
                    if (it.sectionKey == sectionKey) it.copy(items = mergedItems) else it
                }
            )
        }
    }

    fun cacheVodForDetail(vod: MacCmsVodItem) {
        macCmsRepository.putCachedVodSnapshot(vod)
    }

    companion object {
        private const val RECOMMENDED_LEVEL = 9
        private const val MAX_CONCURRENT_SECTION_LOADS = 2
    }
}
