package com.lemon.yingshi.tv.domain.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.lemon.yingshi.tv.domain.model.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ApkInstallResult {
    Started,
    NeedInstallPermission,
    Failed
}

/**
 * 下载服务
 * 用于下载和安装APK（使用OkHttp，避免系统下载提示）
 */
class DownloadService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 下载APK（使用OkHttp，无系统提示）
     * @param versionInfo 版本信息
     * @param onProgress 进度回调 (0-100)
     * @param onComplete 完成回调
     */
    suspend fun downloadApk(
        versionInfo: VersionInfo,
        onProgress: (Int) -> Unit,
        onComplete: (File?) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val downloadFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "Lomen-update-v${versionInfo.versionName}.apk"
            )
            
            val request = Request.Builder()
                .url(versionInfo.downloadUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("DownloadService", "下载失败: ${response.code}")
                    onComplete(null)
                    return@withContext
                }
                
                val body = response.body ?: run {
                    onComplete(null)
                    return@withContext
                }
                
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                
                FileOutputStream(downloadFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
                
                onComplete(downloadFile)
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "下载异常", e)
            onComplete(null)
        }
    }
    
    /**
     * 安装 APK。建议使用 Activity 作为 [launchContext]，以便在 Android 8+ 正常拉起系统安装界面。
     */
    fun installApk(apkFile: File, launchContext: Context = context): ApkInstallResult {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK 不存在: ${apkFile.absolutePath}")
            return ApkInstallResult.Failed
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !launchContext.packageManager.canRequestPackageInstalls()
        ) {
            openInstallPermissionSettings(launchContext)
            return ApkInstallResult.NeedInstallPermission
        }

        return try {
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    launchContext,
                    "${launchContext.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (launchContext !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            launchContext.startActivity(intent)
            ApkInstallResult.Started
        } catch (e: Exception) {
            Log.e(TAG, "启动安装失败", e)
            ApkInstallResult.Failed
        }
    }

    fun openInstallPermissionSettings(launchContext: Context = context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${launchContext.packageName}")
                if (launchContext !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            launchContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开安装权限设置失败", e)
        }
    }

    companion object {
        private const val TAG = "DownloadService"
    }
}