package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.model.VersionInfo
import com.lemon.yingshi.tv.domain.service.VersionCheckService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 版本更新ViewModel
 */
class VersionUpdateViewModel : ViewModel() {
    private val versionCheckService = VersionCheckService()
    
    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)
    val versionInfo: StateFlow<VersionInfo?> = _versionInfo
    
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking
    
    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate
    
    // 下载进度
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading
    
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress
    
    /**
     * 检查版本更新
     */
    fun checkForUpdates(currentVersionCode: Int) {
        viewModelScope.launch {
            _isChecking.value = true
            val version = versionCheckService.checkForUpdates(currentVersionCode)
            _versionInfo.value = version
            _hasUpdate.value = version != null
            _isChecking.value = false
        }
    }
    
    /**
     * 开始下载
     */
    fun startDownloadProgress() {
        _isDownloading.value = true
        _downloadProgress.value = 0
    }
    
    /**
     * 更新下载进度
     */
    fun updateDownloadProgress(progress: Int) {
        _downloadProgress.value = progress
    }
    
    /**
     * 完成下载
     */
    fun completeDownload() {
        viewModelScope.launch {
            _downloadProgress.value = 100
            delay(500) // 显示100%一会儿
            _isDownloading.value = false
            _downloadProgress.value = 0
        }
    }
    
    /**
     * 清除更新提示
     */
    fun clearUpdate() {
        _versionInfo.value = null
        _hasUpdate.value = false
    }
}