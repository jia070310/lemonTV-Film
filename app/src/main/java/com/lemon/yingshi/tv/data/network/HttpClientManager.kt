package com.lemon.yingshi.tv.data.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * HTTP连接池管理器
 * 优化策略：
 * 1. 连接复用 - 使用Keep-Alive保持连接
 * 2. 超时优化 - 合理的连接和读取超时
 * 3. 并发控制 - 限制同时打开的连接数
 */
object HttpClientManager {
    private const val TAG = "HttpClientManager"
    private const val MAX_CONNECTIONS = 10 // 最大并发连接数
    private const val CONNECT_TIMEOUT = 5000 // 连接超时5秒
    private const val READ_TIMEOUT = 10000 // 读取超时10秒

    // 连接计数器
    private val activeConnections = ConcurrentHashMap<String, Int>()

    /**
     * 创建优化的HttpURLConnection
     */
    fun createConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        // 设置超时
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT

        // 启用Keep-Alive（连接复用）
        connection.setRequestProperty("Connection", "keep-alive")
        connection.setRequestProperty("Keep-Alive", "timeout=30, max=100")

        // 禁用缓存（对于API请求）
        connection.useCaches = false

        // 设置通用的请求头
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")

        return connection
    }

    /**
     * 创建HTTPS连接（支持TLS优化）
     */
    fun createHttpsConnection(urlString: String): HttpsURLConnection {
        val connection = createConnection(urlString) as HttpsURLConnection

        // 启用TLS会话复用
        connection.setRequestProperty("Connection", "keep-alive")

        return connection
    }

    /**
     * 等待连接槽位可用
     */
    suspend fun waitForConnectionSlot(host: String): Boolean {
        var attempts = 0
        while (getConnectionCount(host) >= MAX_CONNECTIONS) {
            if (attempts++ > 50) { // 最多等待5秒
                Log.w(TAG, "Connection pool full for $host")
                return false
            }
            kotlinx.coroutines.delay(100)
        }
        return true
    }

    /**
     * 记录连接开始
     */
    fun acquireConnection(host: String) {
        activeConnections[host] = getConnectionCount(host) + 1
    }

    /**
     * 记录连接结束
     */
    fun releaseConnection(host: String) {
        val count = getConnectionCount(host)
        if (count > 0) {
            activeConnections[host] = count - 1
        }
    }

    /**
     * 获取连接数（兼容API 21）
     */
    private fun getConnectionCount(host: String): Int {
        return activeConnections[host] ?: 0
    }

    /**
     * 获取当前活跃连接数
     */
    fun getActiveConnectionCount(): Int {
        return activeConnections.values.sum()
    }
}
