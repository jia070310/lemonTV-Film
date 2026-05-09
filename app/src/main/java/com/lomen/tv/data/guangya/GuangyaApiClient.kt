package com.lomen.tv.data.guangya

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
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 光鸭云盘 API 客户端（参考 AList GuangYaPan 驱动）。
 *
 * 关键接口：
 * - 列表：/nd.bizuserres.s/v1/file/get_file_list
 * - 直链：/nd.bizuserres.s/v1/get_res_download_url
 */
class GuangyaApiClient(
    private val library: ResourceLibrary,
    private val scanConcurrency: Int = 10
) : RemoteFileClient {
    data class GuangyaTokenUpdate(
        val accessToken: String,
        val refreshToken: String,
        val accessExpireAt: Long
    )
    companion object {
        private const val TAG = "GuangyaApiClient"
        private const val API_BASE = "https://api.guangyapan.com"
        private const val PAGE_SIZE = 100
        private val VIDEO_EXTENSIONS =
            listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".3gp")
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var accessToken: String = library.apiToken.trim()
    @Volatile
    private var refreshToken: String = library.apiRefreshToken.trim()
    @Volatile
    private var accessExpireAt: Long = library.apiTokenExpireAt
    private val tokenLock = Mutex()
    @Volatile
    private var lastTokenRefreshError: String? = null

    private fun authHeader(): String = accessToken
    private fun isAccessExpired(bufferMs: Long = 120_000L): Boolean {
        if (accessToken.isBlank()) return true
        if (accessExpireAt <= 0L) return false
        return System.currentTimeMillis() >= (accessExpireAt - bufferMs)
    }

    private suspend fun refreshAccessTokenLocked(): Boolean = withContext(Dispatchers.IO) {
        lastTokenRefreshError = null
        if (refreshToken.isBlank()) return@withContext false
        return@withContext try {
            val body = JSONObject()
                .put("client_id", "aMe-8VSlkrbQXpUR")
                .put("grant_type", "refresh_token")
                .put("refresh_token", refreshToken)
            // 部分接口需要 device_id 做风控/归因；如果库里已保存就一并带上
            library.guangyaDeviceId?.takeIf { it.isNotBlank() }?.let { body.put("device_id", it) }
            val jsonBody = body.toString()
            val req = Request.Builder()
                .url("https://account.guangyapan.com/v1/auth/token")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastTokenRefreshError = "HTTP ${resp.code}: ${text.take(500)}"
                    return@use false
                }
                val json = JSONObject(text)
                val tokenJson = json.optJSONObject("data") ?: json
                val newAccess = tokenJson.optString("access_token").ifBlank {
                    tokenJson.optString("accessToken")
                }
                if (newAccess.isBlank()) {
                    val err = json.optString("error").ifBlank { tokenJson.optString("error") }
                    val errDesc = json.optString("error_description").ifBlank {
                        tokenJson.optString("error_description")
                    }
                    lastTokenRefreshError = buildString {
                        append("missing access_token in response")
                        if (err.isNotBlank()) append(", error=").append(err)
                        if (errDesc.isNotBlank()) append(", desc=").append(errDesc)
                        append(", body=").append(text.take(240))
                    }
                    return@use false
                }
                accessToken = "Bearer $newAccess"
                val newRefresh = tokenJson.optString("refresh_token").ifBlank {
                    tokenJson.optString("refreshToken")
                }
                if (newRefresh.isNotBlank()) refreshToken = newRefresh
                val expiresIn = tokenJson.optLong("expires_in", tokenJson.optLong("expiresIn", 0L))
                accessExpireAt = if (expiresIn > 0) {
                    System.currentTimeMillis() + expiresIn * 1000L - 60_000L
                } else 0L
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "refresh token failed", e)
            lastTokenRefreshError = e.message ?: "unknown error"
            false
        }
    }

    /**
     * 主动刷新 token，并返回最新 token/过期时间，便于把新 token 写回 [ResourceLibrary]。
     */
    suspend fun refreshTokens(forceRefresh: Boolean = true): Result<GuangyaTokenUpdate> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh && !isAccessExpired()) {
                return@withContext Result.success(
                    GuangyaTokenUpdate(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        accessExpireAt = accessExpireAt
                    )
                )
            }
            if (refreshToken.isBlank()) return@withContext Result.failure(IllegalStateException("missing refresh_token"))
            val ok = tokenLock.withLock {
                // 在锁内执行刷新，避免并发导致 token 不一致
                refreshAccessTokenLocked()
            }
            if (!ok) {
                val msg = lastTokenRefreshError ?: "refresh token failed"
                return@withContext Result.failure(IllegalStateException(msg))
            }
            Result.success(
                GuangyaTokenUpdate(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accessExpireAt = accessExpireAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureValidAccessToken(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh && !isAccessExpired()) return true
        return tokenLock.withLock {
            if (!forceRefresh && !isAccessExpired()) return@withLock true
            refreshAccessTokenLocked()
        }
    }

    // 目录 path -> fileId（root = ""）
    private val dirIdCache = ConcurrentHashMap<String, String>()
    private val dirCacheLock = Mutex()

    init {
        dirIdCache["/"] = ""
    }

    private fun normalizePath(path: String): String {
        val p = path.trim()
        if (p.isEmpty()) return "/"
        val withSlash = if (p.startsWith("/")) p else "/$p"
        return withSlash.replace(Regex("/+"), "/")
    }

    private fun encodeFilePathWithId(path: String, fileId: String): String {
        // 将 fileId 编码进 filePath，便于播放时拿直链；对刮削影响最小（文件名来自 name 字段）
        val p = normalizePath(path)
        return "$p?gfid=${URLEncoder.encode(fileId, "UTF-8")}"
    }

    private fun decodeFileId(path: String): String? {
        val marker = "?gfid="
        val idx = path.indexOf(marker)
        if (idx < 0) return null
        return runCatching { URLDecoder.decode(path.substring(idx + marker.length), "UTF-8") }.getOrNull()
    }

    private fun stripQuery(path: String): String = path.substringBefore("?gfid=")

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!ensureValidAccessToken()) {
            throw IllegalStateException("token expired, please relogin")
        }

        suspend fun doPost(): Pair<Int, String> {
            val req = Request.Builder()
                .url("$API_BASE$path")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", authHeader())
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json")
                .build()
            return client.newCall(req).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }

        var (code, text) = doPost()
        if (code == 401 && ensureValidAccessToken(forceRefresh = true)) {
            val retry = doPost()
            code = retry.first
            text = retry.second
        }
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${text.take(200)}")
        }
        JSONObject(text)
    }

    suspend fun validateToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ensureValidAccessToken()) return@withContext false

            suspend fun doCheck(): Int {
                val req = Request.Builder()
                    .url("https://account.guangyapan.com/v1/user/me")
                    .get()
                    .header("Authorization", authHeader())
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                return client.newCall(req).execute().use { it.code }
            }

            var code = doCheck()
            if (code == 401 && ensureValidAccessToken(forceRefresh = true)) {
                code = doCheck()
            }
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun resolveDirId(path: String): String = dirCacheLock.withLock {
        val normalized = normalizePath(path)
        dirIdCache[normalized]?.let { return it }
        if (normalized == "/") return ""

        var currentPath = "/"
        var currentId = ""
        val parts = normalized.trim('/').split('/').filter { it.isNotBlank() }
        for (seg in parts) {
            val nextPath = if (currentPath == "/") "/$seg" else "$currentPath/$seg"
            val cached = dirIdCache[nextPath]
            if (cached != null) {
                currentPath = nextPath
                currentId = cached
                continue
            }

            val list = listByParentId(currentId)
            val folder = list.firstOrNull { it.isDirectory && it.name == seg }
                ?: throw IllegalStateException("Folder not found: $nextPath")
            currentPath = nextPath
            currentId = folder.path
            dirIdCache[nextPath] = currentId
        }
        currentId
    }

    private suspend fun listByParentId(parentId: String): List<WebDavFile> {
        val result = mutableListOf<WebDavFile>()
        var page = 0
        while (true) {
            val json = postJson(
                "/nd.bizuserres.s/v1/file/get_file_list",
                JSONObject()
                    .put("parentId", parentId)
                    .put("page", page)
                    .put("pageSize", PAGE_SIZE)
                    .put("orderBy", 3)
                    .put("sortType", 1)
                    .put("fileTypes", org.json.JSONArray())
            )
            val data = json.optJSONObject("data")
            val list = data?.optJSONArray("list")
            if (list == null || list.length() == 0) break
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val fileId = item.optString("fileId")
                val name = item.optString("fileName")
                val isDir = item.optInt("resType", 1) == 2
                if (name.isBlank() || fileId.isBlank()) continue
                if (isDir) {
                    result += WebDavFile(
                        name = name,
                        path = fileId, // 目录内部使用 fileId 临时返回，外层会改写为 path
                        isDirectory = true,
                        size = item.optLong("fileSize", 0L)
                    )
                } else {
                    result += WebDavFile(
                        name = name,
                        path = fileId, // 文件内部使用 fileId，外层再封装
                        isDirectory = false,
                        size = item.optLong("fileSize", 0L)
                    )
                }
            }
            if (list.length() < PAGE_SIZE) break
            page++
        }
        return result
    }

    override suspend fun listFiles(relativePath: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizePath(relativePath)
            val parentId = resolveDirId(normalized)
            val raw = listByParentId(parentId)
            val mapped = raw.map { item ->
                val childPath = if (normalized == "/") "/${item.name}" else "${normalized.trimEnd('/')}/${item.name}"
                if (item.isDirectory) {
                    dirIdCache[childPath] = item.path // item.path 此时是 fileId
                    item.copy(path = childPath)
                } else {
                    item.copy(path = encodeFilePathWithId(childPath, item.path))
                }
            }
            Result.success(mapped)
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
            val enqueuedDirs = ConcurrentHashMap.newKeySet<String>()
            val scannedDirs = ConcurrentHashMap.newKeySet<String>()
            val queue = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val pending = java.util.concurrent.atomic.AtomicInteger(0)

            roots.map { normalizePath(it) }.forEach { root ->
                if (enqueuedDirs.add(root)) {
                    pending.incrementAndGet()
                    queue.offer(root)
                }
            }

            val maxConcurrency = scanConcurrency.coerceIn(4, 20)
            val semaphore = Semaphore(maxConcurrency)
            coroutineScope {
                val workers = List(maxConcurrency) {
                    async {
                        while (true) {
                            val dir = queue.poll()
                            if (dir == null) {
                                if (pending.get() == 0) break
                                continue
                            }
                            semaphore.acquire()
                            try {
                                if (!scannedDirs.add(dir)) continue
                                val res = listFiles(dir).getOrNull().orEmpty()
                                val videos = mutableListOf<WebDavFile>()
                                res.forEach { f ->
                                    if (f.isDirectory) {
                                        if (enqueuedDirs.add(f.path)) {
                                            pending.incrementAndGet()
                                            queue.offer(f.path)
                                        }
                                    } else if (isVideo(f.name)) {
                                        videos += f
                                    }
                                }
                                if (videos.isNotEmpty()) {
                                    allVideosLock.withLock {
                                        allVideos += videos
                                        onProgress?.invoke(allVideos.size)
                                    }
                                }
                            } finally {
                                pending.decrementAndGet()
                                semaphore.release()
                            }
                        }
                    }
                }
                workers.awaitAll()
            }
            Result.success(allVideos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVideo(name: String): Boolean {
        val lower = name.lowercase()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    override fun getFileUrl(relativePath: String): String {
        // 需要异步请求获取签名直链，这里仅返回原路径（播放器阶段请用 getDownloadUrlByPath）
        return stripQuery(relativePath)
    }

    suspend fun getDownloadUrlByPath(relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileId = decodeFileId(relativePath)
            if (!fileId.isNullOrBlank()) {
                return@withContext getDownloadUrlByFileId(fileId)
            }
            // 兜底：按路径定位文件名并重新列表匹配 fileId
            val normalized = normalizePath(stripQuery(relativePath))
            val parentPath = normalized.substringBeforeLast('/', "/")
            val fileName = normalized.substringAfterLast('/')
            val files = listFiles(parentPath).getOrNull().orEmpty()
            val hit = files.firstOrNull { !it.isDirectory && it.name == fileName }
                ?: return@withContext Result.failure(IllegalStateException("file not found: $relativePath"))
            val hitId = decodeFileId(hit.path)
                ?: return@withContext Result.failure(IllegalStateException("missing gfid in path"))
            getDownloadUrlByFileId(hitId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getDownloadUrlByFileId(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = postJson(
                "/nd.bizuserres.s/v1/get_res_download_url",
                JSONObject().put("fileId", fileId)
            )
            val data = json.optJSONObject("data")
            val signed = data?.optString("signedURL").orEmpty()
            val url = if (signed.isNotBlank()) signed else data?.optString("downloadUrl").orEmpty()
            if (url.isBlank()) return@withContext Result.failure(IllegalStateException("empty download url"))
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoverImage(directoryPath: String): String? = null

    override suspend fun readTextFile(relativePath: String): String? = null

    override suspend fun fileExists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        getDownloadUrlByPath(relativePath).isSuccess
    }
}

