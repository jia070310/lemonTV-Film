package com.lemon.yingshi.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MacCmsHomeNavCategory
import com.lemon.yingshi.tv.domain.model.MacCmsHomeSectionRef
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MacCmsHomeSection(
    val sectionKey: String,
    val typeName: String,
    val typeId: Int,
    /** 首页四大类栏目跳转筛选页时使用；二级子类栏目为 null */
    val navCategory: MacCmsHomeNavCategory? = null,
    val items: List<MacCmsVodItem>,
    val total: Int,
    val error: String? = null
)

data class MacCmsHomeUiState(
    val isLoading: Boolean = false,
    val isConfigured: Boolean = false,
    val sections: List<MacCmsHomeSection> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MacCmsHomeViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository,
    private val categorySortPreferences: MacCmsCategorySortPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacCmsHomeUiState(isLoading = true))
    val uiState: StateFlow<MacCmsHomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
        observeHomeConfigChanges()
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                val savedOrder = categorySortPreferences.sectionOrder.first()
                val savedVisible = categorySortPreferences.visibleSectionKeys.first()
                val visibilityConfigured = categorySortPreferences.visibilityConfigured.first()
                val sectionRefs = MacCmsTaxonomy.buildHomeSectionRefs(
                    savedOrder = savedOrder,
                    savedVisible = savedVisible,
                    visibilityConfigured = visibilityConfigured
                )

                if (sectionRefs.isEmpty()) {
                    _uiState.update {
                        MacCmsHomeUiState(
                            isLoading = false,
                            isConfigured = true,
                            error = "请在设置中开启至少一个首页分类"
                        )
                    }
                    return@launch
                }

                val sections = loadSections(sectionRefs)
                val visibleSections = sections.filter { it.items.isNotEmpty() }
                val loadError = sections.firstOrNull { it.error != null }?.error
                _uiState.update {
                    MacCmsHomeUiState(
                        isLoading = false,
                        isConfigured = true,
                        sections = visibleSections,
                        error = if (visibleSections.isEmpty()) loadError else null
                    )
                }
            } catch (e: Exception) {
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
                .collect { loadHome() }
        }
    }

    private suspend fun loadSections(
        sectionRefs: List<MacCmsHomeSectionRef>
    ): List<MacCmsHomeSection> = coroutineScope {
        sectionRefs.map { ref ->
            async {
                val result = when (ref) {
                    is MacCmsHomeSectionRef.Main ->
                        macCmsRepository.fetchLatestForNavCategory(ref.category)
                    is MacCmsHomeSectionRef.Secondary ->
                        macCmsRepository.fetchLatestForType(ref.typeId)
                }
                val filterTypeId = when (ref) {
                    is MacCmsHomeSectionRef.Main -> 0
                    is MacCmsHomeSectionRef.Secondary -> ref.typeId
                }
                val navCategory = (ref as? MacCmsHomeSectionRef.Main)?.category
                if (result.items.isEmpty()) {
                    return@async if (result.error != null) {
                        MacCmsHomeSection(
                            sectionKey = ref.sectionKey,
                            typeName = ref.displayName,
                            typeId = filterTypeId,
                            navCategory = navCategory,
                            items = emptyList(),
                            total = 0,
                            error = result.error
                        )
                    } else {
                        null
                    }
                }
                MacCmsHomeSection(
                    sectionKey = ref.sectionKey,
                    typeName = ref.displayName,
                    typeId = filterTypeId,
                    navCategory = navCategory,
                    items = result.items,
                    total = result.total
                )
            }
        }.awaitAll().filterNotNull()
    }
}
