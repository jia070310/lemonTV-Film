package com.lemon.yingshi.tv.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.data.admin.MacCmsAdminServer
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
import com.lemon.yingshi.tv.data.repository.MacCmsErrorMessages
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.util.QrCodeBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MacCmsConfigViewModel @Inject constructor(
    val macCmsRepository: MacCmsRepository,
    private val macCmsPreferences: MacCmsPreferences,
    private val categorySortPreferences: MacCmsCategorySortPreferences,
    private val adminServer: MacCmsAdminServer
) : ViewModel() {

    val serverUrl: StateFlow<String> = macCmsPreferences.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val serverList: StateFlow<List<MacCmsServerEntry>> = macCmsPreferences.serverList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastTestTime: StateFlow<Long> = macCmsPreferences.lastTestTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastTestStatus: StateFlow<String> = macCmsPreferences.lastTestStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val siteName: StateFlow<String> = macCmsPreferences.siteName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maccmsVersion: StateFlow<String> = macCmsPreferences.maccmsVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val savedCategoryCount: StateFlow<Int> = macCmsPreferences.categoryCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedApiSource: StateFlow<String> = macCmsPreferences.apiSourceLabel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<MacCmsConnectionResult?>(null)
    val testResult: StateFlow<MacCmsConnectionResult?> = _testResult.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _adminUrls = MutableStateFlow<List<String>>(emptyList())
    val adminUrls: StateFlow<List<String>> = _adminUrls.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _adminError = MutableStateFlow<String?>(null)
    val adminError: StateFlow<String?> = _adminError.asStateFlow()

    fun saveServerUrl(url: String, name: String = "") {
        viewModelScope.launch {
            val oldUrl = macCmsRepository.getServerUrl()
            val normalizedNew = macCmsPreferences.normalizeBaseUrl(url)
            macCmsRepository.saveServerUrl(url, name)
            val tested = _testResult.value
            if (tested != null && macCmsPreferences.normalizeBaseUrl(lastTestedUrl) == normalizedNew) {
                macCmsRepository.applyConnectionMeta(normalizedNew, tested)
            }
            if (oldUrl != normalizedNew) {
                categorySortPreferences.clearHomeCategoryCache()
            }
            _saveMessage.value = MacCmsErrorMessages.settingsSaved()
            delay(4000)
            _saveMessage.value = null
        }
    }

    fun selectServer(url: String) {
        saveServerUrl(url)
    }

    fun removeServer(url: String) {
        viewModelScope.launch {
            val oldUrl = macCmsRepository.getServerUrl()
            macCmsRepository.removeServer(url)
            if (oldUrl != macCmsRepository.getServerUrl()) {
                categorySortPreferences.clearHomeCategoryCache()
            }
        }
    }

    private val _testingUrl = MutableStateFlow("")
    val testingUrl: StateFlow<String> = _testingUrl.asStateFlow()

    private var lastTestedUrl: String = ""

    fun testConnection(url: String) {
        viewModelScope.launch {
            val normalized = macCmsPreferences.normalizeBaseUrl(url)
            lastTestedUrl = url
            _testingUrl.value = normalized
            _isTesting.value = true
            val result = macCmsRepository.testConnection(url)
            _testResult.value = result
            if (serverList.value.any { it.url == normalized }) {
                macCmsRepository.applyConnectionMeta(normalized, result)
            }
            _isTesting.value = false
            _testingUrl.value = ""
        }
    }

    fun startAdminPortal() {
        viewModelScope.launch {
            _adminError.value = null
            try {
                val result = withContext(Dispatchers.IO) { adminServer.start() }
                _adminUrls.value = result.urls
                _qrBitmap.value = result.urls.firstOrNull()?.let { QrCodeBitmap.encode(it) }
                if (result.urls.isEmpty()) {
                    _adminError.value = "未找到局域网 IP，请确认设备已连接 Wi-Fi"
                }
            } catch (error: Exception) {
                _adminError.value = error.message ?: "无法启动扫码管理服务"
                _adminUrls.value = emptyList()
                _qrBitmap.value = null
            }
        }
    }

    fun stopAdminPortal() {
        adminServer.stop()
        _adminUrls.value = emptyList()
        _qrBitmap.value = null
        _adminError.value = null
    }

    override fun onCleared() {
        stopAdminPortal()
        super.onCleared()
    }
}
