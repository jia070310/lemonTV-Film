package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.PrivacyPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.PrivacyHideCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val privacyPreferences: PrivacyPreferences,
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    val serverList: StateFlow<List<MacCmsServerEntry>> = macCmsRepository.serverList
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeServerUrl: StateFlow<String> = privacyPreferences.currentServerUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            privacyPreferences.prepare()
        }
    }

    suspend fun keywordsFor(url: String): String =
        privacyPreferences.getProfile(url).keywords

    fun saveFilterKeywords(url: String, raw: String) {
        viewModelScope.launch {
            privacyPreferences.saveFilterKeywords(url, raw)
        }
    }

    suspend fun saveFilterKeywordsAwait(url: String, raw: String) {
        privacyPreferences.saveFilterKeywords(url, raw)
    }

    fun saveHiddenTypeIds(url: String, typeIds: Set<Int>) {
        viewModelScope.launch {
            privacyPreferences.saveHiddenTypeIds(url, typeIds)
        }
    }

    suspend fun saveHiddenTypeIdsAwait(url: String, typeIds: Set<Int>) {
        privacyPreferences.saveHiddenTypeIds(url, typeIds)
    }

    fun clearAll(url: String) {
        viewModelScope.launch {
            privacyPreferences.clearAll(url)
        }
    }

    suspend fun loadHideCandidates(url: String): Result<List<PrivacyHideItem>> = runCatching {
        if (url.isBlank()) error("请先选择要设置的服务器")
        val taxonomy = macCmsRepository.fetchTaxonomy(forceRefresh = false, baseUrlOverride = url)
        val savedHidden = privacyPreferences.getProfile(url).hiddenTypeIds
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
        val candidates = taxonomy.privacyHideCandidates()
        val effectiveHidden = expandHiddenWithChildren(candidates, savedHidden)
        candidates.map { candidate ->
            PrivacyHideItem(
                candidate = candidate,
                hidden = candidate.typeId in effectiveHidden
            )
        }
    }

    data class PrivacyHideItem(
        val candidate: PrivacyHideCandidate,
        val hidden: Boolean
    ) {
        val typeId: Int get() = candidate.typeId
        val isSecondary: Boolean get() = candidate.isSecondary
        val parentTypeId: Int? get() = candidate.parentTypeId
        val childTypeIds: List<Int> get() = candidate.childTypeIds
        val displayName: String
            get() = if (candidate.isSecondary && !candidate.parentLabel.isNullOrBlank()) {
                "${candidate.parentLabel} · ${candidate.label}"
            } else {
                candidate.label
            }
    }

    companion object {
        fun expandHiddenWithChildren(
            candidates: List<PrivacyHideCandidate>,
            hidden: Set<Int>
        ): Set<Int> {
            if (hidden.isEmpty()) return emptySet()
            val next = hidden.toMutableSet()
            candidates.forEach { candidate ->
                if (!candidate.isSecondary && candidate.typeId in next) {
                    next.addAll(candidate.childTypeIds)
                }
            }
            return next
        }

        fun expandHiddenWithChildrenFromItems(
            items: List<PrivacyHideItem>,
            hidden: Set<Int>
        ): Set<Int> = expandHiddenWithChildren(items.map { it.candidate }, hidden)

        fun toggleHidden(
            items: List<PrivacyHideItem>,
            typeId: Int,
            hide: Boolean,
            currentHidden: Set<Int>
        ): Set<Int> {
            val item = items.find { it.typeId == typeId } ?: return currentHidden
            val next = currentHidden.toMutableSet()
            if (hide) {
                next.add(typeId)
                if (!item.isSecondary) {
                    next.addAll(item.childTypeIds)
                }
            } else {
                next.remove(typeId)
                if (!item.isSecondary) {
                    next.removeAll(item.childTypeIds.toSet())
                } else {
                    item.parentTypeId?.let { next.remove(it) }
                }
            }
            return expandHiddenWithChildrenFromItems(items, next)
        }
    }
}
