package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
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

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { prefs ->
            val normalized = normalizeBaseUrl(url)
            if (normalized.isBlank()) {
                prefs.remove(SERVER_URL_KEY)
            } else {
                prefs[SERVER_URL_KEY] = normalized
            }
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
}
