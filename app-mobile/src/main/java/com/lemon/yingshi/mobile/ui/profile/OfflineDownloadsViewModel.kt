package com.lemon.yingshi.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.service.OfflineDownloadItem
import com.lemon.yingshi.tv.domain.service.OfflineDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OfflineDownloadsViewModel @Inject constructor(
    private val offlineDownloadService: OfflineDownloadService
) : ViewModel() {

    val downloads = offlineDownloadService.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            offlineDownloadService.deleteDownload(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            offlineDownloadService.clearAllDownloads()
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch {
            offlineDownloadService.pauseDownload(id)
        }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            offlineDownloadService.resumeDownload(id)
        }
    }

    fun pauseAll() {
        viewModelScope.launch {
            offlineDownloadService.pauseAllDownloads()
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            offlineDownloadService.resumeAllDownloads()
        }
    }
}
