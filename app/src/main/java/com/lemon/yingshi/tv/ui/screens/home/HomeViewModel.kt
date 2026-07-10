package com.lemon.yingshi.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.service.NotificationService
import com.lemon.yingshi.tv.domain.service.PlaybackStatsService
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val watchHistoryService: WatchHistoryService,
    private val notificationService: NotificationService,
    val playbackStatsService: PlaybackStatsService
) : ViewModel() {

    val recentWatchHistory = watchHistoryService.getRecentWatchHistory(30)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentNotification = notificationService.currentNotification
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    init {
        refreshNotifications()
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            notificationService.refreshNotifications()
        }
    }

    suspend fun getPlaybackInfo(mediaId: String, episodeId: String?) =
        watchHistoryService.getPlaybackInfo(mediaId, episodeId)
}
