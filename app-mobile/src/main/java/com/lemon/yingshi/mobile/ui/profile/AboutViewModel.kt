package com.lemon.yingshi.mobile.ui.profile



import android.content.Context

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.lemon.yingshi.tv.domain.model.AppPlatform
import com.lemon.yingshi.tv.domain.model.VersionInfo

import com.lemon.yingshi.tv.domain.service.DownloadService

import com.lemon.yingshi.tv.domain.service.PlaybackStatsService

import com.lemon.yingshi.tv.domain.service.VersionCheckService

import dagger.hilt.android.lifecycle.HiltViewModel

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch



@HiltViewModel

class AboutViewModel @Inject constructor(

    playbackStatsService: PlaybackStatsService,

    @ApplicationContext private val appContext: Context

) : ViewModel() {



    private val versionCheckService = VersionCheckService(AppPlatform.MOBILE)



    val totalPlaybackTimeMs: StateFlow<Long> = playbackStatsService.totalPlaybackTimeMs

        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)



    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)

    val versionInfo: StateFlow<VersionInfo?> = _versionInfo.asStateFlow()



    private val _hasUpdate = MutableStateFlow(false)

    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()



    private val _isChecking = MutableStateFlow(false)

    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()



    private val _isDownloading = MutableStateFlow(false)

    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()



    private val _downloadProgress = MutableStateFlow(0)

    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()



    private val _downloadFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val downloadFailed: SharedFlow<Unit> = _downloadFailed.asSharedFlow()

    private val _installApk = MutableSharedFlow<java.io.File>(extraBufferCapacity = 1)

    val installApk: SharedFlow<java.io.File> = _installApk.asSharedFlow()



    private val playbackStatsServiceRef = playbackStatsService



    fun checkForUpdates(currentVersionCode: Int) {

        viewModelScope.launch {

            _isChecking.value = true

            val version = versionCheckService.checkForUpdates(currentVersionCode)

            _versionInfo.value = version

            _hasUpdate.value = version != null

            _isChecking.value = false

        }

    }



    fun clearPlaybackStats() {

        viewModelScope.launch {

            playbackStatsServiceRef.clearTotalPlaybackTime()

        }

    }



    fun downloadUpdate() {

        val version = _versionInfo.value ?: return

        if (_isDownloading.value) return

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

                        completeDownload(apkFile)

                    }

                }

            )

        }

    }



    private suspend fun completeDownload(apkFile: java.io.File?) {

        _downloadProgress.value = 100

        delay(COMPLETE_DISPLAY_MS)

        _isDownloading.value = false

        _downloadProgress.value = 0

        if (apkFile != null) {

            _installApk.emit(apkFile)

        } else {

            _downloadFailed.emit(Unit)

        }

    }



    companion object {

        private const val COMPLETE_DISPLAY_MS = 500L

    }

}

