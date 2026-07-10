package com.lemon.yingshi.tv.domain.service

import android.util.Log
import com.lemon.yingshi.tv.domain.model.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本检查服务
 * 用于检查是否有新版本
 */
class VersionCheckService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val githubRepoUrl = "https://api.github.com/repos/jia070310/lemonTV-Film/releases/latest"
    
    /**
     * 检查是否有新版本
     * @param currentVersionCode 当前版本号
     * @return 版本信息，如果没有新版本返回null
     */
    suspend fun checkForUpdates(currentVersionCode: Int): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(githubRepoUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext null
                val json = JSONObject(responseBody)
                
                // 解析版本信息
                val tagName = json.getString("tag_name")
                val versionName = tagName.replace("v", "")
                val versionCode = parseVersionCode(versionName)
                val originalDownloadUrl = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                // 使用加速器
                val downloadUrl = "https://gh-proxy.org/$originalDownloadUrl"
                val releaseNotes = json.getString("body")
                val releaseDate = json.getString("published_at")
                
                Log.d("VersionCheckService", "Current version: $currentVersionCode, Remote version: $versionCode")
                
                // 比较版本号
                if (versionCode > currentVersionCode) {
                    return@withContext VersionInfo(
                        versionName = versionName,
                        versionCode = versionCode,
                        downloadUrl = downloadUrl,
                        releaseNotes = releaseNotes,
                        releaseDate = releaseDate
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VersionCheckService", "Error checking for updates: ${e.message}", e)
        }
        return@withContext null
    }
    
    /**
     * 解析版本号为整数
     * 例如：1.0.1 -> 101
     */
    private fun parseVersionCode(versionName: String): Int {
        return try {
            versionName.split(".").map { it.toInt() }
                .fold(0) { acc, part -> acc * 100 + part }
        } catch (e: Exception) {
            0
        }
    }
}