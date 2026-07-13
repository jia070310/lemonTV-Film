package com.lemon.yingshi.tv.domain.service

import android.util.Log
import com.lemon.yingshi.tv.domain.model.AppPlatform
import com.lemon.yingshi.tv.domain.model.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本检查服务：按 [AppPlatform] 从 GitHub Releases 匹配对应平台的最新包。
 *
 * - TV：tag 不含 mobile（如 v1.0.5），apk 含 tv 或为 app-release.apk
 * - Mobile：tag 含 mobile（如 mobile-v1.0.0），apk 文件名含 mobile
 */
class VersionCheckService(
    private val platform: AppPlatform
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val githubReleasesUrl =
        "https://api.github.com/repos/jia070310/lemonTV-Film/releases?per_page=30"

    suspend fun checkForUpdates(currentVersionCode: Int): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(githubReleasesUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            val releases = JSONArray(responseBody)

            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft", false)) continue

                val tagName = release.optString("tag_name").orEmpty()
                if (!releaseMatchesPlatform(tagName)) continue

                val asset = findPlatformApk(release.optJSONArray("assets") ?: continue) ?: continue
                val versionName = extractVersionName(tagName)
                val versionCode = parseVersionCode(versionName)
                if (versionCode <= 0) continue

                Log.d(
                    TAG,
                    "Platform=$platform tag=$tagName remote=$versionCode current=$currentVersionCode"
                )

                if (versionCode > currentVersionCode) {
                    val originalDownloadUrl = asset.getString("browser_download_url")
                    return@withContext VersionInfo(
                        versionName = versionName,
                        versionCode = versionCode,
                        downloadUrl = "https://gh-proxy.org/$originalDownloadUrl",
                        releaseNotes = release.optString("body"),
                        releaseDate = release.optString("published_at")
                    )
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}", e)
        }
        return@withContext null
    }

    private fun releaseMatchesPlatform(tagName: String): Boolean {
        val tag = tagName.lowercase()
        return when (platform) {
            AppPlatform.TV -> !tag.contains("mobile")
            AppPlatform.MOBILE -> tag.contains("mobile")
        }
    }

    private fun findPlatformApk(assets: JSONArray): JSONObject? {
        var tvFallback: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name").lowercase()
            if (!name.endsWith(".apk")) continue

            when (platform) {
                AppPlatform.MOBILE -> if (name.contains("mobile")) return asset
                AppPlatform.TV -> {
                    if (name.contains("mobile")) continue
                    if (name.contains("tv") || name == "app-release.apk") return asset
                    if (tvFallback == null) tvFallback = asset
                }
            }
        }
        return if (platform == AppPlatform.TV) tvFallback else null
    }

    private fun extractVersionName(tagName: String): String {
        var tag = tagName.trim()
        if (tag.startsWith("v", ignoreCase = true)) {
            tag = tag.substring(1)
        }
        val lower = tag.lowercase()
        return when {
            lower.startsWith("mobile-v") -> tag.substring("mobile-v".length)
            lower.startsWith("mobile") -> tag.substring("mobile".length).trimStart('-', 'v', 'V')
            lower.startsWith("tv-v") -> tag.substring("tv-v".length)
            lower.startsWith("tv-") -> tag.substring(3)
            else -> tag
        }
    }

    private fun parseVersionCode(versionName: String): Int {
        return try {
            versionName.split(".").map { it.toInt() }
                .fold(0) { acc, part -> acc * 100 + part }
        } catch (_: Exception) {
            0
        }
    }

    private companion object {
        const val TAG = "VersionCheckService"
    }
}
