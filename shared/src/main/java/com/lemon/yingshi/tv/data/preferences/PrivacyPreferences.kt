package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privacySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacy_settings"
)

data class PrivacyProfile(
    val keywords: String = "",
    val hiddenTypeIds: String = ""
)

@Singleton
class PrivacyPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val macCmsPreferences: MacCmsPreferences
) {
    private val dataStore = context.privacySettingsDataStore
    private val gson = Gson()
    private val profilesType = object : TypeToken<Map<String, PrivacyProfile>>() {}.type

    companion object {
        private val FILTER_KEYWORDS_KEY = stringPreferencesKey("filter_keywords")
        private val HIDDEN_TYPE_IDS_KEY = stringPreferencesKey("hidden_type_ids")
        private val PROFILES_KEY = stringPreferencesKey("privacy_profiles")
        private val MIGRATED_KEY = stringPreferencesKey("privacy_per_server_v1")

        const val KEYWORD_DELIMITER = ","

        const val KEYWORD_DELIMITER_HINT =
            "多个关键词用英文逗号（,）分隔，例如：伦理,福利,写真"
    }

    val currentServerUrl: Flow<String> = macCmsPreferences.serverUrl.map {
        macCmsPreferences.normalizeBaseUrl(it)
    }

    private val currentProfile: Flow<PrivacyProfile> = combine(
        currentServerUrl,
        dataStore.data
    ) { url, prefs -> resolveProfile(prefs, url) }

    /** 原始关键词字符串（保留用户输入格式），对应当前服务器 */
    val filterKeywordsRaw: Flow<String> = currentProfile.map { it.keywords }

    /** 解析后的敏感关键词列表 */
    val filterKeywords: Flow<List<String>> = filterKeywordsRaw.map { parseKeywords(it) }

    /** 手动隐藏的分类 typeId，对应当前服务器 */
    val hiddenTypeIds: Flow<Set<Int>> = currentProfile.map { parseTypeIds(it.hiddenTypeIds) }

    val privacyConfigChanges: Flow<Unit> = combine(
        currentServerUrl,
        filterKeywords,
        hiddenTypeIds
    ) { _, _, _ -> }

    /** 把旧的全局隐私设置迁到当时正在用的源 */
    suspend fun prepare() {
        migrateIfNeeded()
    }

    fun observeProfile(url: Flow<String>): Flow<PrivacyProfile> =
        combine(url, dataStore.data) { target, prefs -> resolveProfile(prefs, target) }

    suspend fun getProfile(url: String): PrivacyProfile {
        migrateIfNeeded()
        return resolveProfile(dataStore.data.first(), url)
    }

    suspend fun saveFilterKeywords(raw: String) {
        saveFilterKeywords(currentNormalizedUrl(), raw)
    }

    suspend fun saveFilterKeywords(url: String, raw: String) {
        migrateIfNeeded()
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        val trimmed = raw.trim()
        dataStore.edit { prefs ->
            if (normalized.isBlank()) {
                prefs[FILTER_KEYWORDS_KEY] = trimmed
                return@edit
            }
            val map = decodeMap(prefs).toMutableMap()
            val existing = map[normalized] ?: PrivacyProfile()
            map[normalized] = existing.copy(keywords = trimmed)
            prefs[PROFILES_KEY] = encodeMap(map)
        }
    }

    suspend fun saveHiddenTypeIds(typeIds: Set<Int>) {
        saveHiddenTypeIds(currentNormalizedUrl(), typeIds)
    }

    suspend fun saveHiddenTypeIds(url: String, typeIds: Set<Int>) {
        migrateIfNeeded()
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        val encoded = typeIds.sorted().joinToString(",")
        dataStore.edit { prefs ->
            if (normalized.isBlank()) {
                prefs[HIDDEN_TYPE_IDS_KEY] = encoded
                return@edit
            }
            val map = decodeMap(prefs).toMutableMap()
            val existing = map[normalized] ?: PrivacyProfile()
            map[normalized] = existing.copy(hiddenTypeIds = encoded)
            prefs[PROFILES_KEY] = encodeMap(map)
        }
    }

    suspend fun clearAll() {
        clearAll(currentNormalizedUrl())
    }

    suspend fun clearAll(url: String) {
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        dataStore.edit { prefs ->
            if (normalized.isBlank()) {
                prefs.remove(FILTER_KEYWORDS_KEY)
                prefs.remove(HIDDEN_TYPE_IDS_KEY)
                return@edit
            }
            val map = decodeMap(prefs).toMutableMap()
            map[normalized] = PrivacyProfile()
            prefs[PROFILES_KEY] = encodeMap(map)
        }
    }

    /** 新服务器写入列表时建立独立空配置 */
    suspend fun ensureProfile(url: String) {
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        if (normalized.isBlank()) return
        migrateIfNeeded()
        dataStore.edit { prefs ->
            val map = decodeMap(prefs).toMutableMap()
            if (map.containsKey(normalized)) return@edit
            map[normalized] = PrivacyProfile()
            prefs[PROFILES_KEY] = encodeMap(map)
        }
    }

    suspend fun removeProfile(url: String) {
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        if (normalized.isBlank()) return
        dataStore.edit { prefs ->
            val map = decodeMap(prefs).toMutableMap()
            if (map.remove(normalized) != null) {
                prefs[PROFILES_KEY] = encodeMap(map)
            }
        }
    }

    fun parseKeywords(raw: String): List<String> =
        raw.split(KEYWORD_DELIMITER, "、", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private suspend fun currentNormalizedUrl(): String =
        macCmsPreferences.normalizeBaseUrl(macCmsPreferences.serverUrl.first())

    private suspend fun migrateIfNeeded() {
        val current = currentNormalizedUrl()
        val servers = macCmsPreferences.getServerList().map { it.url }
        dataStore.edit { prefs ->
            if (prefs[MIGRATED_KEY] == "1") return@edit
            val map = decodeMap(prefs).toMutableMap()
            val legacyKeywords = prefs[FILTER_KEYWORDS_KEY].orEmpty()
            val legacyHidden = prefs[HIDDEN_TYPE_IDS_KEY].orEmpty()
            if (current.isNotBlank()) {
                map.putIfAbsent(
                    current,
                    PrivacyProfile(keywords = legacyKeywords, hiddenTypeIds = legacyHidden)
                )
            }
            servers.forEach { rawUrl ->
                val normalized = macCmsPreferences.normalizeBaseUrl(rawUrl)
                if (normalized.isNotBlank() && normalized != current) {
                    map.putIfAbsent(normalized, PrivacyProfile())
                }
            }
            prefs[PROFILES_KEY] = encodeMap(map)
            prefs[MIGRATED_KEY] = "1"
        }
    }

    private fun resolveProfile(prefs: Preferences, url: String): PrivacyProfile {
        val normalized = macCmsPreferences.normalizeBaseUrl(url)
        val map = decodeMap(prefs)
        if (normalized.isNotBlank()) {
            map[normalized]?.let { return it }
        }
        return PrivacyProfile()
    }

    private fun decodeMap(prefs: Preferences): Map<String, PrivacyProfile> =
        decodeMap(prefs[PROFILES_KEY])

    private fun decodeMap(raw: String?): Map<String, PrivacyProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, PrivacyProfile>>(raw, profilesType).orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun encodeMap(map: Map<String, PrivacyProfile>): String = gson.toJson(map)

    private fun parseTypeIds(raw: String?): Set<Int> =
        raw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            ?: emptySet()
}
