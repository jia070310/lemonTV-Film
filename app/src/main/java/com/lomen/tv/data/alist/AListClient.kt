package com.lomen.tv.data.alist

import android.util.Log
import com.lomen.tv.data.remote.RemoteFileClient
import com.lomen.tv.data.webdav.WebDavFile
import com.lomen.tv.domain.model.ResourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * AList/OpenList API 客户端。
 *
 * 约定：
 * - [ResourceLibrary.apiBaseUrl] 指向 AList 服务根，例如 http://host:5244
 * - [ResourceLibrary.apiToken] 为 AList token（如需 Bearer 前缀由上层保存时带上）
 * - [ResourceLibrary.selectedPaths] 为用户勾选的扫描目录（AList path），为空则从 "/" 扫描
 */
class AListClient(
    private val library: ResourceLibrary,
    private val scanConcurrency: Int = 10
) : RemoteFileClient {
    companion object {
        private const val TAG = "AListClient"
        private val VIDEO_EXTENSIONS =
            listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".3gp")
        private val COVER_NAMES = listOf(
            "poster.jpg", "poster.png", "cover.jpg", "cover.png",
            "fanart.jpg", "fanart.png", "thumb.jpg", "thumb.png",
            "default.jpg", "default.png", "folder.jpg", "folder.png"
        )
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun baseUrl(): String = library.apiBaseUrl.trimEnd('/')

    private fun authHeaderValue(): String? = library.apiToken.trim().takeIf { it.isNotBlank() }

    private fun normalizePath(path: String): String {
        val p = path.trim()
        if (p.isEmpty()) return "/"
        return if (p.startsWith("/")) p else "/$p"
    }

    override suspend fun listFiles(relativePath: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "${baseUrl()}/api/fs/list"
            val path = normalizePath(relativePath)
            val body = JSONObject()
                .put("path", path)
                .put("page", 1)
                .put("per_page", 2000)
                .toString()

            val request = Request.Builder()
                .url(apiUrl)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { authHeaderValue()?.let { header("Authorization", it) } }
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "listFiles failed http=${response.code}, body=${text.take(200)}")
                    return@withContext Result.failure(Exception("AList list failed: HTTP ${response.code}"))
                }
                val json = JSONObject(text)
                val code = json.optInt("code", 200)
                if (code != 200) {
                    val msg = json.optString("message", "unknown")
                    return@withContext Result.failure(Exception("AList list failed: $code $msg"))
                }
                val content = json.optJSONObject("data")?.optJSONArray("content")
                val files = mutableListOf<WebDavFile>()
                if (content != null) {
                    for (i in 0 until content.length()) {
                        val item = content.optJSONObject(i) ?: continue
                        val name = item.optString("name").orEmpty()
                        if (name.isBlank()) continue
                        val isDir = item.optBoolean("is_dir", false)
                        val size = item.optLong("size", 0L)
                        val itemPath = item.optString("path").takeIf { it.isNotBlank() }
                            ?: (if (path == "/") "/$name" else "${path.trimEnd('/')}/$name")
                        files += WebDavFile(
                            name = name,
                            path = itemPath,
                            isDirectory = isDir,
                            size = size,
                            lastModified = item.optString("modified", "")
                        )
                    }
                }
                Result.success(files)
            }
        } catch (e: Exception) {
            Log.e(TAG, "listFiles error", e)
            Result.failure(e)
        }
    }

    override suspend fun listAllVideoFiles(onProgress: ((Int) -> Unit)?): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            val roots = library.selectedPaths.takeIf { it.isNotEmpty() } ?: listOf("/")
            val allVideos = mutableListOf<WebDavFile>()
            val allVideosLock = Mutex()
            val enqueuedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val scannedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val dirsToScan = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val pendingDirs = java.util.concurrent.atomic.AtomicInteger(0)

            roots.map { normalizePath(it) }.forEach { root ->
                if (enqueuedDirs.add(root)) {
                    pendingDirs.incrementAndGet()
                    dirsToScan.offer(root)
                }
            }

            val maxConcurrency = scanConcurrency.coerceIn(4, 20)
            val semaphore = Semaphore(maxConcurrency)

            coroutineScope {
                val workers = List(maxConcurrency) {
                    async {
                        while (true) {
                            val dirPath = dirsToScan.poll()
                            if (dirPath == null) {
                                if (pendingDirs.get() == 0) break
                                continue
                            }
                            semaphore.acquire()
                            try {
                                if (!scannedDirs.add(dirPath)) continue
                                val filesResult = listFiles(dirPath)
                                if (filesResult.isFailure) continue
                                val items = filesResult.getOrNull().orEmpty()
                                val videos = mutableListOf<WebDavFile>()
                                for (f in items) {
                                    if (f.isDirectory) {
                                        val sub = normalizePath(f.path)
                                        if (enqueuedDirs.add(sub)) {
                                            pendingDirs.incrementAndGet()
                                            dirsToScan.offer(sub)
                                        }
                                    } else if (isVideoFile(f.name)) {
                                        videos += f
                                    }
                                }
                                if (videos.isNotEmpty()) {
                                    allVideosLock.withLock {
                                        allVideos.addAll(videos)
                                        onProgress?.invoke(allVideos.size)
                                    }
                                }
                            } finally {
                                pendingDirs.decrementAndGet()
                                semaphore.release()
                            }
                        }
                    }
                }
                workers.awaitAll()
            }

            Result.success(allVideos)
        } catch (e: Exception) {
            Log.e(TAG, "listAllVideoFiles error", e)
            Result.failure(e)
        }
    }

    private fun isVideoFile(name: String): Boolean {
        val lower = name.lowercase()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    /**
     * 返回可访问 URL（默认走 /d/ 直链模式；若服务端要求签名/鉴权，播放阶段再走 /api/fs/get 获取 raw_url）。
     */
    override fun getFileUrl(relativePath: String): String {
        val p = normalizePath(relativePath)
        val encoded = p.split("/").joinToString("/") { seg ->
            if (seg.isEmpty()) "" else URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        return "${baseUrl()}/d$encoded"
    }

    override suspend fun getCoverImage(directoryPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val filesResult = listFiles(directoryPath)
            if (filesResult.isFailure) return@withContext null
            val files = filesResult.getOrNull().orEmpty()
            for (cover in COVER_NAMES) {
                val hit = files.firstOrNull { !it.isDirectory && it.name.equals(cover, ignoreCase = true) }
                if (hit != null) return@withContext getFileUrl(hit.path)
            }
        } catch (_: Exception) {
        }
        null
    }

    override suspend fun readTextFile(relativePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = getFileUrl(relativePath)
            val request = Request.Builder()
                .url(url)
                .get()
                .apply { authHeaderValue()?.let { header("Authorization", it) } }
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fileExists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = getFileUrl(relativePath)
            val request = Request.Builder()
                .url(url)
                .method("HEAD", null)
                .apply { authHeaderValue()?.let { header("Authorization", it) } }
                .build()
            client.newCall(request).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 通过 AList API 获取 raw_url（更稳的直链方式，优先用于播放）。
     */
    suspend fun getRawUrl(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "${baseUrl()}/api/fs/get"
            val p = normalizePath(path)
            val body = JSONObject().put("path", p).toString()
            val request = Request.Builder()
                .url(apiUrl)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { authHeaderValue()?.let { header("Authorization", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext Result.failure(Exception("AList get failed: HTTP ${response.code}"))
                val json = JSONObject(text)
                if (json.optInt("code", 200) != 200) {
                    return@withContext Result.failure(Exception("AList get failed: ${json.optInt("code")} ${json.optString("message")}"))
                }
                val data = json.optJSONObject("data")
                val raw = data?.optString("raw_url").orEmpty()
                if (raw.isBlank()) return@withContext Result.failure(Exception("AList raw_url empty"))
                Result.success(raw)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

