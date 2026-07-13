package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.lemon.yingshi.tv.domain.service.MediaStorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class CacheSizeSnapshot(
    val playbackBytes: Long = 0L,
    val coverBytes: Long = 0L,
    val homeFeedBytes: Long = 0L,
    val coverMaxBytes: Long = 0L,
    val homeFeedMaxBytes: Long = 0L
) {
    val totalBytes: Long = playbackBytes + coverBytes + homeFeedBytes
}

@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    private val mediaStorageHelper: MediaStorageHelper
) : ViewModel() {

    fun readCacheSizes(): CacheSizeSnapshot = CacheSizeSnapshot(
        playbackBytes = mediaStorageHelper.getPlaybackCacheSizeBytes(),
        coverBytes = mediaStorageHelper.getCoverCacheSizeBytes(),
        homeFeedBytes = mediaStorageHelper.getHomeFeedCacheSizeBytes(),
        coverMaxBytes = mediaStorageHelper.getCoverCacheMaxBytes(),
        homeFeedMaxBytes = mediaStorageHelper.getHomeFeedCacheMaxBytes()
    )

    fun clearPlaybackCache() {
        mediaStorageHelper.clearPlaybackCache()
    }

    fun clearCoverCache() {
        mediaStorageHelper.clearCoverCache()
    }

    fun clearHomeFeedCache() {
        mediaStorageHelper.clearHomeFeedCache()
    }

    fun clearAllCaches() {
        mediaStorageHelper.clearAllCaches()
    }
}
