package com.lomen.tv.data.webdav

import android.util.Base64
import android.util.Log
import com.lomen.tv.data.remote.RemoteFileClient
import com.lomen.tv.domain.model.ResourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class WebDavFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: String = ""
)

class WebDavClient(
    private val library: ResourceLibrary,
    private val scanConcurrency: Int = 10
) : RemoteFileClient {
    companion object {
        private const val TAG = "WebDavClient"
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".3gp")
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var openListToken: String? = null
    private val openListTokenMutex = Mutex()
    @Volatile
    private var preferOpenListApi: Boolean = false

    /** 
     * 返回WebDAV基础URL（包含用户配置的完整路径）
     * 如用户填 /dav/电视电影/，则返回 http://host:port/dav/电视电影/
     */
    private fun getBaseUrl(): String {
        var path = library.path.trim()
        if (path.isEmpty() || path == "/") path = "/"
        else {
            if (!path.startsWith("/")) path = "/$path"
            if (!path.endsWith("/")) path = "$path/"
        }
        return "${library.protocol}://${library.host}:${library.port}$path"
    }

    private fun createAuthHeader(): String? {
        return if (library.username.isNotEmpty() && library.password.isNotEmpty()) {
            val auth = "${library.username}:${library.password}"
            "Basic ${Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)}"
        } else {
            null
        }
    }

    override suspend fun listFiles(relativePath: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            if (preferOpenListApi) {
                val openListResult = tryOpenListApi(relativePath)
                if (openListResult.isSuccess) return@withContext openListResult
            }

            // 构建完整URL（用户配置的完整路径 + 相对子路径）
            val baseUrl = getBaseUrl()
            val cleanPath = if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
            val rawUrl = baseUrl + cleanPath
            // 对路径部分进行 URL 编码（空格→%20，中文→%XX%XX...）
            val encodedUrl = rawUrl.split("/").joinToString("/") { part ->
                if (part.contains(":")) part // 保留协议部分 http:
                else java.net.URLEncoder.encode(part, "UTF-8").replace("+", "%20")
            }
            Log.d(TAG, "Listing files: $encodedUrl")
            
            // 首先尝试PROPFIND方法
            val requestBuilder = Request.Builder()
                .url(encodedUrl)
                .method("PROPFIND", "".toRequestBody("text/xml".toMediaType()))
                .header("Depth", "1")
            
            createAuthHeader()?.let {
                requestBuilder.header("Authorization", it)
            }
            
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful || response.code == 207) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "PROPFIND success code=${response.code}, body length=${responseBody.length}")
                val files = parsePropFindResponse(responseBody, relativePath)
                Log.d(TAG, "Found ${files.size} items in '$relativePath'")
                return@withContext Result.success(files)
            }
            
            // 如果PROPFIND失败，尝试GET请求（某些服务器支持目录列表）
            Log.w(TAG, "PROPFIND failed: ${response.code}, trying GET...")
            response.close()
            
            val getRequestBuilder = Request.Builder()
                .url(encodedUrl)
                .get()
            
            createAuthHeader()?.let {
                getRequestBuilder.header("Authorization", it)
            }
            
            val getRequest = getRequestBuilder.build()
            val getResponse = client.newCall(getRequest).execute()
            
            if (getResponse.isSuccessful) {
                val contentType = getResponse.header("Content-Type")
                val responseBody = getResponse.body?.string() ?: ""
                
                // 如果是HTML，尝试解析目录列表
                if (contentType?.contains("text/html") == true) {
                    // 检查是否是OpenList应用
                    if (responseBody.contains("OpenList")) {
                        Log.d(TAG, "Detected OpenList app, trying API...")
                        preferOpenListApi = true
                        getResponse.close()
                        // 尝试OpenList API
                        return@withContext tryOpenListApi(relativePath)
                    }
                    
                    val files = parseHtmlDirectoryListing(responseBody, relativePath, encodedUrl)
                    Log.d(TAG, "Parsed HTML directory, found ${files.size} items")
                    return@withContext Result.success(files)
                }
            }
            
            getResponse.close()
            Log.e(TAG, "Both PROPFIND and GET failed")
            Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files", e)
            Result.failure(e)
        }
    }
    
    private suspend fun tryOpenListApi(relativePath: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            // OpenList通常使用 /api/fs/list 端点
            val apiUrl = getBaseUrl() + "api/fs/list"
            Log.d(TAG, "Trying OpenList API: $apiUrl")
            
            val jsonBody = """
                {
                    "path": "$relativePath",
                    "page": 1,
                    "per_page": 1000
                }
            """.trimIndent()
            
            // 优先使用已缓存 token，避免每个目录都先触发 401
            val token = openListToken ?: getOrRefreshOpenListToken()
            val request = buildOpenListListRequest(apiUrl, jsonBody, token ?: createAuthHeader())
            val response = client.newCall(request).execute()
            
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "OpenList API response code: ${response.code}")
            Log.d(TAG, "OpenList API response: ${responseBody.take(500)}")
            
            // 检查HTTP状态码
            if (response.code == 401) {
                Log.w(TAG, "OpenList HTTP 401, requires authentication token")
                openListToken = null
                return@withContext tryOpenListLoginAndList(relativePath)
            }
            
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenList API failed: ${response.code}")
                return@withContext Result.failure(Exception("OpenList API HTTP ${response.code}"))
            }
            
            // 检查响应中的code字段（OpenList业务状态码）
            val jsonResponse = try {
                org.json.JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON response", e)
                return@withContext Result.failure(e)
            }
            
            val businessCode = jsonResponse.optInt("code", 200)
            if (businessCode == 401) {
                Log.w(TAG, "OpenList business code 401, requires authentication token")
                openListToken = null
                return@withContext tryOpenListLoginAndList(relativePath)
            }
            
            if (businessCode != 200) {
                Log.e(TAG, "OpenList API business error: $businessCode")
                return@withContext Result.failure(Exception("OpenList API error: $businessCode"))
            }
            
            val files = parseOpenListResponse(responseBody, relativePath)
            Log.d(TAG, "OpenList API found ${files.size} items")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "OpenList API error", e)
            Result.failure(e)
        }
    }
    
    private suspend fun tryOpenListLoginAndList(relativePath: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            val token = getOrRefreshOpenListToken(forceRefresh = true)
                ?: return@withContext Result.failure(Exception("Failed to get token"))
            
            // 使用token获取文件列表
            val apiUrl = getBaseUrl() + "api/fs/list"
            val jsonBody = """
                {
                    "path": "$relativePath",
                    "page": 1,
                    "per_page": 1000
                }
            """.trimIndent()
            
            val request = buildOpenListListRequest(apiUrl, jsonBody, token)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "List response: ${responseBody.take(500)}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenList list failed: ${response.code}")
                return@withContext Result.failure(Exception("List failed: HTTP ${response.code}"))
            }

            val jsonResponse = try {
                org.json.JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse list JSON response", e)
                return@withContext Result.failure(e)
            }
            if (jsonResponse.optInt("code", 200) == 401) {
                // token 失效时清空缓存，交给下一次请求重登
                openListToken = null
                return@withContext Result.failure(Exception("OpenList token invalidated"))
            }
            
            val files = parseOpenListResponse(responseBody, relativePath)
            Log.d(TAG, "OpenList API found ${files.size} items")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "OpenList login error", e)
            Result.failure(e)
        }
    }
    
    private fun parseOpenListToken(json: String): String? {
        return try {
            val jsonObject = org.json.JSONObject(json)
            if (jsonObject.optInt("code", -1) == 200) {
                val data = jsonObject.optJSONObject("data")
                data?.optString("token", null)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token", e)
            null
        }
    }

    private fun buildOpenListListRequest(
        apiUrl: String,
        jsonBody: String,
        authHeader: String?
    ): Request {
        return Request.Builder()
            .url(apiUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .apply {
                authHeader?.let { header("Authorization", it) }
            }
            .build()
    }

    private suspend fun getOrRefreshOpenListToken(forceRefresh: Boolean = false): String? = openListTokenMutex.withLock {
        if (!forceRefresh) {
            openListToken?.let { return it }
        }
        val loginUrl = getBaseUrl() + "api/auth/login"
        Log.d(TAG, "Trying OpenList login: $loginUrl")
        val loginBody = """
            {
                "username": "${library.username}",
                "password": "${library.password}"
            }
        """.trimIndent()
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginBody.toRequestBody("application/json".toMediaType()))
            .build()
        val loginResponse = client.newCall(loginRequest).execute()
        val loginResponseBody = loginResponse.body?.string() ?: ""
        Log.d(TAG, "Login response: ${loginResponseBody.take(500)}")
        if (!loginResponse.isSuccessful) {
            Log.e(TAG, "OpenList login failed: ${loginResponse.code}")
            return null
        }
        val token = parseOpenListToken(loginResponseBody)
        if (token.isNullOrBlank()) {
            Log.e(TAG, "Failed to get token from login response")
            return null
        }
        Log.d(TAG, "Got token: ${token.take(20)}...")
        openListToken = token
        token
    }
    
    private fun parseOpenListResponse(json: String, parentPath: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        
        try {
            val jsonObject = org.json.JSONObject(json)
            
            // 检查是否有data字段
            if (!jsonObject.has("data")) {
                Log.w(TAG, "OpenList response has no 'data' field")
                return files
            }
            
            val data = jsonObject.getJSONObject("data")
            
            // 检查是否有content字段
            if (!data.has("content")) {
                Log.w(TAG, "OpenList response has no 'content' field")
                return files
            }
            
            val content = data.getJSONArray("content")
            
            for (i in 0 until content.length()) {
                val item = content.getJSONObject(i)
                
                val name = item.optString("name", "")
                val path = item.optString("path", name)
                val isDir = item.optBoolean("is_dir", false)
                val size = item.optLong("size", 0L)
                
                if (name.isNotEmpty()) {
                    files.add(WebDavFile(
                        name = name,
                        path = if (parentPath.isEmpty()) name else "$parentPath/$name",
                        isDirectory = isDir,
                        size = size
                    ))
                    Log.d(TAG, "OpenList item: name=$name, isDir=$isDir")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenList response", e)
        }
        
        return files
    }

    private fun parseHtmlDirectoryListing(html: String, parentPath: String, baseUrl: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        
        Log.d(TAG, "Parsing HTML directory listing, HTML length: ${html.length}")
        // 打印更多HTML内容以便分析
        Log.d(TAG, "HTML content: $html")
        
        // 尝试多种HTML链接模式
        // OpenList可能使用不同的格式，尝试更宽松的模式
        val patterns = listOf(
            // 标准双引号
            Regex("<a[^>]+href=\"([^\"]+)\"[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE),
            // 单引号
            Regex("<a[^>]+href='([^']+)'[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE),
            // 无引号
            Regex("<a[^>]+href=([^\\s>]+)[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE),
            // 更宽松的模式 - 可能包含其他属性
            Regex("href=\"([^\"]+)\"[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE),
            Regex("href='([^']+)'[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE)
        )
        
        for ((index, pattern) in patterns.withIndex()) {
            val matches = pattern.findAll(html)
            val matchList = matches.toList()
            Log.d(TAG, "Pattern $index found ${matchList.size} raw matches")
            
            var count = 0
            for (match in matchList) {
                count++
                var href = match.groupValues[1].trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                    .removeSuffix("\"")
                    .removeSuffix("'")
                val name = match.groupValues[2].trim()
                
                Log.d(TAG, "Pattern $index - Match $count: href='$href', name='$name'")
                
                // 跳过父目录和当前目录
                if (href == "../" || href == "./" || href == "/" || 
                    name == "Parent Directory" || name == ".." || name.isEmpty()) continue
                
                // 跳过锚点链接和javascript
                if (href.startsWith("#") || href.startsWith("javascript:")) continue
                
                // 构建完整路径
                val path = if (parentPath.isEmpty()) href else "$parentPath/$href"
                val isDirectory = href.endsWith("/")
                
                files.add(WebDavFile(
                    name = name,
                    path = path,
                    isDirectory = isDirectory
                ))
            }
            
            Log.d(TAG, "Pattern $index: found $count valid links, total files: ${files.size}")
            if (files.isNotEmpty()) break
        }
        
        return files
    }

    override suspend fun listAllVideoFiles(
        onProgress: ((Int) -> Unit)?
    ): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            // 从用户配置的路径开始扫描（空字符串表示从配置的根目录开始）
            val startPath = ""
            Log.d(TAG, "Starting to scan all video files from: ${getBaseUrl()}")
            val allVideos = mutableListOf<WebDavFile>()
            val allVideosLock = Mutex()
            val enqueuedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val scannedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val dirsToScan = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val pendingDirs = java.util.concurrent.atomic.AtomicInteger(1)
            dirsToScan.offer(startPath)
            enqueuedDirs.add(startPath)

            // 并发扫描目录，限制并发数（可配置）
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
                                if (!scannedDirs.add(dirPath)) {
                                    continue
                                }

                                val filesResult = listFiles(dirPath)
                                if (filesResult.isFailure) {
                                    Log.e(
                                        TAG,
                                        "Failed to scan directory: $dirPath, error: ${filesResult.exceptionOrNull()?.message}"
                                    )
                                    continue
                                }

                                val files = filesResult.getOrNull().orEmpty()
                                val videos = mutableListOf<WebDavFile>()

                                for (file in files) {
                                    if (file.name == "." || file.name == "..") continue
                                    if (file.isDirectory) {
                                        val subDir = file.path
                                        if (enqueuedDirs.add(subDir)) {
                                            pendingDirs.incrementAndGet()
                                            dirsToScan.offer(subDir)
                                        }
                                    } else if (isVideoFile(file.name)) {
                                        videos.add(file)
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
            
            Log.d(TAG, "Scan complete, found ${allVideos.size} video files")
            Result.success(allVideos)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan videos", e)
            Result.failure(e)
        }
    }

    private fun isVideoFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun parsePropFindResponse(xml: String, parentPath: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        
        Log.d(TAG, "Parsing PROPFIND response, xml length=${xml.length}, parentPath='$parentPath'")
        if (xml.length < 2000) Log.d(TAG, "PROPFIND XML: $xml")
        
        try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true  // 开启命名空间支持，避免 D:href 等前缀干扰
            }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            
            var eventType = parser.eventType
            var currentHref = ""
            var currentDisplayName = ""
            var isCollection = false
            var currentSize = 0L
            var currentModified = ""
            // 记录哪些 tag 已经读取，防止重复赋值
            var inResponse = false
            
            val baseUrlStripped = getBaseUrl().removeSuffix("/")
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        // 开启 isNamespaceAware 后，parser.name 返回本地名（不含命名空间前缀）
                        when (parser.name.lowercase()) {
                            "response" -> inResponse = true
                            "href" -> if (inResponse) currentHref = parser.nextText().trim()
                            "displayname" -> if (inResponse) {
                                val t = parser.nextText().trim()
                                if (t.isNotEmpty()) currentDisplayName = t
                            }
                            "collection" -> if (inResponse) isCollection = true
                            "getcontentlength" -> if (inResponse) currentSize = parser.nextText().trim().toLongOrNull() ?: 0L
                            "getlastmodified" -> if (inResponse) currentModified = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.lowercase() == "response" && inResponse) {
                            inResponse = false
                            
                            // 解码 URL 编码的路径（如 %20 → 空格）
                            val decodedHref = try {
                                java.net.URLDecoder.decode(currentHref, "UTF-8")
                            } catch (e: Exception) { currentHref }
                            
                            // 提取相对路径
                            val relativePath = when {
                                decodedHref.startsWith(baseUrlStripped) ->
                                    decodedHref.removePrefix(baseUrlStripped)
                                decodedHref.startsWith("http://") || decodedHref.startsWith("https://") -> {
                                    // 绝对 URL，截取路径部分
                                    try { java.net.URL(decodedHref).path } catch (e: Exception) { decodedHref }
                                }
                                else -> decodedHref  // 已经是相对路径
                            }
                            
                            // 清理路径末尾的 /（目录）
                            val cleanPath = relativePath.trimEnd('/')
                            
                            val name = currentDisplayName.ifEmpty {
                                cleanPath.substringAfterLast("/").ifEmpty {
                                    decodedHref.substringAfterLast("/").trimEnd('/')
                                }
                            }
                            
                            Log.d(TAG, "Response: href='$currentHref' → path='$relativePath' name='$name' isDir=$isCollection size=$currentSize")
                            
                            // 跳过当前目录本身（路径与 parentPath 相同）
                            val parentClean = parentPath.trim('/').let { if (it.isEmpty()) "" else "/$it" }
                            val isCurrentDir = cleanPath == parentClean || cleanPath.isEmpty() ||
                                    cleanPath == "/${parentPath.trim('/')}"
                            
                            if (name.isNotEmpty() && !isCurrentDir) {
                                files.add(WebDavFile(
                                    name = name,
                                    path = relativePath,
                                    isDirectory = isCollection,
                                    size = currentSize,
                                    lastModified = currentModified
                                ))
                            }
                            
                            currentHref = ""
                            currentDisplayName = ""
                            isCollection = false
                            currentSize = 0L
                            currentModified = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PROPFIND response", e)
        }
        
        Log.d(TAG, "parsePropFindResponse done: found ${files.size} items")
        return files
    }

    override fun getFileUrl(relativePath: String): String {
        val cleanPath = if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
        return getBaseUrl() + cleanPath
    }

    // 获取目录下的封面图片URL
    override suspend fun getCoverImage(directoryPath: String): String? = withContext(Dispatchers.IO) {
        val coverNames = listOf("poster.jpg", "poster.png", "cover.jpg", "cover.png", 
                                "fanart.jpg", "fanart.png", "thumb.jpg", "thumb.png",
                                "default.jpg", "default.png", "folder.jpg", "folder.png")

        try {
            val filesResult = listFiles(directoryPath)
            if (filesResult.isSuccess) {
                val files = filesResult.getOrNull() ?: emptyList()
                for (coverName in coverNames) {
                    val coverFile = files.find { it.name.equals(coverName, ignoreCase = true) }
                    if (coverFile != null) {
                        Log.d(TAG, "Found cover image: ${coverFile.path}")
                        return@withContext getFileUrl(coverFile.path)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cover image for: $directoryPath", e)
        }
        null
    }

    /**
     * 读取文本文件内容
     * @param relativePath 文件的相对路径
     * @return 文件内容字符串，如果读取失败返回 null
     */
    override suspend fun readTextFile(relativePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = getFileUrl(relativePath)
            val request = Request.Builder()
                .url(url)
                .apply {
                    // 添加认证头
                    if (library.username.isNotBlank() && library.password.isNotBlank()) {
                        val credentials = "${library.username}:${library.password}"
                        val auth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                        header("Authorization", auth)
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Failed to read file: $relativePath, code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $relativePath", e)
            null
        }
    }

    /**
     * 检查文件是否存在
     * @param relativePath 文件的相对路径
     * @return 文件是否存在
     */
    override suspend fun fileExists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = getFileUrl(relativePath)
            val request = Request.Builder()
                .url(url)
                .method("HEAD", null)
                .apply {
                    if (library.username.isNotBlank() && library.password.isNotBlank()) {
                        val credentials = "${library.username}:${library.password}"
                        val auth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                        header("Authorization", auth)
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
