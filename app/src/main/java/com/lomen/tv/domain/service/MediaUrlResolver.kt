package com.lomen.tv.domain.service

import android.util.Base64
import android.util.Log
import com.lomen.tv.data.local.database.dao.EpisodeDao
import com.lomen.tv.data.local.database.dao.MovieDao
import com.lomen.tv.data.local.database.dao.WebDavMediaDao
import com.lomen.tv.data.repository.ResourceLibraryRepository
import com.lomen.tv.data.guangya.GuangyaApiClient
import com.lomen.tv.domain.model.ResourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析媒体URL，处理WebDAV认证和获取真实播放链接
 */
@Singleton
class MediaUrlResolver @Inject constructor(
    private val libraryRepository: ResourceLibraryRepository,
    private val movieDao: MovieDao,
    private val episodeDao: EpisodeDao,
    private val webDavMediaDao: WebDavMediaDao
) {
    companion object {
        private const val TAG = "MediaUrlResolver"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 解析视频路径为可播放的URL
     * 处理WebDAV认证、OpenList/AList API、夸克网盘等
     */
    suspend fun resolvePlaybackUrl(videoPath: String): Result<MediaPlaybackInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving playback URL for: $videoPath")
            
            // 获取视频所属的资源库
            val libraries = libraryRepository.libraries.first()
            if (libraries.isEmpty()) {
                return@withContext Result.failure(Exception("No resource library configured"))
            }

            // 优先通过数据库根据 filePath 精确定位 libraryId
            val entity = webDavMediaDao.getByFilePath(videoPath)
            val library = entity?.libraryId?.let { id -> libraries.firstOrNull { it.id == id } }
                ?: libraries.first()
            
            Log.d(TAG, "Using library: ${library.name}, type: ${library.type}")
            
            // 根据资源库类型选择解析方式
            when (library.type) {
                ResourceLibrary.LibraryType.WEBDAV -> {
                    // WebDAV类型
                    resolveWebDavPlaybackUrl(videoPath, library)
                }
                ResourceLibrary.LibraryType.GUANGYA -> {
                    val client = GuangyaApiClient(library)
                    val direct = client.getDownloadUrlByPath(videoPath)
                    if (direct.isSuccess) {
                        Result.success(
                            MediaPlaybackInfo(
                                url = direct.getOrThrow(),
                                headers = emptyMap(),
                                subtitles = emptyList(),
                                audioTracks = emptyList()
                            )
                        )
                    } else {
                        Result.failure(direct.exceptionOrNull() ?: IllegalStateException("failed to resolve Guangya link"))
                    }
                }
                else -> {
                    // 其他类型，默认使用WebDAV方式
                    resolveWebDavPlaybackUrl(videoPath, library)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve playback URL", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析WebDAV播放URL
     */
    private suspend fun resolveWebDavPlaybackUrl(videoPath: String, library: ResourceLibrary): Result<MediaPlaybackInfo> {
        // 构建基础URL
        val baseUrl = "${library.protocol}://${library.host}:${library.port}${library.path}"
        val fullUrl = if (videoPath.startsWith("http")) {
            videoPath
        } else {
            // 清理路径，移除library name前缀
            val cleanPath = videoPath
                .removePrefix(library.name)
                .removePrefix("/")
                .trim('/')
            
            // URL编码路径中的每个部分（保留斜杠）
            val encodedPath = cleanPath.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")  // 空格编码为%20而不是+
            }
            
            // 确保baseUrl结尾有斜杠，cleanPath开头没有斜杠
            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            "$normalizedBase$encodedPath"
        }
        
        Log.d(TAG, "Full URL: $fullUrl")
        
        // 先尝试检测是否是OpenList/AList
        val isOpenList = isOpenListOrAList(baseUrl, library)
        
        val playbackResult = if (isOpenList) {
            Log.d(TAG, "Detected OpenList/AList server")
            resolveOpenListUrl(fullUrl, library, videoPath)
        } else {
            // 标准WebDAV，直接返回带认证的URL
            Log.d(TAG, "Using standard WebDAV")
            resolveWebDavUrl(fullUrl, library)
        }

        if (playbackResult.isFailure) return playbackResult

        val basePlayback = playbackResult.getOrNull() ?: return playbackResult
        val externalSubtitles = findSubtitles(videoPath, library)
        if (externalSubtitles.isNotEmpty()) {
            Log.d(TAG, "Found ${externalSubtitles.size} external subtitles for $videoPath")
        }
        return Result.success(
            basePlayback.copy(
                subtitles = externalSubtitles
            )
        )
    }
    


    /**
     * 检测是否是OpenList或AList服务
     */
    private suspend fun isOpenListOrAList(baseUrl: String, library: ResourceLibrary): Boolean {
        return try {
            // 首先尝试访问API端点
            val apiRequest = Request.Builder()
                .url("$baseUrl/api/public/settings")
                .get()
                .build()
            
            val apiResponse = client.newCall(apiRequest).execute()
            val apiBody = apiResponse.body?.string() ?: ""
            apiResponse.close()
            
            // 检查API响应（OpenList/AList都有这个端点）
            if (apiBody.contains("\"code\"") && apiBody.contains("\"data\"")) {
                Log.d(TAG, "Server detection: OpenList/AList confirmed via API")
                return true
            }
            
            // 如果API检测失败，尝试访问首页
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val contentType = response.header("Content-Type") ?: ""
            response.close()
            
            // 检查HTML内容特征
            val isOpenList = body.contains("OpenList") || 
                            body.contains("AList") || 
                            body.contains("alist-") ||
                            body.contains("data-base-url") ||
                            body.contains("/api/fs/list")
            
            Log.d(TAG, "Server detection: isOpenList=$isOpenList, contentType=$contentType, bodyPreview=${body.take(200)}")
            isOpenList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect server type", e)
            false
        }
    }

    /**
     * 解析OpenList/AList的播放URL
     * OpenList/AList通常提供直链API
     */
    private suspend fun resolveOpenListUrl(
        fullUrl: String,
        library: ResourceLibrary,
        videoPath: String
    ): Result<MediaPlaybackInfo> {
        return try {
            // 尝试登录获取token
            val token = loginOpenList(library)
            
            if (token != null) {
                // 使用API获取直链
                val directUrl = getOpenListDirectUrl(library, videoPath, token)
                if (directUrl != null) {
                    Log.d(TAG, "Got OpenList direct URL")
                    return Result.success(MediaPlaybackInfo(
                        url = directUrl,
                        headers = mapOf("Authorization" to token),
                        subtitles = emptyList(),
                        audioTracks = emptyList()
                    ))
                }
            }
            
            // 如果API失败，回退到标准WebDAV方式
            Log.w(TAG, "OpenList API failed, falling back to WebDAV")
            resolveWebDavUrl(fullUrl, library)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve OpenList URL", e)
            // 出错时也回退到WebDAV
            resolveWebDavUrl(fullUrl, library)
        }
    }

    /**
     * 登录OpenList获取token
     */
    private suspend fun loginOpenList(library: ResourceLibrary): String? {
        return try {
            val baseUrl = "${library.protocol}://${library.host}:${library.port}"
            val loginUrl = "$baseUrl/api/auth/login"
            val jsonBody = """
                {
                    "username": "${library.username}",
                    "password": "${library.password}"
                }
            """.trimIndent()
            
            Log.d(TAG, "OpenList login attempt: $loginUrl, username=${library.username}")
            
            val request = Request.Builder()
                .url(loginUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "OpenList login response: statusCode=${response.code}, contentType=${response.header("Content-Type")}, body=${responseBody.take(200)}")
            
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                if (json.optInt("code") == 200) {
                    val token = json.optJSONObject("data")?.optString("token")
                    Log.d(TAG, "OpenList login successful, token received")
                    return token
                } else {
                    Log.w(TAG, "OpenList login failed: code=${json.optInt("code")}, message=${json.optString("message")}")
                }
            } else {
                Log.w(TAG, "OpenList login HTTP error: ${response.code}")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "OpenList login failed", e)
            null
        }
    }

    /**
     * 获取OpenList直链
     */
    private suspend fun getOpenListDirectUrl(
        library: ResourceLibrary,
        path: String,
        token: String
    ): String? {
        return try {
            val baseUrl = "${library.protocol}://${library.host}:${library.port}"
            val apiUrl = "$baseUrl/api/fs/get"
            val cleanPath = path.removePrefix(library.name).removePrefix("/")
            
            val jsonBody = """
                {
                    "path": "/$cleanPath"
                }
            """.trimIndent()
            
            Log.d(TAG, "OpenList API request: $apiUrl, path: /$cleanPath")
            
            val request = Request.Builder()
                .url(apiUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("Authorization", token)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "OpenList API response: statusCode=${response.code}, body=${responseBody.take(200)}")
            
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                if (json.optInt("code") == 200) {
                    val data = json.optJSONObject("data")
                    
                    // 优先使用raw_url（带签名的直链）
                    val rawUrl = data?.optString("raw_url")
                    if (!rawUrl.isNullOrEmpty()) {
                        val fullRawUrl = if (rawUrl.startsWith("http")) {
                            rawUrl
                        } else {
                            "$baseUrl$rawUrl"
                        }
                        Log.d(TAG, "Got raw_url from OpenList: $fullRawUrl")
                        return fullRawUrl
                    }
                    
                    // 如果没有raw_url，尝试sign字段（某些OpenList版本）
                    val sign = data?.optString("sign")
                    if (!sign.isNullOrEmpty()) {
                        // 构建 /d/ 格式的URL
                        val encodedPath = cleanPath.split("/").joinToString("/") { segment ->
                            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                        }
                        val directUrl = "$baseUrl/d/$encodedPath?sign=$sign:0"
                        Log.d(TAG, "Built direct URL with sign: $directUrl")
                        return directUrl
                    }
                    
                    Log.w(TAG, "No raw_url or sign found in OpenList response")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get OpenList direct URL", e)
            null
        }
    }

    /**
     * 解析标准WebDAV URL
     */
    private suspend fun resolveWebDavUrl(
        fullUrl: String,
        library: ResourceLibrary
    ): Result<MediaPlaybackInfo> {
        return try {
            Log.d(TAG, "Using WebDAV direct access with auth")
            
            // 测试URL是否可访问（使用GET请求Range头来测试，不下载完整文件）
            val testRequest = Request.Builder()
                .url(fullUrl)
                .get()
                .header("Range", "bytes=0-0")  // 只请求第一个字节
                .apply {
                    createBasicAuthHeaders(library).forEach { (key, value) ->
                        header(key, value)
                    }
                }
                .build()
            
            val response = client.newCall(testRequest).execute()
            val statusCode = response.code
            val contentType = response.header("Content-Type") ?: "unknown"
            val contentLength = response.header("Content-Length") ?: "unknown"
            val acceptRanges = response.header("Accept-Ranges") ?: "unknown"
            response.close()
            
            Log.d(TAG, "WebDAV test response: statusCode=$statusCode, contentType=$contentType, contentLength=$contentLength, acceptRanges=$acceptRanges")
            
            if (statusCode == 401 || statusCode == 403) {
                Log.e(TAG, "WebDAV authentication failed: $statusCode")
                return Result.failure(Exception("认证失败 (HTTP $statusCode)"))
            }
            
            // 206 Partial Content 是正常的（因为我们用了Range请求）
            // 200 OK 也可能出现（如果服务器不支持Range）
            if (statusCode != 200 && statusCode != 206) {
                Log.e(TAG, "WebDAV request failed: $statusCode")
                return Result.failure(Exception("无法访问视频 (HTTP $statusCode)"))
            }
            
            Log.d(TAG, "WebDAV test successful, preparing playback info")
            
            Result.success(MediaPlaybackInfo(
                url = fullUrl,
                headers = createBasicAuthHeaders(library),
                subtitles = emptyList(),
                audioTracks = emptyList()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV URL resolution failed", e)
            Result.failure(e)
        }
    }

    /**
     * 创建Basic认证头
     */
    private fun createBasicAuthHeaders(library: ResourceLibrary): Map<String, String> {
        return if (library.username.isNotEmpty() && library.password.isNotEmpty()) {
            val auth = "${library.username}:${library.password}"
            val encoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
            mapOf("Authorization" to "Basic $encoded")
        } else {
            emptyMap()
        }
    }

    /**
     * 搜索同目录下的字幕文件
     */
    private suspend fun findSubtitles(videoPath: String, library: ResourceLibrary): List<SubtitleInfo> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<SubtitleInfo>()
        
        try {
            val relativeVideoPath = videoPath
                .removePrefix(library.name)
                .removePrefix("/")
                .trim('/')
            if (relativeVideoPath.isBlank() || !relativeVideoPath.contains("/")) {
                return@withContext subtitles
            }

            val videoDir = relativeVideoPath.substringBeforeLast("/")
            val client = com.lomen.tv.data.webdav.WebDavClient(library)
            val filesResult = client.listFiles(videoDir)
            if (filesResult.isFailure) {
                Log.w(TAG, "findSubtitles listFiles failed: ${filesResult.exceptionOrNull()?.message}")
                return@withContext subtitles
            }

            val subtitleFiles = filesResult.getOrNull().orEmpty()
                .filter { !it.isDirectory && isSubtitleFile(it.name) }
                .sortedBy { it.name.lowercase(Locale.ROOT) }

            subtitleFiles.forEachIndexed { idx, file ->
                val subtitleUrl = client.getFileUrl(file.path)
                val cleanName = file.name.substringBeforeLast('.')
                val normalizedLabel = cleanName.ifBlank { "外挂字幕 ${idx + 1}" }
                subtitles.add(
                    SubtitleInfo(
                        url = subtitleUrl,
                        language = detectSubtitleLanguage(file.name),
                        label = "[外挂] $normalizedLabel",
                        mimeType = subtitleMimeType(file.name)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find subtitles", e)
        }
        
        subtitles
    }

    private fun isSubtitleFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".srt") ||
            lower.endsWith(".ass") ||
            lower.endsWith(".ssa") ||
            lower.endsWith(".vtt") ||
            lower.endsWith(".sub")
    }

    private fun subtitleMimeType(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> "text/x-ssa"
            lower.endsWith(".vtt") -> "text/vtt"
            lower.endsWith(".sub") -> "application/octet-stream"
            else -> "application/x-subrip"
        }
    }

    private fun detectSubtitleLanguage(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("chs") || lower.contains("简中") || lower.contains("简体") -> "zh"
            lower.contains("cht") || lower.contains("繁中") || lower.contains("繁体") -> "zh-TW"
            lower.contains("zh") || lower.contains("chi") || lower.contains("中文") -> "zh"
            lower.contains("eng") || lower.contains("english") || lower.contains("en") -> "en"
            else -> "unknown"
        }
    }
}

/**
 * 媒体播放信息
 */
data class MediaPlaybackInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList()
)

/**
 * 字幕信息
 */
data class SubtitleInfo(
    val url: String,
    val language: String,
    val label: String,
    val mimeType: String = "application/x-subrip"
)

/**
 * 音轨信息
 */
data class AudioTrackInfo(
    val index: Int,
    val language: String,
    val label: String
)
