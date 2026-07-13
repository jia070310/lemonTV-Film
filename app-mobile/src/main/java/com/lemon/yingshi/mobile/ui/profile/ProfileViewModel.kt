package com.lemon.yingshi.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.service.FavoriteService
import com.lemon.yingshi.tv.domain.service.OfflineDownloadService
import com.lemon.yingshi.tv.domain.service.OfflineDownloadSummary
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel @Inject constructor(
    watchHistoryService: WatchHistoryService,
    favoriteService: FavoriteService,
    offlineDownloadService: OfflineDownloadService
) : ViewModel() {

    val historyCount = watchHistoryService.getRecentWatchHistory(100)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteCount = favoriteService.getAllFavorites()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val offlineSummary = offlineDownloadService.observeDownloadSummary()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            OfflineDownloadSummary(0, 0)
        )
}
