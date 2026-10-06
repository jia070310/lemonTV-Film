package com.lemon.yingshi.tv.data.admin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.data.preferences.PrivacyPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.PrivacyHideCandidate
import com.lemon.yingshi.tv.util.LanAddresses
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacCmsAdminServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MacCmsRepository,
    private val preferences: MacCmsPreferences,
    private val privacyPreferences: PrivacyPreferences
) {
    private val gson = Gson()
    private val lock = Any()
    private var httpServer: InnerServer? = null

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    private var lastTestUrl: String = ""

    @Volatile
    private var lastTestResult: MacCmsConnectionResult? = null

    data class StartResult(
        val urls: List<String>,
        val port: Int
    )

    fun isRunning(): Boolean = synchronized(lock) { httpServer != null }

    fun start(): StartResult = synchronized(lock) {
        httpServer?.let {
            return StartResult(LanAddresses.listenUrls(port), port)
        }
        var lastError: Exception? = null
        for (candidate in PORT_START..PORT_END) {
            try {
                val server = InnerServer(candidate)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                httpServer = server
                port = candidate
                return StartResult(LanAddresses.listenUrls(candidate), candidate)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法启动资源管理服务")
    }

    fun stop() = synchronized(lock) {
        httpServer?.stop()
        httpServer = null
        port = 0
    }

    private inner class InnerServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            if (Method.OPTIONS == session.method) {
                return cors(
                    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                )
            }
            val uri = session.uri.substringBefore('?').trimEnd('/').ifEmpty { "/" }
            return try {
                when {
                    session.method == Method.GET && uri == "/" ->
                        cors(
                            NanoHTTPD.newFixedLengthResponse(
                                Response.Status.OK,
                                MIME_HTML_UTF8,
                                adminHtml()
                            )
                        )
                    session.method == Method.GET && uri == "/api/state" ->
                        json(stateJson())
                    session.method == Method.POST && uri == "/api/test" -> {
                        val url = readUrl(session)
                        val result = runBlocking {
                            val tested = repository.testConnection(url)
                            val normalized = preferences.normalizeBaseUrl(url)
                            lastTestUrl = normalized
                            lastTestResult = tested
                            if (preferences.getServerList().any { it.url == normalized }) {
                                repository.applyConnectionMeta(normalized, tested)
                            }
                            tested
                        }
                        json(gson.toJson(result))
                    }
                    session.method == Method.POST && uri == "/api/save" -> {
                        val body = readJsonBody(session)
                        val url = body.string("url")
                        runBlocking {
                            repository.saveServerUrl(url, body.string("name"))
                            val tested = lastTestResult
                            if (tested != null && lastTestUrl == preferences.normalizeBaseUrl(url)) {
                                repository.applyConnectionMeta(url, tested)
                            }
                        }
                        json(stateJson())
                    }
                    session.method == Method.POST && uri == "/api/select" -> {
                        runBlocking { repository.saveServerUrl(readUrl(session)) }
                        json(stateJson())
                    }
                    session.method == Method.POST && uri == "/api/delete" -> {
                        runBlocking { repository.removeServer(readUrl(session)) }
                        json(stateJson())
                    }
                    session.method == Method.GET && uri == "/api/privacy" ->
                        json(privacyJson(session.parms["url"].orEmpty(), includeCategories = true))
                    session.method == Method.POST && uri == "/api/privacy/keywords" -> {
                        val body = readJsonBody(session)
                        val url = body.string("url")
                        runBlocking {
                            privacyPreferences.saveFilterKeywords(url, body.string("keywords"))
                        }
                        json(privacyJson(url, includeCategories = false))
                    }
                    session.method == Method.POST && uri == "/api/privacy/hidden" -> {
                        val body = readJsonBody(session)
                        val url = body.string("url")
                        runBlocking {
                            privacyPreferences.saveHiddenTypeIds(url, body.intSet("hiddenTypeIds"))
                        }
                        json(privacyJson(url, includeCategories = false))
                    }
                    session.method == Method.POST && uri == "/api/privacy/clear" -> {
                        val url = readJsonBody(session).string("url")
                        runBlocking { privacyPreferences.clearAll(url) }
                        json(privacyJson(url, includeCategories = true))
                    }
                    else -> cors(
                        NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                    )
                }
            } catch (error: Exception) {
                json(
                    """{"success":false,"message":${gson.toJson(error.message ?: "请求失败")}}""",
                    Response.Status.INTERNAL_ERROR
                )
            }
        }
    }

    private fun adminHtml(): String =
        context.assets.open("maccms_admin.html").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun stateJson(): String = runBlocking {
        val current = repository.getServerUrl()
        val servers = preferences.getServerList().map { entry ->
            mapOf(
                "url" to entry.url,
                "name" to entry.name,
                "lastStatus" to entry.lastStatus,
                "version" to entry.version,
                "categoryCount" to entry.categoryCount,
                "summary" to entry.compactSummary(),
                "active" to (entry.url == current)
            )
        }
        gson.toJson(mapOf("current" to current, "servers" to servers))
    }

    private fun privacyJson(rawUrl: String, includeCategories: Boolean): String = runBlocking {
        privacyPreferences.prepare()
        val current = repository.getServerUrl()
        val url = preferences.normalizeBaseUrl(rawUrl.ifBlank { current })
        val profile = privacyPreferences.getProfile(url)
        val hidden = profile.hiddenTypeIds
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
        val payload = linkedMapOf<String, Any?>(
            "url" to url,
            "keywords" to profile.keywords,
            "hiddenTypeIds" to hidden.sorted(),
            "servers" to preferences.getServerList().map { entry ->
                mapOf(
                    "url" to entry.url,
                    "name" to entry.name,
                    "active" to (entry.url == current)
                )
            }
        )
        if (includeCategories) {
            payload["categories"] = if (url.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    val taxonomy = repository.fetchTaxonomy(forceRefresh = false, baseUrlOverride = url)
                    val candidates = taxonomy.privacyHideCandidates()
                    val effective = expandHiddenWithChildren(candidates, hidden)
                    candidates.map { candidate ->
                        val display = if (candidate.isSecondary && !candidate.parentLabel.isNullOrBlank()) {
                            "${candidate.parentLabel} · ${candidate.label}"
                        } else {
                            candidate.label
                        }
                        mapOf(
                            "typeId" to candidate.typeId,
                            "label" to candidate.label,
                            "displayName" to display,
                            "isSecondary" to candidate.isSecondary,
                            "parentTypeId" to candidate.parentTypeId,
                            "childTypeIds" to candidate.childTypeIds,
                            "hidden" to (candidate.typeId in effective)
                        )
                    }
                }.getOrElse { emptyList() }
            }
        }
        gson.toJson(payload)
    }

    private fun expandHiddenWithChildren(
        candidates: List<PrivacyHideCandidate>,
        hidden: Set<Int>
    ): Set<Int> {
        if (hidden.isEmpty()) return emptySet()
        val next = hidden.toMutableSet()
        candidates.forEach { candidate ->
            if (!candidate.isSecondary && candidate.typeId in next) {
                next.addAll(candidate.childTypeIds)
            }
        }
        return next
    }

    private fun readUrl(session: IHTTPSession): String = readJsonBody(session).string("url")

    private fun readJsonBody(session: IHTTPSession): JsonObject {
        val raw = readUtf8Body(session).trim()
        if (raw.startsWith("{")) {
            return JsonParser.parseString(raw).asJsonObject
        }
        val obj = JsonObject()
        session.parms.forEach { (key, value) ->
            if (!obj.has(key) && !value.isNullOrBlank()) obj.addProperty(key, value)
        }
        if (raw.isNotBlank() && !obj.has("url")) obj.addProperty("url", raw)
        return obj
    }

    /**
     * NanoHTTPD 2.3.1 在 Content-Type 未带 charset 时用 US-ASCII 解析 POST，
     * 中文会被替换成 U+FFFD。这里按 Content-Length 直接读原始字节再按 UTF-8 解码。
     */
    private fun readUtf8Body(session: IHTTPSession): String {
        val length = session.headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.toIntOrNull() ?: 0
        if (length > 0) {
            return String(readFully(session.inputStream, length), Charsets.UTF_8)
        }
        val files = HashMap<String, String>()
        session.parseBody(files)
        val post = files["postData"].orEmpty()
        if (post.isBlank()) return ""
        return decodePossiblyMojibake(post)
    }

    private fun decodePossiblyMojibake(raw: String): String {
        if (raw.any { it == '\uFFFD' }) return raw
        val asUtf8 = String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        return if (asUtf8.contains('\uFFFD')) raw else asUtf8
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == length) bytes else bytes.copyOf(offset)
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.asString.orEmpty()

    private fun JsonObject.intSet(key: String): Set<Int> {
        val el = get(key) ?: return emptySet()
        if (el.isJsonArray) {
            return el.asJsonArray.mapNotNull { it.asInt.takeIf { id -> id > 0 } }.toSet()
        }
        return el.asString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
    }

    private fun json(
        body: String,
        status: Response.Status = Response.Status.OK
    ): Response = cors(NanoHTTPD.newFixedLengthResponse(status, MIME_JSON, body))

    private fun cors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    companion object {
        private const val PORT_START = 18765
        private const val PORT_END = 18774
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val MIME_HTML_UTF8 = "text/html; charset=utf-8"
    }
}
