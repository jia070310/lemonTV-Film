package com.lemon.yingshi.tv.data.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object MacCmsErrorMessages {
    const val REFRESH_HINT = "修改后返回首页，点击右上角刷新按钮重新加载"

    const val CONNECTION_FAILED =
        "无法连接资源服务器，请到「设置 → 资源管理」检查或更换服务器地址。$REFRESH_HINT"

    fun settingsSaved(): String = "配置已保存。$REFRESH_HINT"

    fun connectionSuccess(): String = "连接成功。$REFRESH_HINT"

    fun fromThrowable(throwable: Throwable?, fallback: String = "加载失败"): String =
        fromMessage(throwable?.message, fallback)

    fun fromMessage(message: String?, fallback: String = "加载失败"): String {
        if (message.isNullOrBlank()) return fallback
        if (isConnectionRelated(message)) return CONNECTION_FAILED
        return message
    }

    fun httpFailure(statusCode: Int): String =
        if (statusCode >= 500 || statusCode == 408) {
            CONNECTION_FAILED
        } else {
            "服务器响应异常（HTTP $statusCode），请到「设置 → 资源管理」检查服务器地址。$REFRESH_HINT"
        }

    fun wrapIOException(cause: Exception): IOException =
        IOException(fromThrowable(cause))

    private fun isConnectionRelated(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("failed to connect") ||
            lower.contains("unable to resolve host") ||
            lower.contains("unknown host") ||
            lower.contains("connection refused") ||
            lower.contains("connection reset") ||
            lower.contains("connection timed out") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("network is unreachable") ||
            lower.contains("no route to host") ||
            lower.contains("socket") ||
            lower.contains("ssl") ||
            lower.contains("certificate") ||
            lower.contains("eof") ||
            lower.contains("unreachable") ||
            message.contains("连接失败") ||
            message.contains("网络异常") ||
            message.contains("网络连接") ||
            message.startsWith("请求失败")
    }

    fun isConnectionException(throwable: Throwable): Boolean =
        throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is SSLException ||
            isConnectionRelated(throwable.message.orEmpty())
}
