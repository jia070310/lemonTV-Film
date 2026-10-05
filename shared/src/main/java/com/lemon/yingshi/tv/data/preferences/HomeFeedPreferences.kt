package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.homeFeedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_feed_preferences"
)

@Singleton
class HomeFeedPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.homeFeedDataStore

    companion object {
        private val LAST_SUCCESSFUL_REFRESH_AT = longPreferencesKey("last_successful_refresh_at")
    }

    val lastSuccessfulRefreshAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[LAST_SUCCESSFUL_REFRESH_AT] ?: 0L
    }

    suspend fun getLastSuccessfulRefreshAt(): Long = lastSuccessfulRefreshAt.first()

    suspend fun setLastSuccessfulRefreshAt(timestampMs: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_SUCCESSFUL_REFRESH_AT] = timestampMs
        }
    }
}
