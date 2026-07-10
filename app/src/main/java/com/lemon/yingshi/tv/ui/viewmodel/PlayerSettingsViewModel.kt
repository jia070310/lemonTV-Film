package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.PlayerSettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val playerSettingsPreferences: PlayerSettingsPreferences
) : ViewModel() {

    /**
     * 记忆续播功能开关状态
     */
    val rememberPlaybackPosition: Flow<Boolean> = playerSettingsPreferences.rememberPlaybackPosition

    /**
     * 设置记忆续播功能开关状态
     */
    fun setRememberPlaybackPosition(enabled: Boolean) {
        android.util.Log.d("PlayerSettingsViewModel", "Setting remember playback position to: $enabled")
        viewModelScope.launch {
            playerSettingsPreferences.setRememberPlaybackPosition(enabled)
        }
    }

    /**
     * 快进快退时长（秒）
     */
    val seekDurationSeconds: Flow<Int> = playerSettingsPreferences.seekDurationSeconds

    /**
     * 设置快进快退时长（秒）
     */
    fun setSeekDurationSeconds(seconds: Int) {
        android.util.Log.d("PlayerSettingsViewModel", "Setting seek duration seconds to: $seconds")
        viewModelScope.launch {
            playerSettingsPreferences.setSeekDurationSeconds(seconds)
        }
    }
}
