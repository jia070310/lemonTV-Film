package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.macCmsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "maccms_preferences"
)

@Singleton
class MacCmsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.macCmsDataStore
    private val gson = Gson()
    private val serverListType = object : TypeToken<List<MacCmsServerEntry>>() {}.type

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_LIST_KEY = stringPreferencesKey("server_list")
        private val LAST_TEST_TIME_KEY = longPreferencesKey("last_test_time")
        private val LAST_TEST_STATUS_KEY = stringPreferencesKey("last_test_status")
        private val SITE_NAME_KEY = stringPreferencesKey("site_name")
        private val MACCMS_VERSION_KEY = stringPreferencesKey("maccms_version")
        private val CATEGORY_COUNT_KEY = stringPreferencesKey("category_count")
        private val API_SOURCE_KEY = stringPreferencesKey("api_source")
    }

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: ""
    }

    val serverList: Flow<List<MacCmsServerEntry>> = dataStore.data.map { prefs ->
        mergeCurrentIntoList(
            stored = decodeList(prefs[SERVER_LIST_KEY]),
            current = prefs[SERVER_URL_KEY].orEmpty()
        )
    }

    val lastTestTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[LAST_TEST_TIME_KEY] ?: 0L
    }

    val lastTestStatus: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_TEST_STATUS_KEY] ?: ""
    }

    val siteName: Flow<String> = dataStore.data.map { prefs ->
        prefs[SITE_NAME_KEY] ?: ""
    }

    val maccmsVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[MACCMS_VERSION_KEY] ?: ""
    }

    val categoryCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CATEGORY_COUNT_KEY]?.toIntOrNull() ?: 0
    }

    val apiSourceLabel: Flow<String> = dataStore.data.map { prefs ->
        prefs[API_SOURCE_KEY] ?: ""
    }

    val isConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[SERVER_URL_KEY].isNullOrBlank()
    }

    suspend fun getServerList(): List<MacCmsServerEntry> = serverList.first()

    suspend fun saveServerUrl(url: String, name: String = "") {
        dataStore.edit { prefs ->
            val normalized = normalizeBaseUrl(url)
            if (normalized.isBlank()) {
                prefs.remove(SERVER_URL_KEY)
                return@edit
            }
            prefs[SERVER_URL_KEY] = normalized
            prefs[SERVER_LIST_KEY] = encodeList(
                upsert(
                    decodeList(prefs[SERVER_LIST_KEY]),
                    MacCmsServerEntry(url = normalized, name = name.trim())
                )
            )
        }
    }

    suspend fun removeServer(url: String) {
        dataStore.edit { prefs ->
            val normalized = normalizeBaseUrl(url)
            val remaining = decodeList(prefs[SERVER_LIST_KEY]).filter { it.url != normalized }
            prefs[SERVER_LIST_KEY] = encodeList(remaining)
            val current = prefs[SERVER_URL_KEY].orEmpty()
            if (current == normalized || current.isBlank()) {
                val next = remaining.firstOrNull()?.url
                if (next.isNullOrBlank()) {
                    prefs.remove(SERVER_URL_KEY)
                } else {
                    prefs[SERVER_URL_KEY] = next
                }
            }
        }
    }

    suspend fun updateServerMeta(
        url: String,
        name: String? = null,
        lastStatus: String? = null,
        version: String? = null,
        categoryCount: Int? = null,
        apiSource: String? = null
    ) {
        val normalized = normalizeBaseUrl(url)
        if (normalized.isBlank()) return
        dataStore.edit { prefs ->
            val current = decodeList(prefs[SERVER_LIST_KEY])
            val existing = current.firstOrNull { it.url == normalized } ?: return@edit
            val updated = existing.copy(
                name = name?.takeIf { it.isNotBlank() } ?: existing.name,
                lastStatus = lastStatus?.takeIf { it.isNotBlank() } ?: existing.lastStatus,
                version = version?.takeIf { it.isNotBlank() } ?: existing.version,
                categoryCount = categoryCount ?: existing.categoryCount,
                apiSource = apiSource?.takeIf { it.isNotBlank() } ?: existing.apiSource
            )
            prefs[SERVER_LIST_KEY] = encodeList(upsert(current, updated))
        }
    }

    suspend fun saveConnectionTestResult(
        status: String,
        siteName: String? = null,
        maccmsVersion: String? = null,
        categoryCount: Int? = null,
        apiSourceLabel: String? = null
    ) {
        dataStore.edit { prefs ->
            prefs[LAST_TEST_TIME_KEY] = System.currentTimeMillis()
            prefs[LAST_TEST_STATUS_KEY] = status
            if (!siteName.isNullOrBlank()) {
                prefs[SITE_NAME_KEY] = siteName
            }
            if (!maccmsVersion.isNullOrBlank()) {
                prefs[MACCMS_VERSION_KEY] = maccmsVersion
            }
            if (categoryCount != null) {
                prefs[CATEGORY_COUNT_KEY] = categoryCount.toString()
            }
            if (!apiSourceLabel.isNullOrBlank()) {
                prefs[API_SOURCE_KEY] = apiSourceLabel
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(SERVER_URL_KEY)
            prefs.remove(SERVER_LIST_KEY)
            prefs.remove(LAST_TEST_TIME_KEY)
            prefs.remove(LAST_TEST_STATUS_KEY)
            prefs.remove(SITE_NAME_KEY)
            prefs.remove(MACCMS_VERSION_KEY)
            prefs.remove(CATEGORY_COUNT_KEY)
            prefs.remove(API_SOURCE_KEY)
        }
    }

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isBlank()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    private fun mergeCurrentIntoList(
        stored: List<MacCmsServerEntry>,
        current: String
    ): List<MacCmsServerEntry> {
        if (current.isBlank()) return stored
        return if (stored.any { it.url == current }) stored else {
            listOf(MacCmsServerEntry(url = current)) + stored
        }
    }

    private fun upsert(
        list: List<MacCmsServerEntry>,
        entry: MacCmsServerEntry
    ): List<MacCmsServerEntry> {
        val index = list.indexOfFirst { it.url == entry.url }
        if (index < 0) return list + entry
        val merged = list[index].copy(
            name = entry.name.ifBlank { list[index].name },
            lastStatus = entry.lastStatus.ifBlank { list[index].lastStatus },
            version = entry.version.ifBlank { list[index].version },
            categoryCount = if (entry.categoryCount > 0) entry.categoryCount else list[index].categoryCount,
            apiSource = entry.apiSource.ifBlank { list[index].apiSource }
        )
        return list.toMutableList().also { it[index] = merged }
    }

    private fun decodeList(json: String?): List<MacCmsServerEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<MacCmsServerEntry>>(json, serverListType).orEmpty()
                .mapNotNull { entry ->
                    val url = normalizeBaseUrl(entry.url)
                    if (url.isBlank()) null else entry.copy(
                        url = url,
                        name = entry.name.replace("\uFFFD", "").trim()
                    )
                }
                .distinctBy { it.url }
        }.getOrDefault(emptyList())
    }

    private fun encodeList(list: List<MacCmsServerEntry>): String = gson.toJson(list)
}
