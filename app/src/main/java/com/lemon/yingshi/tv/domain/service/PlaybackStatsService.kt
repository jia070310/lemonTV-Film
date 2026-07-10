package com.lemon.yingshi.tv.domain.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStatsDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_stats")

/**
 * 播放统计服务
 */
@Singleton
class PlaybackStatsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.playbackStatsDataStore

    companion object {
        private val TOTAL_PLAYBACK_TIME_MS = longPreferencesKey("total_playback_time_ms")
    }

    val totalPlaybackTimeMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[TOTAL_PLAYBACK_TIME_MS] ?: 0L
    }

    suspend fun addPlaybackTimeMs(deltaMs: Long) {
        if (deltaMs <= 0L) return
        dataStore.edit { preferences ->
            val current = preferences[TOTAL_PLAYBACK_TIME_MS] ?: 0L
            preferences[TOTAL_PLAYBACK_TIME_MS] = current + deltaMs
        }
    }

    suspend fun clearTotalPlaybackTime() {
        dataStore.edit { preferences ->
            preferences[TOTAL_PLAYBACK_TIME_MS] = 0L
        }
    }
}