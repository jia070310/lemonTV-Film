package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

@Singleton
class PlayerSettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.playerSettingsDataStore

    companion object {
        // 记忆续播功能开关
        val REMEMBER_PLAYBACK_POSITION = booleanPreferencesKey("remember_playback_position")
        // 快进快退时长（秒）
        val SEEK_DURATION_SECONDS = intPreferencesKey("seek_duration_seconds")

        const val DEFAULT_SEEK_DURATION_SECONDS = 15
    }

    /**
     * 获取记忆续播功能开关状态
     */
    val rememberPlaybackPosition: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[REMEMBER_PLAYBACK_POSITION] ?: true
    }

    /**
     * 设置记忆续播功能开关状态
     */
    suspend fun setRememberPlaybackPosition(enabled: Boolean) {
        android.util.Log.d("PlayerSettingsPreferences", "Saving remember playback position: $enabled")
        dataStore.edit { preferences ->
            preferences[REMEMBER_PLAYBACK_POSITION] = enabled
        }
    }

    /**
     * 获取快进快退时长（秒）
     */
    val seekDurationSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SEEK_DURATION_SECONDS] ?: DEFAULT_SEEK_DURATION_SECONDS
    }

    /**
     * 设置快进快退时长（秒）
     */
    suspend fun setSeekDurationSeconds(seconds: Int) {
        android.util.Log.d("PlayerSettingsPreferences", "Saving seek duration seconds: $seconds")
        dataStore.edit { preferences ->
            preferences[SEEK_DURATION_SECONDS] = seconds
        }
    }
}
