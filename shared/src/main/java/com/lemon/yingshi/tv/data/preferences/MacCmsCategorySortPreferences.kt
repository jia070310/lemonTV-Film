package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.macCmsCategorySortDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "maccms_category_sort_preferences"
)

@Singleton
class MacCmsCategorySortPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.macCmsCategorySortDataStore

    companion object {
        private val SORT_ORDER_KEY = stringPreferencesKey("section_order_keys")
        private val VISIBLE_SECTION_KEYS = stringPreferencesKey("visible_section_keys")
        private val VISIBILITY_CONFIGURED_KEY = booleanPreferencesKey("visibility_configured")
    }

    val sectionOrder: Flow<List<String>> = dataStore.data.map { prefs ->
        parseKeys(prefs[SORT_ORDER_KEY])
    }

    val visibleSectionKeys: Flow<Set<String>> = dataStore.data.map { prefs ->
        parseKeys(prefs[VISIBLE_SECTION_KEYS]).toSet()
    }

    val visibilityConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[VISIBILITY_CONFIGURED_KEY] == true
    }

    val homeConfigChanges: Flow<Unit> = combine(sectionOrder, visibleSectionKeys, visibilityConfigured) { _, _, _ -> }

    suspend fun saveCategoryConfig(sectionOrder: List<String>, visibleKeys: Set<String>) {
        dataStore.edit { prefs ->
            prefs[SORT_ORDER_KEY] = sectionOrder.joinToString(",")
            prefs[VISIBLE_SECTION_KEYS] = visibleKeys.joinToString(",")
            prefs[VISIBILITY_CONFIGURED_KEY] = true
        }
    }

    suspend fun clearHomeCategoryCache() {
        dataStore.edit { prefs ->
            prefs.remove(SORT_ORDER_KEY)
            prefs.remove(VISIBLE_SECTION_KEYS)
            prefs.remove(VISIBILITY_CONFIGURED_KEY)
        }
    }

    private fun parseKeys(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}
