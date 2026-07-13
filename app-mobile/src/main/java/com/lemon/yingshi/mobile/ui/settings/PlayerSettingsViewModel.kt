package com.lemon.yingshi.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.PlayerSettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val playerSettingsPreferences: PlayerSettingsPreferences
) : ViewModel() {

    val rememberPlaybackPosition: Flow<Boolean> = playerSettingsPreferences.rememberPlaybackPosition
    val seekDurationSeconds: Flow<Int> = playerSettingsPreferences.seekDurationSeconds

    fun setRememberPlaybackPosition(enabled: Boolean) {
        viewModelScope.launch {
            playerSettingsPreferences.setRememberPlaybackPosition(enabled)
        }
    }

    fun setSeekDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            playerSettingsPreferences.setSeekDurationSeconds(seconds)
        }
    }
}
