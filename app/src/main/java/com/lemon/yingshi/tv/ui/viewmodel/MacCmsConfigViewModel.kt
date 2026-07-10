package com.lemon.yingshi.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacCmsConfigViewModel @Inject constructor(
    private val macCmsRepository: MacCmsRepository,
    private val macCmsPreferences: MacCmsPreferences
) : ViewModel() {

    val serverUrl: StateFlow<String> = macCmsPreferences.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val lastTestTime: StateFlow<Long> = macCmsPreferences.lastTestTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastTestStatus: StateFlow<String> = macCmsPreferences.lastTestStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val siteName: StateFlow<String> = macCmsPreferences.siteName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<MacCmsConnectionResult?>(null)
    val testResult: StateFlow<MacCmsConnectionResult?> = _testResult.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            macCmsRepository.saveServerUrl(url)
            _saveMessage.value = MacCmsErrorMessages.settingsSaved()
            delay(4000)
            _saveMessage.value = null
        }
    }

    fun testConnection(url: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = macCmsRepository.testConnection(url)
            _isTesting.value = false
        }
    }
}
