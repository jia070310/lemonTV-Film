package com.lemon.yingshi.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class HomeSettingsViewModel @Inject constructor(
    private val categorySortPreferences: MacCmsCategorySortPreferences,
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    private val _isLoadingSort = MutableStateFlow(false)
    val isLoadingSort: StateFlow<Boolean> = _isLoadingSort.asStateFlow()

    private val _sortError = MutableStateFlow<String?>(null)
    val sortError: StateFlow<String?> = _sortError.asStateFlow()

    private val _sortItems = MutableStateFlow<List<SettingsDialogs.CategorySortItem>>(emptyList())
    val sortItems: StateFlow<List<SettingsDialogs.CategorySortItem>> = _sortItems.asStateFlow()

    fun loadCategorySort() {
        viewModelScope.launch {
            loadCategorySortInternal()
        }
    }

    suspend fun loadCategorySortAndGet(): Pair<List<SettingsDialogs.CategorySortItem>, String?> {
        return loadCategorySortInternal()
    }

    private suspend fun loadCategorySortInternal(): Pair<List<SettingsDialogs.CategorySortItem>, String?> {
        _isLoadingSort.value = true
        _sortError.value = null
        return runCatching {
            val taxonomy = macCmsRepository.fetchTaxonomy()
            val savedOrder = categorySortPreferences.sectionOrder.first()
            val savedVisible = categorySortPreferences.visibleSectionKeys.first()
            val configured = categorySortPreferences.visibilityConfigured.first()
            val order = taxonomy.resolveSectionOrder(savedOrder)
            val visible = taxonomy.resolveVisibleSectionKeys(savedVisible, configured)
            order.mapNotNull { key ->
                taxonomy.parseSectionKey(key)?.let { ref ->
                    SettingsDialogs.CategorySortItem(
                        key = key,
                        name = ref.displayName,
                        visible = key in visible
                    )
                }
            }
        }.fold(
            onSuccess = { rows ->
                _sortItems.value = rows
                _sortError.value = null
                rows to null
            },
            onFailure = { error ->
                val message = error.message ?: "加载分类失败"
                _sortError.value = message
                _sortItems.value = emptyList()
                emptyList<SettingsDialogs.CategorySortItem>() to message
            }
        ).also {
            _isLoadingSort.value = false
        }
    }

    fun saveCategorySort(items: List<SettingsDialogs.CategorySortItem>) {
        viewModelScope.launch {
            categorySortPreferences.saveCategoryConfig(
                sectionOrder = items.map { it.key },
                visibleKeys = items.filter { it.visible }.map { it.key }.toSet()
            )
        }
    }

    fun clearHomeCategoryCache() {
        viewModelScope.launch {
            categorySortPreferences.clearHomeCategoryCache()
        }
    }
}
