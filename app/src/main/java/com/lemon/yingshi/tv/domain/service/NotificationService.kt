package com.lemon.yingshi.tv.domain.service

import android.util.Log
import com.lemon.yingshi.tv.domain.model.Notification
import com.lemon.yingshi.tv.domain.model.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知服务
 * 负责从远程获取和管理通知
 */
@Singleton
class NotificationService @Inject constructor() {
    
    private val TAG = "NotificationService"
    
    // GitHub 加速器配置
    private val GITHUB_PROXY = "https://gh-proxy.org/"
    private val NOTIFICATION_URL_RAW = "https://raw.githubusercontent.com/jia070310/lemonTV-Film/refs/heads/main/notifications.json"
    
    // 使用加速器的完整 URL
    private val NOTIFICATION_URL = GITHUB_PROXY + NOTIFICATION_URL_RAW
    
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()
    
    private val _currentNotification = MutableStateFlow<Notification?>(null)
    val currentNotification: StateFlow<Notification?> = _currentNotification.asStateFlow()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 获取通知列表
     */
    suspend fun fetchNotifications() {
        withContext(Dispatchers.IO) {
            // 添加随机参数绕过缓存
            val timestamp = System.currentTimeMillis()
            val urlWithCache = "$NOTIFICATION_URL?t=$timestamp"
            
            // 先尝试使用加速器
            val success = tryFetchFromUrl(urlWithCache, "GitHub 加速器")
            
            // 如果加速器失败，尝试直接访问
            if (!success) {
                Log.w(TAG, "GitHub 加速器失败，尝试直接访问...")
                val rawUrlWithCache = "$NOTIFICATION_URL_RAW?t=$timestamp"
                tryFetchFromUrl(rawUrlWithCache, "原始 URL")
            }
        }
    }
    
    /**
     * 从指定 URL 获取通知
     */
    private fun tryFetchFromUrl(url: String, source: String): Boolean {
        return try {
            Log.d(TAG, "Fetching notifications from $source: $url")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000  // 增加到 30 秒
            connection.readTimeout = 30000     // 增加到 30 秒
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Response from $source: $response")
                
                val notificationResponse = json.decodeFromString<NotificationResponse>(response)
                val activeNotifications = notificationResponse.notifications.filter { it.shouldDisplay() }
                
                _notifications.value = activeNotifications
                
                // 更新当前显示的通知（选择第一个有效通知）
                _currentNotification.value = activeNotifications.firstOrNull()
                
                Log.d(TAG, "Successfully fetched ${activeNotifications.size} active notifications from $source")
                connection.disconnect()
                true
            } else {
                Log.e(TAG, "Failed to fetch notifications from $source: HTTP $responseCode")
                connection.disconnect()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching notifications from $source", e)
            false
        }
    }
    
    /**
     * 刷新通知（每次打开首页时调用）
     */
    suspend fun refreshNotifications() {
        fetchNotifications()
    }
}
