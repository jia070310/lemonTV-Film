package com.lemon.yingshi.mobile.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.UserProfilePreferences
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.domain.service.FavoriteService
import com.lemon.yingshi.tv.domain.service.OfflineDownloadService
import com.lemon.yingshi.tv.domain.service.OfflineDownloadSummary
import com.lemon.yingshi.tv.domain.service.PlaybackStatsService
import com.lemon.yingshi.tv.domain.service.UserAvatarStore
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val displayName: String = "",
    val avatarRevision: Long = 0L,
    val hasCustomAvatar: Boolean = false,
    val historyCount: Int = 0,
    val favoriteCount: Int = 0,
    val offlineSummary: OfflineDownloadSummary = OfflineDownloadSummary(0, 0),
    val playbackTimeMs: Long = 0L,
    val siteName: String = "",
    val serverUrl: String = ""
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userProfilePreferences: UserProfilePreferences,
    private val userAvatarStore: UserAvatarStore,
    watchHistoryService: WatchHistoryService,
    favoriteService: FavoriteService,
    offlineDownloadService: OfflineDownloadService,
    playbackStatsService: PlaybackStatsService,
    macCmsPreferences: MacCmsPreferences
) : ViewModel() {

    private val profileMeta = combine(
        userProfilePreferences.displayName,
        userProfilePreferences.avatarRevision
    ) { displayName, avatarRevision ->
        displayName to avatarRevision
    }

    private val usageStats = combine(
        watchHistoryService.getRecentWatchHistory(100).map { it.size },
        favoriteService.getAllFavorites().map { it.size },
        offlineDownloadService.observeDownloadSummary(),
        playbackStatsService.totalPlaybackTimeMs
    ) { historyCount, favoriteCount, offlineSummary, playbackMs ->
        UsageStats(historyCount, favoriteCount, offlineSummary, playbackMs)
    }

    private val coreState = combine(profileMeta, usageStats) { (displayName, avatarRevision), stats ->
        UserProfileUiState(
            displayName = displayName,
            avatarRevision = avatarRevision,
            hasCustomAvatar = userAvatarStore.hasCustomAvatar(),
            historyCount = stats.historyCount,
            favoriteCount = stats.favoriteCount,
            offlineSummary = stats.offlineSummary,
            playbackTimeMs = stats.playbackMs
        )
    }

    val uiState = combine(
        coreState,
        macCmsPreferences.siteName,
        macCmsPreferences.serverUrl
    ) { core, siteName, serverUrl ->
        core.copy(siteName = siteName, serverUrl = serverUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileUiState())

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            userProfilePreferences.setDisplayName(name)
        }
    }

    fun setAvatarFromUri(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = userAvatarStore.saveFromUri(uri)
            if (success) {
                userProfilePreferences.bumpAvatarRevision()
            }
            onResult(success)
        }
    }

    fun clearAvatar(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!userAvatarStore.hasCustomAvatar()) {
                onResult(true)
                return@launch
            }
            userAvatarStore.clear()
            userProfilePreferences.bumpAvatarRevision()
            onResult(true)
        }
    }

    private data class UsageStats(
        val historyCount: Int,
        val favoriteCount: Int,
        val offlineSummary: OfflineDownloadSummary,
        val playbackMs: Long
    )
}
