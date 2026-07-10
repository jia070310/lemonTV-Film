package com.lemon.yingshi.tv.domain.service

import android.content.Context
import com.lemon.yingshi.tv.domain.model.VersionInfo
import com.lemon.yingshi.tv.domain.service.VersionCheckService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新服务
 */
@Singleton
class AppUpdateService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val versionCheckService = VersionCheckService()
    
    /**
     * 检查版本更新
     */
    fun checkForUpdates(currentVersionCode: Int): VersionInfo? {
        return runCatching {
            kotlinx.coroutines.runBlocking {
                versionCheckService.checkForUpdates(currentVersionCode)
            }
        }.getOrNull()
    }
    
    /**
     * 下载并安装更新
     */
    suspend fun downloadAndInstallUpdate(
        versionInfo: VersionInfo,
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        val downloadService = DownloadService(context)
        downloadService.downloadApk(
            versionInfo = versionInfo,
            onProgress = onProgress,
            onComplete = { apkFile ->
                if (apkFile != null) {
                    downloadService.installApk(apkFile)
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
        )
    }
}