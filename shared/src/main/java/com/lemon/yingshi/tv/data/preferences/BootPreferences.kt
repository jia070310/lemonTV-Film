package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Reuse legacy live_settings store so existing boot_startup values are preserved.
private val Context.bootDataStore: DataStore<Preferences> by preferencesDataStore(name = "live_settings")

@Singleton
class BootPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.bootDataStore

    companion object {
        private val BOOT_STARTUP = booleanPreferencesKey("boot_startup")
    }

    val bootStartup: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BOOT_STARTUP] ?: false
    }

    suspend fun setBootStartup(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BOOT_STARTUP] = enabled
        }
    }
}
