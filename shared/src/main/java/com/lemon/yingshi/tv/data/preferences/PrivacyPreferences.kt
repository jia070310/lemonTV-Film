package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacy_settings"
)

@Singleton
class PrivacyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.privacySettingsDataStore

    companion object {
        private val FILTER_KEYWORDS_KEY = stringPreferencesKey("filter_keywords")
        private val HIDDEN_TYPE_IDS_KEY = stringPreferencesKey("hidden_type_ids")

        /** 多个敏感关键词的分隔符（英文逗号） */
        const val KEYWORD_DELIMITER = ","

        const val KEYWORD_DELIMITER_HINT =
            "多个关键词用英文逗号（,）分隔，例如：伦理,福利,写真"
    }

    /** 原始关键词字符串（保留用户输入格式） */
    val filterKeywordsRaw: Flow<String> = dataStore.data.map { prefs ->
        prefs[FILTER_KEYWORDS_KEY].orEmpty()
    }

    /** 解析后的敏感关键词列表 */
    val filterKeywords: Flow<List<String>> = filterKeywordsRaw.map { parseKeywords(it) }

    /** 手动隐藏的分类 typeId */
    val hiddenTypeIds: Flow<Set<Int>> = dataStore.data.map { prefs ->
        parseTypeIds(prefs[HIDDEN_TYPE_IDS_KEY])
    }

    val privacyConfigChanges: Flow<Unit> = combine(filterKeywords, hiddenTypeIds) { _, _ -> }

    suspend fun saveFilterKeywords(raw: String) {
        dataStore.edit { prefs ->
            prefs[FILTER_KEYWORDS_KEY] = raw.trim()
        }
    }

    suspend fun saveHiddenTypeIds(typeIds: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[HIDDEN_TYPE_IDS_KEY] = typeIds.sorted().joinToString(",")
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(FILTER_KEYWORDS_KEY)
            prefs.remove(HIDDEN_TYPE_IDS_KEY)
        }
    }

    fun parseKeywords(raw: String): List<String> =
        raw.split(KEYWORD_DELIMITER, "、", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun parseTypeIds(raw: String?): Set<Int> =
        raw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            ?: emptySet()
}
