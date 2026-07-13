package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryScanDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_scan_settings")

@Singleton
class LibraryScanPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.libraryScanDataStore

    companion object {
        private val SCAN_CONCURRENCY = intPreferencesKey("scan_concurrency")
        const val DEFAULT_SCAN_CONCURRENCY = 10
        val AVAILABLE_CONCURRENCY_LEVELS = listOf(6, 10, 14)
    }

    val scanConcurrency: Flow<Int> = dataStore.data.map { preferences ->
        val configured = preferences[SCAN_CONCURRENCY] ?: DEFAULT_SCAN_CONCURRENCY
        configured.coerceIn(4, 20)
    }

    suspend fun setScanConcurrency(value: Int) {
        dataStore.edit { preferences ->
            preferences[SCAN_CONCURRENCY] = value.coerceIn(4, 20)
        }
    }
}

