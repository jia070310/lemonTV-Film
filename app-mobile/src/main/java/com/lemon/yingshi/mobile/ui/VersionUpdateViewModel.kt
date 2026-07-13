package com.lemon.yingshi.mobile.ui



import android.content.Context

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.lemon.yingshi.tv.domain.model.AppPlatform
import com.lemon.yingshi.tv.domain.model.VersionInfo
import com.lemon.yingshi.tv.domain.service.DownloadService
import com.lemon.yingshi.tv.domain.service.VersionCheckService

import dagger.hilt.android.lifecycle.HiltViewModel

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch



@HiltViewModel

class VersionUpdateViewModel @Inject constructor(

    @ApplicationContext private val appContext: Context

) : ViewModel() {



    private val versionCheckService = VersionCheckService(AppPlatform.MOBILE)



    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)

    val versionInfo: StateFlow<VersionInfo?> = _versionInfo.asStateFlow()



    private val _hasUpdate = MutableStateFlow(false)

    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()



    private val _showTopBanner = MutableStateFlow(false)

    val showTopBanner: StateFlow<Boolean> = _showTopBanner.asStateFlow()



    private val _isDownloading = MutableStateFlow(false)

    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()



    private val _downloadProgress = MutableStateFlow(0)

    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()



    private val _downloadFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val downloadFailed: SharedFlow<Unit> = _downloadFailed.asSharedFlow()



    fun checkForUpdates(currentVersionCode: Int) {

        viewModelScope.launch {

            val version = versionCheckService.checkForUpdates(currentVersionCode)

            _versionInfo.value = version

            val updateAvailable = version != null

            _hasUpdate.value = updateAvailable

            if (updateAvailable) {

                showBannerTemporarily()

            }

        }

    }



    fun downloadUpdate() {

        val version = _versionInfo.value ?: return

        if (_isDownloading.value) return

        dismissTopBanner()

        viewModelScope.launch {

            _isDownloading.value = true

            _downloadProgress.value = 0

            val downloadService = DownloadService(appContext)

            downloadService.downloadApk(

                versionInfo = version,

                onProgress = { progress ->

                    _downloadProgress.value = progress

                },

                onComplete = { apkFile ->

                    viewModelScope.launch {

                        completeDownload(apkFile, downloadService)

                    }

                }

            )

        }

    }



    private suspend fun completeDownload(apkFile: java.io.File?, downloadService: DownloadService) {

        _downloadProgress.value = 100

        delay(COMPLETE_DISPLAY_MS)

        _isDownloading.value = false

        _downloadProgress.value = 0

        if (apkFile != null) {

            downloadService.installApk(apkFile)

        } else {

            _downloadFailed.emit(Unit)

        }

    }



    private fun showBannerTemporarily() {

        _showTopBanner.value = true

        viewModelScope.launch {

            delay(BANNER_AUTO_DISMISS_MS)

            dismissTopBanner()

        }

    }



    fun dismissTopBanner() {

        _showTopBanner.value = false

    }



    companion object {

        private const val BANNER_AUTO_DISMISS_MS = 10_000L

        private const val COMPLETE_DISPLAY_MS = 500L

    }

}

