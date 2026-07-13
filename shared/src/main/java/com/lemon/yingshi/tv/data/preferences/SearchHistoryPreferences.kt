package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "search_history_preferences"
)

@Singleton
class SearchHistoryPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.searchHistoryDataStore

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("search_history")
        private const val MAX_ITEMS = 20
        private const val SEPARATOR = "\u0001"
    }

    val history: Flow<List<String>> = dataStore.data.map { prefs ->
        parseHistory(prefs[HISTORY_KEY])
    }

    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            val current = parseHistory(prefs[HISTORY_KEY]).toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            while (current.size > MAX_ITEMS) {
                current.removeAt(current.lastIndex)
            }
            prefs[HISTORY_KEY] = current.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(HISTORY_KEY) }
    }

    private fun parseHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotBlank() }
    }
}
