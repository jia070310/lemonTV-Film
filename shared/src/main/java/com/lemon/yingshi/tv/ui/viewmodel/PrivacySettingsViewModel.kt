package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.PrivacyPreferences
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.PrivacyHideCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val privacyPreferences: PrivacyPreferences,
    private val macCmsRepository: MacCmsRepository
) : ViewModel() {

    val filterKeywordsRaw: StateFlow<String> = privacyPreferences.filterKeywordsRaw
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val hiddenTypeIds: StateFlow<Set<Int>> = privacyPreferences.hiddenTypeIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    suspend fun saveFilterKeywordsAwait(raw: String) {
        privacyPreferences.saveFilterKeywords(raw)
    }

    fun saveFilterKeywords(raw: String) {
        viewModelScope.launch {
            privacyPreferences.saveFilterKeywords(raw)
        }
    }

    suspend fun saveHiddenTypeIdsAwait(typeIds: Set<Int>) {
        privacyPreferences.saveHiddenTypeIds(typeIds)
    }

    fun saveHiddenTypeIds(typeIds: Set<Int>) {
        viewModelScope.launch {
            privacyPreferences.saveHiddenTypeIds(typeIds)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            privacyPreferences.clearAll()
        }
    }

    suspend fun loadHideCandidates(): Result<List<PrivacyHideItem>> = runCatching {
        val taxonomy = macCmsRepository.fetchTaxonomy()
        val savedHidden = privacyPreferences.hiddenTypeIds.first()
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
        /** 一级被隐藏时，其二级一并计入隐藏集合（用于展示与持久化） */
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

        /**
         * 切换显示/隐藏。
         * - 关闭一级：一级 + 全部二级进入隐藏
         * - 打开一级：一级 + 全部二级取消隐藏
         * - 打开二级：同时取消其一级隐藏，否则仍无法显示
         */
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
